package es.bsc.hp2c.server.funcs;

import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Func;
import es.bsc.hp2c.common.types.Sensor;
import es.bsc.hp2c.server.device.VirtualAmmeter;
import org.json.JSONObject;

import java.util.*;

import static es.bsc.hp2c.HP2CServer.*;
import static es.bsc.hp2c.common.utils.AlarmHandler.*;

public class LoadBalanceAlarm extends Func {
    private float imbalance_range;

    public LoadBalanceAlarm(ArrayList<Sensor<?, ?>> sensors, ArrayList<Actuator<?>> actuators, JSONObject others)
            throws FunctionInstantiationException {
        super(sensors, actuators, others);
        try {
            imbalance_range = others.getFloat("imbalance-range");
            addNewAlarm("LoadBalanceAlarm");
        } catch (Exception e){
            throw new FunctionInstantiationException("[LoadBalanceAlarm] 'imbalance-range' must be defined in " +
                    "'other' section");
        }
    }

    @Override
    public void run() {
        // Map to store edge-device measurements
        Map<String, Map<String, Float>> edgeDeviceMeasurements = new HashMap<>();
        for (String edgeLabel : getEdgeLabels()) {
            ArrayList<Device> ammetersEdge = getDevicesByTypeAndEdge("Ammeter", edgeLabel);

            for (Device d : ammetersEdge) {
                if (!d.getDeviceAvailability()){
                    System.out.println("[LoadBalanceAlarm] Ammeter " + d.getLabel() + " is not available");
                    continue;
                }

                VirtualAmmeter va = (VirtualAmmeter) d;
                // Check if aggregate is "phasor"
                if (!Objects.equals(va.getAggregate(), "phasor")) {
                    System.out.println("[LoadBalanceAlarm] Ammeter " + d.getLabel() + " aggregate is not phasor");
                } else {
                    // Get current values
                    Float[] m = va.getCurrentValues();
                    if (m != null) {
                        // Add edge-device measurement to the map
                        edgeDeviceMeasurements.computeIfAbsent(edgeLabel, k -> new HashMap<>())
                                .put(d.getLabel(), m[0]);
                    }
                }
            }
        }

        // Store min and max values and their corresponding edge-device pairs
        Float maxCurrent = Float.MIN_VALUE;
        Float minCurrent = Float.MAX_VALUE;
        String maxPair = null;
        String minPair = null;

        // Collect all measurements and find min/max with their edge-device pair
        for (Map.Entry<String, Map<String, Float>> edgeEntry : edgeDeviceMeasurements.entrySet()) {
            String edge = edgeEntry.getKey();
            for (Map.Entry<String, Float> deviceEntry : edgeEntry.getValue().entrySet()) {
                String device = deviceEntry.getKey();
                Float value = deviceEntry.getValue();

                if (value > maxCurrent) {
                    maxCurrent = value;
                    maxPair = edge + "-" + device;
                }
                if (value < minCurrent) {
                    minCurrent = value;
                    minPair = edge + "-" + device;
                }
            }
        }

        // Perform imbalance check
        if (!edgeDeviceMeasurements.isEmpty()) {
            float threshold = imbalance_range * maxCurrent;

            if (maxCurrent - minCurrent > threshold) {
                // Write a generic alarm with info message
                String infoMessage = "Load imbalance detected: max=" + maxCurrent + " (" + maxPair + ") " +
                        "min=" + minCurrent + " (" + minPair + ")";
                System.out.println("[LoadBalanceAlarm]" + infoMessage);

                writeAlarm("LoadBalanceAlarm", null, null, infoMessage);
            } else { // Update alarm (check if timeout has expired)
                updateAlarm("LoadBalanceAlarm", null, null);
            }
        }
    }
}
