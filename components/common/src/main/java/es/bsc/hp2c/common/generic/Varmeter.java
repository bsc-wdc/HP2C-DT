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

import java.time.Instant;
import java.util.ArrayList;

import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Sensor;
import es.bsc.hp2c.common.utils.CommUtils;
import es.bsc.hp2c.common.utils.FileUtils;
import es.bsc.hp2c.common.utils.MeasurementWindow;
import org.json.JSONObject;

/**
 * Sensor measuring the reactive power of the network. It has a property (values) measured in VAR (volt-ampere reactive)
 */
public abstract class Varmeter<R> extends Device implements Sensor<R, Float[]> {

    private Float[] values = null;
    private MeasurementWindow<Float[]> window;
    private ArrayList<Runnable> onReadFunctions;

    @Override
    public abstract void sensed(R values);

    @Override
    public void sensed(byte[] messageBytes) {
        sensed(decodeValuesSensor(messageBytes));
    }

    /**
     * Creates a new instance of varmeter;
     *
     * @param label device label
     * @param position device position
     */
    protected Varmeter(String label, float[] position, JSONObject jProperties, JSONObject jGlobalProperties) {
        super(label, position);
        window = new MeasurementWindow(FileUtils.getWindowSize(jProperties, jGlobalProperties, label));
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

    /**
     * Converts the sensed input to a human-readable value
     *
     * @param input input value sensed
     * @return human-readable value
     */
    protected abstract Float[] sensedValues(R input);

    @Override
    public final Float[] getCurrentValues() { return this.values; }

    protected void setValues(Float[] values) {
        this.values = values;
        this.window.addMeasurement(Instant.now(), values);
        this.setLastUpdate();
    }

    @Override
    public final byte[] encodeValuesSensor() {
        Float[] values = this.getCurrentValues();
        return CommUtils.FloatArrayToBytes(values);
    }

    @Override
    public abstract R decodeValuesSensor(byte[] message);

    @Override
    public final boolean isActionable() {
        return false;
    }

    @Override
    public final boolean isSensitive() {
        return true;
    }

}
