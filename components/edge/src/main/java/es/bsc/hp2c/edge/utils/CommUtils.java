package es.bsc.hp2c.edge.utils;

import java.nio.ByteBuffer;

/**
 * Utility class for commonly used methods related to communications
 */
public final class CommUtils {
    private CommUtils(){}

    public static byte[] FloatArrayToBytes(Float[] values) {
        int nFloat = values.length;
        ByteBuffer byteBuffer = ByteBuffer.allocate(nFloat * Float.BYTES);
        for (Float value : values) {
            byteBuffer.putFloat(value);
        }
        return byteBuffer.array();
    }

    public static Float[] BytesToFloatArray(byte[] messageBytes) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(messageBytes);
        int nFloat = messageBytes.length / Float.BYTES;
        Float[] messageValues = new Float[nFloat];
        for (int i = 0; i < nFloat; i++) {
            messageValues[i] = byteBuffer.getFloat();
        }
        return messageValues;
    }
}
