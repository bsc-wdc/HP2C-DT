package es.bsc.hp2c.opalSimulator;

import java.io.DataInputStream;
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

        System.out.println("Server running. Waiting for client requests...");

        while (true) {
            Socket clientSocket = server.accept();
            System.out.println("Accepted connection from: " + clientSocket.getInetAddress().getHostAddress());

            Thread clientHandlerThread = new Thread(() -> handleClient(clientSocket));
            clientHandlerThread.start();
        }
    }

    private static void handleClient(Socket clientSocket) {
        try {
            DataInputStream dis = new DataInputStream(clientSocket.getInputStream());

            while (true) {
                byte[] buffer = new byte[4 * Float.BYTES];
                dis.readFully(buffer);

                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

                while (byteBuffer.remaining() > 0) {
                    System.out.println("Message Received from " + clientSocket.getInetAddress().getHostAddress() +
                            ": " + byteBuffer.getFloat());
                }
            }
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    }
}
