package es.bsc.hp2c.opalSimulator;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


public class OpalSimulator {
    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static final int BASE_TCP_SENSORS_PORT = 11002;
    private static final int BASE_TCP_ACTUATORS_PORT = 31002;
    private static int runClient = 0;
    private static Map<Integer, Float[]> devices = new HashMap<>();
    private static final int BASE_UDP_PORT = 21002;
    private static final double frequency = 1.0 / 20.0;

    public static void main(String[] args) throws InterruptedException {
        int nSockets;
        if (args.length > 0){
            nSockets = Integer.parseInt(args[0]);
        } else {
            System.out.println("User must input as arguments: ");
            System.out.println("    nSockets: Number of edge devices (number of client ant server sockets)");
            System.out.println("    Number of indexes for each edge node (nSockets inputs) ");
            args = new String[]{"2", "5", "0"};
            nSockets = Integer.parseInt(args[0]);
        }

        // while each of the columns represents the indexes within the edge.
        for (int i = 1; i <= nSockets; ++i){
            Float[] edgeI = new Float[Integer.parseInt(args[i])];
            Arrays.fill(edgeI, Float.NEGATIVE_INFINITY);
            devices.put(i, edgeI);
        }

        for (int i = 0; i < nSockets; ++i){
            int port = BASE_TCP_ACTUATORS_PORT + (i * 1000);
            int finalI = i;
            new Thread(() -> {
                try {
                    ServerSocket server = new ServerSocket(port, 0, InetAddress.getByName("0.0.0.0"));
                    System.out.println("Server running in port " + port + ". Waiting for client requests...");
                    Socket clientSocket = null;
                    clientSocket = server.accept();
                    runClient += 1;
                    System.out.println("Accepted connection from: " + clientSocket.getInetAddress().getHostAddress());
                    handleClient(clientSocket, (port / 1000) % 10);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }

        while (runClient < nSockets){
            Thread.sleep(1000);
        }
        Thread.sleep(3000);

        for (int i = 0; i < nSockets; ++i){
            int finalI = i;
            int tcpPort = BASE_TCP_SENSORS_PORT + (finalI * 1000);
            new Thread(() -> {
                System.out.println("Starting TCP communication in port " + tcpPort + " ip " + SERVER_ADDRESS);
                startTCPClient(tcpPort, (tcpPort / 1000) % 10);
            }).start();
        }

        Thread.sleep(5000);
        for (int i = 0; i < nSockets; ++i){
            int finalI = i;
            new Thread(() -> {
                int udpPort = BASE_UDP_PORT + (finalI * 1000);
                System.out.println("Starting UDP communication in port " + udpPort + " ip " + SERVER_ADDRESS);
                startUDPClient(udpPort, (udpPort / 1000) % 10);
            }).start();
        }
    }

    private static void handleClient(Socket clientSocket, int edgeNumber) {
        try {
            if (devices.get(edgeNumber).length < 1){ return; }
            DataInputStream dis = new DataInputStream(clientSocket.getInputStream());

            while (true) {
                byte[] buffer = new byte[devices.get(edgeNumber).length * Float.BYTES];
                dis.readFully(buffer);
                System.out.println("Message Received from " + clientSocket.getInetAddress().getHostAddress());

                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

                int index = 0;
                while (byteBuffer.remaining() > 0) {
                    Float[] aux = devices.get(edgeNumber);
                    aux[index] = byteBuffer.getFloat();
                    devices.put(edgeNumber, aux);
                    index += 1;
                }
            }
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    }

    private static void startTCPClient(int tcpPort, int edgeNumber) {
        if (devices.get(edgeNumber).length < 1){ return; }
        try {
            Socket tcpSocket = new Socket(SERVER_ADDRESS, tcpPort);
            while (true) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(devices.get(edgeNumber).length * Float.BYTES + Integer.BYTES + Character.BYTES);
                float[] values = new float[devices.get(edgeNumber).length];
                for (int i = 0; i < devices.get(edgeNumber).length; i++) {
                    Float[] aux = devices.get(edgeNumber);
                    values[i] = aux[i];
                }
                byteBuffer.putInt(devices.get(edgeNumber).length);
                System.out.println("Length of the message: " + devices.get(edgeNumber).length);
                for (float value : values) {
                    byteBuffer.putFloat(value);
                    System.out.println("Prepared TCP value: " + value);
                }
                byteBuffer.putChar('\n');

                try {
                    DataOutputStream outputStream = new DataOutputStream(tcpSocket.getOutputStream());
                    byte[] buffer = byteBuffer.array();
                    outputStream.write(buffer);
                    System.out.println("Sent TCP packet.\n");
                } catch (IOException e) {
                    System.err.println("Error sending data through TCP: " + e.getMessage());
                    break;
                }
                Thread.sleep(5000);
            }
        } catch (Exception e) {
            System.err.println("Error connecting to TCP server: " + e.getMessage());
        }
    }

    private static void startUDPClient(int udpPort, int edgeNumber) {
        try (DatagramSocket udpSocket = new DatagramSocket()) {
            InetAddress address = InetAddress.getByName(SERVER_ADDRESS);
            while (true) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(25* Float.BYTES);
                float[] values = genSineValues(25);
                // Modify voltages (0, 1, 2)
                values[0] *= (float) Math.sqrt(2) * 230;
                values[1] *= (float) Math.sqrt(2) * 230;
                values[2] *= (float) Math.sqrt(2) * 230;
                for (float value: values) {
                    // float value = (float) Math.random();
                    byteBuffer.putFloat(value);
                }
                byte[] buffer = byteBuffer.array();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, udpPort);
                udpSocket.send(packet);
                System.out.println("Sent UDP packet.");
                Thread.sleep(5000);
            }
        } catch (Exception e) {
            System.err.println("Error sending data through UDP.");
        }
    }

    public static float[] genSineValues(int size) {
        float[] values = new float[size];
        long currentTimeMillis = System.currentTimeMillis();
        double time = currentTimeMillis / 1000.0; // Convert milliseconds to seconds
        double angularFrequency = 2 * Math.PI * frequency;

        for (int i = 0; i < size; i++) {
            double shift = i * (2 * Math.PI / 3);
            values[i] = (float) Math.sin(angularFrequency * time + shift);
        }

        return values;
    }
}
