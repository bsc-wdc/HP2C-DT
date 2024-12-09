package es.bsc.hp2c.common.utils;

import java.time.Instant;
import java.util.Arrays;

public class MeasurementWindow<T>{
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
        for (Measurement<T> measurement : measurements) {
            result.append(measurement).append(System.lineSeparator());
        }
        return result.toString();
    }
}
