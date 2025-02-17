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
import org.influxdb.dto.QueryResult;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.SQLOutput;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static es.bsc.hp2c.HP2CServerContext.isVerbose;
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
    public void write(Float[] values, Instant timestamp, String edgeLabel, String deviceName) {
        long epochNanos = timestamp.getEpochSecond() * 1_000_000_000L + timestamp.getNano();
        for (int i = 0; i < values.length; i++) {
            String tagName = deviceName + "Sensor" + i;
            if (isVerbose()) {
                System.out.println("[DatabaseHandler] Writing DB with '" + edgeLabel + "." +
                        deviceName + "':'" + values[i] + "'");
            }
            influxDB.write(Point.measurement(edgeLabel)
                    .time(epochNanos, TimeUnit.NANOSECONDS)
                    .tag("device", tagName)
                    .addField("value", values[i])
                    .build());
        }
    }

    /**
     * Write a new alarm entry to Influx database.
     * Use the alarmName as the `measurement`, the edgeLabel and deviceLabel as `tag`, and the alarmState as `field`
     *
     * @param timestamp    Message timestamp in long integer format.
     * @param alarmName   Name of the alarm
     * @param edgeLabel    Name of the Influx measurement (series) where we
     *                      will write. Typically, the name of the edge node.
     * @param deviceName   of the Influx tag where we will write. Typically,
     *                     the name of the device.
     * @param alarmStatus  Status of the alarm to write
     * @param info         Info message of the alarm
     */
    public void writeAlarmDB(Instant timestamp, String alarmName, String edgeLabel, String deviceName,
                             boolean alarmStatus, String info) {
        long epochNanos = timestamp.getEpochSecond() * 1_000_000_000L + timestamp.getNano();

        // Replace null values with default strings for tags
        String safeEdgeLabel = edgeLabel != null ? edgeLabel : "global";
        String safeDeviceName = deviceName != null ? deviceName : "global";
        String safeInfo = info != null ? info : " ";
        int safeStatus = alarmStatus ? 1 : 0;

        Point.Builder pointBuilder = Point.measurement("alarms")
                .time(epochNanos, TimeUnit.NANOSECONDS)
                .tag("alarm", alarmName)
                .tag("device", safeDeviceName)
                .tag("edge", safeEdgeLabel)
                .addField("status", safeStatus)
                .addField("info", safeInfo);

        if (isVerbose()) {
            System.out.println("[DatabaseHandler] Writing alarm DB with alarm label: " + alarmName +
                    ", edge label: " + safeEdgeLabel + ", device label: " + safeDeviceName + ", status: " + alarmStatus);
        }

        influxDB.write(pointBuilder.build());
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
            configPath = "config.json";
        }
        // Parse config file
        return getAuth(configPath);
    }

    /*
    * Function to get unique combinations of alarmName, deviceName, and edgeName.
    * A measurement is considered an alarm if it has both 'edge' and 'device' tags.
    *
    * @return Set of unique triples
    * */
    public Set<ArrayList<String>> getUniqueAlarmTriples() {
        String databaseName = "hp2cdt";
        Set<ArrayList<String>> uniqueTriples = new HashSet<>();

        // Query unique tag values
        String tagQuery = "SHOW TAG VALUES FROM alarms WITH KEY IN (\"alarm\", \"edge\", \"device\")";
        QueryResult queryResult = influxDB.query(new Query(tagQuery, databaseName));

        if (queryResult.hasError() || queryResult.getResults() == null) {
            System.err.println("Error retrieving tag values: " + queryResult.getError());
            return uniqueTriples;
        }

        // Store results separately
        List<String> alarms = new ArrayList<>();
        List<String> edges = new ArrayList<>();
        List<String> devices = new ArrayList<>();

        for (QueryResult.Result result : queryResult.getResults()) {
            if (result.getSeries() == null) continue;

            for (QueryResult.Series series : result.getSeries()) {
                String tagKey = series.getName(); // e.g., "alarm", "edge", or "device"

                for (List<Object> value : series.getValues()) {
                    String tagValue = value.get(1).toString(); // Extract actual tag value

                    if ("alarm".equals(value.get(0).toString())) alarms.add(tagValue);
                    if ("edge".equals(value.get(0).toString())) edges.add(tagValue);
                    if ("device".equals(value.get(0).toString())) devices.add(tagValue);
                }
            }
        }

        // Create unique triples
        for (String alarm : alarms) {
            for (String edge : edges) {
                for (String device : devices) {
                    ArrayList<String> triple = new ArrayList<>();
                    triple.add(alarm);
                    triple.add(edge);
                    triple.add(device);
                    uniqueTriples.add(triple);
                }
            }
        }
        Set<ArrayList<String>> finalTriples = new HashSet<>();
        for (ArrayList<String> triple: uniqueTriples){
            String queryStr = "SELECT status FROM alarms WHERE " +
                    "alarm='" + triple.get(0) + "' AND " +
                    "edge='" + triple.get(1) + "' AND " +
                    "device='" + triple.get(2) + "' LIMIT 1";

            QueryResult result = influxDB.query(new Query(queryStr, databaseName));
            if(result.getResults().stream()
                    .flatMap(r -> r.getSeries() != null ? r.getSeries().stream() : null)
                    .flatMap(series -> series.getValues() != null ? series.getValues().stream() : null)
                    .findAny()
                    .isPresent()){
                finalTriples.add(triple);
            }
        }
        return finalTriples;
    }
}
