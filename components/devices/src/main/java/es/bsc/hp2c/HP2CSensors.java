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
package es.bsc.hp2c;

import es.bsc.hp2c.devices.types.Device;
import es.bsc.hp2c.devices.types.Device.DeviceInstantiationException;
import es.bsc.hp2c.devices.funcs.Func;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Contains the main method and two additional methods useful for parsing and
 * storing the devices and functions.
 */
public class HP2CSensors {

    /**
     * Obtain map of devices and load functions.
     * 
     * @param args Setup file.
     */
    public static void main(String[] args) throws Exception {
        String setupFile = args[0];
        // String
        // setupFile="/home/flordan/projects/HP2C-DT/development/testbed/setup/device1.json";
        Map<String, Device> devices = loadDevices(setupFile);
        loadFunctions(setupFile, devices); // loadFunctions(set, dev)
    }

    /**
     * Parse devices from JSON and return a map.
     * 
     * @param setupFile String containing JSON file.
     * @return Map containing all declared devices.
     */
    private static Map<String, Device> loadDevices(String setupFile) throws FileNotFoundException {
        InputStream is = new FileInputStream(setupFile);
        JSONTokener tokener = new JSONTokener(is);
        JSONObject object = new JSONObject(tokener);
        JSONArray jDevices = object.getJSONArray("devices");
        TreeMap<String, Device> devices = new TreeMap<>();
        for (Object jo : jDevices) {
            JSONObject jDevice = (JSONObject) jo;
            try {
                Device d = Device.parseJSON(jDevice);
                devices.put(d.getLabel(), d);
            } catch (DeviceInstantiationException | ClassNotFoundException | JSONException e) {
                System.err.println("Error loading device " + jDevice + ". Ignoring it.");
            }
        }
        return devices;
    }

    /**
     * Parse functions from JSON and, for each one, parse and check its triggers.
     * 
     * @param setupFile String containing JSON file.
     * @param devices   map of the devices within the edge.
     */
    public static void loadFunctions(String setupFile, Map<String, Device> devices) {
        Map<String, Runnable> actions = new HashMap<>();
        JSONArray funcs = new JSONArray();
        try {
            String content = new String(Files.readAllBytes(Paths.get(setupFile)), "UTF-8");
            JSONObject config = new JSONObject(content);
            funcs = config.getJSONArray("funcs");

        } catch (Exception e) {
            System.err.println("Error parsing JSON.");
        }
        
        for (Object jo : funcs) {
            JSONObject jFunc = (JSONObject) jo;
            try {
                String funcLabel = jFunc.optString("label", "");
                Runnable action = Func.functionParseJSON(jFunc, devices, funcLabel);
                actions.put(funcLabel, action);
                Func.checkTriggers(jFunc, devices, action);
            } catch (Exception e) {
                System.err.println("Error parsing " + jFunc + ".");
            }
        }
    }
}
