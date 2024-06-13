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

import es.bsc.hp2c.common.utils.CommUtils;
import org.influxdb.BatchOptions;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static es.bsc.hp2c.HP2CServer.isVerbose;
import static es.bsc.hp2c.common.utils.FileUtils.getJsonObject;

public class DatabaseHandler {
    private final InfluxDB influxDB;

    /**
     * Initializes the "hp2cdt" InfluxDB database instance.
     * localIp is provided in case the proper IP address is not set up in deployment_setup.json
     *
     * @param localIp local IP (server)
     */
    public DatabaseHandler(String localIp) throws IOException {
        // Select database IP
        HashMap<String, Object> connectionMap = CommUtils.parseRemoteIp("database", localIp);
        String ip = (String) connectionMap.get("ip");
        int port = (int) connectionMap.get("port");
        // Create object to handle the communication with InfluxDB.
        String databaseURL = "http://" + ip + ":" + port;
        System.out.println("Connecting to InfluxDB at host " + databaseURL + "...");
        String[] auth = getAuth();
        String username = auth[0];
        String password = auth[1];
        influxDB = InfluxDBFactory.connect(databaseURL, username, password);
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
                System.out.println(" [DatabaseHandler] Writing DB with '" + edgeLabel + "." +
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
        JSONObject jsonObject = getJsonObject(configFile);
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
