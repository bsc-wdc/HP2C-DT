package bsc.es.hp2c.opalSimulator;

import java.io.DataInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

public class OpalSimulator {

    private static final String SERVER_ADDRESS = "hp2c_device_0"; 
    private static final int TCP_PORT = 8080;
    private static final int UDP_PORT = 8081;

    public static void main(String[] args) {
        try {
            // startTCPClient();
            startUDPClient();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void startUDPClient() {
        new Thread(() -> {
            try (DatagramSocket udpSocket = new DatagramSocket()) {
                InetAddress address = InetAddress.getByName(SERVER_ADDRESS);
                while (true) {
                    ByteBuffer byteBuffer = ByteBuffer.allocate(25 * Float.BYTES);
                    for (int i = 0; i < 25; i++) {
                        float value = (float) Math.random();
                        byteBuffer.putFloat(value);
                        System.out.println("Prepared UDP value: " + value);
                    }
                    byte[] buffer = byteBuffer.array();
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, UDP_PORT);
                    udpSocket.send(packet);
                    System.out.println("Sent UDP packet.");
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                System.err.println("Error sending data through UDP.");
            }
        }).start();
    }

    private static void startTCPClient() {
        new Thread(() -> {
            try (Socket socket = new Socket(SERVER_ADDRESS, TCP_PORT);
                 DataInputStream in = new DataInputStream(socket.getInputStream())) {

                System.out.println("Connected to TCP Server at " + SERVER_ADDRESS);
                while (true) {
                    float receivedValue = in.readFloat();
                    System.out.println("Received TCP value: " + receivedValue);
                }
            } catch (Exception e) {
                System.err.println("Error with TCP connection.");
            }
        }).start();
    }

}
