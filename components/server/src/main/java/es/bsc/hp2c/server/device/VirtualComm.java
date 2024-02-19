package es.bsc.hp2c.server.device;

import com.rabbitmq.client.Channel;
import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Sensor;

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

    protected interface VirtualDevice {
        String getEdgeLabel();
    }
    protected interface VirtualSensor<V> extends Sensor<Float[], V>, VirtualDevice {}
    public interface VirtualActuator<V> extends Actuator<V>, VirtualDevice {
        void actuate(String[] stringValues) throws IOException;
        Float[] actuateValues(V value);
        byte[] encodeValues();
    }

    public static void virtualActuate(VirtualActuator actuator, String edgeLabel, byte[] message) throws IOException {
        // Prepare communications
        String actuatorLabel = ((Device) actuator).getLabel();
        Channel channel = HP2CServer.getChannel();
        String routingKey = baseTopic + "." + edgeLabel + "." + intermediateTopic + "." + actuatorLabel;
        System.out.println("VirtualComm.virtualActuate: Sending actuation to " + edgeLabel + "." + actuatorLabel);
        System.out.println("VirtualComm.virtualActuate: Using routingKey " + routingKey);
        // Publish message
        channel.basicPublish(EXCHANGE_NAME_ACTUATORS, routingKey, null, message);
    }
}
