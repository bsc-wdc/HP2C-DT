package es.bsc.hp2c.opalSimulator;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.opalSimulator.utils.CSVTable;
import es.bsc.hp2c.opalSimulator.utils.DeviceWrapper;
import es.bsc.hp2c.opalSimulator.utils.Edge;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import static es.bsc.hp2c.common.utils.FileUtils.getJsonObject;

import static es.bsc.hp2c.common.utils.FileUtils.loadDevices;

public class OpalSimulator {
    private static String SERVER_ADDRESS = "127.0.0.1";
    private static ArrayList<Edge> edges = new ArrayList<>();
    private static final double frequency = 1.0 / 20.0;
    private static boolean runSimulation = false;
    private static CSVTable csvTable;
    private static int runClient = 0;
    private static long timeStep;
    private static ArrayList<File> logFiles = new ArrayList<>();
    private static String simulationName = "";


    /*
    * Runs nEdges TCP servers in order to receive actuations, nEdges TCP clients to update the values on the edges
    * (just TCP sensors), and nEdges UDP servers with the aim of sending UDP sensors values.
    *
    * @param args User should input the deployment directory (name of the directory in path "hp2cdt/deployments", the
    * timeStep, and if the user wants to run a simulation, the simulation name (name of the csv
    * file in "hp2cdt/components/opalSimulator/simulations")
    *
    * */
    public static void main(String[] args) throws IOException {
        String localIp = System.getenv("LOCAL_IP");
        if (localIp != null) SERVER_ADDRESS=localIp;
        boolean inDocker = false;

        String deploymentFile;
        if (args.length > 0) {
            String deploymentName = args[0];
            deploymentFile = "../../deployments/" + deploymentName + "/setup/";
        } else {
            inDocker = true;
            deploymentFile = "/data/edge/";
        }
        File setupDirectory = new File(deploymentFile);
        if (!setupDirectory.exists()){
            throw new FileNotFoundException("Setup directory file not found");
        }

        if(args.length > 1){
            timeStep = Long.parseLong(args[1]);
            System.out.println("TimeStep: " + timeStep);
        } else {
            String timeStepEnv = System.getenv("TIME_STEP");
            if (timeStepEnv != null) timeStep = Long.parseLong(timeStepEnv);
            else timeStep = 1000;
        }

        if (args.length > 2){
            simulationName = args[2];
            runSimulation = true;
            System.out.println("Using simulation name: " + simulationName);
        } else {
            String simulationNameEnv = System.getenv("SIMULATION_NAME");
            if ((simulationNameEnv) != null && !simulationNameEnv.equals("")){
                simulationName = simulationNameEnv;
                runSimulation = true;
            }
        }
        if (runSimulation) {
            String simulationPath;
            if (inDocker){
                simulationPath = "/data/simulations/" + simulationName + ".csv";
            }
            else {
                simulationPath = "simulations/" + simulationName + ".csv";
            }

            File simulationFile = new File(simulationPath);
            if (simulationFile.exists()) csvTable = new CSVTable(simulationPath);
            else throw new FileNotFoundException("Simulation csv not found");
        }
        System.out.println("Using deployment file: " + deploymentFile);
        System.out.println("Using timeStep: " + timeStep);
        System.out.println("Runsimulation: " + runSimulation);
        if (runSimulation){
            System.out.println("Using simulation name: " + simulationName);
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

        if (runSimulation) createLogFiles(simulationName);
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


    /*
    * This method starts a UDP Client for a given edge. From the edge we can obtain the devices and its properties, so
    * that we can know which devices are sensors and generate proper values. WE will get the values from the simulation
    * in case a simulation was specified.
    * */
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
     * This method starts a TCP Client for a given edge. From the edge we can obtain the devices and its properties, so
     * that we can know which devices are sensors and generate proper values. WE will get the values from the simulation
     * in case a simulation was specified.
     * */
    private static void startTCPClient(Edge edge) {
        if (edge.getDevices().isEmpty()) return;
        while (true) {
            Socket tcpSocket = null;
            DataOutputStream outputStream = null;
            try {
                tcpSocket = new Socket(SERVER_ADDRESS, edge.getTcpSensorsPort());

                int tcpIndexes = 0;
                ArrayList<DeviceWrapper> tcpSensors = new ArrayList<>();
                for (DeviceWrapper device : edge.getDevices()) {
                    if (Objects.equals(device.getProtocol(), "opal-tcp")) {
                        tcpIndexes += device.getIndexes().length;
                        if (device.getDevice().isSensitive()) tcpSensors.add(device);
                    }
                }

                int t = 0;
                while (true) {
                    ByteBuffer byteBuffer = ByteBuffer.allocate(tcpIndexes * Float.BYTES + Integer.BYTES + Character.BYTES);
                    float[] values = new float[tcpIndexes];

                    if (runSimulation) {
                        values = getValuesFromCsv(tcpSensors, edge.getLabel(), t, tcpIndexes);
                        if (values == null) {
                            t = 0;
                            continue;
                        }
                        for (DeviceWrapper device : tcpSensors) {
                            if (device.getDevice().isActionable()) {
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
                        for (DeviceWrapper device : tcpSensors) {
                            if (device.getDevice().isActionable()) {
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

                    outputStream = new DataOutputStream(tcpSocket.getOutputStream());
                    byte[] buffer = byteBuffer.array();
                    outputStream.write(buffer);
                    System.out.println("Sent TCP packet.\n");

                    Thread.sleep(timeStep);
                    t += 1;
                }
            } catch (Exception e) {
                System.err.println("Error connecting to TCP server: " + e.getMessage());
                try {
                    if (outputStream != null) {
                        outputStream.close();
                    }
                    if (tcpSocket != null) {
                        tcpSocket.close();
                    }
                    Thread.sleep(5000);
                } catch (IOException | InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }


    //=======================================
    // TCP-Actuators
    //=======================================

    private static void startActuatorsServer() {
        for (Edge edge : edges) {
            int port = edge.getTcpActuatorsPort();
            new Thread(() -> {
                ServerSocket server = null;
                while (true) {
                    try {
                        server = new ServerSocket();
                        server.setReuseAddress(true);
                        server.bind(new InetSocketAddress("0.0.0.0", port));
                        System.out.println("Server running in port " + port + ". Waiting for client requests...");
                        while (true) {
                            Socket clientSocket = server.accept();
                            runClient += 1;
                            System.out.println("Accepted connection from: " + clientSocket.getInetAddress().getHostAddress());
                            handleActuateClient(clientSocket, edge);
                        }
                    } catch (IOException e) {
                        System.err.println("Error in server thread: " + e.getMessage());
                        try {
                            if (server != null && !server.isClosed()) {
                                server.close();
                            }
                        } catch (IOException e1) {
                            System.err.println("Error closing server socket: " + e1.getMessage());
                        }
                        try {
                            Thread.sleep(2000); // Esperar antes de intentar reconectar
                        } catch (InterruptedException ex) {
                            throw new RuntimeException(ex);
                        }
                    }
                }
            }).start();
        }
        System.out.println("");
        System.out.println("Waiting for " + edges.size() + " edges to be connected...");
        System.out.println("");

        while (runClient < edges.size()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }



    /*
     * This method uses a TCP socket to receive actuations and update the corresponding values in each device.
     *
     * @param clientSocket Socket through which we will receive the messages.
     * @param edge
     * */
    private static void handleActuateClient(Socket clientSocket, Edge edge) throws IOException {
        if (edge.getDevices().isEmpty()) return;
        ArrayList<DeviceWrapper> tcpActuators = new ArrayList<>();
        int tcpIndexes = 0;
        for (DeviceWrapper device : edge.getDevices()) {
            if (Objects.equals(device.getProtocol(), "opal-tcp")) {
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
                message.add(newFloat);
                if (newFloat != Float.NEGATIVE_INFINITY) {
                    for (DeviceWrapper actuator : tcpActuators) {
                        for (int i = 0; i < actuator.getIndexes().length; ++i) {
                            if (index == actuator.getIndexes()[i]) {
                                actuator.setValue(newFloat, i);
                            }
                        }
                    }
                }
                index += 1;
            }

            int nInfinity = 0;
            for (Float f : message) {
                if (f == Float.NEGATIVE_INFINITY) nInfinity += 1;
            }
            if (!(nInfinity == tcpIndexes)) {
                System.out.println("    Message is: " + message + " for edge " + edge.getLabel());
                System.out.println("Message Received from " + clientSocket.getInetAddress().getHostAddress());

                if (runSimulation) {
                    writeLog(edge, message);
                }
            }
        }
    }


    //=======================================
    // UTILS
    //=======================================

    private static void writeLog(Edge edge, ArrayList<Float> message) {
        File logFile = null;
        for (File f : logFiles){
            if (f.getName().contains(edge.getLabel())){
                logFile = f;
            }
        }

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");
        String dateTimeString = now.format(formatter);

        StringBuilder line = new StringBuilder();
        line.append(dateTimeString).append(",");
        for (Float val : message) {
            line.append(val).append(",");
        }
        line.deleteCharAt(line.length() - 1);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write(line.toString());
            writer.newLine();
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }


    static void createLogFiles(String simulationName) {
        File logsDirectory = new File("logs");
        if (!logsDirectory.exists()) {
            logsDirectory.mkdirs();
        }

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd_HH-mm-ss");
        String dateTimeString = now.format(formatter);

        for (Edge edge : edges){
            String fileName = simulationName + "_" + dateTimeString + "_" + edge.getLabel() + ".csv";
            File logFile = new File("logs/" + fileName);
            try {
                if (logFile.createNewFile()) {
                    System.out.println("LogFile: " + logFile.getName());

                    StringBuilder line = new StringBuilder();
                    line.append("Time").append(",");
                    for (DeviceWrapper device : edge.getDevices()){
                        if (device.getDevice().isActionable()){
                            int[] indexes = device.getIndexes();
                            for (int i = 0; i < indexes.length; ++i){
                                line.append(device.getLabel()).append("Actuator").append(i).append(",");
                            }
                        }
                    }
                    line.deleteCharAt(line.length() - 1);

                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                        writer.write(line.toString());
                        writer.newLine();
                    } catch (IOException e) {
                        System.err.println("Error writing to log file: " + e.getMessage());
                    }
                    logFiles.add(logFile);
                } else {
                    System.out.println("LogFile already exists.");
                }
            } catch (IOException e) {
                System.out.println("An error occurred while creating the logFile.");
                e.printStackTrace();
            }
        }


    }


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


