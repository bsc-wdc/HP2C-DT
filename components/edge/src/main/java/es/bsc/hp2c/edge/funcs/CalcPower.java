package es.bsc.hp2c.edge.funcs;

import es.bsc.hp2c.common.generic.Ammeter;
import es.bsc.hp2c.common.generic.Voltmeter;
import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Sensor;

import java.util.ArrayList;

import org.json.JSONArray;

/**
 * The method calculates the power and prints it through standard output.
 */
public class CalcPower extends Func {
    private Voltmeter<?> voltmeter;
    private Ammeter<?> ammeter;

    /**
     * Calcpower method constructor.
     * 
     * @param sensors   List of sensors declared for the function.
     * @param actuators List of actuators declared for the function.
     * @param others    Rest of parameters declared for de function.
     */
    public CalcPower(ArrayList<Sensor<?, ?>> sensors, ArrayList<Actuator<?>> actuators, JSONArray others)
            throws IllegalArgumentException {

        super(sensors, actuators, others);

        if (sensors.size() != 2) {
            throw new IllegalArgumentException("Sensors must be exactly two: one voltmeter and one ammeter");
        }

        if (!(sensors.get(0) instanceof Voltmeter && sensors.get(1) instanceof Ammeter)
                && !((sensors.get(1) instanceof Voltmeter && sensors.get(0) instanceof Ammeter))) {
            throw new IllegalArgumentException("Sensors must be one voltmeter and one ammeter");
        }

        if (sensors.get(0) instanceof Voltmeter && sensors.get(1) instanceof Ammeter) {
            this.voltmeter = (Voltmeter<?>) sensors.get(0);
            this.ammeter = (Ammeter<?>) sensors.get(1);
        }

        else {
            this.voltmeter = (Voltmeter<?>) sensors.get(1);
            this.ammeter = (Ammeter<?>) sensors.get(0);
        }
    }

    @Override
    public void run() {
        Float[] voltage = this.voltmeter.getCurrentValues();
        Float[] current = this.ammeter.getCurrentValues();
        System.out.println("Calculating power: ");
        System.out.println("    Power is: " + voltage[0] * current[0] + " W");
    }
}
