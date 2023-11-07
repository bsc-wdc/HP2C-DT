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

package es.bsc.hp2c.devices.generic;

import java.util.ArrayList;

import es.bsc.hp2c.devices.types.Device;
import es.bsc.hp2c.devices.types.Sensor;

/**
 * Sensor measuring the intensity of the network.
 */
public abstract class Wattmeter<T> extends Device implements Sensor<T, Float[]> {

    private Float[] values = { 0.0f };
    private ArrayList<Runnable> onReadFunctions;

    @Override
    public abstract void sensed(T values);

    protected Wattmeter(String label, float[] position) {
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

