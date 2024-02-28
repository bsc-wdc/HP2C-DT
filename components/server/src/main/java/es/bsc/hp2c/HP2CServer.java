package es.bsc.hp2c;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Sensor;
import es.bsc.hp2c.server.UI.CLI;
import es.bsc.hp2c.server.UI.RestListener;
import org.influxdb.BatchOptions;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static es.bsc.hp2c.common.utils.FileUtils.loadDevices;
import static es.bsc.hp2c.common.utils.FileUtils.readEdgeLabel;

/**
 * Implementation of the server logic interacting with an InfluxDB database and
 * with edge devices via AMQP.
 */
public class HP2CServer {
    private static Connection connection;
    private static Channel channel;
    private InfluxDB influxDB;
    private final String EXCHANGE_NAME = "measurements";
    private final long dbPort = 8086;
    private static Map<String, Map<String, Device>> deviceMap = new HashMap<>();
    private static boolean verbose = true;

    /**
     * Constructor of Server instance.
     * Initializes AMQP, InfluxDB, and CLI connections.
     * @param hostIp IP of AMQP broker and database. TODO: use custom IPs for each
     */
    public HP2CServer(String hostIp) throws IOException, TimeoutException {
        initAmqp(hostIp);
        initDB(hostIp, dbPort);
        RestListener restListener = new RestListener(deviceMap);
        restListener.start();
        CLI cli = new CLI(deviceMap);
        cli.start();
    }

