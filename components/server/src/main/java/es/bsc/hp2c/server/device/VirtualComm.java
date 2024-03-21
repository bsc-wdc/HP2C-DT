package es.bsc.hp2c.server.device;

import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Sensor;

import java.io.IOException;
import java.util.ArrayList;

public class VirtualComm {

    protected interface VirtualDevice {
        String getEdgeLabel();
    }
    protected interface VirtualSensor<V> extends Sensor<Float[], V>, VirtualDevice {}
    public interface VirtualActuator<V> extends Actuator<V>, VirtualDevice {
        void actuate(String[] stringValues) throws IOException;
        boolean isCategorical();
        ArrayList<String> getCategories();
        int getSize();
    }

}
