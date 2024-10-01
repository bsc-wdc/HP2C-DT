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


    //=======================================
    // INITIALIZATION
    //=======================================

    /*
    * This method receives the global properties of the edge and, if it is the first call (first declared device),
    * initializes ports and ips for communications.
    *
    * @param jGlobalProperties JSONObject representing the global properties of the edge
    * */
    public static void init(JSONObject jGlobalProperties) {
        if (!initialCall){ return; }
        initialCall = false;
        Thread initSetup = new Thread(() -> {
            try {
                //wait for every device to be loaded before verifying indexes
                waitForDevicesLoaded();
                verifyIndexes(actuatorsList);
                verifyIndexes(udpSensorsList);
                verifyIndexes(tcpSensorsList);

                // Set up udp and tcp connections
                setupComms(jGlobalProperties);
                startUDPServer();
                startTCPServer();
            } catch (Device.DeviceInstantiationException e) {
                System.err.println(e.getMessage());
            }
        });
        initSetup.start();
    }


    private static void waitForDevicesLoaded() {
        synchronized (OpalComm.class) {
            while (!loadedDevices) {
                try { OpalComm.class.wait(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }
    }


    /**
     * Verifies indexes within a list of OpalDevices
     *
     * @param devices Devices involved
     * @throws Device.DeviceInstantiationException
     */
    public static <T extends OpalDevice> void verifyIndexes(List<T> devices) throws Device.DeviceInstantiationException {
        HashSet<Integer> indexesSet = new HashSet<>();
        for (T device : devices) {
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


    //=======================================
    // UDP_SENSORS
    //=======================================


    /*
     * Starts and handles UDP Server for receiving UDP Sensors data.
     * */
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
                printCurrentTime();
                try {
                    byte[] buffer = new byte[maxUdpIndexesLength * Float.BYTES];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);
                    ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData());
                    //convert bytebuffer to Float array (readable values)
                    Float[] messageValues = new Float[maxUdpIndexesLength];
                    for (int i = 0; i < messageValues.length; i++) {
                        messageValues[i] = byteBuffer.getFloat();;
                    }
                    distributeValues(messageValues, udpSensorsList);
                    System.out.println(); // Add empty line at the end of each measurement
                } catch (Exception e) {
                    System.err.println("Error receiving UDP message: " + e.getMessage());
                }
            }
        });
        UDPSensorsThread.setName("UDPSensorsThread");
        UDPSensorsThread.start();
    }


    private static void distributeValues(Float[] messageValues, List<OpalSensor<?>> sensors) {
        synchronized (sensors) {
            //distribute values to their respective sensors
            for (OpalSensor<?> sensor : sensors){
                int[] indexes = sensor.getIndexes();
                Float[] sensedValues = new Float[indexes.length];
                for (int j = 0; j < indexes.length; ++j) {
                    sensedValues[j] = messageValues[indexes[j]];
                }
                sensor.sensed(sensedValues);
                sensor.onRead();
            }
        }
    }


    //=======================================
    // TCP_SENSORS
    //=======================================


    /**
     * Starts and handles TCP Server for receiving TCP Sensors data.
     * */
    private static void startTCPServer() {
        Thread TCPSensorsThread = new Thread(() -> {
            while(true){
                // Initialize TCP server socket to read measurements
                InetAddress serverAddress = null;
                Socket clientSocket = null;
                try {
                    serverAddress = InetAddress.getByName(tcpIP);
                    tcpSocket = new ServerSocket(tcpPORT,0, serverAddress);
                    tcpSocket.setReuseAddress(true); //clean tcpSocket ip and port when socket is closed
                    System.out.println("\nTCP Server running on port: " + tcpPORT + "\n");
                    clientSocket = tcpSocket.accept();
                    //when a connection is established, set every TCP sensor as available
                    setAvailableSensors(tcpSensorsList, true);
                    processTCPConnection(clientSocket);
                } catch (IOException e) {
                    System.err.println("Error starting TCP server: " + e.getMessage());
                    throw new RuntimeException(e);
                }
                try {
                    if (clientSocket != null && !clientSocket.isClosed()) {
                        tcpSocket.close();
                        //when a connection fails, set every TCP sensor as not available
                        setAvailableSensors(tcpSensorsList, false);
                    }
                } catch (IOException ex) { System.err.println("Error closing client socket: " + ex.getMessage()); }
            }
        });
        TCPSensorsThread.setName("TCPSensorsThread");
        TCPSensorsThread.start();
    }


    /**
     * Read values of the TCP sensors from Opal. Check start of message with a readInt() that checks the expected length
     * (number of incoming floats) of the message, then get the buffer with that messageLength, and lastly the end of
     * line (EoL) character, otherwise throwing an error.
     */
    private static void processTCPConnection(Socket clientSocket) {
        try {
            while (true) {
                // Print time each iteration
                printCurrentTime();
                DataInputStream inputStream = new DataInputStream(clientSocket.getInputStream());
                int messageLength = inputStream.readInt();

                byte[] buffer = new byte[messageLength * Float.BYTES];
                inputStream.readFully(buffer);

                char endChar = inputStream.readChar();
                if (endChar != '\n') {
                    throw new IOException("End character not found.");
                }

                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
                //convert bytebuffer to Float array (readable values)
                Float[] messageValues = new Float[messageLength];
                for (int i = 0; i < messageLength; i++) {
                    messageValues[i] = byteBuffer.getFloat();
                }
                distributeValues(messageValues, tcpSensorsList);
                System.out.println(); // Add empty line at the end of each measurement
            }
        } catch (IOException e){
            System.err.println("Error reading messages though TCP: " + e.getMessage());
        }
    }


    //=======================================
    // TCP_ACTUATORS
    //=======================================


    /**
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
        } else{
            JSONArray ipArray = (JSONArray) ipObject;
            for (Object element : ipArray){
                if (element instanceof String){
                    ipList.add((String) element);
                }
            }
        }
        while(!connectedOnce) {
            initActuationSocket(port, ipList);
            try { Thread.sleep(5000); } catch(InterruptedException e){ throw new RuntimeException(e); }
        }
    }


    private static void initActuationSocket(int port, ArrayList<String> ipList) {
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
                //when a connection is established, set every actuator as available
                setAvailableActuators(actuatorsList, true);
                actuationIP = ip;
                actuationPORT = port;
                System.out.println("Connected to server " + ip + " through port " + port);
                connectedOnce = true;
                //send empty message every 2 seconds to test the connection
                new Timer().scheduleAtFixedRate(new connectionTester(), 0, 5000);
                break;
            } catch (IOException e) {
                System.err.println("Failed to connect to server " + ip + " through port " + port + ": " +
                        e.getMessage());
            }
        }
    }


    public static void retryConnectionActuation(){
        synchronized(actuatorsList){
            for (OpalActuator<?> a : actuatorsList){
                if (((Device) a).isActuatorAvailable()){ return; }
            }
        }
        try {
            actuationSocket = new Socket();
            actuationSocket.connect(new InetSocketAddress(actuationIP, actuationPORT), 1000);
            setAvailableActuators(actuatorsList, true);
            synchronized (missedValues){
                for (OpalActuator<?> actuator : missedValues.keySet()){
                    Float[] values = missedValues.get(actuator);
                    if(Arrays.stream(values).anyMatch(element -> element != Double.NEGATIVE_INFINITY)){
                        commitActuation(actuator, values);
                    }
                }
                missedValues = new HashMap<>();
            }
        } catch (IOException e) {
            System.err.println("Failed to connect to server " + actuationIP + " through port " + actuationPORT + ": " +
                    e.getMessage());
        }
    }


    /**
     * Commit values for the involved actuator
     * @param actuator involved actuator
     * @param values committable values
     * */
    public static void commitActuation(OpalActuator<?> actuator, Float[] values) throws IOException {
        if (!useTCPActuators) return;
        try{
            // count the number of floats to be sent
            int nIndexes = getnIndexes();
            ByteBuffer byteBuffer = ByteBuffer.allocate(nIndexes * Float.BYTES);
            // obtain indexes of the actuator and put the proper values in bytebuffer
            int[] indexesLocal = getIndexesLocal(actuator, values, nIndexes, byteBuffer);
            // check data integrity
            if (values.length != indexesLocal.length) {
                throw new IllegalArgumentException("OpalComm.commitActuation: Wrong input length " +
                        "(actual: " + values.length + ", expected: " + indexesLocal.length + ").");
            }
            // send actuation message
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
            // if the actuation misses, close the socket
            synchronized (actuationSocket){
                try { actuationSocket.close(); } catch (IOException ex) { throw new RuntimeException(ex); }
            }
            synchronized (missedValues){
                // Store and update values in missedValues; they will be sent when a connection is established.
                Float[] valuesToUpdate;
                if (missedValues.containsKey(actuator)) {
                    valuesToUpdate = missedValues.get(actuator);
                    int index = 0;
                    for (Float value : values) {
                        if (value != Float.NEGATIVE_INFINITY) {
                            valuesToUpdate[index] = value;
                        }
                        index += 1;
                    }
                } else {
                    valuesToUpdate = values;
                }
                missedValues.put(actuator, valuesToUpdate);
                System.out.println("MissedValues updated:");
                for (OpalActuator<?> ac : missedValues.keySet()){
                    System.out.println("    Actuator " + ((Device) ac).getLabel() + ":");
                    for (Float value : missedValues.get(ac)){
                        System.out.println("        " + value);
                    }
                }
            }
            // Retry connection
            retryConnectionActuation();
        } catch (IllegalArgumentException e){
            throw new IOException(e);
        }
    }


    private static int[] getIndexesLocal(OpalActuator<?> actuator, Float[] values, int nIndexes, ByteBuffer byteBuffer) {
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
        return indexesLocal;
    }


    //=======================================
    // AUXILIARY_CLASSES & INTERFACES
    //=======================================


    /*
    * Tests the connection periodically by sending an empty message. If the connection is not established, it will retry
    */
    private static class connectionTester extends TimerTask {
        public void run(){
            try{
                // count the number of floats to be sent
                int nIndexes = getnIndexes();
                ByteBuffer byteBuffer = ByteBuffer.allocate(nIndexes * Float.BYTES);
                // Assign a dummy -Inf value as a testing message
                for (int i = 0; i < nIndexes; ++i){
                    byteBuffer.putFloat(Float.NEGATIVE_INFINITY);
                }
                DataOutputStream outputStream = new DataOutputStream(actuationSocket.getOutputStream());
                byte[] buffer = byteBuffer.array();
                outputStream.write(buffer);
            } catch (IOException e){
                // when a connection fails, set every actuator as not available
                setAvailableActuators(actuatorsList, false);
                synchronized (actuationSocket){
                    try { actuationSocket.close(); } catch (IOException ex) { throw new RuntimeException(ex); }
                }
                System.err.println("Actuation socket is closed: " + e.getMessage());
                retryConnectionActuation();  //retry connection
            }
        }
    }


    protected interface OpalDevice{
        int[] getIndexes();
    }


    protected interface OpalSensor<V> extends Sensor<Float[], V>, OpalDevice {
        int[] getIndexes();
    }


    protected interface OpalActuator<V> extends Actuator<V>, OpalDevice {
        int[] getIndexes();
    }


    //=======================================
    // UTILS
    //=======================================


    private static int getPort(JSONObject jProtocol) {
        return jProtocol.getInt("port");
    }


    private static String getIp(JSONObject jProtocol) {
        return jProtocol.getString("ip");
    }


    private static JSONObject getjComms(JSONObject jGlobProp) {
        return jGlobProp.getJSONObject("comms");
    }


    public static  void setAvailableSensors(List<OpalSensor<?>> sensors, boolean b){
        for(OpalSensor<?> sensor : sensors){ ((Device) sensor).setSensorAvailable(b); }
    }


    public static  void setAvailableActuators(List<OpalActuator<?>> actuators, boolean b){
        synchronized(actuatorsList){
            for(OpalActuator<?> actuator : actuatorsList){ ((Device) actuator).setActuatorAvailable(b); }
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
            ((Device) sensor).setMaxTimeWithoutUpdate(10000);
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


    private static void printCurrentTime() {
        LocalTime currentTime = LocalTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        String formattedTime = currentTime.format(formatter);
        System.out.println("Current time: " + formattedTime);
    }


    /*
     * Count the number of floats to be sent
     * */
    private static int getnIndexes() {
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
        return nIndexes;
    }

}




