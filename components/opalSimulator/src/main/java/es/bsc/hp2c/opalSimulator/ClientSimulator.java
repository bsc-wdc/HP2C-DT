package es.bsc.hp2c.opalSimulator;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

public class ClientSimulator {

    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static final int BASE_UDP_PORT = 21002;
    private static final double frequency = 1.0 / 20.0;  // period = 20 s

    public static void main(String[] args) throws InterruptedException {
        Thread.sleep(5000);
        // Get number of ports to open
        int nPorts;
        if (args.length == 1) {
            nPorts = Integer.parseInt(args[0]);
        } else{
            // Default to 2 ports
            nPorts = 2;
        }
        // Launch communication threads
        for (int i = 0; i < nPorts; i++) {
            int udpPort = BASE_UDP_PORT + (i * 1000);
            System.out.println("Starting UDP communication in port " + udpPort + " ip " + SERVER_ADDRESS);
            startUDPClient(udpPort);

        }
    }
    
    private static void startUDPClient(int udpPort) {
        new Thread(() -> {
            try (DatagramSocket udpSocket = new DatagramSocket()) {
                InetAddress address = InetAddress.getByName(SERVER_ADDRESS);
                while (true) {
                    ByteBuffer byteBuffer = ByteBuffer.allocate(25 * Float.BYTES);
                    float[] values = genSineValues(25);
                    // Modify voltages (0, 1, 2)
                    values[0] *= (float) Math.sqrt(2) * 230;
                    values[1] *= (float) Math.sqrt(2) * 230;
                    values[2] *= (float) Math.sqrt(2) * 230;
                    for (float value: values) {
                        // float value = (float) Math.random();
                        byteBuffer.putFloat(value);
                        System.out.println("Prepared UDP value: " + value);
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
        }).start();
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
