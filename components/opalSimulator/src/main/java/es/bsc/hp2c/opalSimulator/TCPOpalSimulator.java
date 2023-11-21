package es.bsc.hp2c.opalSimulator;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;

public class TCPOpalSimulator {

    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static final int BASE_TCP_PORT = 11002;
    private static final double frequency = 1.0 / 20.0;  // period = 20 s

    public static void main(String[] args) {
        // Get number of ports to open
        int nPorts;
        if (args.length == 1) {
            nPorts = Integer.valueOf(args[0]);
        } else{
            // Default to 2 ports
            nPorts = 2;
        }
        // Launch communication threads
        for (int i = 0; i < nPorts; i++) {
            int tcpPort = BASE_TCP_PORT + (i * 1000);
            System.out.println("Starting UDP communication in port " + tcpPort + " ip " + SERVER_ADDRESS);
            startTCPClient(tcpPort);
        }
    }

    private static void startTCPClient(int tcpPort) {
        new Thread(() -> {
            try (Socket tcpSocket = new Socket(SERVER_ADDRESS, tcpPort)) {
                while (true) {
                    ByteBuffer byteBuffer = ByteBuffer.allocate(25 * Float.BYTES);
                    float[] values = genSineValues(25);
    
                    float valueToSubtract = 5.0f;
                    for (int i = 0; i < values.length; i++) {
                        values[i] -= valueToSubtract;
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
                        break;  // Salir del bucle si hay un error al enviar
                    }
    
                    Thread.sleep(5000);  // Esperar antes de enviar el siguiente paquete
                }
            } catch (Exception e) {
                System.err.println("Error connecting to TCP server: " + e.getMessage());
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