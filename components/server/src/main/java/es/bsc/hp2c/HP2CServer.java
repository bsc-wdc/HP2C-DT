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
package es.bsc.hp2c;


import java.io.IOException;
import java.util.concurrent.TimeoutException;
import static es.bsc.hp2c.HP2CServerContext.*;

/**
 * Implementation of the server logic interacting with an InfluxDB database and
 * with edge devices via AmqpManager.
 */
public class HP2CServer {

    /** Start and run Server modules. */
    public static void main(String[] args) {
        parseArgs(args);
        // Load setup files
        String hostIp = getHostIp();
        // Deploy listener
        try {
            init(hostIp);
            start();
        } catch (IOException | TimeoutException | InterruptedException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
}
