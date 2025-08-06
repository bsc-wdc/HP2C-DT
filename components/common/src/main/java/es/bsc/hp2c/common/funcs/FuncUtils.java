package es.bsc.hp2c.common.funcs;

import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Sensor;
import es.bsc.hp2c.common.utils.EdgeMap;
import javassist.CannotCompileException;
import javassist.NotFoundException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import static es.bsc.hp2c.common.types.Device.formatLabel;

public class FuncUtils {
    public static void updateSensorList(JSONObject jGlobalFunc, Device device, List<String> deviceList) {
        deviceList.add(device.getLabel());
        jGlobalFunc.getJSONObject("parameters").put("sensors", deviceList);
    }



    public static JSONObject buildJParameters(Device device, JSONObject jGlobalFunc) {
        JSONObject jParameters = jGlobalFunc.getJSONObject("trigger").optJSONObject("parameters");
        if (jParameters == null) jParameters = new JSONObject();

        JSONArray triggerSensorArray = new JSONArray();
        triggerSensorArray.put(device.getLabel());
        jParameters.put("trigger-sensor", triggerSensorArray);

        return jParameters;
    }



    public static JSONObject findDeviceObject(JSONArray jDevices, String label) {
        for (int i = 0; i < jDevices.length(); i++) {
            JSONObject jDevice = jDevices.getJSONObject(i);
            if (Objects.equals(formatLabel(jDevice.getString("label")), label)) {
                return jDevice;
            }
        }
        return null;
    }



    public static void configureAMQP(JSONObject jDevice, JSONObject jGlobalFunc, JSONObject jParameters, String deviceLabel) {
        String amqpAggregate = jDevice.getJSONObject("properties").optString("amqp-aggregate", "");
        JSONObject jOther = new JSONObject();
        JSONObject jAggregate = new JSONObject();

        if (!amqpAggregate.isEmpty()) {
            jAggregate.put("type", amqpAggregate);
        } else {
            amqpAggregate = jGlobalFunc.getJSONObject("parameters")
                    .getJSONObject("other")
                    .getJSONObject("aggregate")
                    .optString("type", "last");
            jAggregate.put("type", amqpAggregate);
        }

        JSONObject aggArgs = jDevice.getJSONObject("properties").optJSONObject("amqp-agg-args");
        if (aggArgs != null) {
            jAggregate.put("args", aggArgs);
        }

        jOther.put("aggregate", jAggregate);
        jGlobalFunc.getJSONObject("parameters").put("other", jOther);

        String amqpTrigger = jDevice.getJSONObject("properties").optString("amqp-trigger", "");
        if (!amqpTrigger.isEmpty()) {
            jGlobalFunc.getJSONObject("trigger").put("type", amqpTrigger);
        }

        int interval = jDevice.getJSONObject("properties").optInt("amqp-interval", -1);
        if (interval != -1) jParameters.put("interval", interval);

        int frequency = jDevice.getJSONObject("properties").optInt("amqp-frequency", -1);
        if (frequency != -1) jParameters.put("frequency", frequency);

        jGlobalFunc.getJSONObject("trigger").put("parameters", jParameters);
    }


    /**
     * Look for the devices in the EdgeMap and append it to the deviceMap converted to Sensor/Actuator.
     *
     * @param json     JSON containing the array of sensors/actuators.
     * @param edgeMap   EdgeMap object containing the devices within each edge.
     * @param funcLabel Label of the function to address.
     * @param isSensor Determines whether the function must look for (and convert to) sensors or actuators.
     * @return Map of the declared devices.
     * @throws Func.FunctionInstantiationException Error raised during the instantiation
     *                                        of the function.
     */
    public static <T> Map<String, ArrayList<T>> processDevices(JSONObject json, EdgeMap edgeMap, String funcLabel,
                                                                boolean isSensor) throws Func.FunctionInstantiationException {
        Map<String, ArrayList<T>> deviceMap = new HashMap<>();

        if (json != null) {
            for (String edgeLabel : json.keySet()) {
                JSONArray deviceArray = json.getJSONArray(edgeLabel);
                for (int i = 0; i < deviceArray.length(); i++) {
                    String label = formatLabel(deviceArray.getString(i));
                    Device d = edgeMap.getDevice(edgeLabel, label);

                    if (d == null) {
                        throw new Func.FunctionInstantiationException(
                                "Function " + funcLabel + " cannot be instantiated because " + label +
                                        " on edge " + edgeLabel + " was not found");
                    }

                    boolean valid = isSensor ? d.isSensitive() : d.isActionable();
                    if (!valid) {
                        throw new Func.FunctionInstantiationException(
                                "Function " + funcLabel + " cannot be instantiated because " + label +
                                        " on edge " + edgeLabel + " is not a " + (isSensor ? "sensor" : "actuator"));
                    }

                    deviceMap
                            .computeIfAbsent(edgeLabel, k -> new ArrayList<>())
                            .add((T) d);
                }
            }
        }

        return deviceMap;
    }


