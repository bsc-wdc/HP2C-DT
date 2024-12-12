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
import org.json.JSONException;
import org.json.JSONObject;

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
     * @param devices   map of the devices within the edge.
     */
    public static void loadFunctions(String setupFile, Map<String, Device> devices) throws IOException {
        // Load setup file
        JSONObject object = getJsonObject(setupFile);
        // Load specific functions
        JSONArray funcs = object.getJSONArray("funcs");
        for (Object jo : funcs) {
            JSONObject jFunc = (JSONObject) jo;
            String funcLabel = jFunc.optString("label", "");
            // Perform Func initialization
            try {
                Runnable action = functionParseJSON(jFunc, devices, funcLabel);
                setupTrigger(jFunc, devices, action);
            } catch (FunctionInstantiationException e) {
                System.err.println("Error loading function " + funcLabel + ": " + e.getMessage());
            }
        }
    }

    public static void loadGlobalFunctions(String setupFile, String defaultsPath, Map<String, Device> devices,
                                           boolean AmqpOn) throws IOException {
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
                // Clone global function for this device
                JSONObject jGlobalFunc = new JSONObject(jGlobalFuncTemplate.toString());

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
                        jParameters.put("trigger-sensor", device.getLabel());
                        jParameters.put("interval", jGlobalFunc.getJSONObject("trigger").optJSONObject("parameters").optInt("interval", -1));

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
                            String amqp_aggregate = jDevice.getJSONObject("properties").optString("amqp-aggregate", "");
                            if (!Objects.equals(amqp_aggregate, "")) {
                                ArrayList<String> other = new ArrayList<>();
                                other.add(amqp_aggregate);
                                jGlobalFunc.getJSONObject("parameters").put("other", other);
                            }

                            String amqp_type = jDevice.getJSONObject("properties").optString("amqp-type", "");
                            if (!Objects.equals(amqp_type, "")) {
                                jGlobalFunc.getJSONObject("trigger").put("type", amqp_type);
                            }

                            int amqp_interval = jDevice.getJSONObject("properties").optInt("amqp-interval", -1);
                            if (amqp_interval != -1) {
                                jParameters.put("interval", amqp_interval);
                            }
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

                // Perform Func initialization for each device
                if (deviceList.isEmpty()) {
                    continue;
                }
                try {
                    Runnable action = functionParseJSON(jGlobalFunc, devices, funcLabel);
                    setupTrigger(jGlobalFunc, devices, action);
                } catch (FunctionInstantiationException e) {
                    System.err.println("Error initializing general function: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Parse function from JSON and return runnable.
     * 
     * @param jFunc     JSON description of the function.
     * @param devices   Map of the devices within the edge.
     * @param funcLabel Label of the function to address.
     * @return Runnable implementing the function defined.
     * @throws FunctionInstantiationException Error raised during the instantiation
     *                                        of the function.
     */
    public static Runnable functionParseJSON(JSONObject jFunc, Map<String, Device> devices,
            String funcLabel)
            throws FunctionInstantiationException {

        String driver = jFunc.optString("method-name", "");
        JSONObject parameters = jFunc.getJSONObject("parameters");
        JSONArray jSensors = parameters.getJSONArray("sensors");
        JSONArray jActuators = parameters.getJSONArray("actuators");

        ArrayList<Sensor> sensors = new ArrayList<>();
        for (int i = 0; i < jSensors.length(); i++) {
            String label = jSensors.getString(i);
            label = formatLabel(label);
            Device d = devices.get(label);
            if (d.isSensitive()) {
                sensors.add((Sensor<?,?>) d);
            } else {
                throw new FunctionInstantiationException(
                        "Function " + funcLabel + " cannot be instantiated because " + label + " is not a sensor");
            }
        }
        ArrayList<Actuator<?>> actuators = new ArrayList<>();
        for (int i = 0; i < jActuators.length(); i++) {
            String label = jActuators.getString(i);
            label = formatLabel(label);
            Device d = devices.get(label);
            if (d == null){
                throw new FunctionInstantiationException(
                        "Function " + funcLabel + " cannot be instantiated because " + label + " was not found");
            }
            if (d.isActionable()) {
                actuators.add((Actuator<?>) d);
            } else {
                throw new FunctionInstantiationException(
                        "Function " + funcLabel + " cannot be instantiated because " + label + " is not an actuator");
            }
        }

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

    /**
     * Selects and sets up the triggering method of a Func.
     * Options:
     *      onFrequency: sets up a timer to run the Runnable function thread at a given frequency.
     *      onRead: adds the Runnable function to the sensor's `addOnReadFunction` so that it is triggered every time
     *          the sensor receives a measurement.
     * 
     * @param jFunc   json description of the function.
     * @param devices map of the devices within the edge.
     * @param action  Runnable that implements the function to trigger.
     */
    public static void setupTrigger(JSONObject jFunc, Map<String, Device> devices, Runnable action) {
        JSONObject jTrigger = jFunc.getJSONObject("trigger");
        String label = jFunc.optString("label", null);
        String triggerType = jTrigger.optString("type", "");
        JSONObject triggerParams = jTrigger.getJSONObject("parameters");

        switch (triggerType) {
            case "onFrequency":
                int freq = triggerParams.getInt("frequency") * 1000;
                new Timer().scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        action.run();
                    }
                }, 0, freq);
                break;

            case "onRead":
                String triggerSensorName = triggerParams.getString("trigger-sensor");
                triggerSensorName = formatLabel(triggerSensorName);
                Sensor<?,?> triggerSensor = (Sensor<?,?>) devices.get(triggerSensorName);

                int interval = triggerParams.optInt("interval", -1);
                if (interval == -1){
                    interval = triggerSensor.getWindow().getSize();
                }
                triggerSensor.addOnReadFunction(action, interval, label);
                break;

            case "onStart":
                action.run();
                break;

            default:
                System.out.print("Wrong trigger: " + triggerType + " defined on the setup file");
        }
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
