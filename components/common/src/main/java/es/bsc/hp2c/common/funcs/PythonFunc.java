package es.bsc.hp2c.common.funcs;

import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Sensor;

import java.io.IOException;
import java.util.ArrayList;

import es.bsc.hp2c.common.python.PythonHandler;
import es.bsc.hp2c.common.python.UDSClient;
import org.json.JSONObject;

import static java.lang.Thread.sleep;

/**
 * General wrapper to call Python functions through a thread running the Python session and Unix Sockets
 */
public class PythonFunc extends Func {
    private final UDSClient socket;
    private final PythonHandler pythonHandler;
    private final ArrayList<Sensor<?, ?>> sensors;
    private final ArrayList<Actuator<?>> actuators;
    private final String moduleName;
    private final String methodName;
    private final JSONObject otherFuncParams;

    /**
     * PythonFunc method constructor.
     *
     * @param sensors   List of sensors declared for the function.
     * @param actuators List of actuators declared for the function.
     * @param jParams   Contains the Python module and function names, and other parameters to the function
     */
    public PythonFunc(ArrayList<Sensor<?, ?>> sensors, ArrayList<Actuator<?>> actuators, JSONObject jParams)
            throws FunctionInstantiationException, IOException, InterruptedException {

        super(sensors, actuators, jParams);
        this.sensors = sensors;
        this.actuators = actuators;
        this.moduleName = jParams.getString("module_name");
        this.methodName = jParams.optString("method_name", null);
        this.otherFuncParams = jParams.optJSONObject("other_func_parameters", null);

        // Initialize the Python server
        this.pythonHandler = new PythonHandler(moduleName);
        this.pythonHandler.start();

        // Now UDSClient connects to that socket as a client
        sleep(1000);  // Give the Python server time to set up the Unix socket
        this.socket = new UDSClient(moduleName, methodName, pythonHandler.getSocketPath());
    }

    @Override
    public void run() {
        System.out.println("[PythonFunc] Calling Python function " + moduleName);
        JSONObject jResponse = socket.call(sensors, actuators, otherFuncParams);
        System.out.println("[PythonFunc] " + moduleName + " results: \n" + jResponse.toString(4));
    }

}
