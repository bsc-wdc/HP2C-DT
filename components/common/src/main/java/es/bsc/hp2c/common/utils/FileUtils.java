package es.bsc.hp2c.common.utils;

import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Device.DeviceInstantiationException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;

public final class FileUtils {
    private FileUtils(){}

    /**
     * Read the provided JSON file to return the edge label.
     * @param setupFile Path of the setup file in String format
     * @return the name of the edge
     */
    public static String readEdgeLabel(String setupFile) {
        InputStream is = null;
        try {
            is = new FileInputStream(setupFile);
        } catch (FileNotFoundException e) {
            System.err.println("Error loading file " + setupFile + ": " + e.getMessage());
            throw new RuntimeException(e);
        }
        JSONTokener tokener = new JSONTokener(is);
        JSONObject object = new JSONObject(tokener);
        JSONObject jGlobProp = object.getJSONObject("global-properties");
        String edgeLabel = jGlobProp.optString("label", "");
        if (edgeLabel.isEmpty()){
            throw new JSONException("Malformed JSON. edge label must be specified inside global-properties");
        }
        return edgeLabel;
    }

    /**
     * Parse devices from JSON and return a map.
     *
     * @param setupFile String containing JSON file.
     * @return Map containing all declared devices.
     */
    public static Map<String, Device> loadDevices(String setupFile) throws FileNotFoundException {
        return loadDevices(setupFile, "driver");
    }

    public static Map<String, Device> loadDevices(String setupFile, String driverType) throws FileNotFoundException {
        InputStream is = new FileInputStream(setupFile);
        JSONTokener tokener = new JSONTokener(is);
        JSONObject object = new JSONObject(tokener);
        JSONArray jDevices = object.getJSONArray("devices");
        JSONObject jGlobProp = object.getJSONObject("global-properties");

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
}
