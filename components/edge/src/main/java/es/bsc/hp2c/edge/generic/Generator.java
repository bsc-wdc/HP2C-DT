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
package es.bsc.hp2c.edge.generic;

import java.util.ArrayList;

import es.bsc.hp2c.edge.types.Actuator;
import es.bsc.hp2c.edge.types.Device;
import es.bsc.hp2c.edge.types.Sensor;

/**
 * Class representing a generator. It has a property (voltageSetPoint) indicating device set point (measured in V).
 */
public abstract class Generator<T> extends Device implements Sensor<T, Float[]>, Actuator<Float[]> {

    public Float[] voltageSetpoint = { 0.0f };

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
    public abstract void sensed(T value);

    @Override
    public abstract void actuate(Float[] value, int[] indexes);

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
     * Converts the sensed input to a known value;
     *
     * @param input input value sensed
     * @return corresponding known value
     */
    protected abstract Float[] sensedValues(Float[] input);

    @Override
    public final Float[] getCurrentValues() {
        return this.voltageSetpoint;
    }

    public void setValues(Float[] values) {
        this.voltageSetpoint = values;
    }

    @Override
    public boolean isActionable() {
        return true;
    }

    @Override
    public final boolean isSensitive() {
        return true;
    }
}
