package es.bsc.hp2c.server.UI;

import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.generic.Switch.State;

import java.io.IOException;
import java.util.Map;
import java.util.Scanner;

import static es.bsc.hp2c.HP2CServer.isInMap;

public class SimpleUI implements Runnable {
    private boolean isRunning = false;
    private Map<String, Map<String, Device>> deviceMap;

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
            System.out.print("Enter a command: ");
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

    private void processInput(String input) {
        String[] tokens = input.split("\\s+");
        String action = tokens[0];
        switch (action) {
            case "actuate":
                System.out.println("Performing Action 1...");
                if (tokens.length < 4) {
                    throw new IllegalArgumentException("'actuate' needs 3 input arguments: edge, actuator and value");
                }
                // Extract values from the array
                String edgeName = tokens[1];
                String actuatorName = tokens[2];
                // Use the remaining as elements of the float array
                Float[] values = new Float[tokens.length - 3];
                for(int i = 3; i < tokens.length; i++){
                    values[i - 3] = Float.parseFloat(tokens[i]);
                }
                actuateAction(edgeName, actuatorName, values);
                break;
            case "action2":
                System.out.println("Performing Action 2...");
                // Perform Action 2 logic here
                break;
            case "stop":
                stop();
                break;
            default:
                throw new IllegalArgumentException("Unrecognized command: " + input);
            }
        }

    private void actuateAction(String edgeName, String actuatorName, Float[] rawValues) {
        // Check input
        if (!isInMap(edgeName, actuatorName, deviceMap)) {
            System.err.println("Edge " + edgeName + ", Device " + actuatorName + " not listed.");
            System.out.println("Options are:");
            for (Map.Entry<String, Map<String, Device>> entry : deviceMap.entrySet()) {
                String groupKey = entry.getKey();
                Map<String, Device> innerMap = entry.getValue();
                System.out.println("Group: " + groupKey);
                for (Map.Entry<String, Device> innerEntry : innerMap.entrySet()) {
                    String deviceKey = innerEntry.getKey();
                    Device device = innerEntry.getValue();
                    System.out.println("  Device: " + deviceKey);
                }
            }
            return;
        }
        // Parse into State[] (TODO: only works for Switch)
        State[] states = new State[rawValues.length];
        for (int i = 0; i < rawValues.length; i++) {
             states[i] = i > 0.5 ? State.ON : State.OFF;
        }
        // Actuate
        Actuator actuator = (Actuator) deviceMap.get(edgeName).get(actuatorName);
        try {
            actuator.actuate(states);
        } catch (IOException e) {
            System.err.println("Actuator " + actuatorName + " failed.");
            throw new RuntimeException(e);
        }
    }
}

