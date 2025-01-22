package es.bsc.hp2c.server.funcs;

import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Func;
import es.bsc.hp2c.common.types.Sensor;
import es.bsc.hp2c.server.device.VirtualAmmeter;
import es.bsc.hp2c.server.device.VirtualVoltmeter;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

import static es.bsc.hp2c.HP2CServer.*;
import static es.bsc.hp2c.common.utils.AlarmHandler.addNewAlarm;
import static es.bsc.hp2c.common.utils.AlarmHandler.writeAlarm;
import static es.bsc.hp2c.common.utils.FileUtils.getJsonObject;
import static java.util.Collections.max;
import static java.util.Collections.min;

public class VoltageFaultDetection extends Func {
    private JSONObject nominalVoltages;
    private float threshold;

    public VoltageFaultDetection(ArrayList<Sensor<?, ?>> sensors, ArrayList<Actuator<?>> actuators, JSONObject others)
            throws FunctionInstantiationException {
        super(sensors, actuators, others);
        try {
            String cwd = Paths.get("").toAbsolutePath().toString();
            System.out.println("Current path: " + cwd);

            String path = "";
            File file = new File("/data/nominal_voltages.json");

            if (file.exists() && file.isFile()) {
                path = "/data/nominal_voltages.json";
            }
            else if (cwd.endsWith("server")) {
                path = "../../deployments/defaults/nominal_voltages.json";
            } else {
                path = "deployments/defaults/nominal_voltages.json";
            }
            nominalVoltages = getJsonObject(path);
        } catch (Exception e){
            throw new FunctionInstantiationException("[VoltageFaultDetection] Error loading nominal_voltages.json");
        }

        try {
            threshold = others.getFloat("threshold");
            addNewAlarm("VoltageFaultDetection");
        } catch (Exception e){
            throw new FunctionInstantiationException("[VoltageFaultDetection] 'threshold' must be defined in 'other' section");
        }
    }

    @Override
    public void run() {
        // Retrieve edge labels and map voltmeters to their edges
        ArrayList<String> edgeLabels = getEdgeLabels();
        Map<String, ArrayList<Device>> voltmetersEdge = new HashMap<>();
        for (String edgeLabel : edgeLabels) {
            voltmetersEdge.put(edgeLabel, getDevicesByTypeAndEdge("Voltmeter", edgeLabel));
        }

        // Store current measurements for each edge
        Map<String, Map<String, Float>> currentMeasurements = new HashMap<>();
        for (String edgeLabel : voltmetersEdge.keySet()) {
            Map<String, Float> edgeMeasurements = new HashMap<>();
            for (Device d : voltmetersEdge.get(edgeLabel)) {
                Sensor s = (Sensor) d;
                VirtualVoltmeter va = (VirtualVoltmeter) s;
                if (Objects.equals(va.getAggregate(), "phasor")) {
                    Float[] m = va.getCurrentValues();
                    if (m != null && m.length > 0) {
                        edgeMeasurements.put(d.getLabel(), m[0]);
                    }
                } else {
                    System.out.println("[VoltageFaultDetection] Voltmeter " + d.getLabel() + " aggregate is not phasor");
                }
            }
            currentMeasurements.put(edgeLabel, edgeMeasurements);
        }

        // Check measurements against nominal voltages
        for (String edgeLabel : currentMeasurements.keySet()) {
            if (!nominalVoltages.has(edgeLabel)) {
                continue;
            }

            Float nominalVoltage = nominalVoltages.optFloat(edgeLabel);
            Map<String, Float> edgeMeasurements = currentMeasurements.get(edgeLabel);

            for (String voltmeterLabel : edgeMeasurements.keySet()) {
                Float currentVoltage = edgeMeasurements.get(voltmeterLabel);
                if (currentVoltage < (threshold * nominalVoltage)) {
                    try {
                        turnOffSwitches(edgeLabel);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    System.out.println("[VoltageFaultDetection] Fault detected on edge " + edgeLabel + ". Voltage is " +
                            (currentVoltage / nominalVoltage * 100) + "%, Threshold is " + (threshold * 100) + "%");
                    writeAlarm("VoltageFaultDetection", edgeLabel, voltmeterLabel);
                }
            }
        }
    }
}

