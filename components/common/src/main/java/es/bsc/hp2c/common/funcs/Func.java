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
package es.bsc.hp2c.common.funcs;

import java.io.IOException;
import java.util.*;

import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Sensor;
import org.json.JSONArray;
import org.json.JSONObject;
import es.bsc.hp2c.common.utils.EdgeMap;

import static es.bsc.hp2c.common.funcs.FuncUtils.*;
import static es.bsc.hp2c.common.utils.FileUtils.getJsonObject;

/**
 * Represents a user-declared function. The function constructor always receives
 * the same parameters: a list of sensors, a list of actuators, and a JSON array
 * called "others" (containing optional additional parameters). Function
 * classes, as they implement Runnable, must include an implementation of the
 * "run()" method.
 */
public abstract class Func implements Runnable {

    /**
     * Exception raised during the instantiation of the function.
     */
    public static class FunctionInstantiationException extends Exception {
        public FunctionInstantiationException(String message) {
            super(message);
        }

        public FunctionInstantiationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Parse functions from JSON and, for each one, parse and check its triggers.
     *
     * @param setupFile String containing JSON file.
     * @param edgeMap   EdgeMap object containing the devices within each edge.
     */
    public static void loadFunctions(String setupFile, EdgeMap edgeMap, Class<?> runtimeHostClass) throws IOException {
        // Load setup file
        JSONObject object = getJsonObject(setupFile);

        // Get the type of the JSON setup (edge/server)
        String type = object.getJSONObject("global-properties").optString("type", "");
        String edgeLabel = object.getJSONObject("global-properties").optString("label", "");

        // Load specific functions
        JSONArray funcs = object.getJSONArray("funcs");
        for (Object jo : funcs) {
            JSONObject jFunc = (JSONObject) jo;

            // Transform function parameters and triggers if type is edge. Converting to
            // server format
            if ("edge".equals(type)) {
                transformFuncToServerFormat(jFunc, edgeLabel);
            }

            String funcLabel = jFunc.optString("label", "");
            try {
                // Perform Func initialization
                Action action = functionParseJSON(jFunc, edgeMap, funcLabel, runtimeHostClass);
                setupTrigger(jFunc, edgeMap, action);
            } catch (FunctionInstantiationException e) {
                System.err.println("Error loading function " + funcLabel + ": " + e.getMessage());
            }
        }
    }


    public static Map<String, String> loadGlobalFunctions(String setupFile, String defaultsPath,
            Map<String, Device> devices,
            boolean AmqpOn) throws IOException {
        Map<String, String> amqpAggregates = new HashMap<>();
        // Load setup file
        JSONObject object = getJsonObject(setupFile);
        // Load specific devices
        JSONArray jDevices = object.getJSONArray("devices");

        // Load generic file
        JSONObject objectGlobal = getJsonObject(defaultsPath);
        JSONObject jGlobProp = objectGlobal.getJSONObject("global-properties");
        JSONArray jGlobalFuncs = jGlobProp.getJSONArray("funcs");
        for (Object jo : jGlobalFuncs) {
            // Initialize function
            JSONObject jGlobalFuncTemplate = (JSONObject) jo;
            String funcLabel = jGlobalFuncTemplate.optString("label");

            if (funcLabel.toLowerCase().contains("amqp") && !AmqpOn) {
                System.err.println(
                        "AMQP " + funcLabel + " global functions declared but AMQP server is not connected. " +
                                "Skipping " + funcLabel + "...");
                continue;
            }

            // Deploy function for each device building its custom jGlobalFunc
            for (Device device : devices.values()) {
                // Clone global function for this device (avoid overwriting the default
                // parameters like type or interval)
                JSONObject jGlobalFunc = new JSONObject(jGlobalFuncTemplate.toString());
                String amqp_aggregate = "";

                ArrayList<String> deviceList = new ArrayList<>();
                switch (funcLabel) {
                    case "AMQPPublish":
                        if (!device.isSensitive()) break;

                        updateSensorList(jGlobalFunc, device, deviceList);

                        JSONObject jParameters = buildJParameters(device, jGlobalFunc);
                        JSONObject jDevice = findDeviceObject(jDevices, device.getLabel());

                        if (jDevice != null && jDevice.has("properties")) {
                            configureAMQP(jDevice, jGlobalFunc, jParameters, device.getLabel());
                        }
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
                if (!deviceList.isEmpty()) {
                    if (funcLabel.equals("AMQPPublish")) {
                        amqpAggregates.put(deviceList.get(0), amqp_aggregate);
                    }
                } else {
                    continue;
                }
                // Perform Func initialization for each device
                try {
                    // Convert to EdgeMap if type is edge
                    String edgeLabel = object.getJSONObject("global-properties").optString("label", "");
                    EdgeMap edgeMap = null;
                    edgeMap = new EdgeMap();
                    for (Map.Entry<String, Device> entry : devices.entrySet()) {
                        edgeMap.addDevice(edgeLabel, entry.getKey(), entry.getValue());
                    }

                    // Transform to server format
                    transformFuncToServerFormat(jGlobalFunc, edgeLabel);

                    Action action = functionParseJSON(jGlobalFunc, edgeMap, funcLabel, null);
                    setupTrigger(jGlobalFunc, edgeMap, action);
                } catch (FunctionInstantiationException e) {
                    System.err.println("Error initializing general function: " + e.getMessage());
                }
            }
        }
        return amqpAggregates;
    }


    /**
     * Function constructor.
     * 
     * @param sensors   Map of sensors declared for the function.
     * @param actuators Map of actuators declared for the function.
     * @param others    Rest of parameters declared for de function.
     */
    protected Func(Map<String, ArrayList<Sensor<?, ?>>> sensors, Map<String, ArrayList<Actuator<?>>> actuators, JSONObject others) {

    }
}
