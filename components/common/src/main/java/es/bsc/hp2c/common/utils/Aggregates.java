package es.bsc.hp2c.common.utils;

import java.util.Arrays;
import java.time.Instant;
import java.time.Duration;

import static es.bsc.hp2c.common.utils.CommUtils.divideArray;

public class Aggregates {

    public static MeasurementWindow<Number[]> sum(MeasurementWindow<?> window) {
        MeasurementWindow<Number[]> resultWindow = new MeasurementWindow<>(1);

        Measurement<?> latestMeasurement = window.getLastMeasurement();
        if (latestMeasurement != null) {
            Object lastValue = latestMeasurement.getValue();
            if (lastValue instanceof Number[]) {
                int size = ((Number[]) lastValue).length; // Get size of each measurement
                Number[] sum = new Number[size];
                Arrays.fill(sum, 0);

                for (Measurement<?> m : window.getMeasurementsNewerToOlder()) {
                    Number[] valueArray = (Number[]) m.getValue();
                    for (int i = 0; i < valueArray.length; i++) {
                        sum[i] = sum[i].doubleValue() + valueArray[i].doubleValue();
                    }
                }

                resultWindow.addMeasurement(Instant.now(), sum);
            } else {
                throw new IllegalArgumentException("The MeasurementWindow does not contain Number[] values.");
            }
        }
        return resultWindow;
    }

    public static MeasurementWindow<Number[]> avg(MeasurementWindow<?> window) {
        MeasurementWindow<Number[]> resultWindow = new MeasurementWindow<>(1);

        MeasurementWindow<Number[]> sumWindow = sum(window);
        Measurement<?> latestSum = sumWindow.getLastMeasurement();
        if (latestSum != null) {
            Number[] avg = divideArray((Number[]) latestSum.getValue(), window.getSize());
            resultWindow.addMeasurement(Instant.now(), avg);
        }
        return resultWindow;
    }

    public static MeasurementWindow<?> all(MeasurementWindow<?> window) {
        MeasurementWindow<Object> resultWindow = new MeasurementWindow<>(window.getCapacity());
        for (Measurement<?> measurement : window.getMeasurementsNewerToOlder()) {
            resultWindow.addMeasurement(measurement.getTimestamp(), measurement.getValue());
        }
        return resultWindow;
    }

    public static MeasurementWindow<?> last(MeasurementWindow<?> window) {
        MeasurementWindow<Object> resultWindow = new MeasurementWindow<>(1);

        Measurement<?> lastMeasurement = window.getLastMeasurement();
        if (lastMeasurement != null) {
            resultWindow.addMeasurement(lastMeasurement.getTimestamp(), lastMeasurement.getValue());
        }
        return resultWindow;
    }

    public static MeasurementWindow<Number[]> phasor(MeasurementWindow<?> window) {
        final double FREQUENCY = 5.0; // Assumed frequency
        Instant aggregateTime = window.getLastMeasurement().getTimestamp();
        MeasurementWindow<Number[]> resultWindow = new MeasurementWindow<>(1);

        // Check data type and ensure the window contains measurements
        Measurement<?> latestMeasurement = window.getLastMeasurement();
        if (latestMeasurement == null || !(latestMeasurement.getValue() instanceof Number[])) {
            throw new IllegalArgumentException("The MeasurementWindow does not contain Number[] values.");
        }
        Number[] phasor = phasorEstimationDFT(window, FREQUENCY);
        resultWindow.addMeasurement(aggregateTime, phasor);
        return resultWindow;
    }

    /**
     * Estimates the phasor properties (magnitude and angle) of a sinusoidal signal from a
     * measurement window using the Discrete Fourier Transform (DFT). Also adjusts the angle
     * relative to the Unix epoch (1970-01-01T00:00:00Z) for consistent phase reference across
     * executions.
     *
     * @param window       The measurement window containing the sampled signal and its start time.
     * @param f            The frequency of the sinusoidal signal (in Hz).
     * @return A `Number[]` containing:
     *         [0] - The magnitude of the phasor (double).
     *         [1] - The angle of the phasor (double, in radians), adjusted to the Unix epoch.
     */
    private static Number[] phasorEstimationDFT(MeasurementWindow<?> window, double f) {
        int N = window.getSize();
        int k = (int) Math.round(N * f / window.getSamplingRate());
        Instant windowStartTime = window.getFirstMeasurement().getTimestamp();

        // Compute real and imaginary parts
        double realPart = 0.0;
        double imagPart = 0.0;

        int i = 0;
        for (Measurement<?> m : window.getMeasurementsOlderToNewer()) {
            Number[] signals = (Number[]) m.getValue();
            double value = signals[0].doubleValue();  // Use only first phase for phasor calculations
            double angle = 2 * Math.PI * k * i / N;
            realPart += value * Math.cos(angle);
            imagPart -= value * Math.sin(angle);
            i++;
        }

        // Normalize the results
        realPart *= 2.0 / N;
        imagPart *= 2.0 / N;

        // Calculate magnitude and angle
        double magnitude = Math.sqrt(realPart * realPart + imagPart * imagPart);
        double rawAngle = Math.atan2(imagPart, realPart);

        // Calculate the phase offset relative to the Unix epoch to adjust angle
        Instant unixEpoch = Instant.ofEpochSecond(0);
        long timeOffsetInNanoseconds = Duration.between(unixEpoch, windowStartTime).toNanos();
        double timeOffset = timeOffsetInNanoseconds / 1_000_000_000.0;  // Get seconds
        double phaseOffset = 2 * Math.PI * f * timeOffset;

        // Adapt angle format
        double angle = rawAngle - phaseOffset;
        double normalizedAngle = angle % (2 * Math.PI);  // Normalize to [0, 2π]
        if (normalizedAngle < 0) {
            normalizedAngle += 2 * Math.PI;  // Ensure positive value in the range [0, 2π]
        }
        normalizedAngle = normalizedAngle * 180 / Math.PI;  // Convert to degrees

        return new Number[]{magnitude, normalizedAngle};
    }

}
