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

/**
 * Ammeter simulated on an Opal-RT.
 */
public class OpalAmmeter extends Ammeter<Float[]> implements OpalSensor<Float[]> {

    private int[] indexes;

    /*
    * Creates a new instance of OpalAmmeter. Useful when the device is declared in a JSON file.
    *
    * @param label device label
    * @param position device position
    * @param properties JSONObject representing device properties
    * */
    public OpalAmmeter(String label, float[] position, JSONObject properties) {
        super(label, position);
        JSONArray jIndexes = properties.getJSONArray("indexes");
        this.indexes = new int[jIndexes.length()];
        for (int i = 0; i < jIndexes.length(); ++i) {
            this.indexes[i] = (jIndexes.getInt(i));
        }
        OpalComm.registerSensor(this);
    }

    /*
     * Creates a new instance of OpalAmmeter. Useful when the device is declared by a three-phase voltmeter.
     *
     * @param label device label
     * @param position device position
     * @param indexes assigned
     * */
    public OpalAmmeter(String label, float[] position, int[] indexes) {
        super(label, position);
        this.indexes = indexes;
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

}
