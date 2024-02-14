package es.bsc.hp2c.server.UI;

import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.server.device.VirtualComm.VirtualActuator;

import java.util.Map;
import java.util.Scanner;

import static es.bsc.hp2c.HP2CServer.isInMap;
import static es.bsc.hp2c.server.device.VirtualComm.virtualActuate;

public class SimpleUI implements Runnable {
    private boolean isRunning = false;
    private final Map<String, Map<String, Device>> deviceMap;

    public SimpleUI(Map<String, Map<String, Device>> deviceMap) {
        this.deviceMap = deviceMap;
    }

    public void start() {
        if (!isRunning) {
            System.out.println("SimpleUI started.");
            isRunning = true;
            // Create and start a new thread for user input
            Thread UIThread = new Thread(this);
            UIThread.start();
        } else {
            System.err.println("SimpleUI is already running.");
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
            System.out.println("SimpleUI stopping...");
        } else {
            System.out.println("SimpleUI is not running.");
        }
    }

    /** Process user's CLI whitespace-separated input. First word is the command name. */
    private void processInput(String input) {
        String[] tokens = input.split("\\s+");
        String action = tokens[0];
        switch (action) {
            case "actuate":
                // For instance: "actuate edge1 ThreePhaseSwitchGen1 ON ON ON"
                System.out.println("Launching actuation...");
                if (tokens.length < 4) {
                    throw new IllegalArgumentException(
                            "'actuate' needs at least 3 input arguments: edge, actuator and value");
                }
                // Extract values from the array
                String edgeName = tokens[1];
                String actuatorName = tokens[2];
                // Use the remaining elements for values
                String[] values = new String[tokens.length - 3];
                System.arraycopy(tokens, 3, values, 0, tokens.length - 3);
                actuateAction(edgeName, actuatorName, values);
                break;
            case "stop":
                stop();
                break;
            default:
                throw new IllegalArgumentException("Unrecognized command: " + input);
            }
        }

    private void actuateAction(String edgeName, String actuatorName, String[] rawValues) {
        // Check input validity
        if (!isInMap(edgeName, actuatorName, deviceMap)) {
            System.err.println("Edge " + edgeName + ", Device " + actuatorName + " not listed.");
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
        VirtualActuator<?> actuator = (VirtualActuator<?>) deviceMap.get(edgeName).get(actuatorName);
        virtualActuate(actuator, edgeName, rawValues);
    }
}

