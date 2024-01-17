package es.bsc.hp2c.edge.opalrt;

import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Sensor;
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

    private static final List<OpalSensor<?>> udpSensorsList = new ArrayList<>();
    private static final List<OpalSensor<?>> tcpSensorsList = new ArrayList<>();
    private static final List<OpalActuator<?>> actuatorsList = new ArrayList<>();
    private static int maxUdpIndexesLength = 25;
    private static int udpPORT;
    private static int tcpPORT;
    private static int actuationPORT;
    private static String udpIP;
    private static String tcpIP;
    private static String actuationIP;
    private static DatagramSocket udpSocket;
    private static ServerSocket tcpSocket;
    private static Socket actuationSocket;
    private static boolean useTCPActuators = false;
    private static boolean initialCall = true;
    private static boolean loadedDevices = false;
    private static boolean connectedOnce = false;
    private static HashMap<OpalActuator<?>, Float[]> missedValues = new HashMap<>();

    /*
    * This method receives the global properties of the edge and, if it is the first call (first declared device),
    * initializes ports and ips for communications.
    *
    * @param jGlobalProperties JSONObject representing the global properties of the edge
    * */
    public static void init(JSONObject jGlobalProperties) {
        if (!initialCall){ return; }
        initialCall = false;
        // Set up udp and tcp connections
        setupComms(jGlobalProperties);
        startUDPServer();
        startTCPServer();
    }

    static {
        Thread verifyIndexes = new Thread(() -> {
            try {
                waitForDevicesLoaded();
                verifyIndexesActuators(actuatorsList);
                verifyIndexesSensors(udpSensorsList);
                verifyIndexesSensors(tcpSensorsList);
            } catch (Device.DeviceInstantiationException e) {
                System.err.println(e.getMessage());
            }
        });
        verifyIndexes.start();
    }

    private static void waitForDevicesLoaded() {
        synchronized (OpalComm.class) {
            while (!loadedDevices) {
                try {
                    OpalComm.class.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Set up UDP and TCP communications
     *
     * @param jGlobalProperties JSONObject containing the setup file
     */
    private static void setupComms(JSONObject jGlobalProperties){
        // Parse setup file
        JSONObject jComms = getjComms(jGlobalProperties);
        JSONObject jUDP = jComms.getJSONObject("opal-udp");
        JSONObject jTCP= jComms.getJSONObject("opal-tcp");

        if (jTCP.has("actuators")){
            setUseTCPActuators(true);
            JSONObject jTcpActuators = jTCP.getJSONObject("actuators");
            int portActuate = getPort(jTcpActuators);
            Object ipObject = jTcpActuators.get("ip");
            new Thread(() -> {
                setActuateSocket(ipObject, portActuate);
            }).start();
        } else{
            System.out.println("In order to enable actuations, 'actuators' must be declared within 'opal-tcp' section");
        }

        JSONObject jUdpSensors = jUDP.getJSONObject("sensors");
        String ipUdpSensors = getIp(jUdpSensors);
        int portUdpSensors = getPort(jUdpSensors);

        JSONObject jTcpSensors = jTCP.getJSONObject("sensors");
        String ipTcpSensors = getIp(jTcpSensors);
        int portTcpSensors = getPort(jTcpSensors);

        // Set local communication parameters
        setUdpIp(ipUdpSensors);
        setUdpPort(portUdpSensors);
        setTcpIp(ipTcpSensors);
        setTcpPort(portTcpSensors);
    }

    /**
     * Verifies indexes within a list of OpalActuators
     *
     * @param devices Devices involved
     * @throws Device.DeviceInstantiationException
     */
    public static void verifyIndexesActuators(List<OpalActuator<?>> devices) throws Device.DeviceInstantiationException {
        HashSet<Integer> indexesSet = new HashSet<>();
        for (OpalActuator<?> device : devices) {
            int[] indexes = device.getIndexes();
            // Check for repeated indices
            for (int index : indexes) {
                if (!indexesSet.add(index)) {
                    throw new Device.DeviceInstantiationException("Repeated index found: " + index);
                }
            }
        }
        // Check if indices are consecutive
        int minIndex = indexesSet.stream().mapToInt(Integer::intValue).min().orElse(-1);
        int maxIndex = indexesSet.stream().mapToInt(Integer::intValue).max().orElse(-1);
        for (int i = minIndex; i <= maxIndex; i++) {
            if (!indexesSet.contains(i)) {
                throw new Device.DeviceInstantiationException("Missing index found: " + i);
            }
        }
    }

    /**
     * Verifies indexes within a list of OpalSensors
     *
     * @param devices Devices involved
     * @throws Device.DeviceInstantiationException
     */
    public static void verifyIndexesSensors(List<OpalSensor<?>> devices) throws Device.DeviceInstantiationException {
        HashSet<Integer> indexesSet = new HashSet<>();
        for (OpalSensor<?> device : devices) {
            int[] indexes = device.getIndexes();
            // Check for repeated indices
            for (int index : indexes) {
                if (!indexesSet.add(index)) {
                    throw new Device.DeviceInstantiationException("Repeated index found: " + index);
                }
            }
        }
        // Check if indices are consecutive
        int minIndex = indexesSet.stream().mapToInt(Integer::intValue).min().orElse(-1);
        int maxIndex = indexesSet.stream().mapToInt(Integer::intValue).max().orElse(-1);
        for (int i = minIndex; i <= maxIndex; i++) {
            if (!indexesSet.contains(i)) {
                throw new Device.DeviceInstantiationException("Missing index found: " + i);
            }
        }
    }

    /**
     * Parse device's UDP port from JSON
     *
     * @param jProtocol JSON object (UDP section or TCP section)
     * @return port
     */
    private static int getPort(JSONObject jProtocol) {
        return jProtocol.getInt("port");
    }

    private static String getIp(JSONObject jProtocol) {
        return jProtocol.getString("ip");
    }

    private static JSONObject getjComms(JSONObject jGlobProp) {
        return jGlobProp.getJSONObject("comms");
    }


    private static void startTCPServer() {
        Thread TCPSensorsThread = new Thread(() -> {
            while(true){
                // Initialize UDP server socket to read measurements
                InetAddress serverAddress = null;
                Socket clientSocket = null;
                try {
                    serverAddress = InetAddress.getByName(tcpIP);
                    tcpSocket = new ServerSocket(tcpPORT,0, serverAddress);
                    tcpSocket.setReuseAddress(true);
                    System.out.println("\nTCP Server running on port: " + tcpPORT + "\n");
                    clientSocket = tcpSocket.accept();
                    processTCPConnection(clientSocket);
                } catch (IOException e) {
                    System.err.println("Error starting TCP server: " + e.getMessage());
                    throw new RuntimeException(e);
                } finally {
                    try {
                        if (clientSocket != null && !clientSocket.isClosed()) {
                            tcpSocket.close();
                        }
                    } catch (IOException ex) {
                        System.err.println("Error closing client socket: " + ex.getMessage());
                    }
                }
            }
        });
        TCPSensorsThread.setName("TCPSensorsThread");
        TCPSensorsThread.start();
    }

    /*
     * This method is in charge of reading the values of the TCP sensors from Opal. To do so, we check start of message
     * with a readInt() that checks the expected length (number of incoming floats) of the message, then get the buffer
     * with that messageLength, and lastly the end of line (EoL) character, otherwise throwing an error. For example, a
     * TCP message could be of the form: "3, Switch[0], Switch[1], Switch[2], "\n", where "3" is the number of floats
     * and "\n" is the EoL character.
     *
     * We also use an attribute called udpSensors to know the number of udp indexes that we need to substract from these
     * tcp sensors in order to start reading from index 0. For example, we have two single-phase udp sensors. udpSensors
     * will take value 2, and therefore, nIndexesUDP will be 2. If the Switch indexes are [2, 3, 4] in the setup file,
     * they are subtracted nIndexesUDP=2 to convert them to [0, 1, 2] and correctly map the TCP message.
     *
     * */
    private static void processTCPConnection(Socket clientSocket) {
        try {
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

                synchronized (tcpSensorsList) {
                    for (OpalSensor<?> sensor : tcpSensorsList) {
                        int[] indexes = sensor.getIndexes();
                        Float[] sensedValues = new Float[indexes.length];
                        for (int j = 0; j < indexes.length; ++j) {
                            sensedValues[j] = messageValues[indexes[j]];
                        }
                        sensor.sensed(sensedValues);
                        sensor.onRead();
                    }
                }
                System.out.println(); // Add empty line at the end of each measurement
            }
        } catch (IOException e){
            System.err.println("Error reading messages though TCP: " + e.getMessage());
        }
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
                    byte[] buffer = new byte[maxUdpIndexesLength * Float.BYTES];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);
                    ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData());
                    Float[] messageValues = new Float[maxUdpIndexesLength];
                    for (int i = 0; i < messageValues.length; i++) {
                        float receivedValue = byteBuffer.getFloat();
                        messageValues[i] = receivedValue;
                    }

                    synchronized (udpSensorsList) {
                        for (OpalSensor<?> sensor : udpSensorsList){
                            int[] indexes = sensor.getIndexes();
                            Float[] sensedValues = new Float[indexes.length];
                            for (int j = 0; j < indexes.length; ++j) {
                                sensedValues[j] = messageValues[indexes[j]];
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

    public static void commitActuation(OpalActuator<?> actuator, Float[] values) {
        if (useTCPActuators){
            try{
                // count the number of floats to be sended
                int nIndexes = 0;
                List<String> labels = new ArrayList<>();
                for (OpalActuator<?> opalActuator : actuatorsList) {
                    int nIndexesDevice = opalActuator.getIndexes().length;
                    labels.add(((Device) opalActuator).getLabel());
                    nIndexes += nIndexesDevice;
                }
                for (OpalSensor<?> opalSensor : tcpSensorsList) {
                    if (!(labels.contains(((Device) opalSensor).getLabel()))) {
                        int nIndexesDevice = opalSensor.getIndexes().length;
                        nIndexes += nIndexesDevice;
                    }
                }
                ByteBuffer byteBuffer = ByteBuffer.allocate(nIndexes * Float.BYTES);

                // Scale actuators indexes (ignore udp sensors)
                int[] indexesLocal = Arrays.copyOf(actuator.getIndexes(), actuator.getIndexes().length);

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

                DataOutputStream outputStream = new DataOutputStream(actuationSocket.getOutputStream());
                byte[] buffer = byteBuffer.array();
                outputStream.write(buffer);
                System.out.println("Packet sent with pairs value/index: ");
                for (int i = 0; i < values.length; ++i){
                    System.out.print(values[i]);
                    System.out.print("/" + indexesLocal[i]);
                    System.out.println("");
                }
            } catch (IOException e){
                System.err.println("Socket exception: " + e.getMessage());
                synchronized (actuationSocket){
                    try { actuationSocket.close(); } catch (IOException ex) { throw new RuntimeException(ex); }
                }
                synchronized (missedValues){
                    if (missedValues.containsKey(actuator)){
                        Float[] previousValues = missedValues.get(actuator);
                        int index = 0;
                        for(Float value : values){
                            if (value != Float.NEGATIVE_INFINITY){ previousValues[index] = value; }
                            index += 1;
                        }
                        missedValues.put(actuator, previousValues);
                        System.out.print("MissedValues updated:");
                        Set<OpalActuator<?>> aux = missedValues.keySet();
                        for (OpalActuator<?> ac : aux){
                            System.out.println("    Actuator " + ((Device) ac).getLabel() + ":");
                            for (Float value : missedValues.get(ac)){
                                System.out.println("        " + value);
                            }
                        }
                    }
                    else{ missedValues.put(actuator, values); }
                }
                setActuateSocket(actuationIP, actuationPORT);
            }
        }
    }

    /*
     * The method will try to connect every IP declared within setup file
     *
     * @param ipObject IP or list of IPs
     * @param port destination port
     * */
    public static void setActuateSocket(Object ipObject, int port) {
        ArrayList<String> ipList = new ArrayList<>();
        if (ipObject instanceof String){
            String ip = (String) ipObject;
            ipList.add(ip);
        }
        else{
            JSONArray ipArray = (JSONArray) ipObject;
            for (Object element : ipArray){
                if (element instanceof String){
                    ipList.add((String) element);
                }
            }
        }
        connectActuationSocket(port, ipList);
        while(!connectedOnce) {
            connectActuationSocket(port, ipList);
            try { Thread.sleep(5000); } catch(InterruptedException e){ throw new RuntimeException(e); }
        }
    }

    private static void connectActuationSocket(int port, ArrayList<String> ipList) {
        for (String ip : ipList) {
            actuationSocket = new Socket();
            char firstChar = ip.charAt(0);
            if (firstChar == '$') {
                String env = ip.substring(1);
                ip = System.getenv(env);
                if (ip == null) { System.err.println("Environment variable " + env + " was not found"); continue; }
            }
            try {
                actuationSocket.connect(new InetSocketAddress(ip, port), 1000);
                actuationIP = ip;
                actuationPORT = port;
                //actuateSocket = new Socket(ip, port);
                System.out.println("Connected to server " + ip + " through port " + port);
                if (!connectedOnce) {
                    connectedOnce = true;
                    new Timer().scheduleAtFixedRate(new connectionTester(), 0, 5000); // send empty message every 2 seconds to test the connection
                }
                synchronized (missedValues){
                    Set<OpalActuator<?>> keys = missedValues.keySet();
                    for (OpalActuator<?> actuator : keys){
                        Float[] values = missedValues.get(actuator);
                        if(Arrays.stream(values).anyMatch(element -> element != Double.NEGATIVE_INFINITY)){
                            commitActuation(actuator, values);
                        }
                    }
                    missedValues = new HashMap<>();
                }
                break;
            } catch (IOException e) {
                System.err.println("Failed to connect to server " + ip + " through port " + port + ": " +
                        e.getMessage());
            }
        }
    }


    /**
     * Register device into the list of sensors.
     *
     * @param sensor Sensor to register
     */
    public static void registerSensor(OpalSensor<?> sensor, String commType) {
        if (commType.equals("opal-udp")){
            synchronized (udpSensorsList) {
                udpSensorsList.add(sensor);
            }
        }
        if (commType.equals("opal-tcp")){
            synchronized (tcpSensorsList) {
                tcpSensorsList.add(sensor);
            }
        }
    }

    public static void registerActuator(OpalActuator<?> actuator) {
        synchronized (actuatorsList) {
            actuatorsList.add(actuator);
        }
    }

    public static void setUdpPort(int port) { udpPORT = port; }

    public static void setTcpPort(int port) { tcpPORT = port; }

    public static void setUdpIp(String udpIp) { udpIP = udpIp; }

    public static void setTcpIp(String tcpIp) { tcpIP = tcpIp; }

    public static void setUseTCPActuators(boolean b) { useTCPActuators = b; }

    public static void setLoadedDevices(boolean b) {
        synchronized (OpalComm.class){
            loadedDevices = b;
            OpalComm.class.notifyAll();
        }
    }

    protected interface OpalSensor<V> extends Sensor<Float[], V> {
        int[] getIndexes();
    }

    protected interface OpalActuator<V> extends Actuator<V> {
        int[] getIndexes();
    }

    private static class connectionTester extends TimerTask {
        public void run(){
            try{
                // count the number of floats to be sended
                int nIndexes = 0;
                List<String> labels = new ArrayList<>();
                for (OpalActuator<?> opalActuator : actuatorsList) {
                    int nIndexesDevice = opalActuator.getIndexes().length;
                    labels.add(((Device) opalActuator).getLabel());
                    nIndexes += nIndexesDevice;
                }
                for (OpalSensor<?> opalSensor : tcpSensorsList) {
                    if (!(labels.contains(((Device) opalSensor).getLabel()))) {
                        int nIndexesDevice = opalSensor.getIndexes().length;
                        nIndexes += nIndexesDevice;
                    }
                }
                ByteBuffer byteBuffer = ByteBuffer.allocate(nIndexes * Float.BYTES);
                // For every float in bytebuffer, if index not in the list assign float minimum value, else assign proper value
                for (int i = 0; i < nIndexes; ++i){
                    byteBuffer.putFloat(Float.NEGATIVE_INFINITY);
                }
                DataOutputStream outputStream = new DataOutputStream(actuationSocket.getOutputStream());
                byte[] buffer = byteBuffer.array();
                outputStream.write(buffer);
            } catch (IOException e){
                System.err.println("Socket is disconnected: " + e.getMessage());
                synchronized (actuationSocket){
                    try { actuationSocket.close(); } catch (IOException ex) { throw new RuntimeException(ex); }
                }
                setActuateSocket(actuationIP, actuationPORT);
            }
        }
    }
}