    /** Parse setup files for all edge nodes and deploy Server. */
    public static void main(String[] args) throws FileNotFoundException {
        // Load setup files
        String hostIp = parseSetupFiles(args);
        // Deploy listener
        try {
            HP2CServer server = new HP2CServer(hostIp);
            server.startListener();
        } catch (IOException | TimeoutException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }

    /** Parse edge nodes files and configure the edge-device map. */
    private static String parseSetupFiles(String[] args) throws FileNotFoundException {
        // Get IP and setup directory
        String hostIp;
        File setupDir;
        if (args.length == 1) {
            setupDir = new File(args[0]);
            hostIp = System.getenv("LOCAL_IP");
        } else {
            setupDir = new File("../../deployments/testbed/setup");
            hostIp = "0.0.0.0";
        }
        File[] setupFiles = setupDir.listFiles();
        if (setupFiles == null) {
            throw new FileNotFoundException("No setup files found in " + setupDir);
        }
        // Fill in edge-devices map
        for (File setupFile: setupFiles) {
            String filePath = setupFile.toString();
            System.out.println("Loading setup configuration for file " + filePath);
            String edgeLabel = readEdgeLabel(filePath);
            deviceMap.put(edgeLabel, loadDevices(filePath, "driver-dt"));
        }
        return hostIp;
    }

    /** Initialize AMQP Channel. */
    private void initAmqp(String hostIp) throws IOException, TimeoutException {
        System.out.println("Connecting to AMQP broker at host IP " + hostIp + "...");
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(hostIp);
        connection = factory.newConnection();
        channel = connection.createChannel();
        System.out.println("AMQP Connection successful");
    }

    /** Initializes the "hp2cdt" InfluxDB database instance. */
    private void initDB(String ip, long port) throws IOException {
        // Create an object to handle the communication with InfluxDB.
        System.out.println("Connecting to InfluxDB at host IP " + ip + ", port " + port + "...");
        String serverURL = "http://" + ip + ":" + port;
        String[] auth = getAuth();
        String username = auth[0];
        String password = auth[1];
        influxDB = InfluxDBFactory.connect(serverURL, username, password);
        System.out.println("InfluxDB Connection successful");

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
     * Start RabbitMQ consumer.
     * The method will run indefinitely thanks to a DeliverCallback that calls
     * writeDB to process each message received.
     * Currently, receiving messages published to any "edge.#" topic.
     */
    private void startListener() throws IOException {
        channel.exchangeDeclare(EXCHANGE_NAME, "topic");
        String routingKey = "edge.*.sensors.*";
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, EXCHANGE_NAME, routingKey);

        System.out.println(" [x] Awaiting requests");

        DeliverCallback callback = (consumerTag, delivery) -> {
            // Parse message. For instance: routingKey = "edge.edge1.voltmeter1"
            byte[] message = delivery.getBody();
            String senderRoutingKey = delivery.getEnvelope().getRoutingKey();
            long timestampMillis = delivery.getProperties().getTimestamp().getTime();
            String edgeLabel = getEdgeLabel(senderRoutingKey);
            String deviceName = getDeviceName(senderRoutingKey);
            // Check existence of pair edge-device
            if (!isInMap(edgeLabel, deviceName, deviceMap)) {
                System.err.println("Edge " + edgeLabel + ", Device " + deviceName
                        + ": message received but device not listed as " + edgeLabel + " digital twin devices.");
                return;
            }
            // Sense to the corresponding sensor
            Sensor<?, ?> sensor = (Sensor<?, ?>) deviceMap.get(edgeLabel).get(deviceName);
            sensor.sensed(message);
            // Write entry in database
            writeDB((Float[]) sensor.decodeValuesSensor(message), timestampMillis, edgeLabel, deviceName);
        };
        channel.basicConsume(queueName, true, callback, consumerTag -> { });
    }

    /**
     * Write to Influx database.
     * Use the second field of the routing key (EDGE_ID) as the `measurement`
     * (time series) and the third field (DEVICE_ID) as the `tag` value.
     * @param values Message of a measurements as an integer format.
     * @param timestamp Message timestamp in long integer format.
     * @param edgeLabel Name of the Influx measurement (series) where we
     *                 will write. Typically, the name of the edge node.
     * @param deviceName Name of the Influx tag where we will write. Typically,
     *                   the name of the device.
     */
    private void writeDB(Float[] values, long timestamp, String edgeLabel, String deviceName) {
        for (int i = 0; i < values.length; i++) {
            String tagName = deviceName + "Sensor" + i;
            if (verbose) {
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

    /** Check actuator validity and return a custom error message upon error.*/
    public static ActuatorValidity checkActuator(String edgeLabel, String actuatorName) {
        // Check if the provided actuator name exists in the map of edge nodes
        StringBuilder msg = new StringBuilder();
        if (!isInMap(edgeLabel, actuatorName, deviceMap)) {
            msg.append("Edge " + edgeLabel + ", Device " + actuatorName + " not listed.\n");
            msg.append("Options are:\n");
            for (HashMap.Entry<String, Map<String, Device>> entry : deviceMap.entrySet()) {
                String groupKey = entry.getKey();
                Map<String, Device> innerMap = entry.getValue();
                msg.append("Group: " + groupKey + "\n");
                for (HashMap.Entry<String, Device> innerEntry : innerMap.entrySet()) {
                    String deviceKey = innerEntry.getKey();
                    if (innerMap.get(deviceKey).isActionable()) {
                        msg.append("  Actuator: " + deviceKey + "\n");
                    }
                }
            }
            return new ActuatorValidity(false, msg.toString());
        }
        // Check if the provided device is an actuator
        Device device = deviceMap.get(edgeLabel).get(actuatorName);
        if (!device.isActionable()) {
            msg.append("Device " + edgeLabel + "." + actuatorName + " is not an actuator.\n");
            return new ActuatorValidity(false, msg.toString());
        }
        return new ActuatorValidity(true, msg.toString());
    }

    /** Auxiliary class to use with checkActuator. */
    public static class ActuatorValidity {
        boolean isValid;
        String msg;
        ActuatorValidity(boolean isValid, String msg) {
            this.isValid = isValid;
            this.msg = msg;
        }
        public boolean isValid() {
            return isValid;
        }
        public String getMessage() {
            return msg;
        }
    }

    /**
     * Check if the combination "edgeLabel" and "deviceName" is in the given nested HashMap
     */
    public static boolean isInMap(String edgeLabel, String deviceName, Map<String, Map<String, Device>> map) {
        if (map.containsKey(edgeLabel)){
            if (!map.get(edgeLabel).containsKey(deviceName)) {;
                return false;
            }
        } else {
            return false;
        }
        return true;
    }

    /*
     * Parse device name from the routing key in deliverer's message.
     */
    public String getEdgeLabel(String routingKey){
        String[] routingKeyParts = routingKey.split("\\.");
        return routingKeyParts[1];
    }
    /*
     * Parse device name from the routing key in deliverer's message.
     */
    public String getDeviceName(String routingKey){
        String[] routingKeyParts = routingKey.split("\\.");
        return routingKeyParts[3];
    }

    public static Channel getChannel() {
        return channel;
    }

    public static void setVerbose(boolean verbose) {
        HP2CServer.verbose = verbose;
    }
}
