package es.bsc.hp2c.edge.funcs;

import es.bsc.hp2c.common.generic.Ammeter;
import es.bsc.hp2c.common.generic.Voltmeter;
import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.funcs.Func;
import es.bsc.hp2c.common.types.Sensor;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Map;

import org.json.JSONObject;

/**
 * The method calculates the power and prints it through standard output.
 */
public class CalcPower extends Func {
    private Voltmeter<?> voltmeter;
    private Ammeter<?> ammeter;

    /**
     * Calcpower method constructor.
     * 
     * @param sensors   Map of edge-sensors declared for the function.
     * @param actuators Map of edge-actuators declared for the function.
     * @param others    Rest of parameters declared for de function.
     */
    public CalcPower(Map<String, ArrayList<Sensor<?, ?>>> sensors, Map<String, ArrayList<Actuator<?>>> actuators, JSONObject others)
            throws IllegalArgumentException {

        super(sensors, actuators, others);

        Sensor<?, ?> sensor1 = null;
        Sensor<?, ?> sensor2 = null;

        try {
            ArrayList<Sensor<?, ?>> sensorsList = sensors.values().iterator().next();
            sensor1 = sensorsList.get(0);
            if (sensorsList.size() == 2) {
                sensor2 = sensorsList.get(1);
            } else if (sensors.size() == 2) {
                sensor2 = sensors.values().iterator().next().get(0);
            }
        }
        catch (Exception e){
            throw new IllegalArgumentException("Sensors must be exactly two: one voltmeter and one ammeter");
        }

        if (!(sensor1 instanceof Voltmeter && sensor2 instanceof Ammeter)
                && !(sensor2 instanceof Voltmeter && sensor1 instanceof Ammeter)) {
            throw new IllegalArgumentException("Sensors must be one voltmeter and one ammeter");
        }

        if (sensor1 instanceof Voltmeter) {
            this.voltmeter = (Voltmeter<?>) sensor1;
            this.ammeter = (Ammeter<?>) sensor2;
        } else {
            this.voltmeter = (Voltmeter<?>) sensor2;
            this.ammeter = (Ammeter<?>) sensor1;
        }
    }

    @Override
    public void run() {
        boolean voltmeterIsAvailable = voltmeter.getSensorAvailability();
        boolean ammeterIsAvailable = ammeter.getSensorAvailability();
        Float[] voltage = this.voltmeter.getCurrentValues();
        Float[] current = this.ammeter.getCurrentValues();
        if (!voltmeterIsAvailable || !ammeterIsAvailable){
            System.err.println("[CalcPower] Warning in function CalcPower: ");
            if (!voltmeterIsAvailable) System.err.println("Voltmeter is not available");
            else if (voltage == null) System.err.println("Voltmeter has no value");
            if (!ammeterIsAvailable) System.err.println("Ammeter is not available");
            else if (current == null) System.err.println("Ammeter has no value");
        }
        if (voltage != null && current != null) {
            System.out.println("[CalcPower] Calculating power: ");
            System.out.println("[CalcPower]     Power is: " + voltage[0] * current[0] + " W");
        }
    }
}
