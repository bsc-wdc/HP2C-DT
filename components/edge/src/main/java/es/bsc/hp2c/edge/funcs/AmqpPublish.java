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

/*
 * Publish current measurement to the corresponding AMQP topic if the requirements are satisfied.
 * The topic has the following format:
 *      edge.<EDGE_ID>.<DEVICE_ID>
 */
public class AmqpPublish extends Func {
    private static String baseTopic = "edge";
    private final Sensor<?, ?> sensor;
    private final Channel channel;
    private final String edgeId;
    private final String EXCHANGE_NAME;
    private final String sensorLabel;

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

        // Initialize AMQP communication
        edgeId = getEdgeLabel();
        channel = HP2CEdge.getChannel();
        EXCHANGE_NAME = HP2CEdge.getExchangeName();

        // Sensor setup (remove whitespaces from label)
        sensor = sensors.get(0);
        sensorLabel = ((Device) sensor).getLabel().replaceAll("\\s", "");
    }

    @Override
    public void run() {
        Float[] values = (Float[]) this.sensor.getCurrentValues();
        // Publish value to the corresponding topic (Format: edge.<EDGE_ID>.<DEVICE_ID>)
        for (int i = 0; i < values.length; i++) {
            String routingKey = baseTopic + "." + edgeId + "." + sensorLabel + "-sensor" + i;
            String message = String.valueOf(values[i]);
            try {
                channel.basicPublish(EXCHANGE_NAME, routingKey, null,
                        message.getBytes(UTF_8));
            } catch (IOException e) {
                System.out.println("IOException during AMQP publish: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }
    }
}
