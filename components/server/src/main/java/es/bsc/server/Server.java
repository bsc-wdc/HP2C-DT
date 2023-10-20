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
    private final String EXCHANGE_NAME = "measurements";
    public Server() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        connection = factory.newConnection();
        channel = connection.createChannel();
    }

    public static void main(String[] args) {
        try {
            Server server = new Server();
            server.call();
        } catch (IOException | TimeoutException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void call() throws IOException {
        channel.exchangeDeclare(EXCHANGE_NAME, "topic");
        String routingKey = "edge.#";
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, EXCHANGE_NAME, routingKey);

        System.out.println(" [x] Awaiting requests");

        DeliverCallback callback = (consumerTag, delivery) -> {
            String message = new String (delivery.getBody(), "UTF-8");
            System.out.println(" [x] Received '" + delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
            storeDB(message);
        };
        channel.basicConsume(queueName, true, callback, consumerTag -> { });
    }

    private void storeDB(String message) {
        // Create an object to handle the communication with InfluxDB.
        // (best practice tip: reuse the 'influxDB' instance when possible)
        final String serverURL = "http://127.0.0.1:8086", username = "root", password = "root";
        final InfluxDB influxDB = InfluxDBFactory.connect(serverURL, username, password);

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

        String measurementName = "edge1";
        // Write points to InfluxDB.
        influxDB.write(Point.measurement(measurementName)
            .time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .tag("device", "voltmeter1")
            .addField("value", Float.valueOf(message))
            .build());


        // Wait a few seconds in order to let the InfluxDB client
        // write your points asynchronously (note: you can adjust the
        // internal time interval if you need via 'enableBatch' call).
        try {
            Thread.sleep(5_000L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Query your data using InfluxQL.
        // https://docs.influxdata.com/influxdb/v1.7/query_language/data_exploration/#the-basic-select-statement
        QueryResult queryResult = influxDB.query(new Query("SELECT * FROM " + measurementName));

        System.out.println(queryResult);
        // It will print something like:
        // QueryResult [results=[Result [series=[Series [name=h2o_feet, tags=null,
        //      columns=[time, level description, location, water_level],
        //      values=[
        //         [2020-03-22T20:50:12.929Z, below 3 feet, santa_monica, 2.064],
        //         [2020-03-22T20:50:12.929Z, between 6 and 9 feet, coyote_creek, 8.12]
        //      ]]], error=null]], error=null]
    }

    @Override
    public void close() throws IOException {
        connection.close();
    }
}
