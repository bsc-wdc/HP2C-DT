package es.bsc.hp2c.opalSimulator;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

public class ServerSimulator {

    private static ServerSocket server;
    private static int port = 31002;

    public static void main(String[] args) throws IOException {
        server = new ServerSocket(port, 0, InetAddress.getByName("0.0.0.0"));

        System.out.println("Waiting for the client request");
        Socket socket = server.accept();
        while (true) {
            DataInputStream dis = new DataInputStream(socket.getInputStream());
            byte[] buffer = new byte[4 * Float.BYTES];
            dis.readFully(buffer);

            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

            while (byteBuffer.remaining() > 0) {
                System.out.println("Message Received: " + byteBuffer.getFloat());
            }
        }
    }
}
