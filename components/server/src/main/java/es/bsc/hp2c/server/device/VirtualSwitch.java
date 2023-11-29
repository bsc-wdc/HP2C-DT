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

import es.bsc.hp2c.edge.generic.Switch;
import org.json.JSONObject;

/**
 * Digital twin Switch.
 */
public class VirtualSwitch extends Switch<Float[]> {
    /**
     * Creates a new instance of VirtualSwitch.
     *
     * @param label device label
     * @param position device position
     * @param properties JSONObject representing device properties
     */
    public VirtualSwitch(String label, float[] position, JSONObject properties) {
        super(label, position, properties.getJSONArray("indexes").length());
    }

    /**
     * Receive a new measurement and preprocess it for storage as the current
     * device state.
     */
    @Override
    public void sensed(Float[] values) {
        float[] sensedValues = new float[values.length];
        for(int i = 0; i < values.length; i++){
            sensedValues[i] = values[i];
            System.out.println("Switch " + i + " " + this.states[i]);
        }
        try {
            setValues(sensedValues(sensedValues));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Preprocess a raw measurement.
     */
    @Override
    protected State[] sensedValues(float[] input) {
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

    /**
     * Update current sensor state.
     */
    public void setValues(State[] values) {
        this.states = values;
    }
}
