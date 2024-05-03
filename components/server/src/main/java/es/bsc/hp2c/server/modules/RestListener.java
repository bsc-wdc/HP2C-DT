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
package es.bsc.hp2c.server.modules;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import es.bsc.hp2c.HP2CServer.ActuatorValidity;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.server.device.VirtualComm;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static es.bsc.hp2c.HP2CServer.checkActuator;
import static es.bsc.hp2c.common.utils.CommUtils.printableArray;
import static es.bsc.hp2c.common.utils.FileUtils.convertHashMapToJson;

/**
 * HTTP Server that listens to REST POST requests.
 */
public class RestListener {
    private static final int REST_PORT = 8080;
    private static Map<String, Map<String, Device>> deviceMap;

    public RestListener(Map<String, Map<String, Device>> deviceMap) {
        RestListener.deviceMap = deviceMap;
    }

    public void start() throws IOException {
        // Create HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(REST_PORT), 0);
        // Create a context for actuate REST endpoint
        server.createContext("/actuate", new ActuateHandler());
        server.createContext("/getEdgesInfo", new GetEdgesInfoHandler());
        server.createContext("/getDevicesInfo", new GetDevicesInfoHandler());
        // Start the server
        server.start();
        System.out.println("HTTP Server started on port " + REST_PORT);
    }


    static class GetEdgesInfoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestMethod = exchange.getRequestMethod();
            if (!requestMethod.equalsIgnoreCase("GET")) {
                exchange.sendResponseHeaders(405, 0); // Method Not Allowed
                return;
            }

            String response = "";
            JSONObject jEdgesInfo;

            try {
                //response = "Received request to get devices info. ";
                jEdgesInfo = getEdgesPositions();
            } catch (JSONException e) {
                response = e.getMessage();
                exchange.sendResponseHeaders(500, 0); // Internal Server Error
                return;
            }

            exchange.sendResponseHeaders(200, response.length() + jEdgesInfo.toString().getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.write(jEdgesInfo.toString().getBytes());
            os.close();
        }

