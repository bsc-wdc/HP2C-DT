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
package es.bsc.hp2c.common.types;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.*;

import org.json.JSONArray;
import org.json.JSONObject;
import es.bsc.hp2c.common.utils.EdgeMap;
import static es.bsc.hp2c.common.types.Device.formatLabel;
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
    public static void loadFunctions(String setupFile, EdgeMap edgeMap) throws IOException {
        // Load setup file
        JSONObject object = getJsonObject(setupFile);

        // Get the type of the JSON setup (edge/server)
        String type = object.getJSONObject("global-properties").optString("type", "");
        String edgeLabel = object.getJSONObject("global-properties").optString("label", "");

        // Load specific functions
        JSONArray funcs = object.getJSONArray("funcs");
        for (Object jo : funcs) {
            JSONObject jFunc = (JSONObject) jo;

            // Transform function parameters and triggers if type is edge. Converting to server format
            if ("edge".equals(type)) {
                transformFuncToServerFormat(jFunc, edgeLabel);
            }

            String funcLabel = jFunc.optString("label", "");
            try {
                // Perform Func initialization
                Runnable action = functionParseJSON(jFunc, edgeMap, funcLabel);
                setupTrigger(jFunc, edgeMap, action);
            } catch (FunctionInstantiationException e) {
                System.err.println("Error loading function " + funcLabel + ": " + e.getMessage());
            }
        }
    }

    // Transform function to server format (edgeLabel-[deviceLabel]
    private static void transformFuncToServerFormat(JSONObject jFunc, String edgeLabel) {
        // Transform parameters section
        JSONObject params = jFunc.getJSONObject("parameters");
        String[] keys = new String[]{"sensors", "actuators"};
        for (String key : keys) {
            if (params.get(key) instanceof JSONArray) {
                JSONArray originalArray = params.getJSONArray(key);
                JSONObject transformed = new JSONObject();
                transformed.put(edgeLabel, originalArray);
                params.put(key, transformed);
            }
        }

        // Transform trigger section
        JSONObject triggerParams = jFunc.getJSONObject("trigger").getJSONObject("parameters");

        if (!triggerParams.has("trigger-sensor")){
            return;
        }
        Object triggerSensor = triggerParams.get("trigger-sensor");
        if (!(triggerSensor instanceof JSONArray)) {
            throw new IllegalArgumentException("Expected 'trigger-sensor' to be a JSONArray, but found: "
                    + triggerSensor.getClass().getSimpleName());
        }
        JSONArray triggerSensorArray = (JSONArray) triggerSensor;
        JSONObject transformed = new JSONObject();
        transformed.put(edgeLabel, triggerSensorArray);
        triggerParams.put("trigger-sensor", transformed);
    }


    public static Map<String, String> loadGlobalFunctions(String setupFile, String defaultsPath, Map<String, Device> devices,
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
                // Clone global function for this device (avoid overwriting the default parameters like type or interval)
                JSONObject jGlobalFunc = new JSONObject(jGlobalFuncTemplate.toString());
                String amqp_aggregate = "";

                ArrayList<String> deviceList = new ArrayList<>();
                switch (funcLabel) {
                    case "AMQPPublish":
                        if (!device.isSensitive()) {
                            continue;
                        }

                        deviceList.add(device.getLabel());
                        jGlobalFunc.getJSONObject("parameters").put("sensors", deviceList);

                        // Modify trigger parameters for this device
                        JSONObject jParameters = new JSONObject();
                        jParameters = jGlobalFunc.getJSONObject("trigger").optJSONObject("parameters");
                        JSONArray triggerSensorArray = new JSONArray();
                        triggerSensorArray.put(device.getLabel());
                        jParameters.put("trigger-sensor", triggerSensorArray);

                        // Check for device-specific properties
                        JSONObject jDevice = null;
                        for (Object d : jDevices) {
                            JSONObject jD = (JSONObject) d;
                            if (Objects.equals(jD.getString("label").replace(" ", "").replace("-", ""), device.getLabel())) {
                                jDevice = jD;
                                break;
                            }
                        }

                        if (jDevice != null && jDevice.has("properties")) {
                            // Check if a concrete amqp-aggregate is specified for the device
                            amqp_aggregate = jDevice.getJSONObject("properties").optString("amqp-aggregate", "");
                            if (!Objects.equals(amqp_aggregate, "")) {
                                ArrayList<String> other = new ArrayList<>();
                                other.add(amqp_aggregate);
                                jGlobalFunc.getJSONObject("parameters").put("other", other);
                            } else {
                                JSONArray aggregate = jGlobalFunc.getJSONObject("parameters").getJSONArray("other");

                                if (!aggregate.isEmpty()) {
                                    amqp_aggregate = aggregate.getString(0);
                                }
                            }

                            // Check if a concrete amqp-type is specified for the device
                            String amqp_type = jDevice.getJSONObject("properties").optString("amqp-type", "");
                            if (!Objects.equals(amqp_type, "")) {
                                jGlobalFunc.getJSONObject("trigger").put("type", amqp_type);
                            }

                            // Check if a concrete amqp-interval is specified for the device
                            int amqp_interval = jDevice.getJSONObject("properties").optInt("amqp-interval", -1);
                            if (amqp_interval != -1) {
                                jParameters.put("interval", amqp_interval);
                            }
                            // Check if a concrete amqp-frequency is specified for the device
                            int amqp_frequency = jDevice.getJSONObject("properties").optInt("amqp-frequency", -1);
                            if (amqp_frequency != -1) {
                                jParameters.put("frequency", amqp_frequency);
                            }
                        }

                        jGlobalFunc.getJSONObject("trigger").put("parameters", jParameters);
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

                    //Transform to server format
                    transformFuncToServerFormat(jGlobalFunc, edgeLabel);

                    Runnable action = functionParseJSON(jGlobalFunc, edgeMap, funcLabel);
                    setupTrigger(jGlobalFunc, edgeMap, action);
                } catch (FunctionInstantiationException e) {
                    System.err.println("Error initializing general function: " + e.getMessage());
                }
            }
        }
        return amqpAggregates;
    }

    /**
     * Parse function from JSON and return runnable.
     * 
     * @param jFunc     JSON description of the function.
     * @param edgeMap   EdgeMap object containing the devices within each edge.
     * @param funcLabel Label of the function to address.
     * @return Runnable implementing the function defined.
     * @throws FunctionInstantiationException Error raised during the instantiation
     *                                        of the function.
     */
    public static Runnable functionParseJSON(JSONObject jFunc, EdgeMap edgeMap, String funcLabel)
            throws FunctionInstantiationException {

        String driver = jFunc.optString("method-name", "");
        JSONObject parameters = jFunc.getJSONObject("parameters");

        // Process Sensors
        JSONObject jSensors = parameters.getJSONObject("sensors");
        ArrayList<Sensor<?, ?>> sensors = new ArrayList<>();

        // Search the Sensors for every edge
        for (String edgeLabel : jSensors.keySet()) {
            JSONArray edgeSensors = jSensors.getJSONArray(edgeLabel);
            for (int i = 0; i < edgeSensors.length(); i++) {
                String label = edgeSensors.getString(i);
                label = formatLabel(label);
                Device d = edgeMap.getDevice(edgeLabel, label);
                if (d == null) {
                    throw new FunctionInstantiationException(
                            "Function " + funcLabel + " cannot be instantiated because " + label + " on edge " +
                                    edgeLabel + " was not found");
                }
                if (d.isSensitive()) {
                    sensors.add((Sensor<?, ?>) d);
                } else {
                    throw new FunctionInstantiationException(
                            "Function " + funcLabel + " cannot be instantiated because " + label + " on edge " +
                                    edgeLabel + " is not a sensor");
                }
            }
        }

        // Process Actuators
        JSONObject jActuators = parameters.getJSONObject("actuators");
        ArrayList<Actuator<?>> actuators = new ArrayList<>();

        // Search the Actuators for every edge
        for (String edgeLabel : jActuators.keySet()) {
            JSONArray edgeActuators = jActuators.getJSONArray(edgeLabel);
            for (int i = 0; i < edgeActuators.length(); i++) {
                String label = edgeActuators.getString(i);
                label = formatLabel(label);
                Device d = edgeMap.getDevice(edgeLabel, label);
                if (d == null) {
                    throw new FunctionInstantiationException(
                            "Function " + funcLabel + " cannot be instantiated because " + label + " on edge " +
                                    edgeLabel + " was not found");
                }
                if (d.isActionable()) {
                    actuators.add((Actuator<?>) d);
                } else {
                    throw new FunctionInstantiationException(
                            "Function " + funcLabel + " cannot be instantiated because " + label + " on edge " +
                                    edgeLabel + " is not an actuator");
                }
            }
        }

        return getAction(funcLabel, driver, parameters, sensors, actuators);
    }


    private static Runnable getAction(String funcLabel, String driver, JSONObject parameters,
                                      ArrayList<Sensor<?, ?>> sensors, ArrayList<Actuator<?>> actuators)
            throws FunctionInstantiationException {
        Constructor<?> ct;
        JSONArray jOther;

        try {
            Class<?> c = Class.forName(driver);
            ct = c.getConstructor(ArrayList.class, ArrayList.class, JSONArray.class);
            jOther = parameters.getJSONArray("other");
            Runnable action = (Runnable)ct.newInstance(sensors, actuators, jOther);
            return action;

        } catch (ClassNotFoundException e) {
            throw new FunctionInstantiationException("Error finding the driver " + driver, e);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new FunctionInstantiationException("Error finding the constructor for " + driver, e);
        } catch (Exception e) {
            e.printStackTrace();
            throw new FunctionInstantiationException("Error instantiating " + funcLabel + " function. ", e);
        }
    }


    private static void setupOnReadTrigger(JSONObject triggerParams, EdgeMap edgeMap, Runnable action, String label, boolean onRead) {
        System.out.println("Setup on read trigger:::::::::::::");
        JSONObject jTriggerSensorMap = triggerParams.optJSONObject("trigger-sensor");

        if (jTriggerSensorMap == null) {
            throw new IllegalArgumentException("Trigger-sensor map is required for onRead triggers.");
        }

        for (String edgeLabel : jTriggerSensorMap.keySet()) {
            JSONArray deviceLabels = jTriggerSensorMap.getJSONArray(edgeLabel);
            for (Object deviceLabelObject : deviceLabels) {
                if (!(deviceLabelObject instanceof String)) {
                    continue;
                }
                String deviceLabel = (String) deviceLabelObject;
                deviceLabel = formatLabel(deviceLabel);
                Sensor<?, ?> triggerSensor = (Sensor<?, ?>) edgeMap.getDevice(edgeLabel, deviceLabel);

                int interval = triggerParams.optInt("interval", -1);
                if (interval == -1) {
                    interval = triggerSensor.getWindow().getSize();
                }
                
                triggerSensor.addOnReadFunction(action, interval, label, onRead);
            }
        }
    }


    /**
     * Selects and sets up the triggering method of a Func.
     * Options:
     *      onFrequency: sets up a timer to run the Runnable function thread at a given frequency.
     *      onRead: adds the Runnable function to the sensor's `addOnReadFunction` so that it is triggered every time
     *          the sensor receives a measurement.
     * 
     * @param jFunc   json description of the function.
     * @param edgeMap EdgeMap object containing the devices within each edge.
     * @param action  Runnable that implements the function to trigger.
     */
    public static void setupTrigger(JSONObject jFunc, EdgeMap edgeMap, Runnable action) {
        JSONObject jTrigger = jFunc.getJSONObject("trigger");
        String label = jFunc.optString("label", null);
        String triggerType = jTrigger.optString("type", "");
        JSONObject triggerParams = jTrigger.getJSONObject("parameters");

        // Print Func summary
        printFuncSummary(jFunc, edgeMap, triggerParams, label, triggerType);
        switch (triggerType) {
            case "onFrequency":
                int freq = triggerParams.getInt("frequency") * 1000;
                new Timer().scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        System.out.println("label: " + label + ".  frequency: " + freq);
                        action.run();
                    }
                }, 0, freq);
                break;

            case "onRead":
                setupOnReadTrigger(triggerParams, edgeMap, action, label, true);
                break;

            case "onChange":
                setupOnReadTrigger(triggerParams, edgeMap, action, label, false);
                break;

            case "onStart":
                action.run();
                break;

            default:
                System.out.print("Wrong trigger: " + triggerType + " defined on the setup file");
        }
    }

    /** Prints summary of a Func in a separate thread so it shows in the main container output */
    private static void printFuncSummary(JSONObject jFunc, EdgeMap edgeMap, JSONObject triggerParams,
                                         String label, String triggerType) {
        Thread tSummary = new Thread() {
            public void run() {
                JSONObject jTriggerSensorMap = triggerParams.optJSONObject("trigger-sensor");
                StringBuilder triggerSensorDetails = new StringBuilder();
                Integer windowLength = null;

                if (jTriggerSensorMap != null) {
                    for (String edgeLabel : jTriggerSensorMap.keySet()) {
                        JSONArray deviceLabels = jTriggerSensorMap.getJSONArray(edgeLabel);
                        for (Object deviceLabelObject : deviceLabels) {
                            if (!(deviceLabelObject instanceof String)){
                                continue;
                            }
                            String deviceLabel = (String) deviceLabelObject;
                            deviceLabel = formatLabel(deviceLabel);
                            Device device = edgeMap.getDevice(edgeLabel, deviceLabel);

                            Sensor<?, ?> triggerSensor = (Sensor<?, ?>) device;
                            try {
                                windowLength = triggerSensor.getWindow().getCapacity();
                            } catch (Exception e) {
                                windowLength = null;
                            }

                            triggerSensorDetails.append("Edge: ").append(edgeLabel)
                                    .append(", Sensor: ").append(deviceLabel).append(", Window length: ")
                                    .append(windowLength).append("\n");
                        }
                    }
                }

                System.out.println("Loading Func " + label + "\n" +
                        "    Type: " + triggerType + "\n" +
                        "    Trigger Sensors (onRead): " + "\n\t\t" + (triggerSensorDetails.length() > 0
                        ? triggerSensorDetails.toString() : "None") +
                        "    Interval (onRead): " + triggerParams.optInt("interval") + "\n" +
                        "    Frequency (onFrequency): " + triggerParams.optInt("frequency") + "\n" +
                        "    Other params or aggregation: "
                        + jFunc.getJSONObject("parameters").getJSONArray("other"));
            }
        };
        tSummary.setName("func-summary-thread");
        tSummary.start();
    }


    /**
     * Function constructor.
     * 
     * @param sensors  List of sensors declared for the function.
     * @param actuators List of actuators declared for the function.
     * @param others  Rest of parameters declared for de function.
     */
    protected Func(ArrayList<Sensor<?,?>> sensors, ArrayList<Actuator<?>> actuators, JSONArray others){

    }
}
