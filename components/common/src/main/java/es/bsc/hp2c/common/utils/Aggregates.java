package es.bsc.hp2c.common.utils;

import java.util.Arrays;
import java.time.Instant;

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
        final double FREQUENCY = 50.0; // Assumed frequency
        MeasurementWindow<Number[]> resultWindow = new MeasurementWindow<>(1);

        // Ensure the window contains measurements
        Measurement<?> latestMeasurement = window.getLastMeasurement();
        if (latestMeasurement != null) {
            Object lastValue = latestMeasurement.getValue();

            // Verify that the values are arrays of numbers (Number[])
            if (lastValue instanceof Number[]) {
                Number[] values = (Number[]) lastValue;

                // Calculate timestep (average interval between timestamps)
                double totalInterval = 0.0;
                int intervalCount = 0;
                Instant previousTimestamp = null;

                for (Measurement<?> measurement : window.getMeasurementsNewerToOlder()) {
                    if (previousTimestamp != null) {
                        double interval = (previousTimestamp.toEpochMilli() - measurement.getTimestamp().toEpochMilli()) / 1000.0;
                        if (interval > 0) {
                            totalInterval += interval;
                            intervalCount++;
                        }
                    }
                    previousTimestamp = measurement.getTimestamp();
                }

                if (intervalCount == 0) {
                    System.out.println("Insufficient data to calculate sampling rate.");
                }

                double samplingRate = totalInterval / intervalCount;
                double deltaT = 1.0 / samplingRate;

                double inPhase = 0.0;
                double quadrature = 0.0;
                int count = 0;
                for (Measurement<?> measurement : window.getMeasurementsNewerToOlder()) {
                    System.out.println("Measurement: " + measurement);
                    Number[] phaseValues = (Number[]) measurement.getValue();
                    double value = phaseValues[0].doubleValue(); // Get the first phase

                    double t = count * deltaT;
                    inPhase += value * Math.cos(2 * Math.PI * FREQUENCY * t);
                    quadrature += value * Math.sin(2 * Math.PI * FREQUENCY * t);
                    count++;
                }

                // Normalize by the number of samples
                inPhase *= 2.0 / window.getSize();
                quadrature *= 2.0 / window.getSize();

                double amplitude = Math.sqrt(inPhase * inPhase + quadrature * quadrature);
                double phase = Math.atan2(quadrature, inPhase);

                Number[] phasor = new Number[]{amplitude, phase};
                resultWindow.addMeasurement(Instant.now(), phasor);
                System.out.println("Phasor: " + Arrays.toString(phasor));
            } else {
                throw new IllegalArgumentException("The MeasurementWindow does not contain Number[] values.");
            }
        }

        return resultWindow;
    }

}
