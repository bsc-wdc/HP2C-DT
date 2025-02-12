package es.bsc.hp2c.common.python;

import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Sensor;
import org.json.JSONObject;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Connects to an existing UNIX socket as a client to make periodic calls of a Python function and handles its output
 */
public class UDSClient {
    private final String moduleName;
    private final String methodName;
    private AFUNIXSocket socket;
    private BufferedWriter writer;
    private BufferedReader reader;

    /**
     * Constructs a new UDSClient instance.
     * This constructor initializes the socket client with the provided module name
     * and the path to the UNIX domain socket, and then establishes a connection to the server.
     *
     * @param moduleName   the exact name of the python module (without .py) that will be called by the Python server.
     * @param methodName (optional) name of the Python function name inside moduleName (typically "main").
     * @param socketPath   path to the UNIX domain socket file.
     *                     The client will attempt to connect to the server at this socket location.
     * @throws IOException if an I/O error occurs while setting up the connection to the server.
     */
    public UDSClient(String moduleName, String methodName, String socketPath) throws IOException {
        this.moduleName = moduleName;
        this.methodName = methodName;
        setupConnection(socketPath);
    }

    private synchronized void setupConnection(String socketPath) throws IOException {
        File socketFile = new File(socketPath);
        socket = AFUNIXSocket.newInstance();
        socket.connect(AFUNIXSocketAddress.of(socketFile));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    /**
     * Runs the Python function, passing the list of parameters funcParams parsed inside a JSON object.
     *
     * @param sensors Map of edge-sensors to add to the python parameters
     * @param actuators Map of edge-actuators to add to the python parameters
     * @param otherFuncParams list of parameters of the destination function
     * @return Result of the Python operation
     */
    public synchronized JSONObject call(Map<String, ArrayList<Sensor<?, ?>>> sensors, Map<String,
            ArrayList<Actuator<?>>> actuators, JSONObject otherFuncParams) {
        if (socket == null || !socket.isConnected()) {
            throw new IllegalStateException("[UDSClient] " + moduleName + ": Socket is not connected.");
        }

        try {
            // Create the JSON object
            JSONObject jsonMessage = composeJSON(moduleName, methodName, sensors, actuators, otherFuncParams);

            // Send the JSON message to the socket
            writer.write(jsonMessage.toString());
            // writer.newLine();  // Optional newline if server expects one
            writer.flush();

            // Read the response from the server
            String response = reader.readLine();
            if (response == null) {
                throw new RuntimeException("[UDSClient] "
                        + moduleName + ": Socket connection closed unexpectedly.");
            }
            return new JSONObject(response);
        } catch (IOException e) {
            throw new RuntimeException("[UDSClient] "
                    + moduleName + ": Error during socket communication: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a JSON object to invoke the Python function with function and module names, sensor values, and
     * additional parameters.
     *
     * @param moduleName      The module name to include in the JSON.
     * @param methodName    The function name to include in the JSON.
     * @param sensors         Map of edge-sensor objects, each having a `getCurrentValues()` method.
     * @param actuators       Map of edge-actuator objects
     * @param otherFuncParams A JSONObject containing additional function parameters.
     * @return A JSONObject with the complete key-value set of params with the following structure
     * {
     *   "module_name": <MODULE_NAME>,
     *   "method_name": <METHOD_NAME>,
     *   "parameters": {
     *     "sensors": {
     *          "edge1":{
     *              "VoltmeterGen1": [<SENSOR_VALUE_1>, <SENSOR_VALUE_2>]
     *           },
     *     },
     *     "actuators": {
     *          "edge1":{
     *              "ThreePhaseSwitchGen1": <ACTUATOR_TYPE_1>
     *          },
     *     },
     *     "param1": <PARAM1_VALUE>,
     *     "param2": <PARAM2_VALUE>,
     *     ...,
     *   }
     * }
     */
    public static JSONObject composeJSON(String moduleName, String methodName, Map<String, ArrayList<Sensor<?, ?>>> sensors,
                                         Map<String, ArrayList<Actuator<?>>> actuators, JSONObject otherFuncParams) {
        JSONObject jPyParams = new JSONObject();
        jPyParams.put("module_name", moduleName);
        jPyParams.put("method_name", methodName);

        // Constructing sensors JSON
        JSONObject jSensors = new JSONObject();
        for (String edgeLabel:sensors.keySet()){
            JSONObject jEdge = new JSONObject();
            for (Sensor<?,?> sensor:sensors.get(edgeLabel)){
                Object[] values = (Object[]) sensor.getCurrentValues();
                String name = ((Device) sensor).getLabel();
                jEdge.put(name, values);
            }
            jSensors.put(edgeLabel, jEdge);
        }


        // Constructing actuators JSON
        JSONObject jActuators = new JSONObject();
        for (String edgeLabel:actuators.keySet()){
            JSONObject jEdge = new JSONObject();
            for (Actuator<?> actuator:actuators.get(edgeLabel)){
                String name = ((Device) actuator).getLabel();
                jEdge.put(name, actuator.getClass().getSimpleName());
            }
            jActuators.put(edgeLabel, jEdge);
        }

        // Constructing parameters object
        JSONObject parameters = new JSONObject();
        parameters.put("sensors", jSensors);
        parameters.put("actuators", jActuators);

        // Merging otherFuncParams into parameters
        for (String key : otherFuncParams.keySet()) {
            parameters.put(key, otherFuncParams.get(key));
        }
        jPyParams.put("parameters", parameters);

        return jPyParams;
    }

    public synchronized void close() {
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (socket != null) socket.close();
            System.out.println("[UDSClient] " + moduleName + ": Socket connection closed.");
        } catch (Exception e) {
            System.err.println("[UDSClient] " + moduleName + ": Error closing the socket: " + e.getMessage());
        }
    }
}
