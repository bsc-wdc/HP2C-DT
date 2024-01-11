package es.bsc.hp2c.opalSimulator;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/*
 * Runs nEdges TCP servers in order to receive actuations, nEdges TCP clients to update the values on the edges
 * (just of TCP sensors), and nEdges UDP servers with the aim of sending UDP sensors values. The OpalSimulator also
 * keeps track of the state of every TCP sensor within each edge by maintaining a map where the second digit of the
 * ports serves as the key (unique for each edge), and a Float array represents the corresponding values.
 *
 */
public class OpalSimulator {
    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static final int BASE_TCP_SENSORS_PORT = 11002;
    private static final int BASE_TCP_ACTUATORS_PORT = 31002;
    private static int runClient = 0;
    private static Map<Integer, Float[]> devices = new HashMap<>();
    private static final int BASE_UDP_PORT = 21002;
    private static final double frequency = 1.0 / 20.0;

    /*
    * Runs nEdges TCP servers in order to receive actuations, nEdges TCP clients to update the values on the edges
    * (just of TCP sensors), and nEdges UDP servers with the aim of sending UDP sensors values.
    *
    * @param args User should input the number of edges and nEdges more arguments indicating the TCP sensors within each
    * node. In this case, the number of UDP sensors will not be read, as we are sending always 25 values.
    * */
    public static void main(String[] args) throws InterruptedException {
        int nEdges;
        if (args.length > 0){
            nEdges = Integer.parseInt(args[0]);
        } else {
            System.out.println("User must input as arguments: ");
            System.out.println("    nEdges: Number of edge devices (number of client ant server sockets)");
            System.out.println("    Number of indexes for each edge node (nEdges inputs) ");
            args = new String[]{"1", "5"};
            nEdges = Integer.parseInt(args[0]);
        }

        // while each of the columns represents the indexes within the edge.
        for (int i = 1; i <= nEdges; ++i){
            Float[] edgeI = new Float[Integer.parseInt(args[i])];
            Arrays.fill(edgeI, Float.NEGATIVE_INFINITY);
            devices.put(i, edgeI);
        }

        for (int i = 0; i < nEdges; ++i){
            int port = BASE_TCP_ACTUATORS_PORT + (i * 1000);
            new Thread(() -> {
                try {
                    ServerSocket server = new ServerSocket(port, 0, InetAddress.getByName("0.0.0.0"));
                    System.out.println("Server running in port " + port + ". Waiting for client requests...");
                    Socket clientSocket = null;
                    clientSocket = server.accept();
                    runClient += 1;
                    System.out.println("Accepted connection from: " + clientSocket.getInetAddress().getHostAddress());
                    handleActuateClient(clientSocket, (port / 1000) % 10);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }

        while (runClient < nEdges){
            Thread.sleep(1000);
        }
        Thread.sleep(3000);

        for (int i = 0; i < nEdges; ++i){
            int tcpPort = BASE_TCP_SENSORS_PORT + (i * 1000);
            new Thread(() -> {
                System.out.println("Starting TCP communication in port " + tcpPort + " ip " + SERVER_ADDRESS);
                startTCPClient(tcpPort, (tcpPort / 1000) % 10);
            }).start();
        }

        Thread.sleep(5000);
        for (int i = 0; i < nEdges; ++i){
            int finalI = i;
            new Thread(() -> {
                int udpPort = BASE_UDP_PORT + (finalI * 1000);
                System.out.println("Starting UDP communication in port " + udpPort + " ip " + SERVER_ADDRESS);
                startUDPClient(udpPort, (udpPort / 1000) % 10);
            }).start();
        }
    }

    /*
    * This method uses a TCP socket to receive actuations and update the corresponding values in "devices" map.
    *
    * @param clientSocket Socket through which we will receive the messages.
    * @param edgeNumber Map key where value must be updated.
    * */
    private static void handleActuateClient(Socket clientSocket, int edgeNumber) {
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
                System.out.println("    Message is: " + Arrays.toString(devices.get(edgeNumber)) +
                        " for edge " + edgeNumber);
            }
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    }

    /*
    * This method starts a TCP client to update the value on the corresponding edge. To do so, we check start of message
    * with a readInt() that checks the expected length (number of floats) of the message, then get the buffer with that
    * messageLength, and lastly the end of line (EoL) character.
    * */
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
