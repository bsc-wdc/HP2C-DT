import java.io.DataOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;

public class TCPServer {

    private static final int TCP_PORT = 8080;
    private static final int UDP_PORT = 8081;

    public static void main(String[] args) {
        try {
            startTCPServer();
            startUDPServer();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void startTCPServer() throws Exception {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(TCP_PORT);
                 Socket clientSocket = serverSocket.accept();
                 DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

                System.out.println("TCP Server started, waiting for client...");
                System.out.println("Client connected: " + clientSocket.getInetAddress().getHostAddress());

                float[] values = new float[10];
                while (true) {
                    for (int i = 0; i < values.length; i++) {
                        values[i] = (float) Math.random();
                        out.writeFloat(values[i]);
                        //System.out.println("Sent TCP value: " + values[i]);
                    }
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                System.err.println("Error starting TCP socket.");
            }
        }).start();
    }

    private static void startUDPServer() throws Exception {
        new Thread(() -> {
            try (DatagramSocket udpSocket = new DatagramSocket(UDP_PORT)) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                while (true) {
                    udpSocket.receive(packet);
                    String receivedString = new String(packet.getData(), 0, packet.getLength());
                    System.out.println("Received UDP message: " + receivedString);
                }
            } catch (Exception e) {
                System.err.println("Error starting UDP socket.");
            }
        }).start();
    }
}

