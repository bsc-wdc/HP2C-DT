package es.bsc.hp2c.opalSimulator;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import es.bsc.hp2c.common.types.Device;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import static es.bsc.hp2c.common.utils.FileUtils.loadDevices;

public class OpalSim {
    private static String SERVER_ADDRESS = "127.0.0.1";
    private static ArrayList<Edge> edges = new ArrayList<>();
    private static final double frequency = 1.0 / 20.0;
    private static boolean runSimulation = false;
    private static CSVTable csvTable;
    private static int runClient = 0;
    private static long timeStep;

    public static void main(String[] args) throws IOException {
        String localIp = System.getenv("LOCAL_IP");
        if (localIp != null) SERVER_ADDRESS=localIp;

        String deploymentName = args[0];
        String deploymentFile = "../../deployments/" + deploymentName + "/setup/";
        System.out.println("Using deployment name: " + deploymentName);

        String simulationName;
        if (args.length > 1){
            simulationName = args[1];
            csvTable = new CSVTable("simulations/" + simulationName + ".csv");
            timeStep = Long.parseLong(args[2]);
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
                System.out.println("Starting UDP communication in port " + edge.getUdpSensorsPort() + " ip " + SERVER_ADDRESS);
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
                if (Objects.equals(device.getProtocol(), "opal-udp") && device.getDevice().isSensitive()){
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
                    if (values == null){
                        t = 0;
                        continue;
                    }
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

                Thread.sleep(timeStep);
                t += 1;
            }
        } catch (Exception e) {
            System.err.println("Error sending data through UDP.");
        }
    }

    private static float[] getValuesFromCsv(ArrayList<DeviceWrapper> sensors, String edgeLabel, int t, int nIndexes){
        if (t >= csvTable.getData().size()) return null;

        float[] values = new float[nIndexes];
        List<Float> row = csvTable.getRow(t);
        List<String> edgeNames = csvTable.getEdgeNames();
        List<String> deviceNames = csvTable.getDeviceNames();
        for (int i = 0; i < edgeNames.size(); ++i){
            if (Objects.equals(edgeNames.get(i), edgeLabel)){
                String subString = deviceNames.get(i).substring(0, deviceNames.get(i).length() - 7);
                int sensorNumber = Integer.parseInt(deviceNames.get(i).substring(deviceNames.get(i).length() - 1));
                // get device label without "SensorX" and sensorNumber(X)
                for (DeviceWrapper sensor : sensors){
                    if (subString.equals(sensor.getLabel())){
                        values[sensor.getIndexes()[sensorNumber]] = row.get(i);
                    }
                }
            }
        }
        return values;
    }

    //=======================================
    // TCP-Sensors
    //=======================================

