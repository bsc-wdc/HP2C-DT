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

import java.util.ArrayList;

import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Sensor;
import es.bsc.hp2c.common.utils.CommUtils;

/**
 * Sensor measuring the power of the network. It a has property (values) measured in Watts.
 */
public abstract class Wattmeter<R> extends Device implements Sensor<R, Float[]> {

    private Float[] values = { 0.0f };
    private ArrayList<Runnable> onReadFunctions;

    @Override
    public abstract void sensed(R values);

    @Override
    public void sensed(byte[] messageBytes) { sensed(decodeValues(messageBytes)); }

    /**
     * Creates a new instance of wattmeter;
     *
     * @param label device label
     * @param position device position
     */
    protected Wattmeter(String label, float[] position) {
        super(label, position);
        this.onReadFunctions = new ArrayList<>();
    }

    /**
     * Adds a runnable to devices "onRead" functions;
     *
     * @param action runnable implementing the action
     */
    public void addOnReadFunction(Runnable action) { this.onReadFunctions.add(action); }

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
    protected abstract Float[] sensedValues(R input);

    @Override
    public final Float[] getCurrentValues() { return this.values; }

    protected void setValues(Float[] values) {
        this.values = values;
        this.setLastUpdate();
    }

    @Override
    public final byte[] encodeValues() {
        Float[] values = this.getCurrentValues();
        return CommUtils.FloatArrayToBytes(values);
    }

    @Override
    public abstract R decodeValues(byte[] message);

    @Override
    public final boolean isActionable() {
        return false;
    }

    @Override
    public final boolean isSensitive() {
        return true;
    }

}

