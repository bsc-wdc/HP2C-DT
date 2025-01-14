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

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.DeliverCallback;
import es.bsc.hp2c.HP2CServer;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Sensor;
import es.bsc.hp2c.common.utils.CommUtils;
import es.bsc.hp2c.common.utils.Measurement;
import es.bsc.hp2c.common.utils.MeasurementWindow;
import es.bsc.hp2c.server.device.VirtualComm.VirtualActuator;
import es.bsc.hp2c.server.edge.VirtualEdge;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class AmqpManager {
    private final DatabaseHandler db;
    private static Channel channel = null;
    private final Map<String, VirtualEdge> edgeMap;
    private static final String EXCHANGE_NAME = "measurements";

    /**
     * Initialize AMQP Channel.
     *
     * @param localIp local IP address where the RabbitMQ broker is deployed
     *                if IP is not configured in deployment_setup.json
     * @param edgeMap Map of the edge nodes and their devices
     */
    public AmqpManager(String localIp, Map<String, VirtualEdge> edgeMap, DatabaseHandler db)
            throws IOException, TimeoutException {
        this.edgeMap = edgeMap;
        this.db = db;
        // Select broker IP
        HashMap<String, Object> connectionMap = CommUtils.parseRemoteIp("broker", localIp);
        // Start connection
        String brokerIp = (String) connectionMap.get("ip");
        int brokerPort = (int) connectionMap.get("port");
        connect(brokerIp, brokerPort);
    }

    /** Start AMQP connection with broker. */
    private void connect(String setupIp, int port) throws IOException {
        // Try connecting to a RabbitMQ server until success
        Connection connection = CommUtils.AmqpConnectAndRetry(setupIp, port);
        channel = connection.createChannel();
        System.out.println("RabbitMQ Connection successful");
    }

    /**
     * Start RabbitMQ consumer.
     * The method will run indefinitely thanks to a DeliverCallback that calls
     * writeDB to process each message received.
     * Currently, receiving messages published to any "edge.#" topic.
     */
    public void startListener() throws IOException {
        String routingKey = "edge.*.sensors.*";
        // Set up connections
        channel.exchangeDeclare(EXCHANGE_NAME, "topic");
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, EXCHANGE_NAME, routingKey);
        System.out.println("[AmqpManager] Awaiting requests");

        DeliverCallback callback = (consumerTag, delivery) -> {
            String senderRoutingKey = delivery.getEnvelope().getRoutingKey();
            try {
                // Parse message. For instance: routingKey = "edge.edge1.sensors.voltmeter1"
                byte[] message = delivery.getBody();
                // Check existence of pair edge-device
                String edgeLabel = getEdgeLabel(senderRoutingKey);
                String deviceName = getDeviceName(senderRoutingKey);
                if (!HP2CServer.isInMap(edgeLabel, deviceName, edgeMap)) {
                    System.err.println("Edge " + edgeLabel + ", Device " + deviceName
                            + ": message received but device not listed as " + edgeLabel + " digital twin devices.");
                    return;
                }

                Sensor<?, ?> sensor = (Sensor<?, ?>) edgeMap.get(edgeLabel).getDevice(deviceName);
                // Decode the MeasurementWindow, setValues in the sensors, and get the new MeasurementWindow<Float[]>
                MeasurementWindow<Float[]> window = sensor.sensed(message);
                sensor.onRead();
                for (Measurement<Float[]> m : window.getMeasurementsOlderToNewer()) {
                    // Store the values in the database
                    db.write(m.getValue(), m.getTimestamp(), edgeLabel, deviceName);
                }
            } catch (Exception e) {
                System.err.println("[AmqpManager] Error sensing incoming message for routing key " + senderRoutingKey);
            }
        };
        channel.basicConsume(queueName, true, callback, consumerTag -> { });
    }

    public void virtualActuate(VirtualActuator actuator, String edgeLabel, byte[] message)
            throws IOException {
        // Prepare communications
        String actuatorLabel = ((Device) actuator).getLabel();
        String baseTopic = "edge";
        String intermediateTopic = "actuators";
        String routingKey = baseTopic + "." + edgeLabel + "." + intermediateTopic + "." + actuatorLabel;
        System.out.println("VirtualComm.virtualActuate: Sending actuation to " + edgeLabel + "." + actuatorLabel);
        System.out.println("VirtualComm.virtualActuate: Using routingKey " + routingKey);
        // Publish message
        channel.basicPublish(EXCHANGE_NAME, routingKey, null, message);
    }

    /** Parse device name from the routing key in deliverer's message. */
    private String getEdgeLabel(String routingKey){
        String[] routingKeyParts = routingKey.split("\\.");
        return routingKeyParts[1];
    }

    /** Parse device name from the routing key in deliverer's message. */
    private String getDeviceName(String routingKey){
        String[] routingKeyParts = routingKey.split("\\.");
        return routingKeyParts[3];
    }

    public Channel getChannel() {
        return channel;
    }

    public String getExchangeName() {
        return EXCHANGE_NAME;
    }
}
