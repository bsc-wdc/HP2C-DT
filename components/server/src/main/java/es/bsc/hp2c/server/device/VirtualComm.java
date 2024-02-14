package es.bsc.hp2c.server.device;

import com.rabbitmq.client.Channel;
import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Sensor;

import es.bsc.hp2c.common.generic.Switch.State;

import es.bsc.hp2c.HP2CServer;

import java.io.IOException;

import static es.bsc.hp2c.common.utils.CommUtils.isNumeric;
import static es.bsc.hp2c.common.utils.CommUtils.printableArray;

public class VirtualComm {
    private static String EXCHANGE_NAME_ACTUATORS = "measurements";
    private static String baseTopic = "edge";
    private static String intermediateTopic = "actuators";

    static {
        Channel channel = HP2CServer.getChannel();
        try {
            channel.exchangeDeclare(EXCHANGE_NAME_ACTUATORS, "topic");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected interface VirtualDevice {}
    protected interface VirtualSensor<V> extends Sensor<Float[], V>, VirtualDevice {}
    public interface VirtualActuator<V> extends Actuator<V>, VirtualDevice {
        Float[] actuateValues(V value);
        byte[] encodeValues();
    }

    public static void virtualActuate(VirtualActuator actuator, String edgeLabel, String[] rawValues) {
        // Parse user input into Float or String (Switch.State)
        Boolean isNumber = null;  // Non-primitive Boolean to get null by default
        Object[] values = new Object[rawValues.length];
        for (int i = 0; i < rawValues.length; i++) {
            if (isNumeric(rawValues[i])){
                if (isNumber != null && !isNumber) {
                    throw new IllegalArgumentException("Mixed numeric and non-numeric types in actuation values.");
                }
                values[i] = Float.parseFloat(rawValues[i]);
                isNumber = true;
            } else {
                if (isNumber != null && isNumber) {
                    throw new IllegalArgumentException("Mixed numeric and non-numeric types in actuation values.");
                }
                values[i] = State.valueOf(rawValues[i]);
                isNumber = false;
            }
        }

        // Get the corresponding value actuator data type (previously Object)
        if (isNumber) {
            Float[] floatValues = new Float[values.length];
            for (int i = 0; i < values.length; i++) {
                floatValues[i] = (Float) values[i];
            }
            values = floatValues;
        } else {
            State[] stateValues = new State[values.length];
            for (int i = 0; i < values.length; i++) {
                stateValues[i] = (State) values[i];
            }
            values = stateValues;
        }

        // Prepare communications
        String actuatorLabel = ((Device) actuator).getLabel();
        System.out.println("VirtualComm.virtualActuate: Sending actuation to "
                + edgeLabel + "." + actuatorLabel + ": " + printableArray(values));
        byte[] message = actuator.encodeValues(values);
        Channel channel = HP2CServer.getChannel();
        String routingKey = baseTopic + "." + edgeLabel + "." + intermediateTopic + "." + actuatorLabel;
        System.out.println("VirtualComm.virtualActuate: Using routingKey " + routingKey);

        // Publish message
        try {
            channel.basicPublish(EXCHANGE_NAME_ACTUATORS, routingKey, null, message);
        } catch (IOException e) {
            System.err.println("IOException during AMQP publishing");
            throw new RuntimeException(e);
        }
    }
}
