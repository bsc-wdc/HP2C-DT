package es.bsc.hp2c.edge.opalrt;

import es.bsc.hp2c.edge.types.Actuator;
import es.bsc.hp2c.edge.types.Sensor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

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
    private static int UDP_PORT;
    private static int TCP_PORT;
    private static String UDP_IP;
    private static String TCP_IP;
    private static int UDP_SENSORS;
    private static DatagramSocket udpSocket;
    private static ServerSocket tcpSocket;
    private static Socket actuateSocket;
    private static boolean useTCPActuators = false;
    private static boolean initialCall = true;

    public static void init(JSONObject jGlobalProperties) {
        if (!initialCall){ return; }
        initialCall = false;
        new Thread (() -> {
            String localIP = System.getenv("LOCAL_IP");
            // Set up udp and tcp connections
            setupComms(jGlobalProperties, localIP);
            startUDPServer();
            startTCPServer();}).start();
    }

    private static void setupComms(JSONObject jGlobalProperties, String localIP){
        // Parse setup file
        JSONObject jComms = getjComms(jGlobalProperties);
        JSONObject jUDP = jComms.getJSONObject("udp");
        JSONObject jTCP= jComms.getJSONObject("tcp_sensors");

        if (jComms.has("tcp_actuators")){
            OpalComm.setUseTCPActuators();
            JSONObject jActuate = jComms.getJSONObject("tcp_actuators");
            TreeMap<Integer, ArrayList<String>> ports_Actuate = getPorts(jActuate);
            OpalComm.setActuateSocket(localIP, ports_Actuate.firstKey());
        } else{
            System.out.println("In order to enable actuations, 'tcp_actuators' must be declared within 'comms' section");
        }

        String ip_udp = getIp(jUDP);
        TreeMap<Integer, ArrayList<String>> ports_udp = getPorts(jUDP);
        String ip_tcp = getIp(jTCP);
        TreeMap<Integer, ArrayList<String>> ports_tcp = getPorts(jTCP);

        // Set local communication parameters
        OpalComm.setUDP_IP(ip_udp);
        OpalComm.setUDP_PORT(ports_udp.firstKey());
        OpalComm.setTCP_IP(ip_tcp);
        OpalComm.setTCP_PORT(ports_tcp.firstKey());
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
        OpalComm.setUDP_SENSORS(jGlobProp.getInt("udp-sensors-indexes"));
        return jGlobProp.getJSONObject("comms");
    }

    private static void startTCPServer() {
        Thread t_tcp = new Thread() {
            public void run() {
                // Initialize UDP server socket to read measurements
                try {
                    InetAddress serverAddress = InetAddress.getByName(TCP_IP);
                    tcpSocket = new ServerSocket(TCP_PORT,0, serverAddress);
                    System.out.println("\nTCP Server running on port: " + TCP_PORT + "\n");

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

                        synchronized (OpalComm.sensors) {
                            for (int i = UDP_SENSORS; i < sensors.size(); ++i) {
                                OpalSensor<?> sensor = sensors.get(i);
                                int[] indexes_local = Arrays.copyOf(sensor.getIndexes(), sensor.getIndexes().length);
                                Float[] sensedValues = new Float[indexes_local.length];
                                for (int k = 0; k < indexes_local.length; ++k) {
                                    indexes_local[k] -= UDP_SENSORS;
                                    sensedValues[k] = messageValues[indexes_local[k]];
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
            }
        };
        t_tcp.setName("Actuators");
        t_tcp.start();
    }

    private static void startUDPServer() {
        Thread t_udp = new Thread() {
            public void run() {
                // Initialize UDP server socket to read measurements
                try {
                    InetAddress serverAddress = InetAddress.getByName(UDP_IP);
                    if (UDP_PORT == 0) {
                        throw new SocketException();
                    }
                    udpSocket = new DatagramSocket(UDP_PORT, serverAddress);
                } catch (SocketException e) {
                    System.err.println("Error initializing UDP socket at IP " + UDP_IP +" and port " +  UDP_PORT);
                    throw new RuntimeException(e);
                } catch (UnknownHostException e) {
                    System.err.println("Unable to resolve " + UDP_IP + " for the specified host.");
                    throw new RuntimeException(e);
                }
                System.out.println("\nUDP socket running on port: " + UDP_PORT + "\n");

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

                        synchronized (OpalComm.sensors) {
                            int checkedIndexes = 0;
                            int i = 0;
                            while (checkedIndexes < UDP_SENSORS){
                                OpalSensor<?> sensor = sensors.get(i);
                                int[] indexes = sensor.getIndexes();
                                checkedIndexes += indexes.length;
                                Float[] sensedValues = new Float[indexes.length];
                                for (int j = 0; j < indexes.length; ++j) {
                                    sensedValues[j] = values[indexes[j]];
                                }
                                sensor.sensed(sensedValues);
                                sensor.onRead();
                                i += 1;
                            }
                        }

                        System.out.println(); // Add empty line at the end of each measurement
                    } catch (Exception e) {
                        System.err.println("Error receiving UDP message: " + e.getMessage());
                    }
                }
            }
        };
        t_udp.setName("OpalComm");
        t_udp.start();
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
            int[] indexes_local = Arrays.copyOf(actuator.getIndexes(), actuator.getIndexes().length);
            for (int i = 0; i < indexes_local.length; ++i){
                indexes_local[i] -= UDP_SENSORS;
            }

            // For every float in bytebuffer, if index not in the list assign float minimum value, else assign proper value
            for (int i = 0; i < nIndexes; ++i){
                // Check if current index is in indexes
                boolean found = false;
                int index = 0; // index in values
                for (int j : indexes_local) {
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
                System.out.print("/" + indexes_local[i]);
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
        synchronized (OpalComm.sensors) {
            sensors.add(sensor);
        }
    }

    public static void registerActuator(OpalActuator<?> actuator) {
        synchronized (OpalComm.actuators) {
            actuators.add(actuator);
        }
    }

    public static void setUDP_PORT(int port) { UDP_PORT = port; }

    public static void setTCP_PORT(int port) { TCP_PORT = port; }

    public static void setUDP_SENSORS(int udp_sensors) { UDP_SENSORS = udp_sensors; }

    public static void setUDP_IP(String udp_ip) { UDP_IP = udp_ip; }

    public static void setTCP_IP(String tcp_ip) { TCP_IP = tcp_ip; }

    public static void setUseTCPActuators() { useTCPActuators = true; }

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
