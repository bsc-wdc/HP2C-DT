package es.bsc.hp2c.edge.funcs;

import es.bsc.hp2c.HP2CEdge;
import es.bsc.hp2c.common.generic.Ammeter;
import es.bsc.hp2c.common.generic.Voltmeter;
import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.funcs.Func;
import es.bsc.hp2c.common.types.Sensor;

import java.util.ArrayList;
import java.util.Map;

import org.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import es.bsc.compss.types.annotations.Parameter;
import es.bsc.compss.types.annotations.Constraints;
import es.bsc.compss.types.annotations.parameter.Direction;
import es.bsc.compss.types.annotations.parameter.Type;
import es.bsc.compss.types.annotations.task.Method;

/**
 * The method calculates the power and prints it through standard output.
 */
public class CalcPower extends Func {
    private Voltmeter<?> voltmeter;
    private Ammeter<?> ammeter;
    private static final Logger logger = LogManager.getLogger("appLogger");

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
        int counter = 0;
        for (int i = 0; i < 10; i++) {
            counter = increment(counter);
        }
        boolean voltmeterIsAvailable = voltmeter.getSensorAvailability();
        boolean ammeterIsAvailable = ammeter.getSensorAvailability();
        Float[] voltage = this.voltmeter.getCurrentValues();
        Float[] current = this.ammeter.getCurrentValues();
        if (!voltmeterIsAvailable || !ammeterIsAvailable){
            logger.error("[CalcPower] Warning in function CalcPower: ");
            if (!voltmeterIsAvailable) logger.error("Voltmeter is not available");
            else if (voltage == null) logger.error("Voltmeter has no value");
            if (!ammeterIsAvailable) logger.error("Ammeter is not available");
            else if (current == null) logger.error("Ammeter has no value");
        }
        if (voltage != null && current != null) {
            logger.info("[CalcPower] Calculating power: ");
            logger.info("[CalcPower]     Power is: " + voltage[0] * current[0] + " W");
        }
    }

    public static int increment(int input) {
        logger.info("INCREMENTED INPUT IS NOW " + input);
        return input + 1;
    }

    public static interface COMPSsItf {
        @Constraints(computingUnits = "1")
        @Method(declaringClass = "es.bsc.hp2c.edge.funcs.CalcPower")
        int increment(
                @Parameter(type = Type.INT, direction = Direction.IN) int input
        );
    }

}
