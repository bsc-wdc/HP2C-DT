package es.bsc.hp2c.common.utils;

import org.json.JSONObject;

import java.time.Instant;

import static es.bsc.hp2c.common.utils.Aggregates.phasor;
import static org.junit.jupiter.api.Assertions.*;

class PhasorTest {

    private static final double FREQUENCY = 5.0; // Hz
    private static final double AMPLITUDE = 1.0;
    private static final double TOLERANCE_MAG = 0.1; // 5%
    private static final double TOLERANCE_ANGLE = 4.5; // degrees (5% of 90)

    private MeasurementWindow<Number[]> generateWave(double sampling_rate, boolean isCosine) {
        int samples = (int) (10000 / sampling_rate); // 10 seconds of data
        MeasurementWindow<Number[]> window = new MeasurementWindow<>(samples);
        Instant now = Instant.now();

        for (int i = 0; i < samples; i++) {
            double t = i * sampling_rate / 1000.0; // time in seconds
            double angle = 2 * Math.PI * FREQUENCY * t;
            double value;
            if (isCosine){
                value = AMPLITUDE * Math.cos(angle);
            } else {
                value = AMPLITUDE * Math.sin(angle);
            }
            window.addMeasurement(now.plusMillis((long)(i * sampling_rate)), new Number[]{value});
        }
        return window;
    }

    private double getPhasorMagnitude(MeasurementWindow<?> window) {
        JSONObject args = new JSONObject();

        // Pass phasor frequency as argument to phasor
        args.put("phasor-freq", FREQUENCY);
        MeasurementWindow<Number[]> result = phasor(window, args);

        // Assert result != null
        assertNotNull(result);
        Number[] resultValues = result.getLastMeasurement().getValue();
        return resultValues[0].doubleValue();
    }

    private double getPhasorAngle(MeasurementWindow<?> window) {
        JSONObject args = new JSONObject();
        args.put("phasor-freq", FREQUENCY);
        MeasurementWindow<Number[]> result = phasor(window, args);
        assertNotNull(result);
        Number[] resultValues = result.getLastMeasurement().getValue();
        return resultValues[1].doubleValue(); // in degrees
    }

    @org.junit.jupiter.api.Test
    void testPhasorMagnitudeAccuracy() {
        double[] sampling_rates = {10.0, 20.0, 50.0};

        for (double sampling_rate : sampling_rates) {
            MeasurementWindow<Number[]> sinWave = generateWave(sampling_rate, false);
            double estimatedMagnitude = getPhasorMagnitude(sinWave);

            double relativeError = Math.abs(estimatedMagnitude - AMPLITUDE) / AMPLITUDE;
            assertTrue(relativeError <= TOLERANCE_MAG,
                    String.format("Magnitude error %.2f%% exceeds tolerance for sampling_rate %.0f ms",
                            relativeError * 100, sampling_rate));
        }
    }

    @org.junit.jupiter.api.Test
    void testPhasorAngleDifferenceBetweenSineAndCosine() {
        MeasurementWindow<Number[]> sinWave = generateWave(10.0, false);
        MeasurementWindow<Number[]> cosWave = generateWave(10.0, true);

        double sinAngle = getPhasorAngle(sinWave);
        double cosAngle = getPhasorAngle(cosWave);

        double angleDiff = Math.abs(sinAngle - cosAngle);
        if (angleDiff > 180) {
            // Get basic angle
            angleDiff = 360 - angleDiff;
        }

        double error = Math.abs(angleDiff - 90.0);
        assertTrue(error <= TOLERANCE_ANGLE,
                String.format("Angle difference %.2f° deviates from 90° by more than %.2f°",
                        angleDiff, TOLERANCE_ANGLE));
    }
}
