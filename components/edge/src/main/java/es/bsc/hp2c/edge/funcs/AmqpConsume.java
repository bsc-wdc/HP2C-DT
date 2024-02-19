package es.bsc.hp2c.edge.funcs;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import es.bsc.hp2c.HP2CEdge;
import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Sensor;
import org.json.JSONArray;

import java.io.IOException;
import java.util.ArrayList;

import static es.bsc.hp2c.HP2CEdge.getEdgeLabel;

/**
 * Start listener for incoming actuation AMQP messages
 * The topic has the following format:
 *      edge.<EDGE_ID>.actuators.<DEVICE_ID>
 */
public class AmqpConsume extends Func {
    private final Actuator actuator;
    private final String edgeLabel;
    private final String actuatorLabel;
    private static String EXCHANGE_NAME;
    private final String routingKey;
    private final Channel channel;

    /**
     * Method constructor.
     *
     * @param sensors   List of sensors declared for the function.
     * @param actuators List of actuators declared for the function.
     * @param others    Rest of parameters declared for de function.
     */
    public AmqpConsume(ArrayList<Sensor<?, ?>> sensors, ArrayList<Actuator<?>> actuators, JSONArray others)
            throws IllegalArgumentException {

        super(sensors, actuators, others);

        if (actuators.size() != 1) {
            throw new IllegalArgumentException("There should be one sensor for each AmqpPublish Func");
        }
        if (!sensors.isEmpty()) {
            throw new IllegalArgumentException("AmqpPublish does not use actuators");
        }
        // Sensor setup (remove whitespaces and dashes to avoid Influx especial characters)
        actuator = actuators.get(0);
        actuatorLabel = ((Device) actuator).getLabel();

        // Initialize AMQP communication
        edgeLabel = getEdgeLabel();
        channel = HP2CEdge.getChannel();
        EXCHANGE_NAME = HP2CEdge.getExchangeName();
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
            System.err.println("Error declaring RabbitMQ consumer queue" +
                    " for edge " + edgeLabel + " and actuator " + actuatorLabel);
            throw new RuntimeException(e);
        }

        // Declare callback to respond to commands
        System.out.println(" [x] Awaiting requests for queue " + routingKey + " at exchange " + EXCHANGE_NAME);
        DeliverCallback callback = (consumerTag, delivery) -> {
            // Parse message. For instance: routingKey = "edge.edge1.actuators.voltmeter1"
            byte[] message = delivery.getBody();
            long timestampMillis = delivery.getProperties().getTimestamp().getTime();
            String senderRoutingKey = delivery.getEnvelope().getRoutingKey();
            String[] routingKeyParts = senderRoutingKey.split("\\.");
            System.out.println("Actuator " + actuatorLabel + " received command.");
            actuator.actuate(message);
        };

        // Start consuming messages
        try {
            channel.basicConsume(queueName, true, callback, consumerTag -> { });
        } catch (IOException e) {
            System.err.println("Error consuming AMQP message" +
                    " for edge " + edgeLabel + " and actuator " + actuatorLabel);
            throw new RuntimeException(e);
        }
    }
}
