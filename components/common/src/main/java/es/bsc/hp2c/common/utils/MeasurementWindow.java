package es.bsc.hp2c.common.utils;

import java.io.*;
import java.time.Duration;
import java.time.Instant;

/**
 * Class in charge of storing an array of measurements. In order to send and receive windows, it implements serializable
 *
 * @param <T> Type of the measurements stored.
 */
public class MeasurementWindow<T> implements Serializable{
    private final Measurement<T>[] window;
    private int start = 0;  // Points to the oldest element
    private int size = 0;   // Current number of elements

    public MeasurementWindow(int capacity) {
        this.window = new Measurement[capacity];
    }


    public void addMeasurement(Instant timestamp, T value) {
        int nextIndex = (start + size) % window.length; // Insert new element
        window[nextIndex] = new Measurement<T>(timestamp, value);

        if (size < window.length) {
            size++; // Increase size if not full
        } else {
            start = (start + 1) % window.length; // Advance start if full
        }
    }

    public Measurement<T>[] getMeasurementsNewerToOlder() {
        Measurement<T>[] result = new Measurement[size];
        for (int i = 0; i < size; i++) {
            int index = (start + size - 1 - i) % window.length; // Traverse backwards
            result[i] = window[index];
        }
        return result;
    }

    public Measurement<T>[] getMeasurementsOlderToNewer() {
        Measurement<T>[] result = new Measurement[size];
        for (int i = 0; i < size; i++) {
            int index = (start + i) % window.length; // Traverse forwards
            result[i] = window[index];
        }
        return result;
    }

    public Measurement<T> getLastMeasurement(){
        if (size == 0){ return null; }
        return window[(start + size - 1) % window.length];
    }

    public Measurement<T> getFirstMeasurement() {
        if (size == 0) {
            return null;
        }
        return window[start];
    }

    public Duration getTotalTimeSpan() {
        if (size < 2) {
            return Duration.ZERO; // Not enough measurements to calculate a span
        }
        Instant oldest = window[start].getTimestamp();
        Instant newest = window[(start + size - 1) % window.length].getTimestamp();
        return Duration.between(oldest, newest);
    }

    public double getSamplingRate() {
        if (size < 2) {
            return 0.0; // Not enough data to calculate a rate
        }
        Duration timeSpan = getTotalTimeSpan();
        long totalNanos = timeSpan.getSeconds() * 1_000_000_000L + timeSpan.getNano();
        if (totalNanos == 0) {
            return 0.0; // Avoid division by zero
        }
        return (double) (size - 1) / (totalNanos / 1_000_000_000.0);
    }

    public int getSize(){
        return this.size;
    }

    public int getCapacity(){
        return this.window.length;
    }

    @Override
    public String toString() {
        Measurement<T>[] measurements = getMeasurementsNewerToOlder();
        StringBuilder result = new StringBuilder();
        int i = 0;
        for (Measurement<T> measurement : measurements) {
            result.append(measurement);
            if (i < measurements.length - 1) {
                // Create new line except for last measurement
                result.append(System.lineSeparator());
            }
            i++;
        }
        return result.toString();
    }

    public byte[] encode(){
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(this);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static MeasurementWindow decode(byte[] data) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (MeasurementWindow) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
