package es.bsc.hp2c.common.utils;

import java.nio.ByteBuffer;

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
}
