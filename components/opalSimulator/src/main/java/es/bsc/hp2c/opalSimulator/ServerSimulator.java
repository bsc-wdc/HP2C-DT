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
    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static final int BASE_TCP_SENSORS_PORT = 11002;
    private static final int BASE_TCP_ACTUATORS_PORT = 31002;
    private static boolean runClient = false;
    private static Float[][] devices;

    public static void main(String[] args) throws InterruptedException {
        int nSockets;
        if (args.length > 0){
            nSockets = Integer.parseInt(args[0]);
        } else {
            System.out.println("User must input as arguments: ");
            System.out.println("    nSockets: Number of edge devices (number of client ant server sockets)");
            System.out.println("    Number of indexes for each edge node (nSockets inputs) ");
            args = new String[]{"1", "5"};
            nSockets = Integer.parseInt(args[0]);
        }

        // Declare Float[][] to store devices values. Each of the lines of the array represents each edge,
        // while each of the columns represents the indexes within the edge.
        devices = new Float[nSockets][];
        for (int i = 1; i <= nSockets; ++i){
            Float[] edgeI = new Float[Integer.parseInt(args[i])];
            for (int j = 0; j < edgeI.length; ++j){
                edgeI[j] = Float.NEGATIVE_INFINITY;
            }
            devices[i - 1] = edgeI;
        }

        for (int i = 0; i < nSockets; ++i){
            int port = BASE_TCP_ACTUATORS_PORT + (i * 1000);
            int finalI = i;
            Thread t = new Thread(() -> {
                try {
                    server = new ServerSocket(port, 0, InetAddress.getByName("0.0.0.0"));
                    System.out.println("Server running. Waiting for client requests...");
                    Socket clientSocket = null;
                    clientSocket = server.accept();
                    runClient = true;
                    System.out.println("Accepted connection from: " + clientSocket.getInetAddress().getHostAddress());
                    handleClient(clientSocket, finalI);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            t.start();
        }
        while (runClient == false){
            Thread.sleep(1000);
        }
        for (int i = 0; i < nSockets; ++i){
            int tcpPort = BASE_TCP_SENSORS_PORT + (i * 1000);
            System.out.println("Starting UDP communication in port " + tcpPort + " ip " + SERVER_ADDRESS);
            startTCPClient(tcpPort, i);
        }
    }

    private static void handleClient(Socket clientSocket, int edgeNumber) {
        try {
            DataInputStream dis = new DataInputStream(clientSocket.getInputStream());

            while (true) {
                byte[] buffer = new byte[devices[edgeNumber].length * Float.BYTES];
                dis.readFully(buffer);
                System.out.println("Message Received from " + clientSocket.getInetAddress().getHostAddress());

                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

                int index = 0;
                while (byteBuffer.remaining() > 0) {
                    devices[edgeNumber][index] = byteBuffer.getFloat();
                    index += 1;
                }
            }
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    }

    private static void startTCPClient(int tcpPort, int edgeNumber) {
        new Thread(() -> {
            try (Socket tcpSocket = new Socket(SERVER_ADDRESS, tcpPort)) {
                while (true) {
                    ByteBuffer byteBuffer = ByteBuffer.allocate(devices[edgeNumber].length * Float.BYTES);
                    float[] values = new float[devices[edgeNumber].length];
                    for (int i = 0; i < devices[edgeNumber].length; i++) {
                        values[i] = devices[edgeNumber][i];
                    }

                    for (float value : values) {
                        byteBuffer.putFloat(value);
                        System.out.println("Prepared TCP value: " + value);
                    }

                    try {
                        DataOutputStream outputStream = new DataOutputStream(tcpSocket.getOutputStream());
                        byte[] buffer = byteBuffer.array();
                        outputStream.write(buffer);
                        System.out.println("Sent TCP packet.");
                    } catch (IOException e) {
                        System.err.println("Error sending data through TCP: " + e.getMessage());
                        break;
                    }

                    Thread.sleep(5000);
                }
            } catch (Exception e) {
                System.err.println("Error connecting to TCP server: " + e.getMessage());
            }
        }).start();
    }

}
