package es.bsc.hp2c.edge.utils;

import java.nio.ByteBuffer;

import static es.bsc.hp2c.common.utils.CommUtils.BytesToFloatArray;
import static es.bsc.hp2c.common.utils.CommUtils.FloatArrayToBytes;
import static org.junit.jupiter.api.Assertions.*;

class CommUtilsTest {

    @org.junit.jupiter.api.Test
    void floatArrayToBytes() {
        // Create a sample float array
        Float[] floatArray = new Float[]{1.5f, 2.7f, 3.9f};

        // Convert the float array to bytes
        byte[] resultBytes = FloatArrayToBytes(floatArray);

        // Calculate expected bytes manually for comparison
        ByteBuffer byteBuffer = ByteBuffer.allocate(floatArray.length * Float.BYTES);
        for (Float floatValue : floatArray) {
            byteBuffer.putFloat(floatValue);
        }
        byte[] expectedBytes = byteBuffer.array();

        // Check if the converted bytes arrays are equal
        assertArrayEquals(expectedBytes, resultBytes, "Arrays should be equal");
    }

    @org.junit.jupiter.api.Test
    void bytesToFloatArray() {
        // Create a sample float array
        Float[] expectedFloatArray = new Float[]{1.5f, 2.7f, 3.9f};

        // Convert the float array to bytes
        byte[] bytes = convertFloatArrayToBytes(expectedFloatArray);

        // Convert the bytes back to a float array
        Float[] resultFloatArray = BytesToFloatArray(bytes);

        // Check if the converted float arrays are equal
        assertArrayEquals(expectedFloatArray, resultFloatArray, "Arrays should be equal");
    }

    // Helper method to convert a Float array to bytes
    private byte[] convertFloatArrayToBytes(Float[] floats) {
        // Implement your logic to convert Float[] to bytes (This is just an example and may not work in all scenarios)
        // Here is a simple example using ByteBuffer
        int floatSize = Float.BYTES;
        byte[] bytes = new byte[floats.length * floatSize];
        for (int i = 0; i < floats.length; i++) {
            int intBits = Float.floatToIntBits(floats[i]);
            bytes[i * floatSize] = (byte) (intBits >> 24);
            bytes[i * floatSize + 1] = (byte) (intBits >> 16);
            bytes[i * floatSize + 2] = (byte) (intBits >> 8);
            bytes[i * floatSize + 3] = (byte) intBits;
        }
        return bytes;
    }
}