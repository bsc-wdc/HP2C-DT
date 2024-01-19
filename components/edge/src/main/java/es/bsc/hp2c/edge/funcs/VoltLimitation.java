package es.bsc.hp2c.edge.funcs;

import es.bsc.hp2c.common.generic.Switch;
import es.bsc.hp2c.common.generic.Voltmeter;
import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Sensor;

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
     * @param sensors   List of sensors declared for the function.
     * @param actuators List of actuators declared for the function.
     * @param others    Rest of parameters declared for de function.
     */
    public VoltLimitation(ArrayList<Sensor<?, ?>> sensors, ArrayList<Actuator<?>> actuators, JSONArray others)
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
        if (voltmeter.isSensorAvailable()){
            Float[] voltage = this.voltmeter.getCurrentValues();
            if (voltage[0] > this.threshold) {
                System.out.println("Voltage limit exceeded. Turning actuators off...");
                try {
                    Switch.State[] states = sw.getCurrentValues();
                    Switch.State[] values = {Switch.State.OFF, Switch.State.ON, Switch.State.ON};
                    states[0] = Switch.State.OFF;
                    if (!sw.isActuatorAvailable()){ System.err.println("Switch is not available"); }
                    sw.actuate(values);
                } catch (Exception e) {
                    System.err.println("Error while setting switch OFF: " + e.getMessage());
                }
            }
        }
        else{
            System.err.print("Error in function VoltLimitation: ");
            System.err.println("Voltmeter is not available");
        }
    }
}
