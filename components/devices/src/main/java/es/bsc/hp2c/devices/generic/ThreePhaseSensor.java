package es.bsc.hp2c.devices.generic;

import es.bsc.hp2c.devices.types.Device;
import es.bsc.hp2c.devices.types.Sensor;

import java.util.ArrayList;

/**
 * ThreePhaseSensor --- Compound abstract class sensor with three inner sensors.
 */
public abstract class ThreePhaseSensor<T, S> extends Device implements Sensor<T, Float[]> {
    private Float[] values = { 0.0f, 0.0f, 0.0f };
    private final int nPhases = 3;
    protected S[] subSensors;
    private ArrayList<Runnable> onReadFunctions;

    protected ThreePhaseSensor(String label, float[] position) {
        super(label, position);
        this.onReadFunctions = new ArrayList<>();
    }

    public void addOnReadFunction(Runnable action) {
        this.onReadFunctions.add(action);
    }

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

    @Override
    public final Float[] getCurrentValues() {
        return this.values;
    }

    public final int getNPhases() {
        return this.nPhases;
    }

    public S[] getSubSensors() {
        return this.subSensors;
    }

    protected void setValues(Float[] values) {
        this.values = values;
    }

    public void setSubSensors(S[] subSensors) {
        this.subSensors = subSensors;
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
