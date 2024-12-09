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
}
