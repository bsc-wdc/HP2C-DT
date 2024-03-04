/**
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
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import es.bsc.hp2c.HP2CServer;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Sensor;
import es.bsc.hp2c.common.utils.CommUtils;
import es.bsc.hp2c.server.device.VirtualComm.VirtualActuator;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class AmqpManager {
    private final DatabaseHandler db;
    private static Channel channel = null;
    private final Map<String, Map<String, Device>> deviceMap;
    private static final String EXCHANGE_NAME = "measurements";

    /**
     * Initialize AMQP Channel.
     *
     * @param localIp local IP address where the RabbitMQ broker is deployed
     *                if IP is not configured in deployment_setup.json
     * @param deviceMap Map of the edge nodes and their devices
     */
    public AmqpManager(String localIp, Map<String, Map<String, Device>> deviceMap, DatabaseHandler db)
            throws IOException, TimeoutException {
        this.deviceMap = deviceMap;
        this.db = db;
        // Select broker IP
        String brokerIp = CommUtils.parseRemoteBrokerIp(localIp);
        // Start connection
        connect(brokerIp);
    }

    /** Start AMQP connection with broker. */
    private static void connect(String setupIp) throws IOException, TimeoutException {
        System.out.println("Connecting to RabbitMQ broker at host IP " + setupIp + "...");
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(setupIp);
        Connection connection = factory.newConnection();
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
        System.out.println(" [x] Awaiting requests");

        DeliverCallback callback = (consumerTag, delivery) -> {
            // Parse message. For instance: routingKey = "edge.edge1.sensors.voltmeter1"
            byte[] message = delivery.getBody();
            String senderRoutingKey = delivery.getEnvelope().getRoutingKey();
            long timestampMillis = delivery.getProperties().getTimestamp().getTime();
            String edgeLabel = getEdgeLabel(senderRoutingKey);
            String deviceName = getDeviceName(senderRoutingKey);
            // Check existence of pair edge-device
            if (!HP2CServer.isInMap(edgeLabel, deviceName, deviceMap)) {
                System.err.println("Edge " + edgeLabel + ", Device " + deviceName
                        + ": message received but device not listed as " + edgeLabel + " digital twin devices.");
                return;
            }
            // Sense to the corresponding sensor
            Sensor<?, ?> sensor = (Sensor<?, ?>) deviceMap.get(edgeLabel).get(deviceName);
            sensor.sensed(message);
            // Write entry in database
            db.write((Float[]) sensor.decodeValuesSensor(message), timestampMillis, edgeLabel, deviceName);
        };
        channel.basicConsume(queueName, true, callback, consumerTag -> { });
    }

    public static void virtualActuate(VirtualActuator actuator, String edgeLabel, byte[] message)
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
}
