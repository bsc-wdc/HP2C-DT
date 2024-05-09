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

import com.rabbitmq.client.*;
import es.bsc.hp2c.server.edge.VirtualEdge;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Heartbeat handler that starts:
 * - an AMQP listener that reads every "edge.*.heartbeat" message
 * - a TimerTask that periodically checks the state of the edge nodes
 */
public class EdgeHeartbeat {

    private static final String QUEUE_NAME = "heartbeats";
    private static final String routingKey = "edge.*.heartbeat";
    private static final int HEARTBEAT_TIMEOUT = 30000;  // milliseconds
    private final Map<String, VirtualEdge> edgeMap;
    private final Channel channel;

    public EdgeHeartbeat(AmqpManager amqp, Map<String, VirtualEdge> edgeMap) throws IOException {
        this.channel = amqp.getChannel();
        channel.exchangeDeclare(amqp.getExchangeName(), "topic");
        this.channel.queueDeclare(QUEUE_NAME, true, false, false, null);
        this.channel.queueBind(QUEUE_NAME, amqp.getExchangeName(), routingKey);
        this.edgeMap = edgeMap;
    }

    /** Start the AMQP listener and checkInactiveEdges threads */
    public void start() throws IOException {
        // Start heartbeat listener
        this.startListener();
        // Start periodic checker of inactive edges (TimerTask with half of the heartbeat timeout interval)
        Timer timer = new Timer();
        CheckInactiveEdges checkInactiveEdges = new CheckInactiveEdges();
        timer.scheduleAtFixedRate(checkInactiveEdges, 0, HEARTBEAT_TIMEOUT / 2);
    }

    /** Deploy the AMQP consumer thread */
    private void startListener() throws IOException {
        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
                try {
                    processHeartbeatMessage(envelope.getRoutingKey(), body);
                } catch (Exception e) {
                    System.err.println("Error processing heartbeat message: " + e.getMessage());
                }
            }
        };
        channel.basicConsume(QUEUE_NAME, true, consumer);
    }

    /**
     * Method called every time a heartbeat message is received.
     * It processes the JSON received from the edge nodes, initializes the VirtualDevices if it is the 
     * first time they register, and updates the heartbeat hash map with the current time.
     * @param routingKey The "edge.*.heartbeat" routing key containing the device label
     * @param body Message sent by the edge node in JSON format, encoded in bytes
     */
    private void processHeartbeatMessage(String routingKey, byte[] body) {
        // Extract edge name from routing key
        String[] routingKeyParts = routingKey.split("\\.");
        String edgeLabel = routingKeyParts[1];
        JSONObject jEdgeSetup = new JSONObject(new String(body, StandardCharsets.UTF_8));

        // Process the heartbeat message
        System.out.println("Received heartbeat for edge '" + edgeLabel + "'");
        if (edgeMap.containsKey(edgeLabel)) {
            // Set last heartbeat
            long heartbeatTime = jEdgeSetup.getJSONObject("global-properties").getLong("heartbeat");
            edgeMap.get(edgeLabel).setLastHeartbeat(heartbeatTime);
        } else {
            // First time a heartbeat is received, load devices and store in the VirtualEdge object
            VirtualEdge edge = new VirtualEdge(jEdgeSetup);
            System.out.println("Loaded edge '" + edgeLabel + "': " + edge);
            edgeMap.put(edgeLabel, edge);
        }
    }

    /** Periodically verify the edge heartbeat and update each `isAvailable` property accordingly. */
    class CheckInactiveEdges extends TimerTask {
        @Override
        public void run() {
            long currentTime = System.currentTimeMillis();
            for (VirtualEdge edge : edgeMap.values()) {
                if (currentTime - edge.getLastHeartbeat() > HEARTBEAT_TIMEOUT) {
                    // Edge not available
                    System.out.println("Edge '" + edge.getLabel() + "' is inactive.");
                    if (edge.isAvailable()) {
                        edge.setAvailable(false);
                    }
                } else {
                    // Edge available
                    System.out.println("Edge '" + edge.getLabel() + "' is active.");
                    if (!edge.isAvailable()) {
                        edge.setAvailable(true);
                    }
                }
            }
        }
    }
}

