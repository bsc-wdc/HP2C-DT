package es.bsc.hp2c.common.utils;

import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Func;
import es.bsc.hp2c.common.types.Sensor;

import java.util.ArrayList;
import org.json.JSONObject;

/**
 * The method checks whether the written voltage is lower than threshold.
 */
public class PythonFunc extends Func {
    private final UnixSocketClient socket;
    private final PythonCaller pythonCaller;
    private final ArrayList<Sensor<?, ?>> sensors;
    private final ArrayList<Actuator<?>> actuators;
    private final String moduleName;
    private JSONObject jParams;

    /**
     * VoltLimitation method constructor.
     *
     * @param sensors   List of sensors declared for the function.
     * @param actuators List of actuators declared for the function.
     * @param jParams    Rest of parameters declared for the function.
     */
    public PythonFunc(ArrayList<Sensor<?, ?>> sensors, ArrayList<Actuator<?>> actuators, JSONObject jParams)
            throws FunctionInstantiationException {

        super(sensors, actuators, jParams);
        this.sensors = sensors;
        this.actuators = actuators;
        this.jParams = jParams;
        this.moduleName = jParams.getString("module_name");

        // Initialize socket and Python environment that will connect to the same socket
        this.socket = new UnixSocketClient(moduleName);
        this.pythonCaller = new PythonCaller(moduleName, this.socket.getSocketPath(), jParams);
        this.pythonCaller.start();
        this.socket.start();
    }

    @Override
    public void run() {
        System.out.println("[PythonFunc - " + moduleName + "] Starting python function!!!");
        socket.call(sensors.get(0).getCurrentValues());  // TODO: pass arguments
    }
}
