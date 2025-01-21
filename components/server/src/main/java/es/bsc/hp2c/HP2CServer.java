/*
 *  Copyright 2002-2023 Barcelona Supercomputing Center (www.bsc.es)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package es.bsc.hp2c;

import es.bsc.hp2c.common.generic.Switch;
import es.bsc.hp2c.common.types.Actuator;
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

import static es.bsc.hp2c.common.utils.FileUtils.*;

/**
 * Implementation of the server logic interacting with an InfluxDB database and
 * with edge devices via AmqpManager.
 */
public class HP2CServer {
    public static AmqpManager amqp;
    public static DatabaseHandler db;
    public static RestListener restServer;
    public static CLI cli;
    private static EdgeHeartbeat heartbeat;
    private static final Map<String, VirtualEdge> edgeMap = new HashMap<>();
    private static boolean verbose = true;
    private static String pathToSetup = "";

    /** Start and run Server modules. */
    public static void main(String[] args) {
        pathToSetup = initPathToSetup(args);
        // Load setup files
        String hostIp = getHostIp();
        // Deploy listener
        try {
            init(hostIp);
            start();
        } catch (IOException | TimeoutException | InterruptedException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    /**
     * Initialize AmqpManager, InfluxDB, and CLI connections.
     * @param hostIp IP of AmqpManager broker and database. TODO: use custom IPs for each module
     */
    public static void init(String hostIp) throws IOException, TimeoutException {
        // Initialize modules
        db = new DatabaseHandler(hostIp);
        amqp = new AmqpManager(hostIp, edgeMap, db);
        heartbeat = new EdgeHeartbeat(amqp, edgeMap);
        restServer = new RestListener(edgeMap);
        cli = new CLI(edgeMap);
    }

    private static String initPathToSetup(String[] args) {
        String pathToSetup = "";

        if (args.length == 1) {
            File fileOrDir = new File(args[0]);

            if (fileOrDir.isDirectory()) {
                File[] jsonFiles = fileOrDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));

                if (jsonFiles != null) {
                    for (File jsonFile : jsonFiles) {
                        try {
                            JSONObject jsonObject = getJsonObject(jsonFile.getAbsolutePath());
                            JSONObject globalProperties = jsonObject.optJSONObject("global-properties");

                            if (globalProperties != null && "server".equals(globalProperties.optString("type"))) {
                                pathToSetup = jsonFile.getAbsolutePath();
                                System.out.println("Selected setup file: " + pathToSetup);
                                return pathToSetup;
                            }
                        } catch (IOException e) {
                            System.err.println("Error reading file: " + jsonFile.getName() + " - " + e.getMessage());
                        }
                    }
                }
                System.err.println("No valid server JSON file found in directory: " + args[0]);
            } else {
                System.err.println("Invalid path provided: " + args[0]);
                System.exit(1);
            }
        } else {
            pathToSetup = "deployments/simple/setup/server.json";
        }
        if (!pathToSetup.isEmpty()) {
            System.out.println("Using server setup file: " + pathToSetup);
        }
        return pathToSetup;
    }


    private static void start() throws IOException, InterruptedException {
        // Start Server modules
        heartbeat.start();
        db.start();
        amqp.startListener();
        restServer.start();
        cli.start();
    }

    private static String getHostIp() {
        // Get IP and setup directory
        String hostIp = System.getenv("LOCAL_IP");
        if (hostIp == null) {
            hostIp = "0.0.0.0";
        }
        return hostIp;
    }


    /** Check actuator validity and return a custom error message upon error.*/
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
     * Check if the combination "edgeLabel" and "deviceName" is in the given nested HashMap
     */
    public static boolean isInMap(String edgeLabel, String deviceName, Map<String, VirtualEdge> edgeMap) {
        if (edgeMap.containsKey(edgeLabel)){
            return edgeMap.get(edgeLabel).containsDevice(deviceName);
        } else {
            return false;
        }
    }

    public static boolean isVerbose() {
        return verbose;
    }

    public static void setVerbose(boolean verbose) {
        HP2CServer.verbose = verbose;
    }

    /**
     * Get a Map containing the devices in every VirtualEdge
     */
    public static EdgeMap getDevicesMap(){
        EdgeMap edgeDevices = new EdgeMap();
        for (VirtualEdge virtualEdge:edgeMap.values()){
            Map<String, VirtualComm.VirtualDevice> devices = virtualEdge.getDeviceMap();
            for (String deviceLabel:devices.keySet()){
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

    public static String getPathToSetup(){
        return pathToSetup;
    }

    public static ArrayList<Sensor> getSensorsByType(String type){
        ArrayList<Sensor> sensors = new ArrayList<>();
        for (VirtualEdge e : edgeMap.values()){
            for (VirtualComm.VirtualDevice d : e.getDeviceMap().values()){
                if (((Device) d).isSensitive() &&
                        Objects.equals(((Device) d).getType(), type)){
                    sensors.add((Sensor) d);
                }
            }
        }
        return sensors;
    }

    public static ArrayList<Device> getDevicesByTypeAndEdge(String type, String edgeLabel){
        ArrayList<Device> devices = new ArrayList<>();
        for (VirtualEdge e : edgeMap.values()){
            for (VirtualComm.VirtualDevice d : e.getDeviceMap().values()){
                if ((Objects.equals(((Device) d).getType(), type))){
                    devices.add((Device) d);
                }
            }
        }
        return devices;
    }

    public static ArrayList<String> getEdgeLabels(){
        ArrayList<String> edgeLabels = new ArrayList<>();
        for (String edgeLabel : edgeMap.keySet()){
            edgeLabels.add(edgeLabel);
        }
        return edgeLabels;
    }

    public static void turnOffSwitches(String edgeLabel) throws IOException {
        if (edgeMap.containsKey(edgeLabel)){
            for (Device d : getDevicesByTypeAndEdge("Switch", edgeLabel)){
                Switch sw = (Switch) d;
                int size = sw.getCurrentValues().length;

                if (!sw.getActuatorAvailability()){
                    System.err.println("Switch is not available"); return;
                }
                Switch.State[] values = new Switch.State[size];

                // Fill the array with the OFF state
                Arrays.fill(values, Switch.State.OFF);
                sw.actuate(values);
            }
        }
    }
}
