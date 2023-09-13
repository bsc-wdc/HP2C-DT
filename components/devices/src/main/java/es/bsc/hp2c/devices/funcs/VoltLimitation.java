package es.bsc.hp2c.devices.funcs;

import es.bsc.hp2c.devices.funcs.Func.FunctionInstantiationException;
import es.bsc.hp2c.devices.generic.Switch;
import es.bsc.hp2c.devices.generic.Voltmeter;
import es.bsc.hp2c.devices.types.Actuator;
import es.bsc.hp2c.devices.types.Sensor;

import java.util.ArrayList;

import org.json.JSONArray;

/**
 * The method checks whether the written voltage is lower than threshold.
 */
public class VoltLimitation extends Func {
    private Float threshold;
    private Voltmeter<?> voltmeter;
    private Switch<?> sw;

    /**
     * VoltLimitation method constructor.
     * 
     * @param sensors  List of sensors declared for the function.
     * @param actuators List of actuators declared for the function.
     * @param others  Rest of parameters declared for de function.
     */
    public VoltLimitation(ArrayList<Sensor<?,?>> sensors, ArrayList<Actuator<?>> actuators, JSONArray others)
            throws FunctionInstantiationException {

        super(sensors, actuators, others);

        if (!(sensors.size() == 1)) {
            throw new FunctionInstantiationException("Sensors must be exactly one voltmeter");
        }

        if (!(sensors.get(0) instanceof Voltmeter)) {
            throw new FunctionInstantiationException("The sensor must be a voltmeter");
        }

        if (!(actuators.size() == 1)) {
            throw new FunctionInstantiationException("Actuators must be exactly one switch");
        }

        if (!(actuators.get(0) instanceof Switch)) {
            throw new FunctionInstantiationException("The actuator must be a switch");
        }

        this.voltmeter = (Voltmeter) sensors.get(0);
        this.sw = (Switch) actuators.get(0);
        this.threshold = others.getFloat(0);
    }

    @Override
    public void run() {
        Float voltage = this.voltmeter.getCurrentValue();
        if (voltage > this.threshold) {
            System.out.println("Voltage limit exceeded. Turning actuators off...");

            try {
                sw.setValue(Switch.State.OFF);
            } catch (Exception e) {
                e.printStackTrace(); //MSSG ERROR
            }   
        }
    }
}
