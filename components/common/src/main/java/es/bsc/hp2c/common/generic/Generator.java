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
import java.time.Instant;

import es.bsc.hp2c.common.funcs.Action;
import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Sensor;
import es.bsc.hp2c.common.utils.*;
import org.json.JSONObject;


/**
 * Class representing a generator. It has a property (voltageSetPoint) indicating device set point (measured in V).
 */
public abstract class Generator<R> extends Device implements Sensor<R, Float[]>, Actuator<Float[]> {

    protected Float[] voltageSetpoint = null;
    protected Float[] powerSetpoint = null;
    private MeasurementWindow<Float[]> window;
    private OnReadFunctions onReadFunctions;

    /**
     * Creates a new instance of generator;
     *
     * @param label device label
     * @param position device position
     */
    protected Generator(String label, float[] position, JSONObject jProperties, JSONObject jGlobalProperties) {
        super(label, position);
        window = new MeasurementWindow(FileUtils.getWindowSize(jProperties, jGlobalProperties, label));
        this.onReadFunctions = new OnReadFunctions();
    }

    @Override
    public abstract void sensed(R value, Instant timestamp);

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
                sensed((R) floats, m.getTimestamp());
                returnWindow.addMeasurement(m.getTimestamp(), floats);
            } else {
                throw new IllegalArgumentException("Expected Number[], got: " + value.getClass());
            }
        }
        return returnWindow;
    }

    @Override
    public abstract void actuate(Float[] value) throws IOException;

    @Override
    public void actuate(byte[] byteValues) throws IOException {
        actuate(decodeValuesActuator(byteValues));
    }

    /**
     * Adds a runnable to devices "onRead" functions;
     *
     * @param action runnable implementing the action
     */
    @Override
    public void addOnReadFunction(Action action, int interval, String label, boolean onRead) {
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
                    orf.getAction().run();
                }
            } else {
                if (orf.getCounter() == orf.getInterval()) {
                    orf.getAction().run();
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

    /** Converts human-readable action into actionable raw value */
    protected abstract Float[] actuatedValues(Float[] values);

    @Override
    public final Float[] getCurrentValues() {
        Float[] combinedValues = new Float[2];
        if (voltageSetpoint == null || powerSetpoint == null) return null;
        combinedValues[0] = this.voltageSetpoint[0];
        combinedValues[1] = this.powerSetpoint[0];
        return combinedValues;
    }

    public MeasurementWindow<Float[]> getWindow(){
        return this.window;
    }

    protected void setValues(Float[] values, Instant timestamp) {
        if (values.length == 2) {
            voltageSetpoint = new Float[1];
            powerSetpoint = new Float[1];
            voltageSetpoint[0] = values[0];
            powerSetpoint[0] = values[1];
            this.window.addMeasurement(timestamp, values);
        } else {
            System.err.println("Values length must be 2 (voltageSetpoint and powerSetpoint)");
        }
    }

    @Override
    public final byte[] encodeValuesSensor() {
        Float[] values = this.getCurrentValues();
        Float[] rawValues = actuatedValues(values);
        return CommUtils.FloatArrayToBytes(rawValues);
    }

    @Override
    public final byte[] encodeValuesActuator(Float[] values) {
        Float[] rawValues = actuatedValues(values);
        return CommUtils.FloatArrayToBytes(rawValues);
    }

    @Override
    public abstract R decodeValuesSensor(byte[] message);

    @Override
    public boolean isActionable() {
        return true;
    }

    @Override
    public final boolean isSensitive() {
        return true;
    }

    @Override
    public JSONObject getDeviceInfo(){
        JSONObject result = new JSONObject();
        int size = this.getSize();
        String className = this.getClass().getSimpleName();
        JSONObject types = getDataTypes();
        result.put("size", size);
        result.put("class-name", className);
        result.put("measurements", this.window.getMeasurementsArray());
        result.put("types", types);
        return result;
    }
}
