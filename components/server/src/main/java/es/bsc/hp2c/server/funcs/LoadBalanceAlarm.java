package es.bsc.hp2c.server.funcs;

import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Func;
import es.bsc.hp2c.common.types.Sensor;
import org.json.JSONObject;

import java.util.ArrayList;

public class LoadBalanceAlarm extends Func {

    public LoadBalanceAlarm(ArrayList<Sensor<?, ?>> sensors, ArrayList<Actuator<?>> actuators, JSONObject others)
            throws Func.FunctionInstantiationException {
        super(sensors, actuators, others);
    }

    @Override
    public void run() {

    }
}
