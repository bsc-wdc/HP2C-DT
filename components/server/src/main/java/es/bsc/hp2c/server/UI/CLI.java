/*
 *  Copyright 2002-2023 Barcelona Supercomputing Center (www.bsc.es)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package es.bsc.hp2c.server.UI;

import es.bsc.hp2c.HP2CServer.ActuatorValidity;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.server.device.VirtualComm.VirtualActuator;

import java.io.IOException;
import java.util.Map;
import java.util.Scanner;

import static es.bsc.hp2c.HP2CServer.checkActuator;
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
        String actuatorLabel = tokens[2];

        // Use the remaining elements for values
        String[] stringValues = new String[tokens.length - 3];
        System.arraycopy(tokens, 3, stringValues, 0, tokens.length - 3);

        // Check actuator validity
        ActuatorValidity checker = checkActuator(edgeLabel, actuatorLabel);
        if (!checker.isValid()) {
            throw new IOException(checker.getMessage());
        }

        // Actuate
        Device device = deviceMap.get(edgeLabel).get(actuatorLabel);
        VirtualActuator<?> actuator = (VirtualActuator<?>) device;
        actuator.actuate(stringValues);
    }

}

