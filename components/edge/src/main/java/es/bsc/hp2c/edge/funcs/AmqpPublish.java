package es.bsc.hp2c.edge.funcs;

import com.rabbitmq.client.AMQP;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.funcs.Func;
import es.bsc.hp2c.common.types.Sensor;

import com.rabbitmq.client.Channel;
import es.bsc.hp2c.common.utils.CommUtils;
import es.bsc.hp2c.common.utils.MeasurementWindow;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import es.bsc.hp2c.HP2CEdgeContext;

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
    private JSONObject aggArgs;

    /**
     * Method constructor.
     *
     * @param sensors   Map of edge-sensors declared for the function.
     * @param actuators Map of edge-actuators declared for the function.
     * @param others    Rest of parameters declared for de function.
     */
    public AmqpPublish(Map<String, ArrayList<Sensor<?, ?>>> sensors, Map<String, ArrayList<Actuator<?>>> actuators, JSONObject others)
            throws IllegalArgumentException, ClassNotFoundException, NoSuchMethodException {
        super(sensors, actuators, others);
        String aggName = others.getJSONObject("aggregate").optString("type", "last");

        JSONObject jAggregate = others.getJSONObject("aggregate");
        aggArgs = new JSONObject();
        if (jAggregate.has("args")){
            aggArgs = jAggregate.getJSONObject("args");
        }

        Class<?> c = Class.forName("es.bsc.hp2c.common.utils.Aggregates");
        Method agg = null;
        try {
            agg = c.getMethod(aggName, MeasurementWindow.class, JSONObject.class);
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodException("Aggregate not found: " + aggName);
        }
        aggregate = agg;

        ArrayList<Sensor<?,?>> sensorsList;
        if (sensors != null && !sensors.isEmpty()) {
            sensorsList = sensors.values().iterator().next();
        } else {
            throw new IllegalArgumentException("The sensors map is empty or null.");
        }

        if (sensorsList.size() != 1) {
            throw new IllegalArgumentException("There should be one sensor for each AmqpPublish Func");
        }
        if (!actuators.isEmpty()) {
            throw new IllegalArgumentException("AmqpPublish does not use actuators");
        }
        // Sensor setup (remove whitespaces and dashes to avoid Influx especial characters)
        sensor = sensorsList.get(0);
        String sensorLabel = ((Device) sensor).getLabel();

        // Initialize AMQP communication
        String edgeLabel = HP2CEdgeContext.getEdgeLabel();
        channel = HP2CEdgeContext.getChannel();
        EXCHANGE_NAME = HP2CEdgeContext.getExchangeName();
        String intermediateTopic = "sensors";
        String baseTopic = "edge";
        routingKey = baseTopic + "." + edgeLabel+ "." + intermediateTopic + "." + sensorLabel;
    }

    @Override
    public void run() {
        try {
            // Create a new MeasurementWindow using the appropriate aggregate function
            if (sensor.getWindow() == null) {
                System.err.println("Sensor window is null");
                return;
            }
            MeasurementWindow<?> aggregateWindow =
                    (MeasurementWindow<?>) aggregate.invoke(null, this.sensor.getWindow(), aggArgs);

            // Handle malformed aggregate window
            if (aggregateWindow == null) {
                System.out.println("[AMQPPublish] Aggregate " + aggregate.getName() + " retrieved a null window. " +
                        "Skipping AMQP publication...");
                return;
            }
            System.out.println("[AMQPPublish] Sending values for sensor "
                    + ((Device) sensor).getLabel() + ": " + aggregateWindow);

            // Prepare body message
            byte[] message = aggregateWindow.encode();

            // Set up timestamping in nanoseconds
            AMQP.BasicProperties props = CommUtils.createAmqpPropertiesNanos();

            // Deliver message
            channel.basicPublish(EXCHANGE_NAME, routingKey, props, message);
        } catch (IOException e) {
            System.err.println("IOException during AMQP publishing");
            throw new RuntimeException(e);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
