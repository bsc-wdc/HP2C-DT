package es.bsc.hp2c.server.device;

import com.rabbitmq.client.Channel;
import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Sensor;

import es.bsc.hp2c.common.generic.Switch.State;

import es.bsc.hp2c.HP2CServer;

import java.io.IOException;

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

    public static void virtualActuate(VirtualActuator actuator, String edgeName, State[] values) {
        // Set up actuator
        String actuatorLabel = ((Device) actuator).getLabel();
        Float[] rawValues = actuator.actuateValues(values);
        System.out.println("Encoding values: " + rawValues);
        byte[] message = actuator.encodeValues();
        // Prepare communications
        Channel channel = HP2CServer.getChannel();
        String routingKey = baseTopic + "." + edgeName + "." + intermediateTopic + "." + actuatorLabel;
        System.out.println("Using routingKey " + routingKey);
        // Publish message
        try {
            channel.basicPublish(EXCHANGE_NAME_ACTUATORS, routingKey, null, message);
        } catch (IOException e) {
            System.err.println("IOException during AMQP publishing");
            throw new RuntimeException(e);
        }
    }
}
