package es.bsc.hp2c;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Sensor;
import org.influxdb.BatchOptions;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
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
public class HP2CServer implements AutoCloseable {
    private final Connection connection;
    private Channel channel;
    private InfluxDB influxDB;
    private final String EXCHANGE_NAME = "measurements";
    private final long dbPort = 8086;
    private final String username = "root", password = "root";
    private static Map<String, Map<String, Device>> deviceMap = new HashMap<>();

    public HP2CServer(String hostIp) throws IOException, TimeoutException {
        // Init RabbitMQ
        System.out.println("Connecting to AMQP broker at host IP " + hostIp + "...");
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(hostIp);
        connection = factory.newConnection();
        channel = connection.createChannel();
        System.out.println("AMQP Connection successful");
        // Init InfluxDB
        initDB(hostIp, dbPort);
    }

    public static void main(String[] args) throws FileNotFoundException {
        // Load setup files
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
        assert setupFiles != null;

        // Fill in edge-devices map
        for (File setupFile: setupFiles) {
            String filePath = setupFile.toString();
            System.out.println("Loading setup configuration for file " + filePath);
            String edgeLabel = readEdgeLabel(filePath);
            deviceMap.put(edgeLabel, loadDevices(filePath, "driver-dt"));
        }

        // Deploy listener
        try {
            HP2CServer server = new HP2CServer(hostIp);
            server.startListener();
        } catch (IOException | TimeoutException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    /**
     * Start RabbitMQ consumer.
     * The method will run indefinitely thanks to a DeliverCallback that calls
     * writeDB to process each message received.
     * Currently, receiving messages published to any "edge.#" topic.
     */
    private void startListener() throws IOException {
        channel.exchangeDeclare(EXCHANGE_NAME, "topic");
        String routingKey = "edge.#";
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, EXCHANGE_NAME, routingKey);

        System.out.println(" [x] Awaiting requests");

        DeliverCallback callback = (consumerTag, delivery) -> {
            // Parse message. For instance: routingKey = "edge.edge1.voltmeter1"
            byte[] message = delivery.getBody();
            String senderRoutingKey = delivery.getEnvelope().getRoutingKey();
            long timestampMillis = delivery.getProperties().getTimestamp().getTime();
            String edgeName = getEdgeName(senderRoutingKey);
            String deviceName = getDeviceName(senderRoutingKey);
            // Check existence of pair edge-device
            if (!isInMap(edgeName, deviceName, deviceMap)) {
                System.err.println("Edge " + edgeName + ", Device " + deviceName
                        + ": message received but device not listed as " + edgeName + " digital twin devices.");
                return;
            }
            // Sense to the corresponding sensor
            Sensor<?, ?> sensor = (Sensor<?, ?>) deviceMap.get(edgeName).get(deviceName);
            sensor.sensed(message);
            // Write entry in database
            writeDB((Float[]) sensor.decodeValues(message), timestampMillis, edgeName, deviceName);
        };
        channel.basicConsume(queueName, true, callback, consumerTag -> { });
    }

    /**
     * Write to Influx database.
     * Use the second field of the routing key (EDGE_ID) as the `measurement`
     * (time series) and the third field (DEVICE_ID) as the `tag` value.
     * @param values Message of a measurements as an integer format.
     * @param timestamp Message timestamp in long integer format.
     * @param edgeName Name of the Influx measurement (series) where we
     *                 will write. Typically, the name of the edge node.
     * @param deviceName Name of the Influx tag where we will write. Typically,
     *                   the name of the device.
     */
    private void writeDB(Float[] values, long timestamp, String edgeName, String deviceName) {
        for (int i = 0; i < values.length; i++) {
            String tagName = deviceName + "Sensor" + i;
            System.out.println(" [ ] Writing DB with '" + edgeName + "." +
                    deviceName + "':'" + values[i] + "'");
            influxDB.write(Point.measurement(edgeName)
                    .time(timestamp, TimeUnit.MILLISECONDS)
                    .tag("device", tagName)
                    .addField("value", values[i])
                    .build());
        }
    }

    /**
     * Initializes the "hp2cdt" InfluxDB database instance.
     */
    private void initDB(String ip, long port) {
        // Create an object to handle the communication with InfluxDB.
        System.out.println("Connecting to InfluxDB at host IP " + ip + ", port " + port + "...");
        String serverURL = "http://" + ip + ":" + port;
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
     * Check if the combination "edgeName" and "deviceName" is in the given nested HashMap
     */
    public static boolean isInMap(String edgeName, String deviceName, Map<String, Map<String, Device>> map) {
        if (map.containsKey(edgeName)){
            if (!map.get(edgeName).containsKey(deviceName)) {;
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
    public String getEdgeName(String routingKey){
        String[] routingKeyParts = routingKey.split("\\.");
        return routingKeyParts[1];
    }
    /*
     * Parse device name from the routing key in deliverer's message.
     */
    public String getDeviceName(String routingKey){
        String[] routingKeyParts = routingKey.split("\\.");
        return routingKeyParts[2];
    }

    @Override
    public void close() throws IOException {
        connection.close();
    }
}
