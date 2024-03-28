package es.bsc.hp2c.opalSimulator;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

import es.bsc.hp2c.common.types.Sensor;
import es.bsc.hp2c.common.types.Device;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import static es.bsc.hp2c.common.utils.FileUtils.loadDevices;

public class OpalSim {
    private static ArrayList<Edge> edges;
    private static boolean runSimulation = false;

    public static void main(String[] args) throws IOException {
        String deploymentName = args[0];
        String deploymentFile = "../../deployments/" + deploymentName + "/setup/";
        System.out.println("Using deployment name: " + deploymentName);
        if (args.length > 1){
            String simulationName = args[1];
            String simulationFile = "simulations/" + simulationName;
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
            Map<String, Device> devices = loadDevices(pathToEdge);
            devicesWrapped = joinDevices(devicesWrapped, devices);
            edge.setDevices(devicesWrapped);
            edges.add(edge);
        }


    }

    private static ArrayList<DeviceWrapper> joinDevices(ArrayList<DeviceWrapper> devicesWrapped, Map<String, Device> devices) {
        for (DeviceWrapper deviceWrapper : devicesWrapped) {
            String label = deviceWrapper.getLabel();

            if (devices.containsKey(label)) {
                Device device = devices.get(label);
                Sensor<?,?> sensor = (Sensor<?,?>) device;
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
            String label = jDevice.getString("label");

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
        JSONObject jComms = getJComms(pathToEdge);

        JSONObject jUDP = jComms.getJSONObject("opal-udp");
        JSONObject jTCP= jComms.getJSONObject("opal-tcp");

        JSONObject jUDPSensors = jUDP.getJSONObject("sensors");
        int udpSensorsPort = jUDPSensors.getInt("port");

        JSONObject jTCPSensors = jTCP.getJSONObject("sensors");
        int tcpSensorsPort = jTCPSensors.getInt("port");

        JSONObject jTCPActuators = jTCP.getJSONObject("actuators");
        int tcpAcuatorsPort = jTCPActuators.getInt("port");

        return new Edge(tcpSensorsPort, tcpAcuatorsPort, udpSensorsPort);
    }

    private static JSONObject getJComms(String pathToEdge) throws IOException {
        InputStream is = new FileInputStream(pathToEdge);
        JSONTokener tokener = new JSONTokener(is);
        JSONObject object = new JSONObject(tokener);
        JSONObject jGlobProp = object.getJSONObject("global-properties");
        return jGlobProp.getJSONObject("comms");
    }
}


class Edge {
    private final int tcpSensorsPort;
    private final int tcpActuatorsPort;
    private final int udpSensorsPort;
    private ArrayList<DeviceWrapper> devices;

    public Edge(int tcpSensorsPort, int tcpActuatorsPort, int udpSensorsPort) {
        this.tcpSensorsPort = tcpSensorsPort;
        this.tcpActuatorsPort = tcpActuatorsPort;
        this.udpSensorsPort = udpSensorsPort;
        this.devices = new ArrayList<>();
    }

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

