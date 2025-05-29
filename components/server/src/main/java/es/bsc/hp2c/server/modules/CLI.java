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
package es.bsc.hp2c.server.modules;

import es.bsc.hp2c.HP2CServerContext.ActuatorValidity;
import es.bsc.hp2c.server.device.VirtualComm;
import es.bsc.hp2c.server.device.VirtualComm.VirtualActuator;
import es.bsc.hp2c.server.edge.VirtualEdge;

import java.io.IOException;
import java.util.Map;
import java.util.Scanner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static es.bsc.hp2c.HP2CServerContext.checkActuator;
import static es.bsc.hp2c.HP2CServerContext.setVerbose;

public class CLI implements Runnable {
    private static final Logger logger = LogManager.getLogger("appLogger");
    private boolean isRunning = false;
    private final Map<String, VirtualEdge> edgeMap;

    public CLI(Map<String, VirtualEdge> edgeMap) {
        this.edgeMap = edgeMap;
    }

    public void start() {
        if (!isRunning) {
            logger.info("CLI started.");
            isRunning = true;
            // Create and start a new thread for user input
            Thread UIThread = new Thread(this);
            UIThread.setName("CLI-thread");
            UIThread.start();
        } else {
            logger.error("CLI is already running.");
        }
    }

    @Override
    public void run() {
        // Create a Scanner object to read input from the user
        Scanner scanner = new Scanner(System.in);
        while (isRunning) {
            // Prompt for user input
            logger.info("Enter a command: ");
            String userInput = scanner.nextLine();
            // Process the user input
            try {
                processInput(userInput);
            } catch (IllegalArgumentException | IOException e) {
                logger.error("CLI error: " + e.getMessage());
            }
        }
        // Close the Scanner
        scanner.close();
    }

    public void stop() {
        if (isRunning) {
            isRunning = false;
            logger.info("CLI stopping...");
        } else {
            logger.info("CLI is not running.");
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
        logger.info("Launching actuation...");
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
        VirtualComm.VirtualDevice device = edgeMap.get(edgeLabel).getDevice(actuatorLabel);
        VirtualActuator<?> actuator = (VirtualActuator<?>) device;
        actuator.actuate(stringValues);
    }

}

