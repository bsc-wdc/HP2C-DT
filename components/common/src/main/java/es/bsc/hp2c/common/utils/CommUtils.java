package es.bsc.hp2c.common.utils;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Utility class for commonly used methods related to communications
 */
public final class CommUtils {
    private CommUtils(){}

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
    public static String parseRemoteBrokerIp(String defaultIp) throws IOException {
        String brokerIp = parseRemoteBrokerIp();
        if (brokerIp.isEmpty()) {
            brokerIp = defaultIp;
        }
        return brokerIp;
    }

    /** Look for the AMQP broker IP in the deployment setup file. */
    public static String parseRemoteBrokerIp() throws IOException {
        // Check existence of file
        String deploymentFile = "/data/deployment_setup.json";
        if (!new File(deploymentFile).isFile()) {
            deploymentFile = "../../deployments/testbed/deployment_setup.json";
            if (!new File(deploymentFile).isFile()) {
                throw new IOException("Could not find 'deployment_setup.json'");
            }
        }
        // Parse JSON file and look for IP address
        InputStream is = Files.newInputStream(Paths.get(deploymentFile));
        JSONTokener tokener = new JSONTokener(is);
        JSONObject object = new JSONObject(tokener);
        return object.getJSONObject("broker").optString("ip");
    }
}
