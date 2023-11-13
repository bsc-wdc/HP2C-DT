package es.bsc.hp2c.edges.generic;

import es.bsc.hp2c.edges.types.Device;
import es.bsc.hp2c.edges.types.Sensor;

import java.util.ArrayList;

/**
 * ThreePhaseSensor --- Compound abstract class sensor with three inner sensors. It has two properties: the number
 * of phases and an array gathering its inner sensors.
 */
public abstract class ThreePhaseSensor<T, S> extends Device implements Sensor<T, Float[]> {
    private final int nPhases = 3;
    protected S[] subSensors;
    private ArrayList<Runnable> onReadFunctions;

    /**
     * Creates a new instance of Three Phase Sensor;
     *
     * @param label device label
     * @param position device position
     */
    protected ThreePhaseSensor(String label, float[] position) {
        super(label, position);
        this.onReadFunctions = new ArrayList<>();
    }

    /**
     * Adds a runnable to devices "onRead" functions;
     *
     * @param action runnable implementing the action
     */
    public void addOnReadFunction(Runnable action) {
        this.onReadFunctions.add(action);
    }

    /**
     * Calls actions to be performed in case of a new read
     */
    public void onRead() {
        for (Runnable action : this.onReadFunctions) {
            action.run();
        }
    }

    @Override
    public abstract void sensed(T values);

    /**
     * Converts the sensed input to a known value;
     *
     * @param input input value sensed
     * @return corresponding known value
     */
    protected abstract Float[] sensedValues(Float[] input);

    public final int getNPhases() {
        return this.nPhases;
    }

    @Override
    public final boolean isActionable() {
        return false;
    }

    @Override
    public final boolean isSensitive() {
        return true;
    }
}
