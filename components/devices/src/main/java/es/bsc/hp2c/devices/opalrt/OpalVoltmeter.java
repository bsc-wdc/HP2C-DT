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
package es.bsc.hp2c.devices.opalrt;

import es.bsc.hp2c.devices.generic.Voltmeter;
import es.bsc.hp2c.devices.opalrt.OpalReader.OpalSensor;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Voltmeter simulated on an Opal-RT.
 */
public class OpalVoltmeter extends Voltmeter<Float[]> implements OpalSensor<Float[]> {

    private int[] indexes;

    /*
     * Creates a new instance of OpalVoltmeter. Useful when the device is declared in a JSON file.
     *
     * @param label device label
     * @param position device position
     * @param properties JSONObject representing device properties
     * */
    public OpalVoltmeter(String label, float[] position, JSONObject properties) {
        super(label, position);
        JSONArray jIndexes = properties.getJSONArray("indexes");
        this.indexes = new int[jIndexes.length()];
        for (int i = 0; i < jIndexes.length(); ++i) {
            this.indexes[i] = (jIndexes.getInt(i));
        }
        OpalReader.registerDevice(this);
    }

    /*
     * Creates a new instance of OpalAmmeter. Useful when the device is declared by a three-phase voltmeter.
     *
     * @param label device label
     * @param position device position
     * @param indexes assigned
     * */
    public OpalVoltmeter(String label, float[] position, int[] indexes) {
        super(label, position);
        this.indexes = indexes;
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
        System.out.println("Sensor " + getLabel() + " sensed " + values[0] + " V");
    }

}
