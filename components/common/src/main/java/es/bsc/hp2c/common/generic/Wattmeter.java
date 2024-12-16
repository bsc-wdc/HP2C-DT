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

import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Sensor;
import es.bsc.hp2c.common.utils.*;
import org.json.JSONObject;

/**
 * Sensor measuring the power of the network. It a has property (values) measured in Watts.
 */
public abstract class Wattmeter<R> extends Device implements Sensor<R, Float[]> {

    private Float[] values = null;
    private MeasurementWindow<Float[]> window;
    private OnReadFunctions onReadFunctions;

    @Override
    public abstract void sensed(R values);

    @Override
    public MeasurementWindow<Float[]> sensed(byte[] bWindow) {
        MeasurementWindow<Float[]> window = MeasurementWindow.decode(bWindow);
        MeasurementWindow<Float[]> returnWindow = new MeasurementWindow<>(window.getCapacity());
        for (Measurement<Float[]> m : window.getMeasurementsOlderToNewer()){
            Object value = m.getValue();
            if (value instanceof Number[]) {
                Number[] numbers = (Number[]) value;
                Float[] floats = new Float[numbers.length];
                for (int i = 0; i < numbers.length; i++) {
                    floats[i] = numbers[i] == null ? null : numbers[i].floatValue();
                }
                sensed((R) floats);
                returnWindow.addMeasurement(m.getTimestamp(), floats);
            } else {
                throw new IllegalArgumentException("Expected Number[], got: " + value.getClass());
            }
        }
        return returnWindow;
    }

    /**
     * Creates a new instance of wattmeter;
     *
     * @param label device label
     * @param position device position
     */
    protected Wattmeter(String label, float[] position, JSONObject jProperties, JSONObject jGlobalProperties) {
        super(label, position);
        window = new MeasurementWindow<Float[]>(FileUtils.getWindowSize(jProperties, jGlobalProperties, label));
        this.onReadFunctions = new OnReadFunctions();
    }

    /**
     * Adds a runnable to devices "onRead" functions;
     *
     * @param action runnable implementing the action
     */
    @Override
    public void addOnReadFunction(Runnable action, int interval, String label, boolean onRead) {
        this.onReadFunctions.addFunc(new OnReadFunction<Float[]>(action, interval, label, onRead));
    }

    /**
     * Calls actions to be performed in case of a new read;
     *
     */
    public void onRead() {
        for (OnReadFunction orf : this.onReadFunctions.getOnReadFuncs()) {
            if (orf.isOnChange()) {
                if (orf.changed(this.getCurrentValues())){ //changed() will update its last value if needed
                    orf.getRunnable().run();
                }
            } else {
                if (orf.getCounter() == orf.getInterval()) {
                    orf.getRunnable().run();
                    orf.resetCounter();
                } else {
                    orf.incrementCounter();
                }
            }
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

    public MeasurementWindow<Float[]> getWindow(){
        return this.window;
    }

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

