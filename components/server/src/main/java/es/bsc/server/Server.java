package es.bsc.server;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import org.influxdb.BatchOptions;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Server implements AutoCloseable{
    private final Connection connection;
    private Channel channel;
    private InfluxDB influxDB;
    private final String EXCHANGE_NAME = "measurements";
    private final String serverURL = "http://127.0.0.1:8086";
    private final String username = "root", password = "root";

    public Server() throws IOException, TimeoutException {
        // Init RabbitMQ
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        connection = factory.newConnection();
        channel = connection.createChannel();
        // Init InfluxDB
        initDB();
    }

    public static void main(String[] args) {
        try {
            Server server = new Server();
            server.startListener();
        } catch (IOException | TimeoutException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void startListener() throws IOException {
        channel.exchangeDeclare(EXCHANGE_NAME, "topic");
        String routingKey = "edge.#";
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, EXCHANGE_NAME, routingKey);

        System.out.println(" [x] Awaiting requests");

        DeliverCallback callback = (consumerTag, delivery) -> {
            String message = new String (delivery.getBody(), "UTF-8");
            String senderRoutingKey = delivery.getEnvelope().getRoutingKey();
            long timestampMillis = delivery.getProperties().getTimestamp().getTime();
            System.out.println(" [x] Received '" + senderRoutingKey + "':'" + message + "'");
            writeDB(message, timestampMillis, senderRoutingKey);
        };
        channel.basicConsume(queueName, true, callback, consumerTag -> { });
    }

    private void writeDB(String message, long timestamp, String routingKey) {
        // Get measurement and tag of the edge measurement. Use "\\" to scape special regex character
        //   Example: routingKey = "edge.edge8080.voltmeter1"
        String[] routingKeyParts = routingKey.split("\\.");
        String measurementName = routingKeyParts[1];
        String tagName = routingKeyParts[2];

        // Write points to InfluxDB.
        influxDB.write(Point.measurement(measurementName)
            .time(timestamp, TimeUnit.MILLISECONDS)
            .tag("device", tagName)
            .addField("value", Float.valueOf(message))
            .build());

        // Wait a few seconds in order to let the InfluxDB client
        // write your points asynchronously (note: you can adjust the
        // internal time interval if you need via 'enableBatch' call).
        try {
            Thread.sleep(5L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Query your data using InfluxQL.
        // https://docs.influxdata.com/influxdb/v1.7/query_language/data_exploration/#the-basic-select-statement
        QueryResult queryResult = influxDB.query(new Query("SELECT * FROM \"" + measurementName + "\""));
        System.out.println(queryResult);
    }

    private void initDB() {
        // Create an object to handle the communication with InfluxDB.
        // (best practice tip: reuse the 'influxDB' instance when possible)
        influxDB = InfluxDBFactory.connect(serverURL, username, password);

        // Create a database...
        // https://docs.influxdata.com/influxdb/v1.7/query_language/database_management/
        String databaseName = "hp2cdt";
        influxDB.query(new Query("CREATE DATABASE " + databaseName));
        influxDB.setDatabase(databaseName);

        // ... and a retention policy, if necessary.
        // https://docs.influxdata.com/influxdb/v1.7/query_language/database_management/
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

        // Close it if your application is terminating or you are not using it anymore.
        Runtime.getRuntime().addShutdownHook(new Thread(influxDB::close));
    }

    @Override
    public void close() throws IOException {
        connection.close();
    }
}
