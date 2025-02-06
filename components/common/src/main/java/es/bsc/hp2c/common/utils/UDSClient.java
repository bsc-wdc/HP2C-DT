package es.bsc.hp2c.common.utils;

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
     * @param sensors List of sensors to add to the python parameters
     * @param actuators List of actuators to add to the python parameters
     * @param otherFuncParams list of parameters of the destination function
     * @return Result of the Python operation
     */
    public synchronized JSONObject call(ArrayList<Sensor<?, ?>> sensors, ArrayList<Actuator<?>> actuators,
                                        JSONObject otherFuncParams) {
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
     * @param sensors         List of sensor objects, each having a `getCurrentValues()` method.
     * @param actuators       List of actuator objects
     * @param otherFuncParams A JSONObject containing additional function parameters.
     * @return A JSONObject with the complete key-value set of params
     */
    public static JSONObject composeJSON(String moduleName, String methodName, ArrayList<Sensor<?, ?>> sensors,
                                         ArrayList<Actuator<?>> actuators, JSONObject otherFuncParams) {
        JSONObject jPyParams = new JSONObject();
        jPyParams.put("module_name", moduleName);
        jPyParams.put("method_name", methodName);

        // Constructing actuators map
        HashMap<String, Object[]> sensorMap = new HashMap<>();
        for (Sensor<?,?> sensor : sensors) {
            Object[] values = (Object[]) sensor.getCurrentValues();
            String name = ((Device) sensor).getLabel();
            sensorMap.put(name, values);
        }

        // Constructing actuators map
        HashMap<String, String> actuatorMap = new HashMap<>();
        for (Actuator<?> actuator : actuators) {
            String name = ((Device) actuator).getLabel();
            actuatorMap.put(name, actuator.getClass().getSimpleName());
        }

        // Constructing parameters object
        JSONObject parameters = new JSONObject();
        parameters.put("sensors", sensorMap);
        parameters.put("actuators", actuatorMap);

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
