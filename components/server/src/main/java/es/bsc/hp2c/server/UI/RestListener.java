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
package es.bsc.hp2c.server.UI;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.server.device.VirtualComm;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.InetSocketAddress;
import java.util.Map;

import static es.bsc.hp2c.common.utils.CommUtils.printableArray;

/**
 * HTTP Server that listens to REST POST requests.
 */
public class RestListener {
    private static final int REST_PORT = 8080;
    private static Map<String, Map<String, Device>> deviceMap = null;

    public RestListener(Map<String, Map<String, Device>> deviceMap) {
        RestListener.deviceMap = deviceMap;
    }

    public void start() throws IOException {
        // Create HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(REST_PORT), 0);
        // Create a context for actuate REST endpoint
        server.createContext("/actuate", new ActuateHandler());
        // Start the server
        server.start();
        System.out.println("HTTP Server started on port " + REST_PORT);
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
            String requestBody = RestUtils.convertStreamToString(exchange.getRequestBody());
            RestUtils.RequestData data = RestUtils.parseRequestBody(requestBody);
            String[] stringValues = data.values;
            String edgeLabel = data.edgeLabel;
            String actuatorLabel = data.actuatorLabel;

            // Check if the provided device is an actuator
            Device device = deviceMap.get(edgeLabel).get(actuatorLabel);
            if (!device.isActionable()) {
                throw new IOException("Device " + actuatorLabel + " is not an actuator.");
            }

            // Actuate
            VirtualComm.VirtualActuator<?> actuator = (VirtualComm.VirtualActuator<?>) device;
            actuator.actuate(stringValues);

            // Send response
            String response = "Received request to actuate with values: " + printableArray(stringValues)
                    + ", edge label: " + edgeLabel
                    + ", actuator label: " + actuatorLabel;
            exchange.sendResponseHeaders(200, response.length());
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

