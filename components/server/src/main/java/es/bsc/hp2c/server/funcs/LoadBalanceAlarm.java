package es.bsc.hp2c.server.funcs;

import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Func;
import es.bsc.hp2c.common.types.Sensor;
import es.bsc.hp2c.server.device.VirtualAmmeter;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;

import static es.bsc.hp2c.HP2CServer.getSensorsByType;
import static java.util.Collections.max;
import static java.util.Collections.min;

public class LoadBalanceAlarm extends Func {
    private float imbalance_range;

    public LoadBalanceAlarm(ArrayList<Sensor<?, ?>> sensors, ArrayList<Actuator<?>> actuators, JSONObject others)
            throws FunctionInstantiationException {
        super(sensors, actuators, others);
        try {
            imbalance_range = others.getFloat("imbalance-range");
        } catch (Exception e){
            throw new FunctionInstantiationException("[LoadBalanceAlarm] 'imbalance-range' must be defined in " +
                    "'other' section");
        }
    }

    @Override
    public void run() {
        ArrayList<Sensor> ammeters = getSensorsByType("Ammeter");
        ArrayList<Float> currentMeasurements = new ArrayList<>();
        Iterator<Sensor> iterator = ammeters.iterator();
        while (iterator.hasNext()) {
            Sensor s = iterator.next();
            VirtualAmmeter va = (VirtualAmmeter) s;
            if (!Objects.equals(va.getAggregate(), "phasor")) {
                System.out.println("[LoadBalanceAlarm] Ammeter " + ((Device) s).getLabel() + " aggregate is not phasor");
                iterator.remove();
            } else {
                Float[] m = va.getCurrentValues();
                if (m != null) {
                    currentMeasurements.add(m[0]);
                }
            }
        }
        if (!currentMeasurements.isEmpty()){

            // Check for load imbalance
            Float maxCurrent = Collections.max(currentMeasurements);
            Float minCurrent = Collections.min(currentMeasurements);
            float threshold = imbalance_range * maxCurrent;

            if (maxCurrent - minCurrent > threshold) {
                System.out.println("[LoadBalanceAlarm] Load imbalance detected: max = " + maxCurrent + ", min = " + minCurrent);
            }
        }
    }
}
