package es.bsc.hp2c.common.utils;

import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Device.DeviceInstantiationException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static es.bsc.hp2c.common.types.Device.formatLabel;

public final class FileUtils {
    private static final Logger logger = LogManager.getLogger("appLogger");
    private FileUtils(){}

    /**
     * Convert a HashMap to JSONObject.
     *
     * @param hashMap The HashMap to be converted to JSON.
     * @return A JSONObject representing the HashMap data.
     */
    public static JSONObject convertHashMapToJson(HashMap<String, Object> hashMap) {
        JSONObject jsonObject = new JSONObject();
        for (String key : hashMap.keySet()) {
            Object value = hashMap.get(key);
            if (value instanceof HashMap) {
                value = convertHashMapToJson((HashMap<String, Object>) value);
            }
            jsonObject.put(key, value);
        }
        return jsonObject;
    }

    /**
     * Read the provided JSON file to return the edge label.
     *
     * @param setupFile Path of the setup file in String format
     * @return the name of the edge
     */
    public static String readEdgeLabel(String setupFile) {
        JSONObject object;
        try {
            object = getJsonObject(setupFile);
        } catch (IOException e) {
            logger.error("Error loading file " + setupFile + ": " + e.getMessage());
            throw new RuntimeException(e);
        }
        JSONObject jGlobProp = object.getJSONObject("global-properties");
        String edgeLabel = jGlobProp.optString("label", "");
        if (edgeLabel.isEmpty()) {
            throw new JSONException("Malformed JSON. edge label must be specified inside global-properties");
        }
        return edgeLabel;
    }

    /**
     * Return a JSON file in the form of a JSONObject instance.
     */
    public static JSONObject getJsonObject(String filepath) throws IOException {
        InputStream is;
        is = Files.newInputStream(Paths.get(filepath));
        JSONTokener tokener = new JSONTokener(is);
        return new JSONObject(tokener);
    }

    /**
     * Parse devices from JSON and return a map.
     *
     * @param setupFile String containing JSON file.
     * @return Map containing all declared devices.
     */

    public static Map<String, Device> loadDevices(String setupFile, boolean executeOpalComm) throws IOException {
        return loadDevices(setupFile, "driver", executeOpalComm);
    }

    public static Map<String, Device> loadDevices(String setupFile, String driverType, boolean executeOpalComm) throws IOException {
        JSONObject object = getJsonObject(setupFile);
        return loadDevices(object, driverType, executeOpalComm);
    }


    public static Map<String, Device> loadDevices(JSONObject jsonObject, String driverType, boolean executeOpalComm) {
        JSONArray jDevices = jsonObject.getJSONArray("devices");
        JSONObject jGlobProp = jsonObject.getJSONObject("global-properties");
        jGlobProp.put("executeOpalComm", executeOpalComm);

        TreeMap<String, Device> devices = new TreeMap<>();
        for (Object jo : jDevices) {
            JSONObject jDevice = (JSONObject) jo;
            try {
                Device d = Device.parseJSON(jDevice, jGlobProp, driverType);
                if (devices.containsKey(d.getLabel())){
                    throw new DeviceInstantiationException("Device label " + d.getLabel() + " is already in use");
                }
                devices.put(d.getLabel(), d);
            } catch (ClassNotFoundException | JSONException e) {
                logger.error("Error loading device " + jDevice + ": " + e.getMessage() + ". Ignoring it. ");
            } catch (DeviceInstantiationException e){
                logger.error("Error loading device. " + e.getMessage());
            }
        }
        return devices;
    }

    public static int getWindowSize(JSONObject jProperties, JSONObject jGlobalProperties, String label) {
        int windowSize;
        if (jProperties.has("window-size")){
            Object value = jProperties.get("window-size");
            if (value instanceof Integer) {
                windowSize = (Integer) value;
            } else {
                throw new JSONException("Malformed JSON. The window_size of the device " + label + " is not an integer. " +
                        "It is: " + value.getClass().getSimpleName());
            }
        } else if (jGlobalProperties.has("window-size")){
            Object value = jGlobalProperties.get("window-size");
            if (value instanceof Integer) {
                windowSize = (Integer) value;
            } else {
                throw new JSONException("Malformed JSON. The window_size declared in global properties is not an integer. " +
                        "It is: " + value.getClass().getSimpleName());
            }
        } else {
            windowSize = 1;
        }
        logger.info(label + " window size: " + windowSize);
        return windowSize;
    }

    public static JSONObject getSensorUnits(String setupFile, String defaultUnitsPath,
                                            Map<String, Device> devices) throws IOException {
        JSONObject sensorUnits = new JSONObject();

        // Load setup file
        JSONObject object = getJsonObject(setupFile);

        // Load specific devices
        JSONArray jDevices = object.getJSONArray("devices");

        // Load generic file
        JSONObject jDefaultUnits = getJsonObject(defaultUnitsPath);

        for (Object deviceObject : jDevices) {
            JSONObject jDevice = new JSONObject();
            if (deviceObject instanceof JSONObject) {
                jDevice = (JSONObject) deviceObject;
            }

            String deviceLabel = formatLabel(jDevice.getString("label"));
            Device device = devices.get(deviceLabel);

            if (jDevice.has("units")) { // concrete units defined for this device
                Object unitsObject = jDevice.get("units");
                if (unitsObject instanceof String) {
                    sensorUnits.put(deviceLabel, (String) unitsObject);
                } else if (unitsObject instanceof JSONArray) {
                    sensorUnits.put(deviceLabel, (JSONArray) unitsObject);
                } else {
                    throw new IllegalArgumentException("Unsupported 'units' type for device: " + deviceLabel);
                }
            } else if (jDefaultUnits.has(device.getType())) { // get the default units defined for this type
                Object unitsObject = jDefaultUnits.get(device.getType());
                if (unitsObject instanceof String) {
                    sensorUnits.put(deviceLabel, (String) unitsObject);
                } else if (unitsObject instanceof JSONArray) {
                    sensorUnits.put(deviceLabel, (JSONArray) unitsObject);
                } else {
                    throw new IllegalArgumentException("Unsupported 'units' type for device type: " + device.getType());
                }
            }
        }
        return sensorUnits;
    }

}
