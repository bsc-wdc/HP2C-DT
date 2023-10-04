package es.bsc.server;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.io.IOException;
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

        System.out.println(" [x] Awaiting RPC requests");

        DeliverCallback callback = (consumerTag, delivery) -> {
            String message = new String (delivery.getBody(), "UTF-8");
            System.out.println(" [x] Received '" + delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
        };
        channel.basicConsume(queueName, true, callback, consumerTag -> { });
    }

    @Override
    public void close() throws IOException {
        connection.close();
    }
}
