package es.bsc.hp2c.common.utils;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import static es.bsc.hp2c.common.utils.FileUtils.getJsonObject;

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
    public static String parseRemoteIp(String component, String defaultIp) throws IOException {
        String brokerIp = parseRemoteIp(component);
        if (brokerIp.isEmpty()) {
            brokerIp = defaultIp;
        }
        return brokerIp;
    }

    /** Look for the IP address of a component (e.g., broker) in the deployment setup file. */
    public static String parseRemoteIp(String component) throws IOException {
        // Check existence of file
        String deploymentFile = "/data/deployment_setup.json";
        if (!new File(deploymentFile).isFile()) {
            deploymentFile = "../../deployments/testbed/deployment_setup.json";
            if (!new File(deploymentFile).isFile()) {
                throw new IOException("Could not find 'deployment_setup.json'");
            }
        }
        // Parse JSON file and look for IP address
        JSONObject object = getJsonObject(deploymentFile);
        String ip = object.getJSONObject(component).optString("ip");
        // Do not use 0.0.0.0 or localhost since containers do not work well with them
        if (ip.equals("0.0.0.0") || ip.equals("localhost") || ip.equals("127.0.0.1")) {
            return "";
        } else {
            return ip;
        }
    }
}
