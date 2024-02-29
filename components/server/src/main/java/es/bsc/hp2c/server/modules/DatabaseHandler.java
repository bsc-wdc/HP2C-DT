/**
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

import org.influxdb.BatchOptions;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static es.bsc.hp2c.HP2CServer.isVerbose;

public class DatabaseHandler {
    private final InfluxDB influxDB;

    /**
     * Initializes the "hp2cdt" InfluxDB database instance.
     *
     * @param ip IP address of the database deployment
     * @param port Database port number
     */
    public DatabaseHandler(String ip, long port) throws IOException {
        // Create an object to handle the communication with InfluxDB.
        System.out.println("Connecting to InfluxDB at host IP " + ip + ", port " + port + "...");
        String serverURL = "http://" + ip + ":" + port;
        String[] auth = getAuth();
        String username = auth[0];
        String password = auth[1];
        influxDB = InfluxDBFactory.connect(serverURL, username, password);
        System.out.println("InfluxDB Connection successful");
    }

    public void start() {
        // Create a database if it does not already exist
        String databaseName = "hp2cdt";
        influxDB.query(new Query("CREATE DATABASE " + databaseName));
        influxDB.setDatabase(databaseName);

        // Set up retention policy
        String retentionPolicyName = "one_day_only";
        influxDB.query(new Query("CREATE RETENTION POLICY " + retentionPolicyName
                + " ON " + databaseName + " DURATION 1d REPLICATION 1 DEFAULT"));
        influxDB.setRetentionPolicy(retentionPolicyName);

        // Enable batch writes to get better performance.
        influxDB.enableBatch(
                BatchOptions.DEFAULTS
                        .threadFactory(runnable -> {
                            Thread thread = new Thread(runnable);
                            thread.setDaemon(true);
                            return thread;
                        })
        );

        // Close when application terminates.
        Runtime.getRuntime().addShutdownHook(new Thread(influxDB::close));
    }

    /**
     * Write to Influx database.
     * Use the second field of the routing key (EDGE_ID) as the `measurement`
     * (time series) and the third field (DEVICE_ID) as the `tag` value.
     *
     * @param values     Message of a measurements as an integer format.
     * @param timestamp  Message timestamp in long integer format.
     * @param edgeLabel  Name of the Influx measurement (series) where we
     *                   will write. Typically, the name of the edge node.
     * @param deviceName Name of the Influx tag where we will write. Typically,
     *                   the name of the device.
     */
    public void write(Float[] values, long timestamp, String edgeLabel, String deviceName) {
        for (int i = 0; i < values.length; i++) {
            String tagName = deviceName + "Sensor" + i;
            if (isVerbose()) {
                System.out.println(" [ ] Writing DB with '" + edgeLabel + "." +
                        deviceName + "':'" + values[i] + "'");
            }
            influxDB.write(Point.measurement(edgeLabel)
                    .time(timestamp, TimeUnit.MILLISECONDS)
                    .tag("device", tagName)
                    .addField("value", values[i])
                    .build());
        }
    }

    private String[] getAuth(String configFile) throws IOException {
        // Parse JSON file
        InputStream is = Files.newInputStream(Paths.get(configFile));
        JSONTokener tokener = new JSONTokener(is);
        JSONObject jsonObject = new JSONObject(tokener);
        // Access the parsed values
        JSONObject databaseObject = jsonObject.getJSONObject("database");
        String username = databaseObject.getString("username");
        String password = databaseObject.getString("password");
        return new String[]{username, password};
    }

    private String[] getAuth() throws IOException {
        // Find config file
        String configPath = "/run/secrets/config.json";
        File configFile = new File(configPath);
        if (!configFile.isFile()) {
            configPath = "../../config.json";
        }
        // Parse config file
        return getAuth(configPath);
    }
}
