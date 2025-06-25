package es.bsc.hp2c.common.utils;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static es.bsc.hp2c.common.utils.FileUtils.getJsonObject;

/**
 * Utility class for commonly used methods related to communications
 */
public final class CommUtils {
    private static final Logger logger = LogManager.getLogger("appLogger");
    private CommUtils(){}

    public static Number[] divideArray(Number[] array, double divisor) {
        if (divisor == 0) {
            throw new ArithmeticException("Division by zero is not allowed.");
        }

        Number[] result = new Number[array.length];
        for (int i = 0; i < array.length; i++) {
            if (array[i] != null) {
                result[i] = array[i].doubleValue() / divisor;
            } else {
                result[i] = null;
            }
        }
        return result;
    }

    /** Covert a Float array into a byte array. */
    public static byte[] FloatArrayToBytes(Float[] values) {
        int nFloat = values.length;
        ByteBuffer byteBuffer = ByteBuffer.allocate(nFloat * Float.BYTES);
        for (Float value : values) {
            byteBuffer.putFloat(value);
        }
        return byteBuffer.array();
    }

    /** Covert a byte array into a Float array . */
    public static Float[] BytesToFloatArray(byte[] messageBytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(messageBytes);
        int nFloat = messageBytes.length / Float.BYTES;
        Float[] messageValues = new Float[nFloat];
        for (int i = 0; i < nFloat; i++) {
            messageValues[i] = byteBuffer.getFloat();
        }
        return messageValues;
    }

    /** Return a comma-separated String with every element of the input array. */
    public static <T> String printableArray(T[] array) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            result.append(array[i].toString());
            if (i < array.length - 1) {
                result.append(", ");
            }
        }
        return result.toString();
    }

    /** Check if a string represents a numeric value. */
    public static boolean isNumeric(String str) {
        // Match a number with optional negative sign '-' and decimal point.
        return str.matches("-?\\d+(\\.\\d+)?");
    }

    /** Checks if the parsed IP address is valid, otherwise return the default IP. */
    public static HashMap<String, Object> parseRemoteIp(String component, String defaultIp)
            throws IOException {
        HashMap<String, Object> connectionMap = parseRemoteIp(component);
        String ip = (String) connectionMap.get("ip");
        if (ip.isEmpty()) {
            connectionMap.put("ip", defaultIp);
        }
        return connectionMap;
    }

    /** Look for the IP address of a component (e.g., broker) in the deployment setup file. */
    public static HashMap<String, Object> parseRemoteIp(String component) throws IOException {
        // Check existence of file
        String deploymentFile = "/data/deployment_setup.json";

        if (!new File(deploymentFile).isFile()) {
            // Check if the current working directory ends with "server"
            deploymentFile = "deployments/testbed/deployment_setup.json";

            if (!new File(deploymentFile).isFile()) {
                throw new IOException("Could not find 'deployment_setup.json'");
            }
        }
        HashMap<String, Object> connectionMap = new HashMap<>();
        // Parse JSON file and look for IP address and port
        JSONObject jObject = getJsonObject(deploymentFile).getJSONObject(component);
        String ip = jObject.optString("ip");
        int port = jObject.optInt("port");
        // Put port in hash map if it exists
        if (jObject.has("port")) {
            connectionMap.put("port", port);
        }
        // Put IP in hash map
        // Do not use 0.0.0.0 or localhost since containers do not work well with them
        if (ip.equals("0.0.0.0") || ip.equals("localhost") || ip.equals("127.0.0.1")) {
            connectionMap.put("ip", "");
        } else {
            connectionMap.put("ip", ip);
        }
        return connectionMap;
    }

    public static Connection AmqpConnectAndRetry(HashMap<String, Object> connectionMap) {
        String ip = (String) connectionMap.get("ip");
        int port = (int) connectionMap.get("port");
        return AmqpConnectAndRetry(ip, port);
    }

    public static Connection AmqpConnectAndRetry(String ip, int port) {
        logger.info("Connecting to RabbitMQ broker at address " + ip + ":" + port + "...");
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(ip);
        factory.setPort(port);
        boolean connected = false;
        Connection connection = null;

        final int[] retryFlag = {1};

        Thread monitorThread = new Thread(() -> {
            logger.info("");
            while (true) {
                if (retryFlag[0] == 1) {
                    logger.error("Error initializing RabbitMQ Connection to address "
                            + ip + ":" + port + ". Retrying...");
                }
                if (retryFlag[0] == 0) {
                    logger.info("Connected to RabbitMQ broker at address " + ip + ":" + port + "...");
                    return;
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    // Ignored
                }
            }
        });
        monitorThread.start();

        while (!connected) {
            try {
                connection = factory.newConnection();
                connected = true;
                retryFlag[0] = 0;
            } catch (IOException | TimeoutException e) {
                retryFlag[0] = 1;
                System.err.println("Error initializing RabbitMQ Connection to address "
                        + ip + ":" + port + ". " + e.getMessage() + ". Retrying...");
                // Retry connection after 5 seconds
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        retryFlag[0] = 0;
        return connection;
    }

    /** Create RabbitMQ BasicProperties object with the current nanoseconds timestamp inserted in headers. */
    public static AMQP.BasicProperties createAmqpPropertiesNanos() {
        Instant now = Instant.now();
        long epochSeconds = now.getEpochSecond();
        int nanos = now.getNano();
        Map<String, Object> headers = new HashMap<>();
        headers.put("epochSeconds", epochSeconds);
        headers.put("nanos", nanos);
        return new AMQP.BasicProperties.Builder()
                .headers(headers)
                .build();
    }

    /** Extract nanos timestamp from RabbitMQ headers and compose them into an Instant object. */
    public static Instant extractNanosFromHeaders(Map<String, Object> headers) {
        if (headers == null || !headers.containsKey("epochSeconds") || !headers.containsKey("nanos")) {
            throw new IllegalArgumentException("No headers found in RabbitMQ message");
        }
        long epochSeconds = (long) headers.get("epochSeconds");
        int nanos = (int) headers.get("nanos");
        return Instant.ofEpochSecond(epochSeconds, nanos);
    }


    public static List<Map<String, Object>> parseAmqpPublishFunctions(JSONObject jEdgeSetup) {
        List<Map<String, Object>> amqpPublishFunctions = new ArrayList<>();

        if (jEdgeSetup.has("AMQPPublishFunctions")) {
            JSONArray amqpPublishArray = jEdgeSetup.getJSONArray("AMQPPublishFunctions");

            for (Object obj : amqpPublishArray) {
                if (obj instanceof JSONObject) {
                    JSONObject func = (JSONObject) obj;

                    Map<String, Object> funcMap = new HashMap<>();
                    funcMap.put("label", func.optString("label"));
                    funcMap.put("aggregate", func.optString("aggregate"));

                    JSONArray devicesArray = func.optJSONArray("devices");
                    List<String> devices = new ArrayList<>();
                    if (devicesArray != null) {
                        for (Object device : devicesArray) {
                            devices.add(device.toString());
                        }
                    }
                    funcMap.put("devices", devices);

                    amqpPublishFunctions.add(funcMap);
                }
            }
        }

        return amqpPublishFunctions;
    }
}
