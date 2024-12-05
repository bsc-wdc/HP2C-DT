package es.bsc.hp2c.common.utils;

public class OnReadFunction {

    private Runnable runnable;
    private int interval;
    private int counter;
    public OnReadFunction(Runnable runnable, int interval) {
        this.runnable = runnable;
        this.interval = interval;
        this.counter = 0;
    }

    public Runnable getRunnable() {
        return runnable;
    }
    public int getInterval() {
        return interval;
    }

    public int getCounter() {
        return counter;
    }

    public void resetCounter() {
        this.counter = 0;
    }

    public void incrementCounter() {
        this.counter += 1;
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