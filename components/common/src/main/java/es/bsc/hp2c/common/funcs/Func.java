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
import java.lang.reflect.Constructor;
import java.util.*;

import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Sensor;
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

    // Transform function to server format (edgeLabel-[deviceLabel]
    private static void transformFuncToServerFormat(JSONObject jFunc, String edgeLabel) {
        // Transform parameters section
        JSONObject params = jFunc.getJSONObject("parameters");
        String[] keys = new String[] { "sensors", "actuators" };
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

        if (!triggerParams.has("trigger-sensor")) {
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
                            if (Objects.equals(formatLabel(jD.getString("label")), device.getLabel())) {
                                jDevice = jD;
                                break;
                            }
                        }

                        if (jDevice != null && jDevice.has("properties")) {
                            // Check if a concrete amqp-aggregate is specified for the device
                            amqp_aggregate = jDevice.getJSONObject("properties").optString("amqp-aggregate", "");
                            if (!Objects.equals(amqp_aggregate, "")) {
                                JSONObject jOther = new JSONObject();
                                JSONObject jAggregate = new JSONObject();
                                jAggregate.put("type", amqp_aggregate);
                                jOther.put("aggregate", jAggregate);
                                jGlobalFunc.getJSONObject("parameters").put("other", jOther);
                            } else {
                                amqp_aggregate = jGlobalFunc.getJSONObject("parameters")
                                        .getJSONObject("other")
                                        .getJSONObject("aggregate")
                                        .optString("type", "last");
                            }

                            // Check if aggregate arguments have been declared for the device
                            JSONObject amqp_aggArgs = jDevice.getJSONObject("properties")
                                    .optJSONObject("amqp-agg-args");
                            if (amqp_aggArgs != null) {
                                JSONObject jOther = new JSONObject();
                                JSONObject jAggregate = new JSONObject();
                                jAggregate.put("type", amqp_aggregate);
                                jAggregate.put("args", amqp_aggArgs);
                                jOther.put("aggregate", jAggregate);
                                jGlobalFunc.getJSONObject("parameters").put("other", jOther);
                            }

                            // Check if a concrete amqp-trigger is specified for the device
                            String amqp_trigger = jDevice.getJSONObject("properties").optString("amqp-trigger", "");
                            if (!Objects.equals(amqp_trigger, "")) {
                                jGlobalFunc.getJSONObject("trigger").put("type", amqp_trigger);
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
     * Parse function from JSON and return runnable.
     * 
     * @param jFunc     JSON description of the function.
     * @param edgeMap   EdgeMap object containing the devices within each edge.
     * @param funcLabel Label of the function to address.
     * @return Runnable implementing the function defined.
     * @throws FunctionInstantiationException Error raised during the instantiation
     *                                        of the function.
     */
    public static Action functionParseJSON(JSONObject jFunc, EdgeMap edgeMap, String funcLabel,
            Class<?> runtimeHostClass) throws FunctionInstantiationException {

        String driver = jFunc.optString("method-name", "");
        JSONObject parameters = jFunc.getJSONObject("parameters");
        String lang = jFunc.getString("lang");
        String functionType = jFunc.optString("type", "");

        // Process Sensors
        JSONObject jSensors = parameters.optJSONObject("sensors");
        Map<String, ArrayList<Sensor<?, ?>>> sensors = new HashMap<>();
        if (jSensors != null) {
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
                        if(sensors.containsKey(edgeLabel)) {
                            sensors.get(edgeLabel).add((Sensor<?, ?>) d);
                        }
                        else{
                            ArrayList<Sensor<?,?>> aux = new ArrayList<>();
                            aux.add((Sensor<?, ?>) d);
                            sensors.put(edgeLabel, aux);
                        }
                    } else {
                        throw new FunctionInstantiationException(
                                "Function " + funcLabel + " cannot be instantiated because " + label + " on edge " +
                                        edgeLabel + " is not a sensor");
                    }
                }
            }
        }

        // Process Actuators
        JSONObject jActuators = parameters.optJSONObject("actuators");
        Map<String, ArrayList<Actuator<?>>> actuators = new HashMap<>();
        if (jActuators != null) {

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
                        if(actuators.containsKey(edgeLabel)) {
                            actuators.get(edgeLabel).add((Actuator<?>) d);
                        }
                        else{
                            ArrayList <Actuator<?>> aux = new ArrayList<>();
                            aux.add((Actuator<?>) d);
                            actuators.put(edgeLabel, aux);
                        }
                    } else {
                        throw new FunctionInstantiationException(
                                "Function " + funcLabel + " cannot be instantiated because " + label + " on edge " +
                                        edgeLabel + " is not an actuator");
                    }
                }
            }
        }
        return getAction(funcLabel, driver, parameters, sensors, actuators, lang, functionType, runtimeHostClass);
    }

    private static Action getAction(String funcLabel, String driver, JSONObject parameters,
          Map<String, ArrayList<Sensor<?, ?>>> sensors, Map<String, ArrayList<Actuator<?>>> actuators, String lang,
          String functionType, Class<?> runtimeHostClass) throws FunctionInstantiationException {
        // Use a specific driver for Python funcs if driver is not specified
        if (driver.isEmpty() && (lang.equals("python") || lang.equals("Python") || lang.equals("PYTHON"))) {
            driver = "es.bsc.hp2c.common.funcs.PythonFunc";
        }

        Constructor<?> ct;
        JSONObject jOther;
        try {
            Class<?> c = Class.forName(driver);
            // Handle COMPSs workflow cases
            if (functionType.equals("workflow") && runtimeHostClass != null) {
                if (lang.equalsIgnoreCase("java")) {
                    // Initialize COMPSs Java Handler
                    COMPSsHandler compssHandler = new COMPSsHandler(runtimeHostClass, driver, c);
                    // Instrument class
                    c = compssHandler.instrumentClass(driver);
                } else {
                    throw new UnsupportedOperationException(
                            "Lang " + lang + "for driver " + driver + " is not supported");
                }
            }
            // Get constructor
            ct = c.getConstructor(Map.class, Map.class, JSONObject.class);
            //Instantiate function class
            jOther = parameters.getJSONObject("other");
            Object classInstance = ct.newInstance(sensors, actuators, jOther);

            // Return the action with both instance and class (in case is needed for workflows mode)
            return new Action(classInstance, c);

        } catch (ClassNotFoundException e) {
            throw new FunctionInstantiationException("Error finding the driver " + driver, e);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new FunctionInstantiationException("Error finding the constructor for " + driver, e);
        } catch (Exception e) {
            e.printStackTrace();
            throw new FunctionInstantiationException("Error instantiating " + funcLabel + " function. ", e);
        }
    }

    private static void setupOnReadTrigger(JSONObject triggerParams, EdgeMap edgeMap, Action action, String label,
            boolean onRead) throws FunctionInstantiationException {
        JSONObject jTriggerSensorMap = triggerParams.optJSONObject("trigger-sensor");

        if (jTriggerSensorMap == null) {
            throw new IllegalArgumentException("Trigger-sensor map is required for onRead triggers.");
        }
        HashMap<Sensor<?, ?>, Integer> triggerSensors = new HashMap<>();

        for (String edgeLabel : jTriggerSensorMap.keySet()) {
            JSONArray deviceLabels = jTriggerSensorMap.getJSONArray(edgeLabel);
            for (Object deviceLabelObject : deviceLabels) {
                if (!(deviceLabelObject instanceof String)) {
                    continue;
                }
                String deviceLabel = (String) deviceLabelObject;
                deviceLabel = formatLabel(deviceLabel);

                Device d = edgeMap.getDevice(edgeLabel, deviceLabel);
                if (d != null) {
                    Sensor<?, ?> triggerSensor = (Sensor<?, ?>) edgeMap.getDevice(edgeLabel, deviceLabel);

                    int interval = triggerParams.optInt("interval", 1);
                    if (interval == -1) {
                        interval = triggerSensor.getWindow().getCapacity();
                    }
                    triggerSensors.put(triggerSensor, interval);
                } else {
                    throw new FunctionInstantiationException(
                            "Function " + label + " cannot be instantiated because trigger sensor" + deviceLabel
                                    + " on edge " +
                                    edgeLabel + " was not found"); // if a device is missing the func must be discarded
                                                                   // (awaiting a new edge to come)
                }
            }
        }

        for (Sensor<?, ?> triggerSensor : triggerSensors.keySet()) {
            int interval = triggerSensors.get(triggerSensor);
            triggerSensor.addOnReadFunction(action, interval, label, onRead);
        }
    }

    /**
     * Selects and sets up the triggering method of a Func.
     * Options:
     * onFrequency: sets up a timer to run the Runnable function thread at a given
     * frequency.
     * onRead: adds the Runnable function to the sensor's `addOnReadFunction` so
     * that it is triggered every time
     * the sensor receives a measurement.
     * 
     * @param jFunc   json description of the function.
     * @param edgeMap EdgeMap object containing the devices within each edge.
     * @param action  Runnable that implements the function to trigger.
     */
    public static void setupTrigger(JSONObject jFunc, EdgeMap edgeMap, Action action)
            throws FunctionInstantiationException {
        JSONObject jTrigger = jFunc.getJSONObject("trigger");
        String label = jFunc.optString("label", null);
        String triggerType = jTrigger.optString("type", "");
        JSONObject triggerParams = jTrigger.getJSONObject("parameters");

        // Print Func summary
        printFuncSummary(jFunc, edgeMap, triggerParams, label, triggerType);
        switch (triggerType) {
            case "onFrequency":
                int freq = triggerParams.getInt("frequency");
                new Timer().scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
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
                System.out.println("Wrong trigger: " + triggerType + " defined on the setup file");
        }
    }

    /**
     * Prints summary of a Func in a separate thread so it shows in the main
     * container output
     */
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
                            if (!(deviceLabelObject instanceof String)) {
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
                        "    Type: " + jFunc.optString("type") + "\n" +
                        "    Trigger type: " + triggerType + "\n" +
                        "    Trigger Sensors (onRead): " + "\n\t\t" + (triggerSensorDetails.length() > 0
                                ? triggerSensorDetails.toString()
                                : "None")
                        +
                        "    Interval (onRead): " + triggerParams.optInt("interval") + "\n" +
                        "    Frequency (onFrequency): " + triggerParams.optInt("frequency") + "\n" +
                        "    Other params or aggregation: "
                        + jFunc.getJSONObject("parameters").getJSONObject("other"));
            }
        };
        tSummary.setName("func-summary-thread");
        tSummary.start();
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
