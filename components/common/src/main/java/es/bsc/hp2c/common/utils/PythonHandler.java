package es.bsc.hp2c.common.utils;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import java.util.Map;
import java.util.UUID;

/**
 * Executes a Python script in a separate thread, passing function parameters via a Unix Domain Socket (UDS).
 * Captures and prints the Python script's output and error streams in real time.
 * The class constructs a unique UDS path and invokes the Python script with the specified function module
 * and its parameters (in JSON format). The script output and error streams are handled by separate threads.
 *
 */
public class PythonHandler extends Thread {
    private final String serverPath = "/home/eiraola/projects/hp2cdt/components/udsServer/uds_server.py";  // TODO: implement for relative folders and Docker
    private final String moduleName;
    private final String socketPath;

    /**
     * Constructs a new PythonHandler.
     *
     * @param moduleName The Python function module name (without .py) that will ultimately be called by the Python
     *                   server. Func modules must be located in the 'udsServer/funcs' directory
     */
    public PythonHandler(String moduleName) {
        this.moduleName = moduleName;
        UUID uuid = UUID.randomUUID();
        this.socketPath = "/tmp/hp2c_" + moduleName + "_" + uuid + ".sock";
    }

    /**
     * Executes the Python script as a subprocess and handles its output and error streams.
     */
    public void run() {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("python", serverPath, socketPath, moduleName);

        // Set environment variables
        // TODO: Set PYTHONUNBUFFERED to flush Python prints (but lower performance!)
        Map<String, String> environment = processBuilder.environment();
        environment.put("PYTHONUNBUFFERED", "1");

        try {
            // Start the process
            Process process = processBuilder.start();

            // Capture the output (stdout) from the Python script in a separate thread
            Thread outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[PythonHandler] PYTHON OUTPUT: " + line);  // Print output in real-time
                    }
                } catch (IOException e) {
                    System.err.println("[PythonHandler] Error reading output: " + e.getMessage());
                }
            });

            // Capture the error (stderr) from the Python script in a separate thread
            Thread errorThread = new Thread(() -> {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        System.err.println("[PythonHandler] PYTHON ERR: " + line);  // Print error output in real-time
                    }
                } catch (IOException e) {
                    System.err.println("[PythonHandler] Error reading error output: " + e.getMessage());
                }
            });

            // Start the threads
            outputThread.start();
            errorThread.start();

            // Wait for the process to finish
            int exitCode = process.waitFor();

            // Ensure threads finish before proceeding
            outputThread.join();
            errorThread.join();

            System.out.println("[PythonHandler] Exited with code: " + exitCode);

        } catch (IOException | InterruptedException e) {
            System.err.println("[PythonHandler] Error while executing python: " + e.getMessage());
        }
    }

    public String getSocketPath() {
        return socketPath;
    }

}