    public static Action getClassInstance(String driver, JSONObject parameters, Map<String, ArrayList<Sensor<?, ?>>> sensors,
                                    Map<String, ArrayList<Actuator<?>>> actuators, String lang, String functionType,
                                    Class<?> runtimeHostClass) throws ClassNotFoundException, NotFoundException,
            CannotCompileException, NoSuchMethodException, InstantiationException, IllegalAccessException,
            InvocationTargetException {
        JSONObject jOther;
        Constructor<?> ct;
        COMPSsHandler compssHandler = null;
        Class<?> c = Class.forName(driver);
        // Handle COMPSs workflow cases
        if (functionType.equals("workflow") && runtimeHostClass != null) {
            if (lang.equalsIgnoreCase("java")) {
                // Initialize COMPSs Java Handler
                compssHandler = new COMPSsHandler(runtimeHostClass, driver, c);
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
        return new Action(classInstance, c, compssHandler);
    }


    public static void setupOnReadTrigger(JSONObject triggerParams, EdgeMap edgeMap, Action action, String label,
                                           boolean onRead) throws Func.FunctionInstantiationException {
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
                    throw new Func.FunctionInstantiationException(
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
     * Prints summary of a Func in a separate thread so it shows in the main
     * container output
     */
    public static void printFuncSummary(JSONObject jFunc, EdgeMap edgeMap, JSONObject triggerParams,
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


    // Transform function to server format (edgeLabel-[deviceLabel]
    public static void transformFuncToServerFormat(JSONObject jFunc, String edgeLabel) {
        // Transform parameters section
        JSONObject params = jFunc.getJSONObject("parameters");
        String[] keys = new String[] { "sensors", "actuators" };
        for (String key : keys) {
            if (params.get(key) instanceof JSONArray) {
                JSONArray originalArray = params.getJSONArray(key);
                JSONObject transformed = new JSONObject();
                transformed.put(edgeLabel, originalArray);
                params.put(key, transformed);
            } else {
                throw new IllegalArgumentException("Expected " + key + " to be a JSONArray, but found: "
                        + params.get(key).getClass().getSimpleName());
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


    /**
     * Parse function from JSON and return runnable.
     *
     * @param jFunc     JSON description of the function.
     * @param edgeMap   EdgeMap object containing the devices within each edge.
     * @param funcLabel Label of the function to address.
     * @return Runnable implementing the function defined.
     * @throws Func.FunctionInstantiationException Error raised during the instantiation
     *                                        of the function.
     */
    public static Action functionParseJSON(JSONObject jFunc, EdgeMap edgeMap, String funcLabel,
                                           Class<?> runtimeHostClass) throws Func.FunctionInstantiationException {

        String driver = jFunc.optString("method-name", "");
        JSONObject parameters = jFunc.getJSONObject("parameters");
        String lang = jFunc.getString("lang");
        String functionType = jFunc.optString("type", "");

        //Process sensors
        JSONObject jSensors = parameters.optJSONObject("sensors");
        Map<String, ArrayList<Sensor<?, ?>>> sensors = processDevices(jSensors, edgeMap, funcLabel, true);

        //Process actuators
        JSONObject jActuators = parameters.optJSONObject("actuators");
        Map<String, ArrayList<Actuator<?>>> actuators = processDevices(jActuators, edgeMap, funcLabel, false
        );

        return getAction(funcLabel, driver, parameters, sensors, actuators, lang, functionType, runtimeHostClass);
    }


    private static Action getAction(String funcLabel, String driver, JSONObject parameters,
                                    Map<String, ArrayList<Sensor<?, ?>>> sensors, Map<String, ArrayList<Actuator<?>>> actuators, String lang,
                                    String functionType, Class<?> runtimeHostClass) throws Func.FunctionInstantiationException {
        // Use a specific driver for Python funcs if driver is not specified
        if (driver.isEmpty() && (lang.equals("python") || lang.equals("Python") || lang.equals("PYTHON"))) {
            driver = "es.bsc.hp2c.common.funcs.PythonFunc";
        }

        try {
            return getClassInstance(driver, parameters, sensors, actuators, lang, functionType, runtimeHostClass);

        } catch (ClassNotFoundException e) {
            throw new Func.FunctionInstantiationException("Error finding the driver " + driver, e);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new Func.FunctionInstantiationException("Error finding the constructor for " + driver, e);
        } catch (Exception e) {
            e.printStackTrace();
            throw new Func.FunctionInstantiationException("Error instantiating " + funcLabel + " function. ", e);
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
            throws Func.FunctionInstantiationException {
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
}
