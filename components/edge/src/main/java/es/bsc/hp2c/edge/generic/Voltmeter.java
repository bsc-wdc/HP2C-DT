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

import es.bsc.hp2c.edge.types.Device;
import es.bsc.hp2c.edge.types.Sensor;

/**
 * Sensor measuring the voltage of the network. It has only one property representing devices current voltage
 */
public abstract class Voltmeter<T> extends Device implements Sensor<T, Float[]> {

    private Float[] values = { 0.0f };
    private ArrayList<Runnable> onReadFunctions;

    /**
     * Creates a new instance of voltmeter;
     *
     * @param label device label
     * @param position device position
     */
    protected Voltmeter(String label, float[] position) {
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
    public abstract void sensed(T value);

    /**
     * Converts the sensed input to a known value;
     *
     * @param input input value sensed
     * @return corresponding known value
     */
    protected abstract Float[] sensedValues(T input);

    @Override
    public final Float[] getCurrentValues() {
        return this.values;
    }

    protected void setValues(Float[] values) {
        this.values = values;
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
