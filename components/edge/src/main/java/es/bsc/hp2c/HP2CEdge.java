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
import java.util.Timer;
import java.util.TimerTask;

import static es.bsc.hp2c.common.utils.FileUtils.*;

/**
 * Contains the main method and two additional methods useful for parsing and
 * storing the devices and functions.
 */
public class HP2CEdge {
    private static String edgeLabel;
    private static final String EXCHANGE_NAME = "measurements";
    private static final long HEARTBEAT_RATE = 10000;
    private static Connection connection;
    private static Channel channel;

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

        // Set up AMQP messaging
        boolean amqpOn = setUpMessaging(brokerIp);
        if (amqpOn) {
            JSONObject jEdgeSetup = getJsonObject(setupFile);
            Timer timer = new Timer();
            Heartbeat heartbeat = new Heartbeat(jEdgeSetup, edgeLabel);
            timer.scheduleAtFixedRate(heartbeat, 0, HEARTBEAT_RATE);
        } else {
            System.out.println("Heartbeat could not start. AMQP not available");
        }

        // Load devices and functions
        Map<String, Device> devices = loadDevices(setupFile, "driver", true);
        OpalComm.setLoadedDevices(true);
        Func.loadFunctions(setupFile, devices);
        Func.loadGlobalFunctions(defaultsPath, devices, amqpOn);
    }

    private static boolean setUpMessaging(String ip) {
        // Try connecting to a RabbitMQ server until success
        connection = CommUtils.AmqpConnectAndRetry(ip);
        // After establishing a connection, set up channel and exchange
        try {
            channel = connection.createChannel();
            channel.exchangeDeclare(EXCHANGE_NAME, "topic");
        } catch (IOException e) {
            System.err.println("Error setting up RabbitMQ channel and exchange: " + e.getMessage());
            throw new RuntimeException(e);
        }
        System.out.println("[setUpMessaging] AMQP connection started.");
        return true;
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

    /**
     * TimerTask that sends a periodic heartbeat message to the server.
     */
    static class Heartbeat extends TimerTask {
        private final JSONObject jEdgeSetup;
        private final String routingKey;
        public Heartbeat(JSONObject jEdgeSetup, String edgeLabel) {
            System.out.println("[Heartbeat] Instantiating heartbeat scheduler for edge " + edgeLabel);
            this.jEdgeSetup = jEdgeSetup;
            this.routingKey = "edge" + "." + edgeLabel + "." + "heartbeat";
        }
        @Override
        public void run() {
            // Add timestamp and status to the JSON object
            JSONObject jGlobalProps = jEdgeSetup.getJSONObject("global-properties");
            jGlobalProps.put("heartbeat", System.currentTimeMillis());
            jGlobalProps.put("available", true);
            // Convert the string to bytes
            byte[] message = jEdgeSetup.toString().getBytes();
            try {
                channel.basicPublish(EXCHANGE_NAME, routingKey, null, message);
            } catch (IOException e) {
                System.err.println("Exception in " + edgeLabel + " edge heartbeat: " + e.getMessage());
            }
            System.out.println("[Heartbeat] Sent JSON message to routing key " + routingKey);
        }
    }
}
