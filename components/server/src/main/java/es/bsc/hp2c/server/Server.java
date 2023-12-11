package es.bsc.hp2c.server;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import es.bsc.hp2c.edge.types.Device;
import es.bsc.hp2c.edge.types.Sensor;
import org.influxdb.BatchOptions;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static es.bsc.hp2c.HP2CEdge.loadDevices;

/**
 * Implementation of the server logic interacting with an InfluxDB database and
 * with edge devices via AMQP.
 */
public class Server implements AutoCloseable {
    private final Connection connection;
    private Channel channel;
    private InfluxDB influxDB;
    private final String EXCHANGE_NAME = "measurements";
    private final String serverURL = "http://127.0.0.1:8086";
    private final String username = "root", password = "root";
    private static Map<String, Device> devices = new HashMap<>();

    public Server() throws IOException, TimeoutException {
        // Init RabbitMQ
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        connection = factory.newConnection();
        channel = connection.createChannel();
        // Init InfluxDB
        initDB();
    }

    public static void main(String[] args) throws FileNotFoundException {
        // Load setup files
        File setupDir;
        if (args.length == 1) {
            setupDir = new File(args[0]);
        } else {
            setupDir = new File("/home/eiraola/projects/hp2cdt/deployments/testbed");
        }
        setupDir = new File(setupDir, "setup");
        File[] setupFiles = setupDir.listFiles();
        ArrayList<Map<String, Device>> deviceMapList = new ArrayList<>();
        for (File setupFile: setupFiles) {
            System.out.println(setupFile.toString());
            deviceMapList.add(loadDevices(setupFile.toString(), "driver-dt"));
        }

        // Merge device maps from each edge
        for (Map<String, Device> deviceMap : deviceMapList) {
            for (String deviceLabel : deviceMap.keySet()) {
                Device deviceObject = deviceMap.get(deviceLabel);
                if (devices.containsKey(deviceLabel)) {
                    System.err.println("Warning: Device '" + deviceLabel + "' is being overridden.");
                }
                devices.put(deviceLabel, deviceObject);
            }
        }

        // Deploy listener
        try {
            Server server = new Server();
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
            // Sense to the corresponding sensor
            Sensor<?, ?> sensor = (Sensor<?, ?>) devices.get(deviceName);
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
            System.out.println(" [x] Received '" + edgeName + "." + deviceName + "':'" + Arrays.toString(values) + "'");
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
    private void initDB() {
        // Create an object to handle the communication with InfluxDB.
        influxDB = InfluxDBFactory.connect(serverURL, username, password);

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

    /*
     * Parse device name from the routing key in deliverer's message.
     */
    public String getDeviceName(String routingKey){
        String[] routingKeyParts = routingKey.split("\\.");
        return routingKeyParts[2];
    }

    /*
     * Parse device name from the routing key in deliverer's message.
     */
    public String getEdgeName(String routingKey){
        String[] routingKeyParts = routingKey.split("\\.");
        return routingKeyParts[1];
    }

    @Override
    public void close() throws IOException {
        connection.close();
    }
}