        private JSONObject getEdgesPositions() {
            JSONObject jEdgesInfo = new JSONObject();
            for (Map.Entry<String, Map<String, Device>> entry : deviceMap.entrySet()) {
                String key = entry.getKey();

                Random random = new Random();
                JSONObject positionObject = new JSONObject();
                positionObject.put("y", 37.2f + random.nextFloat() * (43.4f - 37.2f));
                positionObject.put("x", -6.3f + random.nextFloat() * (0.2f + 6.3f));

                ArrayList<String> connexions = new ArrayList<>();
                for (Map.Entry<String, Map<String, Device>> innerEntry : deviceMap.entrySet()) {
                    String innerkey = innerEntry.getKey();
                    if (innerkey != key && random.nextBoolean()) connexions.add(innerkey);
                }
                JSONObject edgeDetails = new JSONObject();
                edgeDetails.put("position", positionObject);
                edgeDetails.put("connexions", connexions);
                jEdgesInfo.put(key, edgeDetails);
            }
            return jEdgesInfo;
        }
    }


    static class GetDevicesInfoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestMethod = exchange.getRequestMethod();
            if (!requestMethod.equalsIgnoreCase("GET")) {
                exchange.sendResponseHeaders(405, 0); // Method Not Allowed
                return;
            }

            String response = "";
            JSONObject jDeviceInfo = new JSONObject();

            try {
                //response = "Received request to get devices info. ";
                jDeviceInfo = RestUtils.getInfoFromDeviceMap();
            } catch (JSONException e) {
                response = e.getMessage();
                exchange.sendResponseHeaders(500, 0); // Internal Server Error
                return;
            }

            exchange.sendResponseHeaders(200, response.length() + jDeviceInfo.toString().getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.write(jDeviceInfo.toString().getBytes());
            os.close();
        }
    }


    /** Handler that implements the logic for received requests. */
    static class ActuateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Get request method
            String requestMethod = exchange.getRequestMethod();
            if (!requestMethod.equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, 0); // Method Not Allowed
                return;
            }

            // Get request and parse (assume JSON format)
            String response;
            String requestBody = RestUtils.convertStreamToString(exchange.getRequestBody());
            int responseCode = 200;
            try {
                RestUtils.RequestData data = RestUtils.parseRequestBody(requestBody);
                String[] stringValues = data.values;
                String edgeLabel = data.edgeLabel;
                String actuatorLabel = data.actuatorLabel;
                // Check actuator validity (Actuator is actionable and is in map)
                ActuatorValidity checker = checkActuator(edgeLabel, actuatorLabel);
                if (checker.isValid()) {
                    // Actuate
                    Device device = deviceMap.get(edgeLabel).get(actuatorLabel);
                    VirtualComm.VirtualActuator<?> actuator = (VirtualComm.VirtualActuator<?>) device;
                    actuator.actuate(stringValues);
                    // Send response
                    response = "Received request to actuate with values: " + printableArray(stringValues)
                            + ", edge label: " + edgeLabel
                            + ", actuator label: " + actuatorLabel;
                } else {
                    // Get error message
                    response = checker.getMessage();
                    responseCode = 400;
                }
            } catch (JSONException | IOException e) {
                response = e.getMessage();
                responseCode = 400;
            }

            exchange.sendResponseHeaders(responseCode, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    /** Utility methods to parse input requests */
    static class RestUtils {
        static String convertStreamToString(InputStream is) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        }

        static JSONObject getInfoFromDeviceMap() {
            JSONObject jDevicesInfo = new JSONObject();
            for (HashMap.Entry<String, Map<String, Device>> entry : deviceMap.entrySet()) {
                String groupKey = entry.getKey();
                Map<String, Device> innerMap = entry.getValue();
                JSONObject jEdge = new JSONObject();
                for (HashMap.Entry<String, Device> innerEntry : innerMap.entrySet()) {
                    String deviceKey = innerEntry.getKey();
                    JSONObject jDevice = new JSONObject();
                    boolean isActionable = false;
                    Device device = innerMap.get(deviceKey);
                    if (device.isActionable()) {
                        VirtualComm.VirtualActuator<?> actuator = (VirtualComm.VirtualActuator<?>) device;
                        isActionable = true;
                        boolean isCategorical = actuator.isCategorical();
                        jDevice.put("isCategorical", isCategorical);
                        jDevice.put("size", actuator.getSize());
                        if (isCategorical) {
                            jDevice.put("categories", actuator.getCategories());
                        }
                    }
                    else{
                        VirtualComm.VirtualSensor<?> sensor = (VirtualComm.VirtualSensor<?>) device;
                        jDevice.put("size", sensor.getSize());
                    }
                    jDevice.put("isActionable", isActionable);
                    jEdge.put(deviceKey, jDevice);
                }
                jDevicesInfo.put(groupKey, jEdge);
            }
            return jDevicesInfo;
        }

        static RequestData parseRequestBody(String requestBody) throws JSONException {
            JSONObject jsonObject = new JSONObject(requestBody);

            JSONArray valuesArray = jsonObject.getJSONArray("values");
            String[] values = new String[valuesArray.length()];
            for (int i = 0; i < valuesArray.length(); i++) {
                values[i] = valuesArray.getString(i);
            }

            String edgeLabel = jsonObject.getString("edgeLabel");
            String actuatorLabel = jsonObject.getString("actuatorLabel");

            return new RequestData(values, edgeLabel, actuatorLabel);
        }

        static class RequestData {
            String[] values;
            String edgeLabel;
            String actuatorLabel;

            RequestData(String[] values, String edgeLabel, String actuatorLabel) {
                this.values = values;
                this.edgeLabel = edgeLabel;
                this.actuatorLabel = actuatorLabel;
            }
        }
    }
}

