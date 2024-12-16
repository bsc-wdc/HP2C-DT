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

import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Sensor;
import es.bsc.hp2c.common.utils.*;
import org.json.JSONObject;

/**
 * This class interacts with a switch of the electrical network. It has a property (states), representing device's
 * states (switch state defined as ON/OFF)
 */
public abstract class Switch<R> extends Device implements Sensor<R, Switch.State[]>, Actuator<Switch.State[]> {

    public enum State {
        ON,
        OFF,
        NULL;

        @Override
        public String toString() {
            switch (this) {
                case ON:
                    return "ON";
                case OFF:
                    return "OFF";
                case NULL:
                    return "NULL";
                default:
                    return super.toString();
            }
        }
    }

    protected State[] states;

    private MeasurementWindow<State[]> window;

    private OnReadFunctions onReadFunctions;

    /**
     * Creates a new instance of switch;
     *
     * @param label device label
     * @param position device position
     * @param size device number of phases
     */
    protected Switch(String label, float[] position, int size, JSONObject jProperties, JSONObject jGlobalProperties) {
        super(label, position);
        window = new MeasurementWindow(FileUtils.getWindowSize(jProperties, jGlobalProperties, label));
        this.onReadFunctions = new OnReadFunctions();
        this.states = new State[size];
        for (int i = 0; i < size; ++i){
            this.states[i] = null;
        }
    }

    /** Check if a String can be parsed into one of the enum states */
    protected boolean isState(String str) {
        for (State state : State.values()) {
            if (state.name().equals(str.toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public abstract void sensed(R values);

    @Override
    public MeasurementWindow<Float[]> sensed(byte[] bWindow) {
        MeasurementWindow<State[]> window = MeasurementWindow.decode(bWindow);
        MeasurementWindow<Float[]> returnWindow = new MeasurementWindow<>(window.getCapacity());
        for (Measurement<State[]> m : window.getMeasurementsOlderToNewer()){
            Float[] value = actuatedValues(m.getValue());
            sensed((R) value);
            returnWindow.addMeasurement(m.getTimestamp(), value);
        }
        return returnWindow;
    }

    @Override
    public abstract void actuate(State[] values) throws IOException;

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
    public void addOnReadFunction(Runnable action, int interval, String label, boolean onRead) {
        this.onReadFunctions.addFunc(new OnReadFunction<State[]>(action, interval, label, onRead));
    }

    /**
     * Calls actions to be performed in case of a new read;
     *
     */
    public void onRead() {
        for (OnReadFunction orf : this.onReadFunctions.getOnReadFuncs()) {
            if (orf.isOnRead()) {
                if (orf.getCounter() == orf.getInterval()) {
                    orf.getRunnable().run();
                    orf.resetCounter();
                } else {
                    orf.incrementCounter();
                }
            } else {
                if (orf.changed(this.getCurrentValues())){ //changed() will update its last value if needed
                    orf.getRunnable().run();
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
    protected abstract State[] sensedValues(R input);

    /** Converts human-readable action into actionable raw value */
    protected abstract Float[] actuatedValues(State[] values);

    @Override
    public final State[] getCurrentValues() { return this.states; }

    public MeasurementWindow<State[]> getWindow(){
        return this.window;
    }

    protected void setValues(State[] values) {
        this.states = values;
        this.window.addMeasurement(Instant.now(), values);
        this.setLastUpdate();
    }
    
    @Override
    public final byte[] encodeValuesSensor() {
        State[] state = this.getCurrentValues();
        Float[] values = actuatedValues(state);
        return CommUtils.FloatArrayToBytes(values);
    }
    @Override
    public final byte[] encodeValuesActuator(State[] values) {
        Float[] rawValues = actuatedValues(values);
        return CommUtils.FloatArrayToBytes(rawValues);
    }

    @Override
    public abstract R decodeValuesSensor(byte[] message);

    @Override
    public abstract State[] decodeValuesActuator(byte[] message);

    @Override
    public boolean isActionable() {
        return true;
    }

    @Override
    public final boolean isSensitive() {
        return true;
    }
}
