package es.bsc.hp2c.devices.funcs;

import es.bsc.hp2c.HP2CSensors;
import es.bsc.hp2c.devices.types.Actuator;
import es.bsc.hp2c.devices.types.Sensor;

import com.rabbitmq.client.Channel;
import org.json.JSONArray;

import java.io.IOException;
import java.util.ArrayList;

import static java.nio.charset.StandardCharsets.UTF_8;

/*
 * Publish current measurement to the corresponding AMQP topic if the requirements are satisfied.
 * The topic has the following format:
 *      edge.<EDGE_ID>.<DEVICE_ID>
 */
public class AmqpPublish extends Func {
    private final Sensor<?, ?> sensor;
    private final Channel channel;
    private final String baseTopic;
    private final String EXCHANGE_NAME;
    private final int[] indexes;

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
        String edgeId = String.valueOf((int) (Math.random() * 100));
        baseTopic = "edge." + edgeId;
        channel = HP2CSensors.getChannel();
        EXCHANGE_NAME = HP2CSensors.getExchangeName();

        // Sensor setup
        sensor = sensors.get(0);
        indexes = new int[] {Integer.valueOf(edgeId)};  // Arbitrary
    }

    @Override
    public void run() {
        Float[] values = (Float[]) this.sensor.getCurrentValues();

        // Publish value to the corresponding topic
        // TODO: check why getCurrentValues does not work
        // float[] measurement = (float[]) sensor.getCurrentValues();
        for (int index : indexes) {
            String routingKey = baseTopic + "." + index;
            String message = String.valueOf(values[index]);
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
