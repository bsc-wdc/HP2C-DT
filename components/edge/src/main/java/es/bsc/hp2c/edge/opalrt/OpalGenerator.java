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

import es.bsc.hp2c.common.generic.Generator;
import es.bsc.hp2c.edge.opalrt.OpalComm.OpalSensor;
import es.bsc.hp2c.edge.opalrt.OpalComm.OpalActuator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import static es.bsc.hp2c.common.utils.CommUtils.BytesToFloatArray;

/**
 * Represent a switch implemented accessible within a local OpalRT.
 */
public class OpalGenerator extends Generator<Float[]> implements OpalSensor<Float[]>, OpalActuator<Float[]> {

    private int[] indexes;

    /*
     * Creates a new instance of opalGenerator when the device is declared in the JSON file. If an Opal device is used by
     * the edge, OpalComm.init() initializes ports and ips for communications according to the data in jGlobalProperties.
     *
     * @param label device label
     * @param position device position
     * @param jProperties JSONObject representing device properties
     * @param jGlobalProperties JSONObject representing the global properties of the edge
     * */
    public OpalGenerator(String label, float[] position, JSONObject jProperties, JSONObject jGlobalProperties) {
        super(label, position);
        JSONArray jIndexes = jProperties.getJSONArray("indexes");
        this.indexes = new int[jIndexes.length()];
        if (this.indexes.length != 2){
            throw new IllegalArgumentException("Generator indexes must be 2: 1 for voltageSetpoint and 1 " +
                    "for powerSetpoint");
        }
        for (int i = 0; i < jIndexes.length(); ++i) {
            this.indexes[i] = (jIndexes.getInt(i));
        }

        String commType = jProperties.getString("comm-type");
        if (jGlobalProperties.getBoolean("executeOpalComm")) {
            OpalComm.registerSensor(this, commType);
            OpalComm.registerActuator(this);
            OpalComm.init(jGlobalProperties);
        }
    }

    @Override
    public void sensed(Float[] values) {
        super.setValues(sensedValues(values));
        System.out.println("Device " + getLabel() + " voltage set point is " + this.voltageSetpoint[0] + " V");
        System.out.println("Device " + getLabel() + " power set point is " + this.powerSetpoint[0] + " W");
    }

    @Override
    public void actuate(Float[] values) throws IOException {
        // Check length of input values
        if (values.length != this.indexes.length) {
            throw new IOException("OpalGenerator.actuate: Wrong input length " +
                    "(actual: " + values.length + ", expected: " + this.indexes.length + ").");
        }
        // Actuate
        Float[] rawValues = actuatedValues(values);
        OpalComm.commitActuation(this, rawValues);
    }

    protected Float[] actuatedValues(Float[] values){
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

    @Override
    public final Float[] decodeValuesSensor(byte[] message) {
        return BytesToFloatArray(message);
    }

    @Override
    public final Float[] decodeValuesActuator(byte[] message) {
        return BytesToFloatArray(message);
    }
}

