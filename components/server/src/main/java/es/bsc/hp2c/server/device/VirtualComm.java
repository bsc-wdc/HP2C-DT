package es.bsc.hp2c.server.device;

import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Sensor;

import java.io.IOException;

public class VirtualComm {

    protected interface VirtualDevice {
        String getEdgeLabel();
    }
    protected interface VirtualSensor<V> extends Sensor<Float[], V>, VirtualDevice {}
    public interface VirtualActuator<V> extends Actuator<V>, VirtualDevice {
        void actuate(String[] stringValues) throws IOException;
    }

}
