package es.bsc.hp2c.common.utils;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Objects;

/**
 * Class collecting information from each onReadFunction (the method itself, the interval, the counter and its
 * identifier)
 *
 */
public class OnReadFunction<T> {
    private Runnable runnable;
    private int interval; // Number of reads needed for each execution
    private int counter; // Current number of reads
    private String label;
    private T last;
    private boolean onRead;

    public OnReadFunction(Runnable runnable, int interval, String label, boolean onRead) {
        this.runnable = runnable;
        this.interval = interval;
        this.counter = 0;
        this.label = label;
        this.onRead = onRead;
    }

    public Runnable getRunnable() {
        return runnable;
    }

    public boolean isOnChange(){ return !this.onRead; }

    public int getInterval() {
        return interval;
    }

    public int getCounter() {
        return counter;
    }

    public String getLabel(){
        return this.label;
    }

    public void resetCounter() {
        this.counter = 1;
    }

    public void incrementCounter() {
        this.counter += 1;
    }

    public boolean changed(T value) {
        if (value instanceof Object[] && last instanceof Object[]) {
            Object[] newArray = (Object[]) value;
            Object[] lastArray = (Object[]) last;

            if (!Arrays.equals(newArray, lastArray)) {
                last = value;
                return true;
            }
        } else if (!Objects.equals(value, last)) {
            last = value;
            return true;
        }
        return false;
    }


    @Override
    public String toString() {
        return "OnReadFunction{" +
                "runnable=" + runnable +
                ", interval=" + interval +
                ", counter=" + counter +
                '}';
    }

}