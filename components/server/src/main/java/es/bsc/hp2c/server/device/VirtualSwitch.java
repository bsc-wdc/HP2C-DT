/**
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

package es.bsc.hp2c.server.device;

import es.bsc.hp2c.common.generic.Switch;
import es.bsc.hp2c.server.device.VirtualComm.VirtualActuator;
import es.bsc.hp2c.server.device.VirtualComm.VirtualSensor;
import es.bsc.hp2c.common.utils.CommUtils;
import org.json.JSONObject;

import java.io.IOException;

import static es.bsc.hp2c.common.utils.CommUtils.printableArray;
import static es.bsc.hp2c.server.device.VirtualComm.virtualActuate;


/**
 * Digital twin Switch.
 */
public class VirtualSwitch extends Switch<Float[]> implements VirtualSensor<Switch.State[]>, VirtualActuator<Switch.State[]> {
    private String edgeLabel;

    /**
     * Creates a new instance of VirtualSwitch.
     *
     * @param label device label
     * @param position device position
     * @param properties JSONObject representing device properties
     * @param jGlobalProperties JSONObject representing the global properties of the edge
     */
    public VirtualSwitch(String label, float[] position, JSONObject properties, JSONObject jGlobalProperties) {
        super(label, position, properties.getJSONArray("indexes").length());
        this.edgeLabel = jGlobalProperties.getString("label");
    }

    /**
     * Receive a new measurement and preprocess it for storage as the current
     * device state.
     */
    @Override
    public void sensed(Float[] values) {
        Float[] sensedValues = new Float[values.length];
        for (int i = 0; i < values.length; i++){
            sensedValues[i] = values[i];
        }
        setValues(sensedValues(sensedValues));
    }

    @Override
    public void actuate(State[] values) throws IOException {
        byte[] byteValues = encodeValuesActuator(values);
        virtualActuate(this, edgeLabel, byteValues);
    }

    @Override
    public void actuate(String[] stringValues) throws IOException{
        State[] values = new State[stringValues.length];
        for (int i = 0; i < stringValues.length; i++) {
            if (isState(stringValues[i])) {
                values[i] = State.valueOf(stringValues[i].toUpperCase());
            } else{
                throw new IOException("Values passed to Switch " +
                        "(" + edgeLabel + "." + getLabel() + ") must be of type State.\n" +
                        "Options are: " + printableArray(State.values()));
            }
        }
        actuate(values);
    }

    /**
     * Preprocess a raw measurement.
     */
    @Override
    protected State[] sensedValues(Float[] input) {
        State[] states = new State[input.length];
        // check if the number of input values equals the number of phases
        if (input.length != this.states.length) {
            throw new IllegalArgumentException("Input length must be equal to switch's size");
        }
        for (int i = 0; i < input.length; ++i) {
            states[i] = input[i] > 0.5f ? State.ON : State.OFF;
        }
        return states;
    }

    @Override
    public Float[] actuatedValues(State[] values) {
        Float[] outputValues = new Float[values.length];
        for (int i = 0; i < values.length; ++i) {
            if (values[i] == State.ON){
                outputValues[i] = 1.0f;
            } else if (values[i] == State.OFF) {
                outputValues[i] = 0.0f;
            } else if (values[i] == State.NULL) {
                outputValues[i] = Float.NEGATIVE_INFINITY;
            } else {
                throw new UnsupportedOperationException("State " + values[i] + " not implemented.");
            }
        }
        return outputValues;
    }

    /**
     * Update current sensor state.
     */
    public void setValues(State[] values) {
        this.states = values;
    }

    @Override
    public final Float[] decodeValuesSensor(byte[] message) {
        return CommUtils.BytesToFloatArray(message);
    }

    @Override
    public final State[] decodeValuesActuator(byte[] message) {
        return sensedValues(CommUtils.BytesToFloatArray(message));
    }

    @Override
    public String getEdgeLabel() {
        return this.edgeLabel;
    }
}
