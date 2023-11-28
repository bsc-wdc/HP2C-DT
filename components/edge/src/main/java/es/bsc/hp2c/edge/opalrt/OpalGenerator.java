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

import es.bsc.hp2c.edge.generic.Generator;
import es.bsc.hp2c.edge.opalrt.OpalComm.OpalSensor;
import es.bsc.hp2c.edge.opalrt.OpalComm.OpalActuator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Represent a switch implemented accessible within a local OpalRT.
 */
public class OpalGenerator extends Generator<Float[]> implements OpalSensor<Float[]>, OpalActuator<Float[]> {

    private int[] indexes;

    /*
     * Creates a new instance of OpalGenerator.
     *
     * @param label device label
     * @param position device position
     * @param properties JSONObject representing device properties
     * */
    public OpalGenerator(String label, float[] position, JSONObject properties) {
        super(label, position);
        JSONArray jIndexes = properties.getJSONArray("indexes");
        this.indexes = new int[jIndexes.length()];
        if (this.indexes.length < 2 ){
            throw new IllegalArgumentException("Generator indexes must be 2 at least: 1 for voltage Setpoint and 1 " +
                    "for powerSetpoint");
        }
        for (int i = 0; i < jIndexes.length(); ++i) {
            this.indexes[i] = (jIndexes.getInt(i));
        }
        OpalComm.registerSensor(this);
        OpalComm.registerActuator(this);
    }

    @Override
    public void sensed(Float[] values) {
        super.setValues(sensedValues(values));
        System.out.println("Device " + getLabel() + " voltage set point is " + this.voltageSetpoint[0] + " V");
        System.out.println("Device " + getLabel() + " power set point is " + this.powerSetpoint[0] + " V");
    }

    @Override
    public void actuate(Float[] raw_values) throws IOException {
        Float[] values = actuateValues(raw_values);
        OpalComm.commitActuation(this, values);
    }

    protected Float[] actuateValues(Float[] values){
        return values;
    }

    @Override
    public int[] getIndexes() {
        return this.indexes;
    }

    @Override
    protected Float[] sensedValues(Float[] input) {
        return input;
    }

}

