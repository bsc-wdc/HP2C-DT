package es.bsc.hp2c.common.utils;

import java.util.Arrays;

import static es.bsc.hp2c.common.utils.CommUtils.divideArray;

public class Aggregates {
    public static Measurement<Number[]> sum(MeasurementWindow<?> window){
        Measurement<?> latestMeasurement = window.getLastMeasurement();
        if (latestMeasurement != null) {
            Object lastValue = latestMeasurement.getValue();
            if (lastValue instanceof Number[]) {
                int size = ((Number[]) lastValue).length; //get the size of each measurement
                Number[] sum = new Number[size];
                Arrays.fill(sum, 0);

                for (Measurement m : window.getMeasurementsNewerToOlder()) {
                    Number[] valueArray = (Number[]) m.value;
                    for (int i = 0; i < valueArray.length; i++) {
                        sum[i] = sum[i].doubleValue() + valueArray[i].doubleValue();
                    }
                }
                return new Measurement<>(latestMeasurement.timestamp, sum);
            } else {
                throw new IllegalArgumentException("The MeasurementWindow does not contain Number[] values.");
            }
        } else {
            System.out.println("The MeasurementWindow is empty.");
        }
        return null;
    }

    public static Measurement<Number[]> avg(MeasurementWindow<?> window){
        Measurement<Number[]> sum = sum(window);
        if (sum != null){
            return new Measurement<>(sum.timestamp, divideArray(sum.value, window.getSize()));
        }
        else{ return null; }
    }

    public static Measurement<?>[] all(MeasurementWindow<?> window){
        return window.getMeasurementsNewerToOlder();
    }

    public static Measurement<?> last(MeasurementWindow<?> window){
        return window.getLastMeasurement();
    }
}
