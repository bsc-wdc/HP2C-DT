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
import es.bsc.hp2c.server.edge.VirtualEdge;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import static es.bsc.hp2c.HP2CServer.checkActuator;
import static es.bsc.hp2c.common.utils.CommUtils.printableArray;

/**
 * HTTP Server that listens to REST POST requests.
 */
public class RestListener {
    private static final int REST_PORT = 8080;
    private static Map<String, VirtualEdge> edgeMap;

    public RestListener(Map<String, VirtualEdge> edgeMap) {
        RestListener.edgeMap = edgeMap;
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
                // Collect information from all edge nodes
                jEdgesInfo = new JSONObject();
                for (VirtualEdge edge : edgeMap.values()) {
                    jEdgesInfo.put(edge.getLabel(), edge.getEdgeInfo());
                }
            } catch (JSONException e) {
                System.err.println("Exception handling JSON object: " + e.getMessage());
                exchange.sendResponseHeaders(500, 0); // Internal Server Error
                return;
            }

            System.out.println(" [RestListener] Sending requested EdgesInfo: " + jEdgesInfo);
            exchange.sendResponseHeaders(200, response.length() + jEdgesInfo.toString().getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.write(jEdgesInfo.toString().getBytes());
            os.close();
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
            JSONObject jDeviceInfo;

            try {
                //response = "Received request to get devices info. ";
                jDeviceInfo = RestUtils.getInfoFromEdgeMap();
            } catch (JSONException e) {
                System.err.println("Exception handling JSON object: " + e.getMessage());
                exchange.sendResponseHeaders(500, 0); // Internal Server Error
                return;
            }

            System.out.println(" [RestListener] Sending requested DevicesInfo: " + jDeviceInfo);
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
                    Device device = edgeMap.get(edgeLabel).getDevice(actuatorLabel);
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

        static JSONObject getInfoFromEdgeMap() {
            JSONObject jDevicesInfo = new JSONObject();
            for (HashMap.Entry<String, VirtualEdge> entry : edgeMap.entrySet()) {
                String edgeLabel = entry.getKey();
                VirtualEdge edge = entry.getValue();
                jDevicesInfo.put(edgeLabel, edge.getDevicesInfo());
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

