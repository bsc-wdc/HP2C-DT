/**
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

import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.server.modules.AmqpManager;
import es.bsc.hp2c.server.modules.CLI;
import es.bsc.hp2c.server.modules.DatabaseHandler;
import es.bsc.hp2c.server.modules.RestListener;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static es.bsc.hp2c.common.utils.FileUtils.loadDevices;
import static es.bsc.hp2c.common.utils.FileUtils.readEdgeLabel;

/**
 * Implementation of the server logic interacting with an InfluxDB database and
 * with edge devices via AmqpManager.
 */
public class HP2CServer {
    private static AmqpManager amqp;
    public static DatabaseHandler db;
    public static RestListener restServer;
    public static CLI cli;
    private final long dbPort = 8086;
    private static final Map<String, Map<String, Device>> deviceMap = new HashMap<>();
    private static boolean verbose = true;

    /**
     * Constructor of Server instance.
     * Initializes AmqpManager, InfluxDB, and CLI connections.
     * @param hostIp IP of AmqpManager broker and database. TODO: use custom IPs for each
     */
    public HP2CServer(String hostIp) throws IOException, TimeoutException {
        db = new DatabaseHandler(hostIp, dbPort);
        amqp = new AmqpManager(hostIp, deviceMap, db);
        restServer = new RestListener(deviceMap);
        cli = new CLI(deviceMap);
    }

    /** Parse setup files for all edge nodes and deploy Server. */
    public static void main(String[] args) throws FileNotFoundException {
        // Load setup files
        String hostIp = parseSetupFiles(args);
        // Deploy listener
        try {
            HP2CServer server = new HP2CServer(hostIp);
            server.start();
        } catch (IOException | TimeoutException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }

    private void start() throws IOException {
        db.start();
        amqp.startListener();
        restServer.start();
        cli.start();
    }

    /** Parse edge nodes files and configure the edge-device map. */
    private static String parseSetupFiles(String[] args) throws FileNotFoundException {
        // Get IP and setup directory
        String hostIp;
        File setupDir;
        if (args.length == 1) {
            setupDir = new File(args[0]);
            hostIp = System.getenv("LOCAL_IP");
        } else {
            setupDir = new File("../../deployments/testbed/setup");
            hostIp = "0.0.0.0";
        }
        File[] setupFiles = setupDir.listFiles();
        if (setupFiles == null) {
            throw new FileNotFoundException("No setup files found in " + setupDir);
        }
        // Fill in edge-devices map
        for (File setupFile: setupFiles) {
            String filePath = setupFile.toString();
            System.out.println("Loading setup configuration for file " + filePath);
            String edgeLabel = readEdgeLabel(filePath);
            deviceMap.put(edgeLabel, loadDevices(filePath, "driver-dt"));
        }
        return hostIp;
    }

    /** Check actuator validity and return a custom error message upon error.*/
    public static ActuatorValidity checkActuator(String edgeLabel, String actuatorName) {
        // Check if the provided actuator name exists in the map of edge nodes
        StringBuilder msg = new StringBuilder();
        if (!isInMap(edgeLabel, actuatorName, deviceMap)) {
            msg.append("Edge " + edgeLabel + ", Device " + actuatorName + " not listed.\n");
            msg.append("Options are:\n");
            for (HashMap.Entry<String, Map<String, Device>> entry : deviceMap.entrySet()) {
                String groupKey = entry.getKey();
                Map<String, Device> innerMap = entry.getValue();
                msg.append("Group: " + groupKey + "\n");
                for (HashMap.Entry<String, Device> innerEntry : innerMap.entrySet()) {
                    String deviceKey = innerEntry.getKey();
                    if (innerMap.get(deviceKey).isActionable()) {
                        msg.append("  Actuator: " + deviceKey + "\n");
                    }
                }
            }
            return new ActuatorValidity(false, msg.toString());
        }
        // Check if the provided device is an actuator
        Device device = deviceMap.get(edgeLabel).get(actuatorName);
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
    public static boolean isInMap(String edgeLabel, String deviceName, Map<String, Map<String, Device>> map) {
        if (map.containsKey(edgeLabel)){
            if (!map.get(edgeLabel).containsKey(deviceName)) {;
                return false;
            }
        } else {
            return false;
        }
        return true;
    }

    public static boolean isVerbose() {
        return verbose;
    }

    public static void setVerbose(boolean verbose) {
        HP2CServer.verbose = verbose;
    }
}
