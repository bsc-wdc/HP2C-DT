package es.bsc.hp2c.common.funcs;

import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Sensor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import es.bsc.hp2c.common.python.PythonHandler;
import es.bsc.hp2c.common.python.UDSClient;
import org.json.JSONArray;
import org.json.JSONObject;

import static java.lang.Thread.sleep;

/**
 * General wrapper to call Python functions through a thread running the Python session and Unix Sockets
 */
public class PythonFunc extends Func {
    private final UDSClient socket;
    private final PythonHandler pythonHandler;
    private final Map<String, ArrayList<Sensor<?, ?>>> sensors;
    private final Map<String, ArrayList<Actuator<?>>> actuators;
    private final String moduleName;
    private final String methodName;
    private final JSONObject otherFuncParams;

    /**
     * PythonFunc method constructor.
     *
     * @param sensors   Map of edge-sensors declared for the function.
     * @param actuators Map of edge-actuators declared for the function.
     * @param jParams   Contains the Python module and function names, and other parameters to the function
     */
    public PythonFunc(Map<String, ArrayList<Sensor<?, ?>>> sensors, Map<String,
            ArrayList<Actuator<?>>> actuators, JSONObject jParams)
            throws FunctionInstantiationException, IOException, InterruptedException {

        super(sensors, actuators, jParams);
        this.sensors = sensors;
        this.actuators = actuators;
        this.moduleName = jParams.getString("module_name");
        this.methodName = jParams.optString("method_name", null);
        this.otherFuncParams = jParams.optJSONObject("other_func_parameters");

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
        if (jResponse.get("actuations") != null){
            JSONObject actuations = jResponse.getJSONObject("actuations");
            for (String edgeName:actuations.keySet()){
                JSONObject edge = actuations.getJSONObject(edgeName);
                for (String deviceName:edge.keySet()){
                    JSONArray values = edge.optJSONArray(deviceName);
                    if (values != null){
                        Actuator<?> actuator = getActuator(edgeName, deviceName);
                        try {
                            actuator.actuate(getStringArray(values));
                        } catch (IOException e) {
                            System.err.print("[PythonFunc] Error actuating over actuator " +
                                    ((Device) actuator).getLabel() + " in func " + moduleName + ": " + e);
                        }
                    }
                }
            }
        }
        System.out.println("[PythonFunc] " + moduleName + " results: \n" + jResponse.toString(4));
    }

    public Actuator<?> getActuator(String edgeName, String deviceName){
        while(actuators.values().iterator().hasNext()){
            String currentEdgeName = actuators.keySet().iterator().next();
            if(currentEdgeName.equals(edgeName)){
                for (Actuator<?> actuator:actuators.get(currentEdgeName)){
                    if (deviceName.equals(((Device) actuator).getLabel())){
                        return actuator;
                    }
                }
                return null;
            }
        }
        return null;
    }

    public static String[] getStringArray(JSONArray jsonArray) {
        if (jsonArray == null) {
            return new String[0];
        }
        String[] stringArray = new String[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); i++) {
            stringArray[i] = jsonArray.optString(i);
        }
        return stringArray;
    }

}
