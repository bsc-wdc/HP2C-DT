package es.bsc.hp2c.edge.funcs;

import es.bsc.hp2c.HP2CEdge;
import es.bsc.hp2c.edge.types.Actuator;
import es.bsc.hp2c.edge.types.Device;
import es.bsc.hp2c.edge.types.Sensor;

import com.rabbitmq.client.Channel;
import org.json.JSONArray;

import java.io.IOException;
import java.util.ArrayList;

import static es.bsc.hp2c.HP2CEdge.getEdgeLabel;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Publish current measurement to the corresponding AMQP topic if the requirements are satisfied.
 * The topic has the following format:
 *      edge.<EDGE_ID>.<DEVICE_ID>
 */
public class AmqpPublish extends Func {
    private static String baseTopic = "edge";
    private final Sensor<?, ?> sensor;
    private final Channel channel;
    private final String EXCHANGE_NAME;
    private final String routingKey;

    /**
     * Method constructor.
     *
     * @param sensors   List of sensors declared for the function.
     * @param actuators List of actuators declared for the function.
     * @param others    Rest of parameters declared for de function.
     */
    public AmqpPublish(ArrayList<Sensor<?, ?>> sensors, ArrayList<Actuator<?>> actuators, JSONArray others)
            throws IllegalArgumentException {

        super(sensors, actuators, others);

        if (sensors.size() != 1) {
            throw new IllegalArgumentException("There should be one sensor for each AmqpPublish Func");
        }
        if (!actuators.isEmpty()) {
            throw new IllegalArgumentException("AmqpPublish does not use actuators");
        }
        // Sensor setup (remove whitespaces and dashes to avoid Influx especial characters)
        sensor = sensors.get(0);
        String sensorLabel = ((Device) sensor).getLabel();

        // Initialize AMQP communication
        String edgeId = getEdgeLabel();
        channel = HP2CEdge.getChannel();
        EXCHANGE_NAME = HP2CEdge.getExchangeName();
        routingKey = baseTopic + "." + edgeId + "." + sensorLabel;
    }

    @Override
    public void run() {
        String message = this.sensor.getCurrentValuesAsString();
        try {
            channel.basicPublish(EXCHANGE_NAME, routingKey, null, message.getBytes(UTF_8));
        } catch (IOException e) {
            System.err.println("IOException during AMQP publishing");
            throw new RuntimeException(e);
        }
    }
}
