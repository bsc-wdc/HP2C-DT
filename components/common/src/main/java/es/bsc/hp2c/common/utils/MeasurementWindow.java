package es.bsc.hp2c.common.utils;

import java.time.Instant;
import java.util.Arrays;

public class MeasurementWindow<T>{
    private static class Measurement<T>{
        Instant timestamp;
        T value;

        public Measurement(Instant timestamp, T value) {
            this.timestamp = timestamp;
            this.value = value;
        }

        @Override
        public String toString() {
            return "Measurement{" +
                    "timestamp=" + timestamp +
                    ", value=" + (value != null && value.getClass().isArray()
                    ? java.util.Arrays.toString((Object[]) value)
                    : value) +
                    '}';
        }
    }

    private final Measurement[] window;
    private int start = 0;  // Points to the oldest element
    private int size = 0;   // Current number of elements

    public MeasurementWindow(int capacity) {
        this.window = new Measurement[capacity];
    }

    public void addMeasurement(Instant timestamp, T value) {
        int nextIndex = (start + size) % window.length; // Insert new element
        window[nextIndex] = new Measurement(timestamp, value);

        if (size < window.length) {
            size++; // Increase size if not full
        } else {
            start = (start + 1) % window.length; // Advance start if full
        }
    }

    public Measurement[] getMeasurementsNewerToOlder() {
        Measurement[] result = new Measurement[size];
        for (int i = 0; i < size; i++) {
            int index = (start + size - 1 - i) % window.length; // Traverse backwards
            result[i] = window[index];
        }
        return result;
    }

    public Measurement[] getMeasurementsOlderToNewer() {
        Measurement[] result = new Measurement[size];
        for (int i = 0; i < size; i++) {
            int index = (start + i) % window.length; // Traverse forwards
            result[i] = window[index];
        }
        return result;
    }

    @Override
    public String toString() {
        Measurement[] measurements = getMeasurementsNewerToOlder();
        StringBuilder result = new StringBuilder();
        for (Measurement measurement : measurements) {
            result.append(measurement).append(System.lineSeparator());
        }
        return result.toString();
    }
}
