package es.bsc.hp2c.server.UI;

import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.server.device.VirtualComm.VirtualActuator;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import static es.bsc.hp2c.HP2CServer.isInMap;
import static es.bsc.hp2c.HP2CServer.setVerbose;

public class CLI implements Runnable {
    private boolean isRunning = false;
    private final Map<String, Map<String, Device>> deviceMap;

    public CLI(Map<String, Map<String, Device>> deviceMap) {
        this.deviceMap = deviceMap;
    }

    public void start() {
        if (!isRunning) {
            System.out.println("CLI started.");
            isRunning = true;
            // Create and start a new thread for user input
            Thread UIThread = new Thread(this);
            UIThread.setName("CLI-thread");
            UIThread.start();
        } else {
            System.err.println("CLI is already running.");
        }
    }

    @Override
    public void run() {
        // Create a Scanner object to read input from the user
        Scanner scanner = new Scanner(System.in);
        while (isRunning) {
            // Prompt for user input
            System.out.println("Enter a command: ");
            String userInput = scanner.nextLine();
            // Process the user input
            try {
                processInput(userInput);
            } catch (IllegalArgumentException | IOException e) {
                System.err.println("CLI error: " + e.getMessage());
            }
        }
        // Close the Scanner
        scanner.close();
    }

    public void stop() {
        if (isRunning) {
            isRunning = false;
            System.out.println("CLI stopping...");
        } else {
            System.out.println("CLI is not running.");
        }
    }

    /** Process user's CLI whitespace-separated input. First word is the command name. */
    private void processInput(String input) throws IOException {
        String[] tokens = input.split("\\s+");
        String action = tokens[0];
        switch (action) {
            case "actuate":
                actuateAction(tokens);
                break;
            case "silence":
                setVerbose(false);
                break;
            case "verbose":
                setVerbose(true);
                break;
            case "stop":
                stop();
                break;
            default:
                throw new IllegalArgumentException("Unrecognized command: " + input);
            }
        }

    /**
     * Parse user input and call virtualActuate.
     * For instance: "actuate edge1 ThreePhaseSwitchGen1 ON OFF ON"
     *               "actuate edge1 GeneratorGen1 0.5 0.75"
     */
    private void actuateAction(String[] tokens) throws IOException {
        // Parse input prompt
        System.out.println("Launching actuation...");
        if (tokens.length < 4) {
            throw new IllegalArgumentException(
                    "'actuate' needs at least 3 input arguments: edge, actuator and value");
        }
        String edgeLabel = tokens[1];
        String actuatorName = tokens[2];
        // Use the remaining elements for values
        String[] stringValues = new String[tokens.length - 3];
        System.arraycopy(tokens, 3, stringValues, 0, tokens.length - 3);

        // Check if the provided device name exists
        if (!isInMap(edgeLabel, actuatorName, deviceMap)) {
            System.err.println("Options are:");
            for (HashMap.Entry<String, Map<String, Device>> entry : deviceMap.entrySet()) {
                String groupKey = entry.getKey();
                Map<String, Device> innerMap = entry.getValue();
                System.err.println("Group: " + groupKey);
                for (HashMap.Entry<String, Device> innerEntry : innerMap.entrySet()) {
                    String deviceKey = innerEntry.getKey();
                    if (innerMap.get(deviceKey).isActionable()) {
                        System.err.println("  Actuator: " + deviceKey);
                    }
                }
            }
            throw new IOException("Edge " + edgeLabel + ", Device " + actuatorName + " not listed.");
        }
        // Check if the provided device is an actuator
        Device device = deviceMap.get(edgeLabel).get(actuatorName);
        if (!device.isActionable()) {
            throw new IOException("Device " + actuatorName + " is not an actuator.");
        }

        // Actuate
        VirtualActuator<?> actuator = (VirtualActuator<?>) device;
        actuator.actuate(stringValues);
    }
}

