package es.bsc.hp2c.server.UI;

import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.server.device.VirtualComm.VirtualActuator;

import java.util.Map;
import java.util.Scanner;

import static es.bsc.hp2c.HP2CServer.isInMap;
import static es.bsc.hp2c.HP2CServer.setVerbose;
import static es.bsc.hp2c.server.device.VirtualComm.virtualActuate;

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
            } catch (IllegalArgumentException e) {
                System.err.println("Error: " + e.getMessage());
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
    private void processInput(String input) {
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
    private void actuateAction(String[] tokens) {
        // Parse input prompt
        System.out.println("Launching actuation...");
        if (tokens.length < 4) {
            throw new IllegalArgumentException(
                    "'actuate' needs at least 3 input arguments: edge, actuator and value");
        }
        String edgeLabel = tokens[1];
        String actuatorName = tokens[2];
        // Use the remaining elements for values
        String[] rawValues = new String[tokens.length - 3];
        System.arraycopy(tokens, 3, rawValues, 0, tokens.length - 3);

        // Check input validity
        if (!isInMap(edgeLabel, actuatorName, deviceMap)) {
            System.err.println("Edge " + edgeLabel + ", Device " + actuatorName + " not listed.");
            System.out.println("Options are:");
            for (Map.Entry<String, Map<String, Device>> entry : deviceMap.entrySet()) {
                String groupKey = entry.getKey();
                Map<String, Device> innerMap = entry.getValue();
                System.out.println("Group: " + groupKey);
                for (Map.Entry<String, Device> innerEntry : innerMap.entrySet()) {
                    String deviceKey = innerEntry.getKey();
                    System.out.println("  Device: " + deviceKey);
                }
            }
            return;
        }

        // Actuate
        VirtualActuator<?> actuator = (VirtualActuator<?>) deviceMap.get(edgeLabel).get(actuatorName);
        virtualActuate(actuator, edgeLabel, rawValues);
    }
}

