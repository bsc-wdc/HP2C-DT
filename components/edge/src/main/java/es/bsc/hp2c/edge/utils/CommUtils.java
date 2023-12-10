package es.bsc.hp2c.edge.utils;

import java.io.*;

/**
 * Utility class for commonly used methods related to communications
 */
public final class CommUtils {
    private CommUtils(){}

    public static byte[] FloatArrayToBytes(Float[] values) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ObjectOutputStream out = null;
            out = new ObjectOutputStream(bos);
            out.writeObject(values);
            out.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            System.err.println("Error converting Float[] to bytes[]");
            throw new RuntimeException(e);
        }
    }

    public static Float[] BytesToFloatArray(byte[] messageBytes) {
        ByteArrayInputStream bis = new ByteArrayInputStream(messageBytes);
        try (ObjectInput in = new ObjectInputStream(bis)) {
            ObjectInputStream obj = (ObjectInputStream) in.readObject();
            return (Float[]) obj.readObject();
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Error converting bytes[] to Float[]");
            throw new RuntimeException(e);
        }
    }

}
