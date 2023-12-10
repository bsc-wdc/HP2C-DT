package es.bsc.hp2c.edge.generic;

import es.bsc.hp2c.edge.types.Device;
import es.bsc.hp2c.edge.types.Sensor;

import java.util.ArrayList;

import static es.bsc.hp2c.edge.utils.CommUtils.FloatArrayToBytes;

/**
 * ThreePhaseSensor --- Compound abstract class sensor with three inner sensors. It has two properties: the number
 * of phases and an array gathering its inner sensors.
 */
public abstract class ThreePhaseSensor<R, S> extends Device implements Sensor<R, Float[]> {
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
    public abstract void sensed(R values);

    /**
     * Converts the sensed input to a known value;
     *
     * @param input input value sensed
     * @return corresponding known value
     */
    protected abstract Float[] sensedValues(R input);

    @Override
    public final byte[] encodeValues() {
        Float[] values = this.getCurrentValues();
        return FloatArrayToBytes(values);
    }

    @Override
    public abstract T decodeValues(byte[] message);

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
