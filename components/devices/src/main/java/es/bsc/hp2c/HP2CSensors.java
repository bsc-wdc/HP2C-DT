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
import es.bsc.hp2c.devices.funcs.Func.FunctionInstantiationException;
import es.bsc.hp2c.devices.opalrt.OpalReader;

import com.rabbitmq.client.*;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLOutput;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeoutException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Contains the main method and two additional methods useful for parsing and
 * storing the devices and functions.
 */
public class HP2CSensors {

    private static final String EXCHANGE_NAME = "measurements";
    private static Connection connection;
    private static Channel channel;

    /**
     * Obtain map of devices and load functions.
     * 
     * @param args Setup file.
     */
    public static void main(String[] args) throws Exception {
        String setupFile;
        if (args.length == 1) {
            setupFile = args[0];
        } else {
            setupFile = "/home/eiraola/projects/hp2cdt/deployments/testbed/setup/device1.json";
        }
        setUpMessaging();
        OpalReader.setUDPPort(getJComms(setupFile));
        Map<String, Device> devices = loadDevices(setupFile);
        loadFunctions(setupFile, devices); // loadFunctions(set, dev)
    }

    private static void setUpMessaging() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        connection = factory.newConnection();
        channel = connection.createChannel();
        channel.exchangeDeclare(EXCHANGE_NAME, "topic");
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
                System.err.println("Error loading device " + jDevice + ". Ignoring it. " + e.getMessage());
            }
        }
        return devices;
    }

    private static int getJComms(String setupFile) {
        InputStream is = null;
        try {
            is = new FileInputStream(setupFile);
        } catch (FileNotFoundException e) {
            System.err.println("Setup file not found.");
        }
        JSONTokener tokener = new JSONTokener(is);
        JSONObject object = new JSONObject(tokener);
        JSONObject jGlobProp = object.getJSONObject("global-properties");
        JSONObject jComms = jGlobProp.getJSONObject("comms");
        JSONObject jUDPPort = jComms.getJSONObject("udp");

        List<String> keysList = new ArrayList<>();
        Iterator<String> keys = jUDPPort.keys();
        while(keys.hasNext()) {
            keysList.add(keys.next());
        }
        int udpPort = Integer.parseInt(keysList.get(0));
        return udpPort;
    }

    /**
     * Parse functions from JSON and, for each one, parse and check its triggers.
     * 
     * @param setupFile String containing JSON file.
     * @param devices   map of the devices within the edge.
     */
    public static void loadFunctions(String setupFile, Map<String, Device> devices) {
        String content = null;
        try {
            content = new String(Files.readAllBytes(Paths.get(setupFile)), "UTF-8");
        } catch (IOException e) {
            System.err.println("Error reading setup file: " + setupFile);
        }
        JSONObject config = new JSONObject(content);

        // Load specific functions
        try {
        JSONArray funcs = config.getJSONArray("funcs");
        for (Object jo : funcs) {
            JSONObject jFunc = (JSONObject) jo;
            String funcLabel = jFunc.optString("label", "");
            // Perform Func initialization
            try {
                Runnable action = Func.functionParseJSON(jFunc, devices, funcLabel);
                Func.setupTrigger(jFunc, devices, action);
            } catch (FunctionInstantiationException e) {
                System.err.println("Error initializing function " + funcLabel);
            }
            }
        } catch (Exception e) {
            System.err.println("Warning: Specific funcs might not be available in setup.");;
        }

        // Load generic functions
        JSONObject jGlobProp = config.getJSONObject("global-properties");
        JSONObject jGlobalFuncs = jGlobProp.getJSONObject("funcs");
        // Loop allSensors functions that apply to all sensors available
        JSONArray jAllSensorFuncs = jGlobalFuncs.getJSONArray("allSensors");
        for (Object jo: jAllSensorFuncs) {
            JSONObject jGlobalFunc = (JSONObject) jo;
            String funcLabel = jGlobalFunc.optString("label");
            // Skip device if not a sensor
            for (Device device: devices.values()) {
                if (!device.isSensitive()) {
                    continue;
                }
                // Create ArrayList of a single sensor and set it to the JSONObject
                ArrayList<String> sensorList = new ArrayList<>();
                sensorList.add(device.getLabel());
                jGlobalFunc.getJSONObject("parameters").put("sensors", sensorList);
                // Do same modification to triggers in JSON
                jGlobalFunc.getJSONObject("trigger").put("parameters", sensorList);
                // Perform Func initialization
                try {
                    Runnable action = Func.functionParseJSON(jGlobalFunc, devices, funcLabel);
                    Func.setupTrigger(jGlobalFunc, devices, action);
                } catch (FunctionInstantiationException e) {
                    System.err.println("Error initializing general function " + funcLabel);
                }
            }
        }
    }

    public static Connection getConnection() {
        return connection;
    }

    public static Channel getChannel() {
        return channel;
    }

    public static String getExchangeName() {
        return EXCHANGE_NAME;
    }
}
