package es.bsc.hp2c.common.utils;

import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Device.DeviceInstantiationException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public final class FileUtils {
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
            System.err.println("Error loading file " + setupFile + ": " + e.getMessage());
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
                System.err.println("Error loading device " + jDevice + ": " + e.getMessage() + ". Ignoring it. ");
            } catch (DeviceInstantiationException e){
                System.err.println("Error loading device. " + e.getMessage());
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
        System.out.println("Label: " + label);
        System.out.println("Window size: " + windowSize);
        return windowSize;
    }
}
