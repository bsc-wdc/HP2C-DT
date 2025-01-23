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
        // Map to store edge-device-measurement relationships
        Map<String, Map<String, Float>> edgeDeviceMeasurements = new HashMap<>();
        for (String edgeLabel : getEdgeLabels()) {
            ArrayList<Device> ammetersEdge = getDevicesByTypeAndEdge("Ammeter", edgeLabel);

            for (Device d : ammetersEdge) {
                VirtualAmmeter va = (VirtualAmmeter) d;

                // Check if aggregate is "phasor"
                if (!Objects.equals(va.getAggregate(), "phasor")) {
                    System.out.println("[LoadBalanceAlarm] Ammeter " + d.getLabel() + " aggregate is not phasor");
                } else {
                    // Get current values
                    Float[] m = va.getCurrentValues();
                    if (m != null) {
                        // Add edge-device-measurement to the map
                        if (!edgeDeviceMeasurements.containsKey(edgeLabel)) {
                            edgeDeviceMeasurements.put(edgeLabel, new HashMap<>());
                        }
                        edgeDeviceMeasurements.get(edgeLabel).put(d.getLabel(), m[0]);
                    }
                }
            }
        }

        // Collect all measurements
        ArrayList<Float> currentMeasurements = new ArrayList<>();
        for (String edge : edgeDeviceMeasurements.keySet()) {
            for (String device : edgeDeviceMeasurements.get(edge).keySet()) {
                currentMeasurements.add(edgeDeviceMeasurements.get(edge).get(device));
            }
        }

        // Perform imbalance check
        if (!currentMeasurements.isEmpty()) {
            Float maxCurrent = Collections.max(currentMeasurements);
            Float minCurrent = Collections.min(currentMeasurements);
            float threshold = imbalance_range * maxCurrent;

            if (maxCurrent - minCurrent > threshold) {
                System.out.println("[LoadBalanceAlarm] Load imbalance detected: max = " + maxCurrent + ", min = " + minCurrent);

                // Write alarm for each edge-device pair
                for (String edge : edgeDeviceMeasurements.keySet()) {
                    for (String device : edgeDeviceMeasurements.get(edge).keySet()) {
                        writeAlarm("LoadBalanceAlarm", edge, device);
                    }
                }
            } else { // update alarm (check if timeout has expired)
                for (String edge : edgeDeviceMeasurements.keySet()) {
                    for (String device : edgeDeviceMeasurements.get(edge).keySet()) {
                        updateAlarm("LoadBalanceAlarm", edge, device);
                    }
                }
            }
        }
    }


}
