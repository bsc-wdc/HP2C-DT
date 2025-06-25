package es.bsc.hp2c.edge.funcs;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import es.bsc.hp2c.HP2CEdge;
import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.funcs.Func;
import es.bsc.hp2c.common.types.Sensor;
import org.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import es.bsc.hp2c.HP2CEdgeContext;

/**
 * Start listener for incoming actuation AMQP messages
 * The topic has the following format:
 *      edge.<EDGE_ID>.actuators.<DEVICE_ID>
 */
public class AmqpConsume extends Func {
    private final Actuator<?> actuator;
    private final String edgeLabel;
    private final String actuatorLabel;
    private static String EXCHANGE_NAME;
    private final String routingKey;
    private final Channel channel;
    private static final Logger logger = LogManager.getLogger("appLogger");

    /**
     * Method constructor.
     *
     * @param sensors   Map of edge-sensors declared for the function.
     * @param actuators List of edge-actuators declared for the function.
     * @param others    Rest of parameters declared for de function.
     */
    public AmqpConsume(Map<String, ArrayList<Sensor<?, ?>>> sensors, Map<String, ArrayList<Actuator<?>>> actuators, JSONObject others)
            throws IllegalArgumentException {

        super(sensors, actuators, others);
        ArrayList<Actuator<?>> actuatorsList;
        if (actuators != null && !actuators.isEmpty()) {
            actuatorsList = actuators.values().iterator().next();
        } else {
            throw new IllegalArgumentException("The actuators map is empty or null.");
        }

        if (actuatorsList.size() != 1) {
            throw new IllegalArgumentException("There should be one sensor for each AmqpPublish Func");
        }
        if (!sensors.isEmpty()) {
            throw new IllegalArgumentException("AmqpPublish does not use actuators");
        }
        // Sensor setup (remove whitespaces and dashes to avoid Influx especial characters)
        actuator = actuatorsList.get(0);
        actuatorLabel = ((Device) actuator).getLabel();

        // Initialize AMQP communication
        edgeLabel = HP2CEdgeContext.getEdgeLabel();
        channel = HP2CEdgeContext.getChannel();
        EXCHANGE_NAME = HP2CEdgeContext.getExchangeName();
        String baseTopic = "edge";
        String intermediateTopic = "actuators";
        routingKey = baseTopic + "." + edgeLabel + "." + intermediateTopic + "." + actuatorLabel;
    }

    @Override
    public void run() {
        // Setup RabbitMQ channel
        String queueName;
        try {
            queueName = channel.queueDeclare().getQueue();
            channel.queueBind(queueName, EXCHANGE_NAME, routingKey);
        } catch (IOException e) {
            logger.error("Error declaring RabbitMQ consumer queue" +
                    " for edge " + edgeLabel + " and actuator " + actuatorLabel);
            throw new RuntimeException(e);
        }

        // Declare callback to respond to commands
        logger.info("[AmqpConsume] Awaiting requests for queue " + routingKey + " at exchange " + EXCHANGE_NAME);
        DeliverCallback callback = (consumerTag, delivery) -> {
            // Parse message. For instance: routingKey = "edge.edge1.actuators.voltmeter1"
            byte[] message = delivery.getBody();
            long timestampMillis = delivery.getProperties().getTimestamp().getTime();
            String senderRoutingKey = delivery.getEnvelope().getRoutingKey();
            String[] routingKeyParts = senderRoutingKey.split("\\.");
            logger.info("Actuator " + actuatorLabel + " received a command.");
            try {
                actuator.actuate(message);
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        };

        // Start consuming messages
        try {
            channel.basicConsume(queueName, true, callback, consumerTag -> { });
        } catch (IOException e) {
            logger.error("Error consuming AMQP message" +
                    " for edge " + edgeLabel + " and actuator " + actuatorLabel);
            throw new RuntimeException(e);
        }
    }
}