    private static void startTCPSensors() {
        for (Edge edge : edges){
            new Thread(() -> {
                System.out.println("Starting TCP communication in port " + edge.getTcpSensorsPort() + " ip " + SERVER_ADDRESS);
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

            int tcpIndexes = 0;
            ArrayList <DeviceWrapper> tcpSensors = new ArrayList<>();
            for (DeviceWrapper device : edge.getDevices()){
                if (Objects.equals(device.getProtocol(), "opal-tcp")){
                    tcpIndexes += device.getIndexes().length;
                    if (device.getDevice().isSensitive()) tcpSensors.add(device);
                }
            }

            int t = 0;
            while (true) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(tcpIndexes * Float.BYTES + Integer.BYTES + Character.BYTES);
                float[] values = new float[tcpIndexes];

                if (runSimulation){
                    values = getValuesFromCsv(tcpSensors, edge.getLabel(), t, tcpIndexes);
                    if (values == null) {
                        t = 0;
                        continue;
                    }
                    for (DeviceWrapper device : tcpSensors){
                        if (device.getDevice().isActionable()){
                            float[] v = device.getValues();
                            for (int i : device.getIndexes()) {
                                if (values[i] != Float.NEGATIVE_INFINITY) v[i - device.getIndexes()[0]] = values[i];
                            }
                            device.setValues(v);

                            int j = 0;
                            for (int i : device.getIndexes()) {
                                values[i] = device.getValues()[j];
                                j += 1;
                            }
                        }
                    }
                } else {
                    values = genSineValues(tcpIndexes);
                    for (DeviceWrapper device : tcpSensors){
                        if (device.getDevice().isActionable()){
                            int j = 0;
                            for (int i : device.getIndexes()) {
                                values[i] = device.getValues()[j];
                                j += 1;
                            }
                        }
                    }
                }

                byteBuffer.putInt(tcpIndexes);
                System.out.println("Length of the message: " + tcpIndexes);
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
                Thread.sleep(timeStep);
                t += 1;
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
            int port = edge.getTcpActuatorsPort();
            new Thread(() -> {
                try {
                    ServerSocket server = new ServerSocket(port, 0, InetAddress.getByName("0.0.0.0")); //TODO: check with SERVER_ADDRESS
                    System.out.println("Server running in port " + port + ". Waiting for client requests...");
                    Socket clientSocket = null;
                    clientSocket = server.accept();
                    runClient += 1;
                    System.out.println("Accepted connection from: " + clientSocket.getInetAddress().getHostAddress());
                    handleActuateClient(clientSocket, edge);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }
        System.out.println("");
        System.out.println("Waiting for " + edges.size() + " edges to be connected...");
        System.out.println("");

        while (runClient < edges.size()){
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
            if (edge.getDevices().isEmpty()) return;
            ArrayList <DeviceWrapper> tcpActuators = new ArrayList<>();
            int tcpIndexes = 0;
            for (DeviceWrapper device : edge.getDevices()){
                if (Objects.equals(device.getProtocol(), "opal-tcp")){
                    tcpIndexes += device.getIndexes().length;
                    if (device.getDevice().isSensitive()) tcpActuators.add(device);
                }
            }

            DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
            while (true) {
                byte[] buffer = new byte[tcpIndexes * Float.BYTES];
                dis.readFully(buffer);

                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

                int index = 0;
                ArrayList<Float> message = new ArrayList<>();
                while (byteBuffer.remaining() > 0) {
                    Float newFloat = byteBuffer.getFloat();
                    if (newFloat != Float.NEGATIVE_INFINITY){
                        message.add(newFloat);
                        for (DeviceWrapper actuator : tcpActuators){
                            for (int i = 0; i < actuator.getIndexes().length; ++i){
                                if (index == actuator.getIndexes()[i]){
                                    actuator.setValue(newFloat, i);
                                }
                            }
                        }
                    }
                    index += 1;
                }
                if (!message.isEmpty()) {
                    System.out.println("    Message is: " + message + " for edge " + edge.getLabel());
                    System.out.println("Message Received from " + clientSocket.getInetAddress().getHostAddress());
                }
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
        double angularFrequency = 2 * Math.PI * frequency;

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

        JSONObject jTCPSensors = jTCP.getJSONObject("sensors");
        int tcpSensorsPort = jTCPSensors.getInt("port");

        JSONObject jTCPActuators = jTCP.getJSONObject("actuators");
        int tcpActuatorsPort = jTCPActuators.getInt("port");

        return new Edge(label, tcpSensorsPort, tcpActuatorsPort, udpSensorsPort);
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
    private final int tcpActuatorsPort;
    private final int udpSensorsPort;
    private ArrayList<DeviceWrapper> devices;

    public Edge(String label, int tcpSensorsPort, int tcpActuatorsPort, int udpSensorsPort) {
        this.label = label;
        this.tcpSensorsPort = tcpSensorsPort;
        this.tcpActuatorsPort = tcpActuatorsPort;
        this.udpSensorsPort = udpSensorsPort;
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
    private float[] values;


    public DeviceWrapper(String label, String protocol, int[] indexes) {
        this.label = label;
        this.protocol = protocol;
        this.indexes = indexes;
        this.values = new float[indexes.length];
        Arrays.fill(values, Float.NEGATIVE_INFINITY);
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public float[] getValues(){
        return values;
    }

    public void setValues(float[] values){
        this.values = values;
    }

    public void setValue(float value, int index){
        this.values[index] = value;
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

class CSVTable {
    private List<String> edgeNames;
    private List<String> deviceNames;
    private List<List<Float>> data;

    public CSVTable(String csvFilePath) {
        edgeNames = new ArrayList<>();
        deviceNames = new ArrayList<>();
        data = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvFilePath))) {
            // Read the first line to get edge names
            String[] edges = br.readLine().split(",");
            for (int i = 1; i < edges.length; i++) {
                edgeNames.add(edges[i].replaceAll("\\s", ""));
            }

            // Read the second line to get device names
            String[] devices = br.readLine().split(",");
            for (int i = 1; i < devices.length; i++) {
                deviceNames.add(devices[i].replaceAll("\\s", ""));
            }

            // Read remaining lines to populate data
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) {
                    return;}
                String[] values = line.split(",", -1);
                List<Float> rowData = new ArrayList<>();
                for (int i = 1; i < values.length; i++) {
                    String val = values[i].replaceAll("\\s", "");
                    Float value = Float.NEGATIVE_INFINITY;
                    if (!val.isEmpty()) value = Float.parseFloat(val);
                    rowData.add(value);
                }
                data.add(rowData);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<String> getEdgeNames() {
        return edgeNames;
    }

    public List<String> getDeviceNames() {
        return deviceNames;
    }

    public List<List<Float>> getData() {
        return data;
    }

    public List<Float> getRow(int rowIndex) {
        return data.get(rowIndex);
    }

    public void printTable() {
        // Print edge names, device names, and data
        for (int i = 0; i < edgeNames.size(); i++) {
            System.out.print(edgeNames.get(i) + "\t" + deviceNames.get(i) + "\t");
            for (int j = 0; j < data.get(i).size(); j++) {
                System.out.print(data.get(i).get(j) + "\t");
            }
            System.out.println();
        }
    }
}
