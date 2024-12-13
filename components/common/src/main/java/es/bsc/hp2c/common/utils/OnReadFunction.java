package es.bsc.hp2c.common.utils;

/**
 * Class collecting information from each onReadFunction (the method itself, the interval, the counter and its
 * identifier)
 *
 */
public class OnReadFunction {
    private Runnable runnable;
    private int interval; // Number of reads needed for each execution
    private int counter; // Current number of reads
    private String label;

    public OnReadFunction(Runnable runnable, int interval, String label) {
        this.runnable = runnable;
        this.interval = interval;
        this.counter = 0;
        this.label = label;
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

    public String getLabel(){
        return this.label;
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