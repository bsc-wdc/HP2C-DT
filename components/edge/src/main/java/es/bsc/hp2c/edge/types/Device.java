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
package es.bsc.hp2c.edge.types;

import java.lang.reflect.Constructor;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents any part of the electrical network.
 */
public abstract class Device {

    /**
     * Parse function from JSON and return device.
     * 
     * @param jDevice json description of the device.
     * @return Device object corresponding to the description.
     * @throws JSONException                Malformed JSON.
     * @throws ClassNotFoundException       the indicated driver not found in the
     *                                      classpath.
     * @throws DeviceInstantiationException Error raised during the instantiation of
     *                                      the device.
     */
    public static Device parseJSON(JSONObject jDevice, JSONObject jGlobalProperties)
            throws JSONException, ClassNotFoundException, DeviceInstantiationException {
        String driver = jDevice.optString("driver", null);
        if (driver == null) {
            throw new JSONException("Malformed JSON. No driver indicated");
        }
        System.out.println("looking for class " + driver);
        Class<?> c;
        try{
            c = Class.forName(driver);
        } catch(ClassNotFoundException e){
            throw new DeviceInstantiationException("Error finding the driver " + driver, e);
        }
        Constructor<?> ct;
        try {
            ct = c.getConstructor(String.class, float[].class, JSONObject.class, JSONObject.class);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new DeviceInstantiationException("Error finding the constructor for " + driver, e);
        }

        String label = jDevice.optString("label", "");

        JSONObject jpos = jDevice.optJSONObject("position");
        float[] position;
        if (jpos != null) {
            position = new float[jpos.length()];
        } else {
            position = new float[] { 0, 0, 0 };
        }

        JSONObject jProperties = jDevice.optJSONObject("properties");
        try {
            return (Device) ct.newInstance(label, position, jProperties, jGlobalProperties);
        } catch (Exception e) {
            throw new DeviceInstantiationException(label, e);
        }
    }

    private final String label;
    private final float[] position;

    protected Device(String label, float[] position) {
        this.label = label;
        this.position = position;
    }

    /**
     * Returns whether the component admits running actions on it or not.
     *
     * @return {@literal true} if it admits running actions; otherwise
     *         {@literal false}.
     */
    public abstract boolean isActionable();

    /**
     * Returns whether the component is a sensor or not.
     *
     * @return {@literal true} if it admits running actions; otherwise
     *         {@literal false}.
     */
    public abstract boolean isSensitive();

    /**
     * Get the device label.
     *
     * @return Label of the device.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Exception raised during the instantiation of the device.
     */
    public static class DeviceInstantiationException extends Exception {

        public DeviceInstantiationException(String message) {
            super(message);
        }

        public DeviceInstantiationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
