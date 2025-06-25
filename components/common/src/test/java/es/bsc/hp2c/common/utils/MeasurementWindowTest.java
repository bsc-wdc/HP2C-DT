package es.bsc.hp2c.common.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class MeasurementWindowTest {

    @org.junit.jupiter.api.Test
    void testConstructor() {
        int capacity = 5;
        MeasurementWindow<Float> window = new MeasurementWindow<>(capacity);

        // Assert start == 0, size == 0, window.capacity == capacity
        assertEquals(0, window.getStart(), "Start index should be 0 after initialization");
        assertEquals(0, window.getSize(), "Size should be 0 after initialization");
        assertEquals(capacity, window.getCapacity(), "Capacity should match constructor parameter");
    }

    @org.junit.jupiter.api.Test
    void testAddMeasurementAndGetMeasurementOlderToNewer() {
        MeasurementWindow<Float> window = new MeasurementWindow<>(3);
        Instant now = Instant.now();

        // Add first two measurements
        window.addMeasurement(now, 0f);
        window.addMeasurement(now.plusSeconds(1), 1f);

        Measurement<Float>[] measurements = window.getMeasurementsOlderToNewer();

        // Assert size == 2, 0f -> first measurement, 1f -> second measurement
        assertEquals(2, window.getSize());
        assertEquals(0f, measurements[0].getValue());
        assertEquals(1f, measurements[1].getValue());

        // Add two more measurements (exceeding capacity)
        window.addMeasurement(now.plusSeconds(2), 2f);
        window.addMeasurement(now.plusSeconds(3), 3f);

        measurements = window.getMeasurementsOlderToNewer();

        // Assert size == 3, 1f -> first measurement, 2f -> second measurement, 3f -> third measurement
        assertEquals(3, window.getSize());
        assertEquals(1f, measurements[0].getValue());
        assertEquals(2f, measurements[1].getValue());
        assertEquals(3f, measurements[2].getValue());
    }

    @org.junit.jupiter.api.Test
    void testGetMeasurementsNewerToOlder() {
        MeasurementWindow<Float> window = new MeasurementWindow<>(3);
        Instant now = Instant.now();

        // Add first two measurements
        window.addMeasurement(now, 0f);
        window.addMeasurement(now.plusSeconds(1), 1f);

        Measurement<Float>[] measurements = window.getMeasurementsNewerToOlder();

        // Assert 1f -> first measurement, 0f -> second measurement
        assertEquals(1f, measurements[0].getValue());
        assertEquals(0f, measurements[1].getValue());
    }

    @org.junit.jupiter.api.Test
    void testGetFirstAndLastMeasurement() {
        MeasurementWindow<Float> window = new MeasurementWindow<>(3);
        assertNull(window.getFirstMeasurement(), "First measurement should be null for empty window");
        assertNull(window.getLastMeasurement(), "Last measurement should be null for empty window");

        Instant now = Instant.now();
        window.addMeasurement(now, 0f);
        window.addMeasurement(now.plusSeconds(1), 1f);

        // Assert 0f -> first measurement, 1f -> second measurement
        assertEquals(0f, window.getFirstMeasurement().getValue());
        assertEquals(1f, window.getLastMeasurement().getValue());
    }

    @org.junit.jupiter.api.Test
    void testGetTotalTimeSpan() {
        MeasurementWindow<Float> window = new MeasurementWindow<>(3);
        assertEquals(Duration.ZERO, window.getTotalTimeSpan(), "Span should be 0 for empty window");

        Instant now = Instant.now();
        window.addMeasurement(now, 0f);
        window.addMeasurement(now.plusSeconds(1), 1f);
        window.addMeasurement(now.plusSeconds(2), 2f);

        // Assert span == 2 (2-second difference between first and last measurement)
        assertEquals(Duration.ofSeconds(2), window.getTotalTimeSpan(), "Span should match timestamp difference");
    }

    @org.junit.jupiter.api.Test
    void testGetSamplingRate() {
        MeasurementWindow<Float> window = new MeasurementWindow<>(3);
        assertEquals(0.0, window.getSamplingRate(), "Rate should be 0 with <2 measurements");

        Instant now = Instant.now();
        window.addMeasurement(now, 0f);
        window.addMeasurement(now.plusSeconds(2), 1f);
        window.addMeasurement(now.plusSeconds(4), 2f);

        double rate = window.getSamplingRate();

        // Assert samplingRate == 0.5 (2 values in 4 seconds)
        assertEquals(0.5, rate, "Rate should be 0.5");
    }

    @org.junit.jupiter.api.Test
    void testGetSizeAndGetCapacity(){
        int capacity = 3;
        MeasurementWindow<Float> window = new MeasurementWindow<>(capacity);
        Instant now = Instant.now();

        // Add first two measurements
        window.addMeasurement(now, 0f);
        window.addMeasurement(now.plusSeconds(1), 1f);

        // Assert size == 2, window.capacity == capacity
        assertEquals(2, window.getSize());
        assertEquals(capacity, window.getCapacity());

        // Add two more measurements (exceeding capacity)
        window.addMeasurement(now.plusSeconds(2), 2f);
        window.addMeasurement(now.plusSeconds(3), 3f);

        // Assert size == 3, window.capacity == capacity
        assertEquals(3, window.getSize());
        assertEquals(capacity, window.getCapacity());
    }
}
