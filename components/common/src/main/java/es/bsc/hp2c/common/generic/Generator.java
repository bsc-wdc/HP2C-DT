/*
 *  Copyright 2002-2023 Barcelona Supercomputing Center (www.bsc.es)
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package es.bsc.hp2c.common.generic;

import java.io.IOException;
import java.util.ArrayList;

import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Sensor;
import es.bsc.hp2c.common.utils.CommUtils;


/**
 * Class representing a generator. It has a property (voltageSetPoint) indicating device set point (measured in V).
 */
public abstract class Generator<R> extends Device implements Sensor<R, Float[]>, Actuator<Float[]> {

    protected Float[] voltageSetpoint = null;
    protected Float[] powerSetpoint = null;

    private ArrayList<Runnable> onReadFunctions;

    /**
     * Creates a new instance of generator;
     *
     * @param label device label
     * @param position device position
     */
    protected Generator(String label, float[] position) {
        super(label, position);
        this.onReadFunctions = new ArrayList<>();
    }

    @Override
    public abstract void sensed(R value);

    @Override
    public void sensed(byte[] messageBytes) {
        sensed(decodeValuesRaw(messageBytes));
    }

    @Override
    public abstract void actuate(Float[] value) throws IOException;

    @Override
    public void actuate(byte[] byteValues) throws IOException {
        actuate(decodeValues(byteValues));
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

    /**
     * Converts the sensed input to a human-readable value
     *
     * @param input input value sensed
     * @return human-readable value
     */
    protected abstract Float[] sensedValues(R input);

    /** Converts human-readable action into actionable raw value */
    protected abstract Float[] actuateValues(Float[] values);

    @Override
    public final Float[] getCurrentValues() {
        Float[] combinedValues = new Float[2];
        if (voltageSetpoint == null || powerSetpoint == null) return null;
        combinedValues[0] = this.voltageSetpoint[0];
        combinedValues[1] = this.powerSetpoint[0];
        return combinedValues;
    }

    protected void setValues(Float[] values) {
        if (values.length == 2) {
            voltageSetpoint = new Float[1];
            powerSetpoint = new Float[1];
            voltageSetpoint[0] = values[0];
            powerSetpoint[0] = values[1];
        } else {
            System.err.println("Values length must be 2 (voltageSetpoint and powerSetpoint)");
        }
    }

    @Override
    public final byte[] encodeValues() {
        Float[] values = this.getCurrentValues();
        Float[] rawValues = actuateValues(values);
        return CommUtils.FloatArrayToBytes(rawValues);
    }

    @Override
    public final byte[] encodeValues(Float[] values) {
        Float[] rawValues = actuateValues(values);
        return CommUtils.FloatArrayToBytes(rawValues);
    }

    @Override
    public abstract R decodeValuesRaw(byte[] message);


    @Override
    public boolean isActionable() {
        return true;
    }

    @Override
    public final boolean isSensitive() {
        return true;
    }
}
