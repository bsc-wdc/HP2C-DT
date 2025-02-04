package es.bsc.hp2c.common.utils;

import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Func;
import es.bsc.hp2c.common.types.Sensor;

import java.io.IOException;
import java.util.ArrayList;
import org.json.JSONObject;

import static java.lang.Thread.sleep;

/**
 * General wrapper to call Python functions through a thread running the Python session and Unix Sockets
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
     * @param jParams   Rest of parameters declared for the function.
     */
    public PythonFunc(ArrayList<Sensor<?, ?>> sensors, ArrayList<Actuator<?>> actuators, JSONObject jParams)
            throws FunctionInstantiationException, IOException, InterruptedException {

        super(sensors, actuators, jParams);
        this.sensors = sensors;
        this.actuators = actuators;
        this.jParams = jParams;
        this.moduleName = jParams.getString("module_name");

        // Initialize the Python server
        this.pythonCaller = new PythonCaller(moduleName, jParams);
        this.pythonCaller.start();
        // Now UnixSocketClient connects to that socket as a client
        sleep(1000);  // Give the Python server time to set up the Unix socket
        this.socket = new UnixSocketClient(moduleName, pythonCaller.getSocketPath());
    }

    @Override
    public void run() {
        System.out.println("[PythonFunc] Calling Python function " + moduleName);
        socket.call(sensors.get(0).getCurrentValues());  // TODO: pass arguments
    }
}
