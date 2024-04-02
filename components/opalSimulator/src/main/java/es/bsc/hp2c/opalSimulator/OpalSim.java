package es.bsc.hp2c.opalSimulator;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import es.bsc.hp2c.common.types.Device;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import static es.bsc.hp2c.common.utils.FileUtils.loadDevices;

public class OpalSim {
    private static String SERVER_ADDRESS = "127.0.0.1";
    private static ArrayList<Edge> edges = new ArrayList<>();
    private static boolean runSimulation = false;
    private static String simulationFile;

    public static void main(String[] args) throws IOException {
        String localIp = System.getenv("LOCAL_IP");
        if (localIp != null) SERVER_ADDRESS=localIp;

        String deploymentName = args[0];
        String deploymentFile = "../../deployments/" + deploymentName + "/setup/";
        System.out.println("Using deployment name: " + deploymentName);

        String simulationName;
        if (args.length > 1){
            simulationName = args[1];
            simulationFile = "simulations/" + simulationName;
            float timeStep = Float.parseFloat(args[2]);
            runSimulation = true;
            System.out.println("Using simulation name: " + simulationName);
            System.out.println("TimeStep = " + timeStep);
        }

        File setupDirectory = new File(deploymentFile);
        if (!setupDirectory.exists()) {
            deploymentFile = "/data/edge/";
            setupDirectory = new File(deploymentFile);
        }

        for (String edgeFile : Objects.requireNonNull(setupDirectory.list())){
            String pathToEdge = deploymentFile + edgeFile;
            Edge edge = initEdgeComms(pathToEdge);
            ArrayList<DeviceWrapper> devicesWrapped = getDevices(pathToEdge);
            Map<String, Device> devices = loadDevices(pathToEdge, false);
            devicesWrapped = joinDevices(devicesWrapped, devices);
            edge.setDevices(devicesWrapped);
            edges.add(edge);
        }
        startActuatorsServer();
        startTCPSensors();
        startUDPSensors();
    }


    //=======================================
    // UDP
    //=======================================

    private static void startUDPSensors() {
        for (Edge edge : edges){
            new Thread(() -> {
                System.out.println("Starting UDP communication in port " + edge.getUdpSensorsPort() + " ip " + edge.getUdpSensorsIP());
                startUDPClient(edge);
            }).start();
        }
    }


