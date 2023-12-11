package es.bsc.hp2c.edge.opalrt;

import es.bsc.hp2c.edge.types.Actuator;
import es.bsc.hp2c.edge.types.Sensor;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * The class OpalComm simulates written values for every sensor.
 */
public class OpalComm {

    private static final List<OpalSensor<?>> sensors = new ArrayList<>();
    private static final List<OpalActuator<?>> actuators = new ArrayList<>();
    private static float[] values = new float[25];
    private static int udpPORT;
    private static int tcpPORT;
    private static String udpIP;
    private static String tcpIP;
    private static int udpSensors;
    private static DatagramSocket udpSocket;
    private static ServerSocket tcpSocket;
    private static Socket actuateSocket;
    private static boolean useTCPActuators = false;
    private static boolean initialCall = true;

    /*
    * This method receives the global properties of the edge and, if it is the first call (first declared device),
    * initializes ports and ips for communications.
    *
    * @param jGlobalProperties JSONObject representing the global properties of the edge
    * */
    public static void init(JSONObject jGlobalProperties) {
        if (!initialCall){ return; }
        initialCall = false;
        new Thread (() -> {
            String localIP = System.getenv("LOCAL_IP");
            // Set up udp and tcp connections
            setupComms(jGlobalProperties, localIP);
            startUDPServer();
            startTCPServer();
        }).start();
    }

    private static void setupComms(JSONObject jGlobalProperties, String localIP){
        // Parse setup file
        JSONObject jComms = getjComms(jGlobalProperties);
        JSONObject jUDP = jComms.getJSONObject("udp");
        JSONObject jTCP= jComms.getJSONObject("tcp-sensors");

        if (jComms.has("tcp-actuators")){
            setUseTCPActuators(true);
            JSONObject jActuate = jComms.getJSONObject("tcp-actuators");
            TreeMap<Integer, ArrayList<String>> portsActuate = getPorts(jActuate);
            setActuateSocket(localIP, portsActuate.firstKey());
        } else{
            System.out.println("In order to enable actuations, 'tcp-actuators' must be declared within 'comms' section");
        }

        String ipUdp = getIp(jUDP);
        TreeMap<Integer, ArrayList<String>> portsUdp = getPorts(jUDP);
        String iptcp = getIp(jTCP);
        TreeMap<Integer, ArrayList<String>> portsTcp = getPorts(jTCP);

        // Set local communication parameters
        setUdpIp(ipUdp);
        setUdpPort(portsUdp.firstKey());
        setTcpIp(iptcp);
        setTcpPort(portsTcp.firstKey());
    }

    /**
     * Parse device's UDP port from JSON
     *
     * @param jProtocol JSON object (UDP section or TCP section)
     * @return map of pairs IP-Ports (each port can be linked to a list of sensors)
     */
    private static TreeMap<Integer, ArrayList<String>> getPorts(JSONObject jProtocol) {
        // Map of pairs IP - Ports (each port can be linked to a list of sensors)
        TreeMap<Integer, ArrayList<String>> portsDevicesMap = new TreeMap<>();
        JSONObject jPorts = jProtocol.getJSONObject("ports");

        List<String> keysList = new ArrayList<>();
        Iterator<String> keys = jPorts.keys();
        while(keys.hasNext()) {
            keysList.add(keys.next());
        }
        for (int i = 0; i < keysList.size(); ++i){
            String sPort = keysList.get(i);
            ArrayList<String> sDevices = new ArrayList<>();
            JSONArray jDevices = jPorts.getJSONArray(sPort);
            for (int j = 0; j < jDevices.length(); j++) {
                sDevices.add(jDevices.getString(i));
            }
            portsDevicesMap.put(Integer.parseInt(sPort), sDevices);
        }
        return portsDevicesMap;
    }

    private static String getIp(JSONObject jProtocol) {
        return jProtocol.getString("ip");
    }

