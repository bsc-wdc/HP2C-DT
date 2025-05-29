package es.bsc.hp2c.edge.funcs;

import es.bsc.hp2c.HP2CEdge;
import es.bsc.hp2c.common.generic.Switch;
import es.bsc.hp2c.common.generic.Voltmeter;
import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.funcs.Func;
import es.bsc.hp2c.common.types.Sensor;

import java.util.ArrayList;
import java.util.Map;

import org.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The method checks whether the written voltage is lower than threshold.
 */
public class VoltLimitation extends Func {
    private static final Logger logger = LogManager.getLogger("appLogger");
    private Float threshold;
    private Voltmeter<?> voltmeter;
    private Switch<?> sw;

    /**
     * VoltLimitation method constructor.
     * 
     * @param sensors   Map of edge-sensors declared for the function.
     * @param actuators Map of edge-actuators declared for the function.
     * @param others    Rest of parameters declared for de function.
     */
    public VoltLimitation(Map<String, ArrayList<Sensor<?, ?>>> sensors, Map<String, ArrayList<Actuator<?>>> actuators, JSONObject others)
            throws FunctionInstantiationException {

        super(sensors, actuators, others);

        ArrayList<Sensor<?,?>> sensorsList;
        if (sensors != null && !sensors.isEmpty()) {
            sensorsList = sensors.values().iterator().next();
        } else {
            throw new IllegalArgumentException("The sensors map is empty or null.");
        }

        ArrayList<Actuator<?>> actuatorsList;
        if (actuators != null && !actuators.isEmpty()) {
            actuatorsList = actuators.values().iterator().next();
        } else {
            throw new IllegalArgumentException("The actuators map is empty or null.");
        }

        if (sensorsList.size() != 1) {
            throw new FunctionInstantiationException("Sensors must be exactly one voltmeter");
        }

        if (actuatorsList.size() != 1) {
            throw new FunctionInstantiationException("Actuators must be exactly one switch");
        }

        Sensor<?, ?> sensor = sensorsList.get(0);
        if (!(sensor instanceof Voltmeter)) {
            throw new FunctionInstantiationException("The sensor must be a voltmeter");
        }
        this.voltmeter = (Voltmeter<?>) sensor;

        Actuator<?> actuator = actuatorsList.get(0);
        if (!(actuator instanceof Switch)) {
            throw new FunctionInstantiationException("The actuator must be a switch");
        }
        this.sw = (Switch) actuator;

        try {
            this.threshold = others.getFloat("threshold");
        } catch (Exception e) {
            throw new FunctionInstantiationException("'threshold' field must be provided");
        }
    }


    @Override
    public void run() {
        if (voltmeter.getSensorAvailability() && voltmeter.getCurrentValues() != null){
            Float[] voltage = this.voltmeter.getCurrentValues();
            if (voltage[0] > this.threshold) {
                logger.info("Voltage limit exceeded. Turning actuators off...");
                try {
                    if (!sw.getActuatorAvailability()){
                        logger.error("[VoltLimitation] Switch is not available");
                        return;
                    }
                    Switch.State[] values = {Switch.State.OFF, Switch.State.ON, Switch.State.ON};
                    sw.actuate(values);
                } catch (Exception e) {
                    logger.error("Error while setting switch OFF: " + e.getMessage());
                }
            }
        }
        else{
            logger.error("Error in function VoltLimitation: ");
            if (!voltmeter.getSensorAvailability()) logger.error("Voltmeter is not available");
            else if (voltmeter.getCurrentValues() == null) logger.error("Voltmeter has no value");
        }
    }
}
