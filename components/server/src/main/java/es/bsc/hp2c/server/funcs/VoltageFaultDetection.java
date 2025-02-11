package es.bsc.hp2c.server.funcs;

import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.funcs.Func;
import es.bsc.hp2c.common.types.Sensor;
import es.bsc.hp2c.server.device.VirtualVoltmeter;
import es.bsc.hp2c.server.modules.AlarmHandler;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

import static es.bsc.hp2c.HP2CServer.*;
import static es.bsc.hp2c.server.modules.AlarmHandler.*;
import static es.bsc.hp2c.common.utils.FileUtils.getJsonObject;

public class VoltageFaultDetection extends Func {
    private JSONObject nominalVoltages;
    private float threshold;
    private AlarmHandler alarms;

    public VoltageFaultDetection(ArrayList<Sensor<?, ?>> sensors, ArrayList<Actuator<?>> actuators, JSONObject others)
            throws FunctionInstantiationException {
        super(sensors, actuators, others);
        try {
            alarms = getAlarms();
            String cwd = Paths.get("").toAbsolutePath().toString();
            System.out.println("Current path: " + cwd);

            String path = "";
            File file = new File("/data/nominal_voltages.json");

            if (file.exists() && file.isFile()) {
                path = "/data/nominal_voltages.json";
            } else {
                path = "deployments/defaults/nominal_voltages.json";
            }
            nominalVoltages = getJsonObject(path);
        } catch (Exception e){
            throw new FunctionInstantiationException("[VoltageFaultDetection] Error loading nominal_voltages.json");
        }

        try {
            threshold = others.getFloat("threshold");
        } catch (Exception e){
            throw new FunctionInstantiationException("[VoltageFaultDetection] 'threshold' must be defined in 'other' section");
        }
        try {
            alarms.addNewAlarm("VoltageFaultDetection");
        } catch (Exception e){
            throw new FunctionInstantiationException("Error creating new alarm VoltageFaultDetection");
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
                if (!d.getDeviceAvailability()){
                    System.out.println("[VoltageFaultDetection] Voltmeter " + d.getLabel() + " is not available");
                    continue;
                }

                Sensor s = (Sensor) d;
                VirtualVoltmeter va = (VirtualVoltmeter) s;
                if (Objects.equals(va.getAggregate(), "phasor")) {
                    Float[] m = va.getCurrentValues();
                    if (m != null && m.length > 0) {
                        edgeMeasurements.put(d.getLabel(), m[0]);
                    }
                } else {
                    System.out.println("[VoltageFaultDetection] Voltmeter " + d.getLabel() + " - " + edgeLabel +
                            " aggregate is not phasor (" + va.getAggregate() +")");
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
              
                if (currentVoltage < ((1-threshold) * nominalVoltage) || currentVoltage > (1+threshold) * nominalVoltage) {
                    try {
                        turnOffSwitches(edgeLabel);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    String infoMessage = "Fault detected on edge " + edgeLabel + ". Voltage is " +
                            (currentVoltage / nominalVoltage * 100) + "%, Threshold is " + (threshold * 100) + "%";

                    System.out.println("[VoltageFaultDetection] " + infoMessage);
                    alarms.writeAlarm("VoltageFaultDetection", edgeLabel, voltmeterLabel, infoMessage, true);
                } else { // update alarm (check if timeout has expired)
                    alarms.writeAlarm("VoltageFaultDetection", edgeLabel, voltmeterLabel, null, false);

                }
            }
        }
    }
}

