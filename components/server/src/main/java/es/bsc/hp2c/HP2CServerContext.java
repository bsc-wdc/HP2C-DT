package es.bsc.hp2c;

import es.bsc.hp2c.common.generic.Switch;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Sensor;
import es.bsc.hp2c.common.utils.EdgeMap;
import es.bsc.hp2c.server.device.VirtualComm;
import es.bsc.hp2c.server.edge.VirtualEdge;
import es.bsc.hp2c.server.modules.*;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeoutException;

import static es.bsc.hp2c.common.utils.COMPSsUtils.setResources;
import static es.bsc.hp2c.common.utils.FileUtils.getJsonObject;

public class HP2CServerContext {
    public static AmqpManager amqp;
    public static DatabaseHandler db;
    public static RestListener restServer;
    public static CLI cli;
    private static EdgeHeartbeat heartbeat;
    private static AlarmHandler alarms;
    private static final Map<String, VirtualEdge> edgeMap = new HashMap<>();
    private static boolean verbose = true;
    private static boolean useMetrics = false;
    private static String pathToSetup = "deployments/simple/setup/server.json";
    private static MetricsHandler metrics = null;

    public static void parseArgs(String[] args) throws IOException {
        // The server is running on docker
        File dockerEnv = new File(".dockerenv");
        if (dockerEnv.isFile()) {
            pathToSetup = "/data/setup.json"; // take the path mounted in deploy_server.sh
            String useMetricsString = System.getenv("ENABLE_METRICS"); // take the env declared in deploy_server.sh
            useMetrics = (useMetricsString != null && useMetricsString.equals("1"));
        }

        // The server is running locally
        else {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--setup_path":
                        if (i + 1 < args.length) {
                            pathToSetup = validateSetupPath(args[++i]);
                        } else {
                            System.err.println("Error: --setup_path requires a value.");
                            System.exit(1);
                        }
                        break;
                    case "--metrics":
                    case "-m":
                        useMetrics = true;
                        break;
                    default:
                        System.err.println("Warning: Ignoring unrecognized argument: " + args[i]);
                }
            }
        }
        System.out.println("Setup file: " + pathToSetup);
        System.out.println("Use metrics: " + useMetrics);

        // Add resources to server agent
        setResources(pathToSetup);
    }

    /**
     * Initialize AmqpManager, InfluxDB, and CLI connections.
     * 
     * @param hostIp IP of AmqpManager broker and database. TODO: use custom IPs for
     *               each module
     */
    public static void init(String hostIp, Class<?> runtimeHost) throws IOException, TimeoutException {

        // Initialize modules
        db = new DatabaseHandler(hostIp);
        alarms = new AlarmHandler(pathToSetup, db);
        amqp = new AmqpManager(hostIp, edgeMap, db);
        heartbeat = new EdgeHeartbeat(amqp, edgeMap, runtimeHost);
        restServer = new RestListener(edgeMap);
        cli = new CLI(edgeMap);
        if (useMetrics) {
            metrics = new MetricsHandler();
        }
    }

    private static String validateSetupPath(String path) {
        File fileOrDir = new File(path);
        if (fileOrDir.isDirectory()) {
            File[] jsonFiles = fileOrDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
            if (jsonFiles != null) {
                for (File jsonFile : jsonFiles) {
                    try {
                        JSONObject jsonObject = getJsonObject(jsonFile.getAbsolutePath());
                        JSONObject globalProperties = jsonObject.optJSONObject("global-properties");
                        if (globalProperties != null && "server".equals(globalProperties.optString("type"))) {
                            System.out.println("Selected setup file: " + jsonFile.getAbsolutePath());
                            return jsonFile.getAbsolutePath();
                        }
                    } catch (IOException e) {
                        System.err.println("Error reading file: " + jsonFile.getName() + " - " + e.getMessage());
                    }
                }
            }
            System.err.println("No valid server JSON file found in directory: " + path);
        } else {
            System.err.println("Invalid path provided: " + path);
            System.exit(1);
        }
        return pathToSetup;
    }

    public static void start() throws IOException, InterruptedException {
        // Start Server modules
        heartbeat.start();
        db.start();
        amqp.startListener();
        restServer.start();
    }

    public static String getHostIp() {
        // Get IP and setup directory
        String hostIp = System.getenv("LOCAL_IP");
        if (hostIp == null) {
            hostIp = "0.0.0.0";
        }
        return hostIp;
    }

    /** Check actuator validity and return a custom error message upon error. */
    public static ActuatorValidity checkActuator(String edgeLabel, String actuatorName) {
        // Check if the provided actuator name exists in the map of edge nodes
        StringBuilder msg = new StringBuilder();
        if (!isInMap(edgeLabel, actuatorName, edgeMap)) {
            msg.append("Edge " + edgeLabel + ", Device " + actuatorName + " not listed.\n");
            msg.append("Options are:\n");
            for (VirtualEdge edge : edgeMap.values()) {
                msg.append("Group: " + edgeLabel + "\n");
                for (String deviceLabel : edge.getDeviceLabels()) {
                    Device d = (Device) edge.getDevice(deviceLabel);
                    if (d.isActionable()) {
                        msg.append("  Actuator: " + deviceLabel + "\n");
                    }
                }
            }
            return new ActuatorValidity(false, msg.toString());
        }
        // Check if the provided device is an actuator
        Device device = (Device) edgeMap.get(edgeLabel).getDevice(actuatorName);
        if (!device.isActionable()) {
            msg.append("Device " + edgeLabel + "." + actuatorName + " is not an actuator.\n");
            return new ActuatorValidity(false, msg.toString());
        }
        return new ActuatorValidity(true, msg.toString());
    }

    /** Auxiliary class to use with checkActuator. */
    public static class ActuatorValidity {
        boolean isValid;
        String msg;

        ActuatorValidity(boolean isValid, String msg) {
            this.isValid = isValid;
            this.msg = msg;
        }

        public boolean isValid() {
            return isValid;
        }

        public String getMessage() {
            return msg;
        }
    }

    /**
     * Check if the combination "edgeLabel" and "deviceName" is in the given nested
     * HashMap
     */
    public static boolean isInMap(String edgeLabel, String deviceName, Map<String, VirtualEdge> edgeMap) {
        if (edgeMap.containsKey(edgeLabel)) {
            return edgeMap.get(edgeLabel).containsDevice(deviceName);
        } else {
            return false;
        }
    }

    public static boolean isVerbose() {
        return verbose;
    }

    public static void setVerbose(boolean v) {
        verbose = v;
    }

    /**
     * Get a Map containing the devices in every VirtualEdge
     */
    public static EdgeMap getDevicesMap() {
        EdgeMap edgeDevices = new EdgeMap();
        for (VirtualEdge virtualEdge : edgeMap.values()) {
            Map<String, VirtualComm.VirtualDevice> devices = virtualEdge.getDeviceMap();
            for (String deviceLabel : devices.keySet()) {
                edgeDevices.addDevice(virtualEdge.getLabel(), deviceLabel, (Device) devices.get(deviceLabel));
            }
        }
        return edgeDevices;
    }

    public static VirtualEdge findEdgeByDevice(Device device) {
        for (VirtualEdge edge : edgeMap.values()) {
            if (edge.getDeviceMap().equals(device)) {
                return edge;
            }
        }
        return null;
    }

    public static String getPathToSetup() {
        return pathToSetup;
    }

    public static ArrayList<Sensor> getSensorsByType(String type) {
        ArrayList<Sensor> sensors = new ArrayList<>();
        for (VirtualEdge e : edgeMap.values()) {
            for (VirtualComm.VirtualDevice d : e.getDeviceMap().values()) {
                if (((Device) d).isSensitive() &&
                        Objects.equals(((Device) d).getType(), type)) {
                    sensors.add((Sensor) d);
                }
            }
        }
        return sensors;
    }

    public static ArrayList<Device> getDevicesByTypeAndEdge(String type, String edgeLabel) {
        ArrayList<Device> devices = new ArrayList<>();
        if (edgeMap.containsKey(edgeLabel)) {
            VirtualEdge e = edgeMap.get(edgeLabel);
            for (VirtualComm.VirtualDevice d : e.getDeviceMap().values()) {
                Device device = (Device) d;
                if ((Objects.equals(device.getType(), type))) {
                    devices.add(device);
                }
            }
        }
        return devices;
    }

    public static ArrayList<String> getEdgeLabels() {
        ArrayList<String> edgeLabels = new ArrayList<>();
        for (String edgeLabel : edgeMap.keySet()) {
            edgeLabels.add(edgeLabel);
        }
        return edgeLabels;
    }

    public static void turnOffSwitches(String edgeLabel) throws IOException {
        if (edgeMap.containsKey(edgeLabel)) {
            for (Device d : getDevicesByTypeAndEdge("Switch", edgeLabel)) {
                Switch sw = (Switch) d;
                int size = sw.getCurrentValues().length;

                if (!sw.getActuatorAvailability()) {
                    System.err.println("Switch is not available");
                    return;
                }
                Switch.State[] values = new Switch.State[size];

                // Fill the array with the OFF state
                Arrays.fill(values, Switch.State.OFF);
                sw.actuate(values);
            }
        }
    }

    public static AlarmHandler getAlarms() {
        return alarms;
    }

    public static DatabaseHandler getDB() {
        return db;
    }

    public static MetricsHandler getMetrics() {
        return metrics;
    }
}