    private static void startUDPClient(Edge edge) {
        try (DatagramSocket udpSocket = new DatagramSocket()) {
            InetAddress address = InetAddress.getByName(SERVER_ADDRESS);
            int udpSensorsIndexes = 0;
            ArrayList <DeviceWrapper> udpSensors = new ArrayList<>();
            for (DeviceWrapper device : edge.getDevices()){
                if (device.getProtocol() == "opal-udp" && device.getDevice().isSensitive()){
                    udpSensorsIndexes += device.getIndexes().length;
                    udpSensors.add(device);
                }
            }

            int t = 0;
            while (true) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(udpSensorsIndexes * Float.BYTES);
                float[] values;
                if (runSimulation){
                    values = getValuesFromCsv(udpSensors, edge.getLabel(), t, udpSensorsIndexes);
                } else {
                    values = genSineValues(udpSensorsIndexes);
                }

                for (float value: values) {
                    byteBuffer.putFloat(value);
                }
                byte[] buffer = byteBuffer.array();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, edge.getUdpSensorsPort());
                udpSocket.send(packet);
                System.out.println("Sent UDP packet.");

                //TODO: timeStep
                Thread.sleep(5000);
                t += 1;
            }
        } catch (Exception e) {
            System.err.println("Error sending data through UDP.");
        }
    }

    private static float[] getValuesFromCsv(ArrayList<DeviceWrapper> sensors, String edgeLabel, int t, int nIndexes){
        float[] values = new float[nIndexes];
        ArrayList<String> labels = new ArrayList<>();
        for (DeviceWrapper sensor : sensors){
            labels.add(sensor.getLabel());
        }

        try (BufferedReader br = new BufferedReader(new FileReader(simulationFile))) {
            String line;

            while ((line = br.readLine()) != null) {
                String[] fields = line.split(",");
                if (!Objects.equals(fields[0], edgeLabel)) continue;

                // get device label without "SensorX" and sensorNumber(X)
                String subString = fields[1].substring(0, fields[1].length() - 7);
                int sensorNumber = Integer.parseInt(fields[1].substring(fields[1].length() - 1));
                for (DeviceWrapper sensor : sensors){
                    if (subString.equals(sensor.getLabel())){
                        values[sensor.getIndexes()[sensorNumber]] = Float.parseFloat(fields[t + 2]);
                    }
                }
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return values;
    }

    //=======================================
    // TCP-Sensors
    //=======================================

    private static void startTCPSensors() {
        for (Edge edge : edges){
            new Thread(() -> {
                System.out.println("Starting TCP communication in port " + edge.getTcpSensorsPort() + " ip " + edge.getTcpSensorsIP());
                startTCPClient(edge);
            }).start();
        }
    }

    /*
     * This method starts a TCP client to update the value on the corresponding edge. To do so, we check start of message
     * with a readInt() that checks the expected length (number of floats) of the message, then get the buffer with that
     * messageLength, and lastly the end of line (EoL) character.
     * */
    private static void startTCPClient(Edge edge) {
        if (edge.getDevices().isEmpty()) return;
        try {
            Socket tcpSocket = new Socket(SERVER_ADDRESS, edge.getTcpSensorsPort());
            while (true) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(devices.get(edgeNumber).length * Float.BYTES + Integer.BYTES + Character.BYTES);
                float[] values = new float[devices.get(edgeNumber).length];
                for (int i = 0; i < devices.get(edgeNumber).length; i++) {
                    Float[] aux = devices.get(edgeNumber);
                    values[i] = aux[i];
                }
                byteBuffer.putInt(devices.get(edgeNumber).length);
                System.out.println("Length of the message: " + devices.get(edgeNumber).length);
                for (float value : values) {
                    byteBuffer.putFloat(value);
                    System.out.println("Prepared TCP value: " + value);
                }
                byteBuffer.putChar('\n');

                try {
                    DataOutputStream outputStream = new DataOutputStream(tcpSocket.getOutputStream());
                    byte[] buffer = byteBuffer.array();
                    outputStream.write(buffer);
                    System.out.println("Sent TCP packet.\n");
                } catch (IOException e) {
                    System.err.println("Error sending data through TCP: " + e.getMessage());
                    break;
                }
                Thread.sleep(5000);
            }
        } catch (Exception e) {
            System.err.println("Error connecting to TCP server: " + e.getMessage());
        }
    }

    //=======================================
    // TCP-Actuators
    //=======================================

    private static void startActuatorsServer() {
        for (Edge edge : edges){
            int port = BASE_TCP_ACTUATORS_PORT + (i * 1000);
            new Thread(() -> {
                try {
                    ServerSocket server = new ServerSocket(port, 0, InetAddress.getByName("0.0.0.0"));
                    System.out.println("Server running in port " + port + ". Waiting for client requests...");
                    Socket clientSocket = null;
                    clientSocket = server.accept();
                    runClient += 1;
                    System.out.println("Accepted connection from: " + clientSocket.getInetAddress().getHostAddress());
                    handleActuateClient(clientSocket, (port / 1000) % 10);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }
        System.out.println("");
        System.out.println("Waiting for " + nEdges + " edges to be connected...");
        System.out.println("");
        while (runClient < nEdges){
            try { Thread.sleep(1000); } catch (InterruptedException e) { throw new RuntimeException(e); }
        }
    }

    /*
     * This method uses a TCP socket to receive actuations and update the corresponding values in "devices" map.
     *
     * @param clientSocket Socket through which we will receive the messages.
     * @param edgeNumber Map key where value must be updated.
     * */
    private static void handleActuateClient(Socket clientSocket, Edge edge) {
        try {
            if (devices.get(edgeNumber).length < 1){ return; }
            DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
            while (true) {
                byte[] buffer = new byte[devices.get(edgeNumber).length * Float.BYTES];
                dis.readFully(buffer);
                System.out.println("Message Received from " + clientSocket.getInetAddress().getHostAddress());

                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

                int index = 0;
                while (byteBuffer.remaining() > 0) {
                    Float[] aux = devices.get(edgeNumber);
                    Float newFloat = byteBuffer.getFloat();
                    if (newFloat != Float.NEGATIVE_INFINITY){
                        aux[index] = newFloat;
                    }
                    devices.put(edgeNumber, aux);
                    index += 1;
                }
                System.out.println("    Message is: " + Arrays.toString(devices.get(edgeNumber)) +
                        " for edge " + edgeNumber);
            }
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    }

    //=======================================
    // UTILS
    //=======================================

    public static float[] genSineValues(int size) {
        float[] values = new float[size];
        long currentTimeMillis = System.currentTimeMillis();
        double time = currentTimeMillis / 1000.0; // Convert milliseconds to seconds
        double angularFrequency = 2 * Math.PI * 1;

        for (int i = 0; i < size; i++) {
            double shift = i * (2 * Math.PI / 3);
            values[i] = (float) Math.sin(angularFrequency * time + shift);
            // Modify voltages (0, 1, 2)
            values[i] *= (float) Math.sqrt(2) * 230;
        }
        return values;
    }


    private static ArrayList<DeviceWrapper> joinDevices(ArrayList<DeviceWrapper> devicesWrapped, Map<String, Device> devices) {
        for (DeviceWrapper deviceWrapper : devicesWrapped) {
            String label = deviceWrapper.getLabel();

            if (devices.containsKey(label)) {
                Device device = devices.get(label);
                deviceWrapper.setDevice(device);
            } else {
                System.out.println("Device not found with label: " + label);
            }
        }

        return devicesWrapped;
    }

    private static ArrayList<DeviceWrapper> getDevices(String pathToEdge) throws IOException {
        ArrayList<DeviceWrapper> devices = new ArrayList<>();

        InputStream is = Files.newInputStream(Paths.get(pathToEdge));
        JSONTokener tokener = new JSONTokener(is);
        JSONObject object = new JSONObject(tokener);
        JSONArray jDevices = object.getJSONArray("devices");

        for (Object jo : jDevices){
            JSONObject jDevice = (JSONObject) jo;
            JSONObject jDProperties = jDevice.getJSONObject("properties");
            String label = jDevice.getString("label").replaceAll("[\\s-]", "");

            String protocol = jDProperties.getString("comm-type");

            JSONArray jIndexes = jDProperties.getJSONArray("indexes");
            int[] indexes = new int[jIndexes.length()];
            for (int i = 0; i < jIndexes.length(); i++) {
                indexes[i] = jIndexes.getInt(i);
            }

            devices.add(new DeviceWrapper(label, protocol, indexes));
        }
        return devices;
    }

    private static Edge initEdgeComms(String pathToEdge) throws IOException {
        JSONObject jGlobProp = getJGlobalProperties(pathToEdge);
        JSONObject jComms = jGlobProp.getJSONObject("comms");
        String label = jGlobProp.getString("label");

        JSONObject jUDP = jComms.getJSONObject("opal-udp");
        JSONObject jTCP= jComms.getJSONObject("opal-tcp");

        JSONObject jUDPSensors = jUDP.getJSONObject("sensors");
        int udpSensorsPort = jUDPSensors.getInt("port");
        String udpSensorsIP = jUDPSensors.getString("ip");

        JSONObject jTCPSensors = jTCP.getJSONObject("sensors");
        int tcpSensorsPort = jTCPSensors.getInt("port");
        String tcpSensorsIP = jTCPSensors.getString("ip");

        JSONObject jTCPActuators = jTCP.getJSONObject("actuators");
        int tcpAcuatorsPort = jTCPActuators.getInt("port");
        String tcpActuatorsIP = jTCPActuators.getString("ip");

        return new Edge(label, tcpSensorsPort, tcpSensorsIP, tcpAcuatorsPort, tcpActuatorsIP, udpSensorsPort, udpSensorsIP);
    }

    private static JSONObject getJGlobalProperties(String pathToEdge) throws IOException {
        InputStream is = new FileInputStream(pathToEdge);
        JSONTokener tokener = new JSONTokener(is);
        JSONObject object = new JSONObject(tokener);
        return object.getJSONObject("global-properties");
    }
}


class Edge {
    private final String label;
    private final int tcpSensorsPort;
    private final String tcpSensorsIP;
    private final int tcpActuatorsPort;
    private final String tcpActuatorsIP;
    private final int udpSensorsPort;
    private final String udpSensorsIP;
    private ArrayList<DeviceWrapper> devices;

    public Edge(String label, int tcpSensorsPort, String tcpSensorsIP, int tcpActuatorsPort, String tcpActuatorsIP, int udpSensorsPort, String udpSensorsIP) {
        this.label = label;
        this.tcpSensorsPort = tcpSensorsPort;
        this.tcpSensorsIP = tcpSensorsIP;
        this.tcpActuatorsPort = tcpActuatorsPort;
        this.tcpActuatorsIP = tcpActuatorsIP;
        this.udpSensorsPort = udpSensorsPort;
        this.udpSensorsIP = udpSensorsIP;
        this.devices = new ArrayList<>();
    }

    public String getLabel(){ return label; }

    public int getTcpSensorsPort() {
        return tcpSensorsPort;
    }

    public int getTcpActuatorsPort() {
        return tcpActuatorsPort;
    }

    public int getUdpSensorsPort() {
        return udpSensorsPort;
    }

    public String getTcpSensorsIP() {
        return tcpSensorsIP;
    }

    public String getTcpActuatorsIP() {
        return tcpActuatorsIP;
    }

    public String getUdpSensorsIP() {
        return udpSensorsIP;
    }

    public ArrayList<DeviceWrapper> getDevices() {
        return devices;
    }

    public void setDevices(ArrayList<DeviceWrapper> devices) {
        this.devices = devices;
    }

    public void addDevice(DeviceWrapper device) {
        devices.add(device);
    }
}

class DeviceWrapper {
    private String label;
    private String protocol;
    private int[] indexes;
    private Device device;

    public DeviceWrapper(String label, String protocol, int[] indexes) {
        this.label = label;
        this.protocol = protocol;
        this.indexes = indexes;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public int[] getIndexes() {
        return indexes;
    }

    public void setIndexes(int[] indexes) {
        this.indexes = indexes;
    }

    public Device getDevice() {
        return device;
    }

    public void setDevice(Device device) {
        this.device = device;
    }
}

