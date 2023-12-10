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

package es.bsc.hp2c.edge.opalrt;

import es.bsc.hp2c.edge.generic.Switch;
import es.bsc.hp2c.edge.opalrt.OpalComm.OpalSensor;
import es.bsc.hp2c.edge.opalrt.OpalComm.OpalActuator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import static es.bsc.hp2c.edge.utils.CommUtils.BytesToFloatArray;

/**
 * Represent a switch implemented accessible within a local OpalRT.
 */
public class OpalSwitch extends Switch<Float[]> implements OpalSensor<Switch.State[]>, OpalActuator<Switch.State[]> {

    private int[] indexes;

    /*
     * Creates a new instance of OpalSwitch when the device is declared in the JSON file. If an Opal device is used by
     * the edge, OpalComm.init() initializes ports and ips for communications according to the data in jGlobalProperties.
     *
     * @param label device label
     * @param position device position
     * @param jProperties JSONObject representing device properties
     * @param jGlobalProperties JSONObject representing the global properties of the edge
     * */
    public OpalSwitch(String label, float[] position, JSONObject jProperties, JSONObject jGlobalProperties) {
        super(label, position, jProperties.getJSONArray("indexes").length());
        JSONArray jIndexes = jProperties.getJSONArray("indexes");
        if (jIndexes.length() != 1 && jIndexes.length() != 3){
            throw new IllegalArgumentException("The switch must have either one or three indexes.");
        }
        this.indexes = new int[jIndexes.length()];
        for (int i = 0; i < jIndexes.length(); ++i) {
            this.indexes[i] = (jIndexes.getInt(i));
        }

        String commType = jProperties.getString("comm-type");
        OpalComm.registerSensor(this, commType);
        OpalComm.registerActuator(this);
        OpalComm.init(jGlobalProperties);
    }

    @Override
    public void sensed(Float[] values) {
        setValues(sensedValues(values));
        System.out.println(getLabel() + " states are: ");
        for(int i = 0; i < this.states.length; ++i){
            System.out.println("Switch " + i + " " + this.states[i]);
        }
    }

    @Override
    public void actuate(State[] values) throws IOException {
        Float[] rawValues = actuateValues(values);
        OpalComm.commitActuation(this, rawValues);
    }

    protected Float[] actuateValues(State[] values){
        Float[] outputValues = new Float[values.length];
        for (int i = 0; i < values.length; ++i){
            if (values[i] == State.ON){
                outputValues[i] = 1.0f;
            }
            else {
                outputValues[i] = 0.0f;
            }
        }
        return outputValues;
    }

    @Override
    public int[] getIndexes() {
        return this.indexes;
    }

    @Override
    protected State[] sensedValues(Float[] input) {
        State[] states = new State[input.length];
        // check if the number of input values equals the number of phases
        if (input.length != this.indexes.length) {
            throw new IllegalArgumentException("Input length must be equal to switch's size");
        }
        for (int i = 0; i < input.length; ++i) {
            states[i] = input[i] > 0.5f ? State.ON : State.OFF;
        }
        return states;
    }

    @Override
    public void setValues(State[] values) {
        this.states = values;
        if (this.indexes.length > 1){
            System.out.println("New switch states has been set: ");
            System.out.println("New states are: ");
            for(int i = 0; i < this.states.length; ++i){
                System.out.println("Switch " + i + " " + this.states[i]);
            }
        }
        else{
            System.out.println("New switch state has been set: ");
            System.out.println("New state is " + this.states[0]);
        }
    }

    @Override
    public final State[] decodeValues(byte[] message) {
        Float[] rawValues = BytesToFloatArray(message);
        return sensedValues(rawValues);
    }
}
