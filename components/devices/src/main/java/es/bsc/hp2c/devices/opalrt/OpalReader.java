package es.bsc.hp2c.devices.opalrt;

import es.bsc.hp2c.devices.generic.Voltmeter;
import es.bsc.hp2c.devices.types.Sensor;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The class OpalReader simulates written values for every sensor.
 */
public class OpalReader {

    private static final List<OpalSensor<?>> sensors = new ArrayList<>();
    private static float[] values = new float[25];

    static {
        Thread t = new Thread() {
            public void run() {
                while (true) {
                    // Print time each iteration
                    LocalTime currentTime = LocalTime.now();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
                    String formattedTime = currentTime.format(formatter);
                    System.out.println("Current time: " + formattedTime);

                    for (int i = 0; i < values.length; i++) {
                        // Receive sensed values
                        values[i] = (float) Math.random();
                    }
                    synchronized (OpalReader.sensors) {
                        for (OpalSensor<?> sensor : sensors) {
                            int idx = sensor.getIndex();
                            float sensedValue;
                            if (sensor instanceof Voltmeter) {
                                // Sense voltage between 210 and 260 volts
                                sensedValue = 210 + 50 * values[idx];
                            } else {
                                // Rest of sensors: 0 to 1 values
                                sensedValue = values[idx];
                            }
                            sensor.sensed(sensedValue);
                            sensor.onRead();
                        }

                        System.out.println(); // Add empty line at the end of each measurement
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        // Ignore
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

    protected static interface OpalSensor<V> extends Sensor<Float, V> {

        public int getIndex();
    }
}
