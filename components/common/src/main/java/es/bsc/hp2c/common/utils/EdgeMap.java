package es.bsc.hp2c.common.utils;

import java.util.HashMap;
import java.util.Map;
import es.bsc.hp2c.common.types.Device;

public class EdgeMap {
    private final Map<String, Map<String, Device>> edgeMap;

    public EdgeMap() {
        this.edgeMap = new HashMap<>();
    }

    public void addDevice(String edgeLabel, String deviceLabel, Device device) {
        edgeMap.computeIfAbsent(edgeLabel, k -> new HashMap<>()).put(deviceLabel, device);
    }

    public Device getDevice(String edgeLabel, String deviceLabel) {
        Map<String, Device> devices = edgeMap.get(edgeLabel);
        if (devices != null) {
            return devices.get(deviceLabel);
        }
        return null;
    }

    public String getEdgeParent(Device device) {
        for (Map.Entry<String, Map<String, Device>> entry : edgeMap.entrySet()) {
            if (entry.getValue().containsValue(device)) {
                return entry.getKey();
            }
        }
        return null;
    }


    @Override
    public String toString() {
        return edgeMap.toString();
    }
}
