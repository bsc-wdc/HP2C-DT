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
package es.bsc.hp2c.edge.funcs;

import es.bsc.hp2c.edge.types.Actuator;
import es.bsc.hp2c.edge.types.Device;
import es.bsc.hp2c.edge.types.Sensor;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Map;

import java.util.Timer;
import java.util.TimerTask;
import org.json.JSONArray;
import org.json.JSONObject;

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

        String driver = jFunc.optString("method_name", "");
        JSONObject parameters = jFunc.getJSONObject("parameters");
        JSONArray jSensors = parameters.getJSONArray("sensors");
        JSONArray jActuators = parameters.getJSONArray("actuators");

        ArrayList<Sensor> sensors = new ArrayList<>();
        for (int i = 0; i < jSensors.length(); i++) {
            String label = jSensors.getString(i);
            Device d = devices.get(label);
            if (d.isSensitive()) {
                sensors.add((Sensor<?,?>) d);
            } else {
                throw new FunctionInstantiationException(
                        "Function " + funcLabel + "cannot be instantiated because " + label + " is not a sensor");
            }
        }
        ArrayList<Actuator<?>> actuators = new ArrayList<>();
        for (int i = 0; i < jActuators.length(); i++) {
            String label = jActuators.getString(i);
            Device d = devices.get(label);
            if (d.isActionable()) {
                actuators.add((Actuator<?>) d);
            } else {
                throw new FunctionInstantiationException(
                        "Function " + funcLabel + "cannot be instantiated because " + label + " is not an actuator");
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
            throw new FunctionInstantiationException("Class " + driver + " not found. ", e);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new FunctionInstantiationException("Error finding the constructor for " + driver, e);
        } catch (Exception e) {
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
        String triggerType = jTrigger.optString("type", "");
        JSONArray triggerParams = jTrigger.getJSONArray("parameters");

        switch (triggerType) {
            case "onFrequency":
                int freq = triggerParams.getInt(0) * 1000;
                new Timer().scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        action.run();
                    }
                }, 0, freq);
                break;

            case "onRead":
                String triggerSensorName = triggerParams.getString(0);
                Sensor<?,?> triggerSensor = (Sensor<?,?>) devices.get(triggerSensorName);
                triggerSensor.addOnReadFunction(action);
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
