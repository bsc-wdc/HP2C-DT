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

import es.bsc.hp2c.edge.opalrt.OpalComm;
import es.bsc.hp2c.common.types.Device;
import static es.bsc.hp2c.common.utils.FileUtils.loadDevices;
import static es.bsc.hp2c.common.utils.FileUtils.readEdgeLabel;

import es.bsc.hp2c.edge.funcs.Func;
import es.bsc.hp2c.edge.funcs.Func.FunctionInstantiationException;

import com.rabbitmq.client.*;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Contains the main method and two additional methods useful for parsing and
 * storing the devices and functions.
 */
public class HP2CEdge {
    private static String edgeLabel;
    private static final String EXCHANGE_NAME = "measurements";
    private static Connection connection;
    private static Channel channel;
    private static boolean amqpOn = true;

    /**
     * Obtain map of devices and load functions.
     * 
     * @param args Setup file.
     */
    public static void main(String[] args) throws FileNotFoundException{
        // Get input data
        String setupFile;
        if (args.length == 1) {
            setupFile = args[0];
        } else {
            setupFile = "../../deployments/testbed/setup/edge1.json";
        }
        String localIP = System.getenv("LOCAL_IP");

        // Set up AMQP connections
        setUpMessaging(localIP);

        // Load devices and functions
        edgeLabel = readEdgeLabel(setupFile);
        Map<String, Device> devices = loadDevices(setupFile);
        OpalComm.setLoadedDevices(true);
        loadFunctions(setupFile, devices);
    }

    private static void setUpMessaging(String ip) {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(ip);
            connection = factory.newConnection();
            channel = connection.createChannel();
            channel.exchangeDeclare(EXCHANGE_NAME, "topic");
            System.out.println("AMQP connection started.");
        } catch (IOException | TimeoutException e) {
            amqpOn = false;
            System.err.println("Error initializing messaging: " + e.getMessage());
            System.err.println("Continuing without AMQP connection.");
        }
    }

    /**
     * Parse functions from JSON and, for each one, parse and check its triggers.
     * 
     * @param setupFile String containing JSON file.
     * @param devices   map of the devices within the edge.
     */
    public static void loadFunctions(String setupFile, Map<String, Device> devices) throws FileNotFoundException {
        InputStream is = new FileInputStream(setupFile);

        JSONTokener tokener = new JSONTokener(is);
        JSONObject object = new JSONObject(tokener);

        // Load specific functions
        try {
            JSONArray funcs = object.getJSONArray("funcs");
            for (Object jo : funcs) {
                JSONObject jFunc = (JSONObject) jo;
                String funcLabel = jFunc.optString("label", "");
                // Perform Func initialization
                try {
                    Runnable action = Func.functionParseJSON(jFunc, devices, funcLabel);
                    Func.setupTrigger(jFunc, devices, action);
                } catch (FunctionInstantiationException e) {
                    System.err.println("Error loading function. " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Specific funcs might not be available in setup: " + e.getMessage());
        }

        // Load generic functions
        JSONObject jGlobProp = object.getJSONObject("global-properties");
        JSONArray jGlobalFuncs = jGlobProp.getJSONArray("funcs");
        for (Object jo: jGlobalFuncs) {
            // Initialize function
            JSONObject jGlobalFunc = (JSONObject) jo;
            String funcLabel = jGlobalFunc.optString("label");
            if (funcLabel.toLowerCase().contains("amqp") && !amqpOn) {
                System.err.println(
                        "AMQP " + funcLabel + " global functions declared but AMQP server is not connected. " +
                        "Skipping " + funcLabel + "...");
                continue;
            }
            // Deploy function for each device building its custom jGlobalFunc
            for (Device device: devices.values()) {
                ArrayList<String> deviceList = new ArrayList<>();
                switch (funcLabel) {
                    case "AMQPPublish":
                        // Conditions to skip device
                        if (!device.isSensitive()) {
                            continue;
                        }
                        // Create ArrayList of a single device and set it to the JSONObject
                        deviceList.add(device.getLabel());
                        jGlobalFunc.getJSONObject("parameters").put("sensors", deviceList);
                        // Do same modification to triggers in JSON
                        jGlobalFunc.getJSONObject("trigger").put("parameters", deviceList);
                        break;
                    case "AMQPConsume":
                        // Conditions to skip device
                        if (!device.isActionable()) {
                            continue;
                        }
                        // Create ArrayList of a single device and set it to the JSONObject
                        deviceList.add(device.getLabel());
                        jGlobalFunc.getJSONObject("parameters").put("actuators", deviceList);
                        // No triggers used for actuators, function triggers on start
                        // jGlobalFunc.getJSONObject("trigger").put("parameters", actuatorList);
                        break;
                    default:
                        continue;
                }
                // Perform Func initialization for each device
                if (deviceList.isEmpty()) {
                    continue;
                }
                try {
                    Runnable action = Func.functionParseJSON(jGlobalFunc, devices, funcLabel);
                    Func.setupTrigger(jGlobalFunc, devices, action);
                } catch (FunctionInstantiationException e) {
                    System.err.println("Error initializing general function: " + e.getMessage());
                }
            }
        }
    }

    public static String getEdgeLabel() {
        return edgeLabel;
    }

    public static Connection getConnection() {
        return connection;
    }

    public static Channel getChannel() {
        return channel;
    }

    public static String getExchangeName() {
        return EXCHANGE_NAME;
    }
}