    private static JSONObject getjComms(JSONObject jGlobProp) {
        setUdpSensors(jGlobProp.getInt("udp-sensors"));
        return jGlobProp.getJSONObject("comms");
    }

    private static void startTCPServer() {
        Thread TCPSensorsThread = new Thread(() -> {
            // Initialize UDP server socket to read measurements
            try {
                InetAddress serverAddress = InetAddress.getByName(tcpIP);
                tcpSocket = new ServerSocket(tcpPORT,0, serverAddress);
                System.out.println("\nTCP Server running on port: " + tcpPORT + "\n");

                Socket clientSocket = tcpSocket.accept();

                while (true) {
                    // Print time each iteration
                    LocalTime currentTime = LocalTime.now();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
                    String formattedTime = currentTime.format(formatter);
                    System.out.println("Current time: " + formattedTime);

                    DataInputStream inputStream = new DataInputStream(clientSocket.getInputStream());
                    int messageLength = inputStream.readInt();

                    byte[] buffer = new byte[messageLength * Float.BYTES];
                    inputStream.readFully(buffer);

                    char endChar = inputStream.readChar();
                    if (endChar != '\n') {
                        throw new IOException("End character not found.");
                    }

                    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
                    Float[] messageValues = new Float[messageLength];
                    for (int i = 0; i < messageLength; i++) {
                        messageValues[i] = byteBuffer.getFloat();
                    }

                    synchronized (sensors) {
                        int nIndexes = 0;
                        for (int i = 0; i < udpSensors; ++i) {
                            OpalSensor<?> sensor = sensors.get(i);
                            nIndexes += sensor.getIndexes().length;
                        }

                        for (int i = udpSensors; i < sensors.size(); ++i) {
                            OpalSensor<?> sensor = sensors.get(i);
                            int[] indexesLocal = Arrays.copyOf(sensor.getIndexes(), sensor.getIndexes().length);
                            Float[] sensedValues = new Float[indexesLocal.length];

                            for (int k = 0; k < indexesLocal.length; ++k) {
                                indexesLocal[k] -= nIndexes;
                                sensedValues[k] = messageValues[indexesLocal[k]];
                            }
                            sensor.sensed(sensedValues);
                            sensor.onRead();
                        }
                    }
                    System.out.println(); // Add empty line at the end of each measurement
                }
            } catch (IOException e) {
                System.err.println("Error initializing TCP server: " + e.getMessage());
            }
        });
        TCPSensorsThread.setName("TCPSensorsThread");
        TCPSensorsThread.start();
    }

