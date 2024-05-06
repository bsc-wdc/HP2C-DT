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

import es.bsc.hp2c.common.utils.CommUtils;
import es.bsc.hp2c.edge.opalrt.OpalComm;
import es.bsc.hp2c.common.types.Device;

import es.bsc.hp2c.common.types.Func;

import com.rabbitmq.client.*;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static es.bsc.hp2c.common.utils.FileUtils.*;

/**
 * Contains the main method and two additional methods useful for parsing and
 * storing the devices and functions.
 */
public class HP2CEdge {
    private static String edgeLabel;
    private static final String EXCHANGE_NAME = "measurements";
    private static Connection connection;
    private static Channel channel;
    private static boolean amqpOn = true;

    /**
     * Obtain map of devices and load functions.
     * 
     * @param args Setup file.
     */
    public static void main(String[] args) throws IOException {
        // Get input data
        String setupFile;
        if (args.length == 1) {
            setupFile = args[0];
        } else {
            setupFile = "../../deployments/testbed/setup/edge1.json";
        }
        // Get defaults file
        String defaultsPath = "/data/edge_default.json";
        File defaultsFile = new File(defaultsPath);
        if (!defaultsFile.isFile()) {
            defaultsPath = "../../deployments/defaults/setup/edge_default.json";
        }
        edgeLabel = readEdgeLabel(setupFile);

        // Get IP address
        String localIp = System.getenv("LOCAL_IP");
        String brokerIp = CommUtils.parseRemoteIp("broker", localIp);

        // Set up AMQP connections TODO: modularize this part into a func or external class?
        setUpMessaging(brokerIp);
        if (amqpOn) {
            String hearBeatRoutingKey = "edge" + "." + edgeLabel + "." + "heartbeat";
            JSONObject jsonEdgeSetup = getJsonObject(setupFile);
            // Add timestamp and status to the JSON object
            jsonEdgeSetup.put("timestamp", System.currentTimeMillis());
            jsonEdgeSetup.put("isAvailable", true);
            // Convert the string to bytes
            byte[] message = jsonEdgeSetup.toString().getBytes();
            // Publish the message to the queue
            channel.basicPublish(EXCHANGE_NAME, hearBeatRoutingKey, null, message);
            System.out.println(" [x] Sent JSON message in routing key " + hearBeatRoutingKey + ": '" + jsonEdgeSetup + "'");
        }

        // Load devices and functions
        Map<String, Device> devices = loadDevices(setupFile);
        OpalComm.setLoadedDevices(true);
        Func.loadFunctions(setupFile, devices);
        Func.loadGlobalFunctions(defaultsPath, devices, isAmqpOn());
    }

    private static void setUpMessaging(String ip) {
        try {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(ip);
            connection = factory.newConnection();
            channel = connection.createChannel();
            channel.exchangeDeclare(EXCHANGE_NAME, "topic");
            System.out.println("AMQP connection started.");
        } catch (IOException | TimeoutException e) {
            amqpOn = false;
            System.err.println("Error initializing messaging: " + e.getMessage());
            System.err.println("Continuing without AMQP connection.");
        }
    }

    public static String getEdgeLabel() {
        return edgeLabel;
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

    public static boolean isAmqpOn() {
        return amqpOn;
    }
}
