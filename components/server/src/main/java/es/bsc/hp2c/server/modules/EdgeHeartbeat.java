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
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static es.bsc.hp2c.common.utils.FileUtils.loadDevices;

/**
 * Heartbeat handler that starts:
 * - an AMQP listener that reads every "edge.*.heartbeat" message
 * - a TimerTask that periodically checks the state of the edge nodes
 */
public class EdgeHeartbeat {

    private static final String QUEUE_NAME = "heartbeats";
    private static final String routingKey = "edge.*.heartbeat";
    private static final int HEARTBEAT_TIMEOUT = 10000;  // milliseconds
    private final Map<String, VirtualEdge> edgeMap;
    private final Channel channel;
    private final Map<String, Long> lastHeartbeatTimes;

    public EdgeHeartbeat(AmqpManager amqp, Map<String, VirtualEdge> edgeMap) throws IOException {
        this.channel = amqp.getChannel();
        channel.exchangeDeclare(amqp.getExchangeName(), "topic");
        this.channel.queueDeclare(QUEUE_NAME, true, false, false, null);
        this.channel.queueBind(QUEUE_NAME, amqp.getExchangeName(), routingKey);
        this.edgeMap = edgeMap;
        this.lastHeartbeatTimes = new HashMap<>();
    }

    /** Start the AMQP listener and checkInactiveEdges threads */
    public void start() throws IOException {
        this.startListener();
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkInactiveEdges();
            }
        }, 0, HEARTBEAT_TIMEOUT / 2); // Check every half of the heartbeat timeout interval
    }

    /** Deploy the AMQP consumer thread */
    private void startListener() throws IOException {
        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
                processHeartbeatMessage(envelope.getRoutingKey(), body);
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
        JSONObject setupJson = new JSONObject(new String(body, StandardCharsets.UTF_8));

        // Process the heartbeat message
        if (!edgeMap.containsKey(edgeLabel)) {
            // The first time a heartbeat is received we load the devices and store it in the VirtualEdge object
            VirtualEdge edge = new VirtualEdge(loadDevices(setupJson, "driver-dt"));
            edgeMap.put(edgeLabel, edge);
        }
        // TODO: else with Update device information?

        // Update last heartbeat time for this edge TODO: Check usage of this map together with edgeMap. Redundancy?
        System.out.println("Received heartbeat for edge '" + edgeLabel + "': " + setupJson);
        lastHeartbeatTimes.put(edgeLabel, System.currentTimeMillis());
    }

    /** Periodically verify the heartbeat hash map and update each `isAvailable` property accordingly. */
    private void checkInactiveEdges() {
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : lastHeartbeatTimes.entrySet()) {
            if (currentTime - entry.getValue() > HEARTBEAT_TIMEOUT) {
                // Device is not available
                String edgeLabel = entry.getKey();
                System.out.println("Edge '" + edgeLabel + "' is inactive.");
                // TODO: Set isAvailable to false
            }
        }
    }
}

