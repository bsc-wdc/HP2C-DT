package es.bsc.hp2c.common.utils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * Connects to an existing UNIX socket as a client
 */
public class UnixSocketClient {
    private final String funcModule;
    private AFUNIXSocket socket;
    private BufferedWriter writer;
    private BufferedReader reader;

    /**
     * Constructs a new UnixSocketClient instance.
     * This constructor initializes the socket client with the provided module name
     * and the path to the UNIX domain socket, and then establishes a connection to the server.
     *
     * @param funcModule the exact name of the python module (without .py) that will be called by the Python server.
     * @param socketPath path to the UNIX domain socket file.
     *                   The client will attempt to connect to the server at this socket location.
     * @throws IOException if an I/O error occurs while setting up the connection to the server.
     */
    public UnixSocketClient(String funcModule, String socketPath) throws IOException {
        this.funcModule = funcModule;
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
     * @param funcParams list of parameters of the destination function
     * @return Result of the Python operation
     */
    public synchronized String call(Object... funcParams) {
        if (socket == null || !socket.isConnected()) {
            throw new IllegalStateException("[UnixSocketClient] " + funcModule + ": Socket is not connected.");
        }

        try {
            // Create the JSON object with name and info fields
            JSONObject jsonMessage = new JSONObject();
            jsonMessage.put("method_name", "main");
            jsonMessage.put("info", "");

            // Add the funcParams as a JSON array
            JSONArray jsonParams = new JSONArray();
            for (Object param : funcParams) {
                jsonParams.put(param);
            }
            jsonMessage.put("funcParams", jsonParams);

            // Send the JSON message to the socket
            writer.write(jsonMessage.toString());
            // writer.newLine();  // Optional newline if server expects one
            writer.flush();

            // Read the response from the server
            String response = reader.readLine();
            if (response == null) {
                throw new RuntimeException("[UnixSocketClient] "
                        + funcModule + ": Socket connection closed unexpectedly.");
            }
            return response;
        } catch (IOException e) {
            throw new RuntimeException("[UnixSocketClient] "
                    + funcModule + ": Error during socket communication: " + e.getMessage(), e);
        }
    }

    public synchronized void close() {
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (socket != null) socket.close();
            System.out.println("[UnixSocketClient] " + funcModule + ": Socket connection closed.");
        } catch (Exception e) {
            System.err.println("[UnixSocketClient] " + funcModule + ": Error closing the socket: " + e.getMessage());
        }
    }
}