    private static void startUDPServer() {
        Thread UDPSensorsThread = new Thread(() -> {
            // Initialize UDP server socket to read measurements
            try {
                InetAddress serverAddress = InetAddress.getByName(udpIP);
                if (udpPORT == 0) {
                    throw new SocketException();
                }
                udpSocket = new DatagramSocket(udpPORT, serverAddress);
            } catch (SocketException e) {
                System.err.println("Error initializing UDP socket at IP " + udpIP +" and port " + udpPORT);
                throw new RuntimeException(e);
            } catch (UnknownHostException e) {
                System.err.println("Unable to resolve " + udpIP + " for the specified host.");
                throw new RuntimeException(e);
            }
            System.out.println("\nUDP socket running on port: " + udpPORT + "\n");

            while (true) {
                // Print time each iteration
                LocalTime currentTime = LocalTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
                String formattedTime = currentTime.format(formatter);
                System.out.println("Current time: " + formattedTime);

                try {
                    byte[] buffer = new byte[values.length * Float.BYTES];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);
                    ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData());
                    for (int i = 0; i < values.length; i++) {
                        float receivedValue = byteBuffer.getFloat();
                        values[i] = receivedValue;
                    }

                    synchronized (sensors) {
                        for (int i = 0; i < udpSensors; ++i){
                            OpalSensor<?> sensor = sensors.get(i);
                            int[] indexes = sensor.getIndexes();
                            Float[] sensedValues = new Float[indexes.length];
                            for (int j = 0; j < indexes.length; ++j) {
                                sensedValues[j] = values[indexes[j]];
                            }
                            sensor.sensed(sensedValues);
                            sensor.onRead();
                        }

                    }

                    System.out.println(); // Add empty line at the end of each measurement
                } catch (Exception e) {
                    System.err.println("Error receiving UDP message: " + e.getMessage());
                }
            }
        });
        UDPSensorsThread.setName("UDPSensorsThread");
        UDPSensorsThread.start();
    }

    public static void commitActuation(OpalActuator<?> actuator, Float[] values) throws IOException {
        if (useTCPActuators){
            int nIndexes = 0;
            for (OpalActuator<?> opalActuator : actuators) {
                int nIndexesDevice = opalActuator.getIndexes().length;
                nIndexes += nIndexesDevice;
            }
            ByteBuffer byteBuffer = ByteBuffer.allocate(nIndexes * Float.BYTES);

            // Scale actuators indexes (ignore udp sensors)
            int[] indexesLocal = Arrays.copyOf(actuator.getIndexes(), actuator.getIndexes().length);
            int nIndexesUdp = 0;
            for (int i = 0; i < udpSensors; ++i) {
                OpalSensor<?> sensor = sensors.get(i);
                nIndexesUdp += sensor.getIndexes().length;
            }

            for (int i = 0; i < indexesLocal.length; ++i){
                indexesLocal[i] -= nIndexesUdp;
            }

            // For every float in bytebuffer, if index not in the list assign float minimum value, else assign proper value
            for (int i = 0; i < nIndexes; ++i){
                // Check if current index is in indexes
                boolean found = false;
                int index = 0; // index in values
                for (int j : indexesLocal) {
                    if (j == i) {
                        found = true;
                        break;
                    }
                    index += 1;
                }
                if (found){ byteBuffer.putFloat(values[index]); }
                else { byteBuffer.putFloat(Float.NEGATIVE_INFINITY); }
            }

            DataOutputStream outputStream = new DataOutputStream(actuateSocket.getOutputStream());
            byte[] buffer = byteBuffer.array();
            outputStream.write(buffer);
            System.out.println("Packet sent with pairs value/index: ");
            for (int i = 0; i < values.length; ++i){
                System.out.print(values[i]);
                System.out.print("/" + indexesLocal[i]);
                System.out.println("");
            }
        }
    }

    /**
     * Register device into the list of sensors.
     *
     * @param sensor Sensor to register
     */
    public static void registerSensor(OpalSensor<?> sensor) {
        synchronized (sensors) {
            sensors.add(sensor);
        }
    }

    public static void registerActuator(OpalActuator<?> actuator) {
        synchronized (actuators) {
            actuators.add(actuator);
        }
    }

    public static void setUdpPort(int port) { udpPORT = port; }

    public static void setTcpPort(int port) { tcpPORT = port; }

    public static void setUdpSensors(int inputUDPSensors) { udpSensors = inputUDPSensors; }

    public static void setUdpIp(String udpIp) { udpIP = udpIp; }

    public static void setTcpIp(String tcpIp) { tcpIP = tcpIp; }

    public static void setUseTCPActuators(boolean b) { useTCPActuators = b; }

    public static void setActuateSocket(String ip, int port){
        try {
            actuateSocket = new Socket(ip, port);
            System.out.println("Connected to server " + ip + " through port " + port);
        } catch (Exception e) {
            System.out.println("Failed to connect to server " + ip + " through port " + port);
            System.err.println("Error connecting to TCP server: " + e.getMessage());
        }
    }

    protected interface OpalSensor<V> extends Sensor<Float[], V> {
        int[] getIndexes();
    }

    protected interface OpalActuator<V> extends Actuator<V> {
        int[] getIndexes();
    }
}
