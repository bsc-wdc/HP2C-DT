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
import java.util.UUID;
import java.nio.file.Files;

/**
 * Creates a Unix Socket with a unique name
 */
public class UnixSocketClient extends Thread {
    private final String name;
    private final String socketPath;
    private final File socketFile;
    private AFUNIXSocket socket;
    private BufferedWriter writer;
    private BufferedReader reader;

    public UnixSocketClient(String name) {
        this.name = name;
        UUID uuid = UUID.randomUUID();
        this.socketPath = "/tmp/hp2c_" + name + "_" + uuid + ".sock";
        this.socketFile = new File(socketPath);
    }

    public String getSocketPath() {
        return socketPath;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Remove and recreate the socket file if needed
                restartSocketFile();
                // Only set up the connection if it is not already active
                if (socket == null || !socket.isConnected()) {
                    setupConnection();
                    System.out.println("[UnixSocketClient] Socket connection " + socketPath + " established.");
                }
            } catch (Exception e) {
                System.err.println("[UnixSocketClient] Error in connection: " + e.getMessage());
                closeConnection(); // Ensure proper cleanup
                System.out.println("[UnixSocketClient] Attempting to reconnect...");
            }
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupt status
                break; // Exit loop on interrupt
            }
        }
        closeConnection(); // Cleanup if thread is interrupted
    }

    private synchronized void setupConnection() throws IOException {
        if (!socketFile.exists()) {
            // Create the empty file that will be used by the server
            boolean created = socketFile.createNewFile();
            if (created) {
                System.out.println("[UnixSocketClient] Socket file created at " + socketPath);
            } else {
                System.out.println("[UnixSocketClient] Socket file already exists at " + socketPath);
            }
        }
        socket = AFUNIXSocket.newInstance();
        socket.connect(AFUNIXSocketAddress.of(socketFile));
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    private synchronized void restartSocketFile() {
        try {
            if (socketFile.exists()) {
                Files.delete(socketFile.toPath());
            }
        } catch (IOException e) {
            System.err.println("[UnixSocketClient] Failed to delete socket file: " + e.getMessage());
        }
    }

    public synchronized String call(Object... funcParams) {
        if (socket == null || !socket.isConnected()) {
            throw new IllegalStateException("Socket is not connected.");
        }

        try {
            // Create the JSON object with name and info fields
            JSONObject jsonMessage = new JSONObject();
            jsonMessage.put("method_name", "main");
            jsonMessage.put("info", "");

            // Add the funcParams as a JSON array
            JSONArray jsonParams = new JSONArray();
            for (Object param : funcParams) {
                jsonParams.put(param); // Automatically converts each param to a JSON-friendly format
            }
            jsonMessage.put("funcParams", jsonParams);

            // Send the JSON message to the socket
            writer.write(jsonMessage.toString());
            writer.newLine(); // Optional newline if server expects one
            writer.flush();

            // Read the response from the server
            String response = reader.readLine();
            if (response == null) {
                throw new RuntimeException("Socket connection closed unexpectedly.");
            }
            return response;
        } catch (IOException e) {
            throw new RuntimeException("Error during socket communication: " + e.getMessage(), e);
        }
    }

    public synchronized void closeConnection() {
        try {
            if (writer != null) writer.close();
            if (reader != null) reader.close();
            if (socket != null) socket.close();
            System.out.println("[UnixSocketClient] Socket connection closed.");
        } catch (Exception e) {
            System.err.println("[UnixSocketClient] Error closing the socket: " + e.getMessage());
        }
    }
}
