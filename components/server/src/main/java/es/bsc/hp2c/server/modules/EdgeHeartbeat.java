package es.bsc.hp2c.server.modules;

import com.rabbitmq.client.*;
import es.bsc.hp2c.common.types.Device;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static es.bsc.hp2c.common.utils.FileUtils.loadDevices;

public class EdgeHeartbeat {

    private static final String QUEUE_NAME = "heartbeats";
    private static final String routingKey = "edge.*.heartbeat";
    private static final int HEARTBEAT_TIMEOUT = 10000; // 1 minute
    private Map<String, Map<String, Device>> deviceMap;
    private final Channel channel;
    private Map<String, Long> lastHeartbeatTimes;

    public EdgeHeartbeat(AmqpManager amqp, Map<String, Map<String, Device>> deviceMap) throws IOException {
        this.channel = amqp.getChannel();
        channel.exchangeDeclare(amqp.getExchangeName(), "topic");
        this.channel.queueDeclare(QUEUE_NAME, true, false, false, null);
        this.channel.queueBind(QUEUE_NAME, amqp.getExchangeName(), routingKey);
        this.deviceMap = deviceMap;
        this.lastHeartbeatTimes = new HashMap<>();
    }
    public void start() throws IOException {
        this.startListener();
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                checkInactiveEdges();
            }
        }, 0, HEARTBEAT_TIMEOUT / 2); // Check every half of the heartbeat timeout interval
    }

    private void startListener() throws IOException {
        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
                processHeartbeatMessage(envelope.getRoutingKey(), body);
            }
        };
        channel.basicConsume(QUEUE_NAME, true, consumer);
    }

    private void processHeartbeatMessage(String routingKey, byte[] body) {
        // Extract edge name from routing key
        String[] routingKeyParts = routingKey.split("\\.");
        String edgeLabel = routingKeyParts[1];
        JSONObject setupJson = new JSONObject(new String(body, StandardCharsets.UTF_8));

        // Update last heartbeat time for this edge TODO: Check usage of this map together with deviceMap. Redundancy?
        System.out.println("Received heartbeat for edge '" + edgeLabel + "': " + setupJson);
        lastHeartbeatTimes.put(edgeLabel, System.currentTimeMillis());

        // Process the heartbeat message
        if (deviceMap.containsKey(edgeLabel)) {
            // The first time a heartbeat is received we load the device virtual driver
            deviceMap.put(edgeLabel, loadDevices(setupJson, "driver-dt"));
        }
        // TODO: else with Update device information?
    }

    private void checkInactiveEdges() {
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : lastHeartbeatTimes.entrySet()) {
            if (currentTime - entry.getValue() > HEARTBEAT_TIMEOUT) {
                // Device is not available
                String edgeLabel = entry.getKey();
                System.out.println("Edge '" + edgeLabel + "' is inactive.");
                // TODO: Set isAvailable to false
            }
        }
    }
}

