package es.bsc.hp2c.devices.opalrt;

import es.bsc.hp2c.devices.generic.Voltmeter;
import es.bsc.hp2c.devices.types.Sensor;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The class OpalReader simulates written values for every sensor.
 */
public class OpalReader {

    private static final List<OpalSensor<?>> sensors = new ArrayList<>();
    private static final List<ThreePhaseOpalSensor<?>> threePhaseSensors = new ArrayList<>();
    private static float[] values = new float[25];
    private static int UDP_PORT;
    private static DatagramSocket udpSocket;

    static {
        Thread t = new Thread() {
            public void run() {
                // Initialize UDP server socket to read measurements
                try {
                    if (UDP_PORT == 0) {
                        throw new SocketException();
                    }
                    udpSocket = new DatagramSocket(UDP_PORT);
                } catch (SocketException e) {
                    System.err.println("Error initializing UDP socket at port " + UDP_PORT);
                    throw new RuntimeException(e);
                }
                System.out.println("\nConnected to port: " + UDP_PORT+ "\n");

                while (true) {
                    // Print time each iteration
                    LocalTime currentTime = LocalTime.now();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
                    String formattedTime = currentTime.format(formatter);
                    System.out.println("Current time: " + formattedTime);

                    try {
                        byte[] buffer = new byte[values.length * Float.BYTES];
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        udpSocket.receive(packet);

                        ByteBuffer byteBuffer = ByteBuffer.wrap(packet.getData());
                        for (int i = 0; i < values.length; i++) {
                            float receivedValue = byteBuffer.getFloat();
                            values[i] = receivedValue;
                        }

                        synchronized (OpalReader.sensors) {
                            for (OpalSensor<?> sensor : sensors) {
                                int idx = sensor.getIndex();
                                float sensedValue;
                                if (sensor instanceof Voltmeter) {
                                    // Sense voltage between 210 and 260 volts
                                    sensedValue = 210 + 50 * values[idx];
                                } else {
                                    sensedValue = values[idx];
                                }
                                sensor.sensed(sensedValue);
                                sensor.onRead();
                            }
                        }

                        System.out.println(); // Add empty line at the end of each measurement
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        System.err.println("Error receiving UDP message.");
                    }
                }
            }
        };
        t.setName("OpalReader");
        t.start();
    }

    /**
     * Register device into the list of sensors.
     *
     * @param sensor Sensor to register
     */
    public static void registerDevice(OpalSensor<?> sensor) {
        synchronized (OpalReader.sensors) {
            sensors.add(sensor);
        }
    }

    public static void registerThreePhaseDevice(ThreePhaseOpalSensor<?> composedSensor) {
        synchronized (OpalReader.threePhaseSensors) {
            threePhaseSensors.add(composedSensor);
        }
    }


    public static void setUDPPort(int port){
        UDP_PORT = port;
    }

    protected static interface OpalSensor<V> extends Sensor<Float, V> {
        public int getIndex();
    }

    protected static interface ThreePhaseOpalSensor<V> extends Sensor<Float[], V> {

        public int getIndex();
    }

    protected static interface ThreePhaseOpalSensor<V> extends Sensor<Float[], V> {

        public int getIndex();
    }
}
