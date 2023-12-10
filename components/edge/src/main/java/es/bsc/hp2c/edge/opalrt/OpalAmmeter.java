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

import es.bsc.hp2c.edge.generic.Ammeter;
import es.bsc.hp2c.edge.opalrt.OpalComm.OpalSensor;

import org.json.JSONArray;
import org.json.JSONObject;

import static es.bsc.hp2c.edge.utils.CommUtils.BytesToFloatArray;

/**
 * Ammeter simulated on an Opal-RT.
 */
public class OpalAmmeter extends Ammeter<Float[]> implements OpalSensor<Float[]> {

    private int[] indexes;

    /*
    * Creates a new instance of OpalAmmeter when the device is declared in the JSON file. If an Opal device is used by
    * the edge, OpalComm.init() initializes ports and ips for communications according to the data in jGlobalProperties.
    *
    * @param label device label
    * @param position device position
    * @param jProperties JSONObject representing device properties
    * @param jGlobalProperties JSONObject representing the global properties of the edge
    * */
    public OpalAmmeter(String label, float[] position, JSONObject jProperties, JSONObject jGlobalProperties) {
        super(label, position);
        JSONArray jIndexes = jProperties.getJSONArray("indexes");
        if (jIndexes.length() != 1 && jIndexes.length() != 3){
            throw new IllegalArgumentException("The ammeter must have either one or three indexes.");
        }
        this.indexes = new int[jIndexes.length()];
        for (int i = 0; i < jIndexes.length(); ++i) {
            this.indexes[i] = (jIndexes.getInt(i));
        }

        String commType = jProperties.getString("comm-type");
        OpalComm.registerSensor(this, commType);
        OpalComm.init(jGlobalProperties);
    }

    @Override
    public int[] getIndexes() {
        return this.indexes;
    }

    @Override
    public void sensed(Float[] values) {
        super.setValues(sensedValues(values));
        for (Float value : values) {
            System.out.println("Device " + getLabel() + " sensed " + value + " A");
        }
    }

    @Override
    protected Float[] sensedValues(Float[] input) {
        return input;
    }

    @Override
    public final Float[] decodeValues(byte[] message) {
        return BytesToFloatArray(message);
    }

}
