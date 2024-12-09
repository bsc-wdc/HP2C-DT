package es.bsc.hp2c.edge.funcs;

import com.rabbitmq.client.AMQP;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import es.bsc.hp2c.HP2CEdge;
import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Func;
import es.bsc.hp2c.common.types.Sensor;

import com.rabbitmq.client.Channel;
import es.bsc.hp2c.common.utils.CommUtils;
import es.bsc.hp2c.common.utils.MeasurementWindow;
import org.json.JSONArray;

import java.io.IOException;
import java.util.ArrayList;

import static es.bsc.hp2c.HP2CEdge.getEdgeLabel;

/**
 * Publish current measurement to the corresponding AMQP topic if the requirements are satisfied.
 * The topic has the following format:
 *      edge.<EDGE_ID>.<DEVICE_ID>
 */
public class AmqpPublish extends Func {
    private final Sensor<?, ?> sensor;
    private final Channel channel;
    private final String EXCHANGE_NAME;
    private final String routingKey;
    private final Method aggregate;

    /**
     * Method constructor.
     *
     * @param sensors   List of sensors declared for the function.
     * @param actuators List of actuators declared for the function.
     * @param others    Rest of parameters declared for de function.
     */
    public AmqpPublish(ArrayList<Sensor<?, ?>> sensors, ArrayList<Actuator<?>> actuators, JSONArray others)
            throws IllegalArgumentException, ClassNotFoundException, NoSuchMethodException {
        super(sensors, actuators, others);

        String aggName = others.optString(0, "last");
        Class<?> c = Class.forName("es.bsc.hp2c.common.utils.Aggregates");
        Method agg = null;
        try {
            agg = c.getMethod(aggName, MeasurementWindow.class);
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodException("Method not found: " + aggName);
        }
        aggregate = agg;

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
        String edgeLabel = getEdgeLabel();
        channel = HP2CEdge.getChannel();
        EXCHANGE_NAME = HP2CEdge.getExchangeName();
        String intermediateTopic = "sensors";
        String baseTopic = "edge";
        routingKey = baseTopic + "." + edgeLabel+ "." + intermediateTopic + "." + sensorLabel;
    }

    @Override
    public void run() {
        // Prepare body message
        byte[] message = this.sensor.encodeValuesSensor();

        // Set up timestamping in nanoseconds
        AMQP.BasicProperties props = CommUtils.createAmqpPropertiesNanos();

        // Deliver message
        try {
            channel.basicPublish(EXCHANGE_NAME, routingKey, props, message);
        } catch (IOException e) {
            System.err.println("IOException during AMQP publishing");
            throw new RuntimeException(e);
        }
    }
}
