package es.bsc.hp2c.server.edge;

import es.bsc.hp2c.common.types.Device;

import java.util.ArrayList;
import java.util.Map;

public class VirtualEdge {
    private final Map<String, Device> devices;
    private boolean isAvailable;

    public VirtualEdge(Map<String, Device> devices) {
        this.devices = devices;
        this.isAvailable = true;
    }

    public boolean containsDevice(String deviceLabel) {
        return devices.containsKey(deviceLabel);
    }

    public Device getDevice(String deviceLabel) {
        return devices.get(deviceLabel);
    }

    public ArrayList<String> getDeviceLabels() {
        return new ArrayList<>(devices.keySet());
    }

    public ArrayList<Device> getDevices() {
        return new ArrayList<>(devices.values());
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public void setAvailable(boolean available) {
        isAvailable = available;
    }

    public Map<String, Device> getDeviceMap(){
        return devices;
    }
}
