package es.bsc.hp2c.common.utils;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import java.util.Map;

public class PythonCaller extends Thread {
    private final String serverPath = "/home/eiraola/projects/hp2cdt/components/unixSocketServer/unix_socket_server.py";
    private final String funcModule;
    private final String socketPath;
    private final JSONObject jsonParams;

    public PythonCaller(String funcModule, String socketPath, JSONObject jsonParams) {
        this.funcModule = funcModule;
        this.socketPath = socketPath;
        this.jsonParams = jsonParams;
    }

    public void run() {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("python", serverPath, socketPath, funcModule, jsonParams.toString());

        // Set environment variables
        Map<String, String> environment = processBuilder.environment();
        // TODO: Set PYTHONUNBUFFERED to flush Python prints (but lower performance!)
        environment.put("PYTHONUNBUFFERED", "1");

        try {
            // Start the process
            Process process = processBuilder.start();

            // Capture the output (stdout) from the Python script in a separate thread
            Thread outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("*******[PYTHON OUTPUT] " + line);  // Print output in real-time
                    }
                } catch (IOException e) {
                    System.err.println("[PythonCaller] Error reading output: " + e.getMessage());
                }
            });

            // Capture the error (stderr) from the Python script in a separate thread
            Thread errorThread = new Thread(() -> {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        System.err.println("*******[PYTHON ERR] " + line);  // Print error output in real-time
                    }
                } catch (IOException e) {
                    System.err.println("[PythonCaller] Error reading error output: " + e.getMessage());
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

            System.out.println("[PythonCaller] Exited with code: " + exitCode);

        } catch (IOException | InterruptedException e) {
            System.err.println("[PythonCaller] Error while executing python: " + e.getMessage());
        }
    }

}