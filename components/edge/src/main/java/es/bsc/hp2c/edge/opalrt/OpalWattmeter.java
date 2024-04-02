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

import es.bsc.hp2c.common.generic.Wattmeter;
import es.bsc.hp2c.edge.opalrt.OpalComm.OpalSensor;

import org.json.JSONArray;
import org.json.JSONObject;

import static es.bsc.hp2c.common.utils.CommUtils.BytesToFloatArray;

/**
 * Voltmeter simulated on an Opal-RT.
 */
public class OpalWattmeter extends Wattmeter<Float[]> implements OpalSensor<Float[]> {

    private int[] indexes;

    /*
     * Creates a new instance of OpalWattmeter when the device is declared in the JSON file. If an Opal device is used by
     * the edge, OpalComm.init() initializes ports and ips for communications according to the data in jGlobalProperties.
     *
     * @param label device label
     * @param position device position
     * @param jProperties JSONObject representing device properties
     * @param jGlobalProperties JSONObject representing the global properties of the edge
     * */
    public OpalWattmeter(String label, float[] position, JSONObject jProperties, JSONObject jGlobalProperties) {
        super(label, position);
        JSONArray jIndexes = jProperties.getJSONArray("indexes");
        if (jIndexes.length() != 1){
            throw new IllegalArgumentException("The wattmeter must have one index.");
        }
        this.indexes = new int[jIndexes.length()];
        for (int i = 0; i < jIndexes.length(); ++i) {
            this.indexes[i] = (jIndexes.getInt(i));
        }

        if (jGlobalProperties.getBoolean("executeOpalComm")) {
            String commType = jProperties.getString("comm-type");
            OpalComm.registerSensor(this, commType);
            OpalComm.init(jGlobalProperties);
        }
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
    public void sensed(Float[] values) {
        super.setValues(sensedValues(values));
        for (Float value : values){
            System.out.println("Device " + getLabel() + " sensed " + value + " W");
        }
    }

    @Override
    public final Float[] decodeValuesSensor(byte[] message) {
        return BytesToFloatArray(message);
    }
}
