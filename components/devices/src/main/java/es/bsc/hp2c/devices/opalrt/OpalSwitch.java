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

import es.bsc.hp2c.devices.generic.Switch;
import es.bsc.hp2c.devices.opalrt.OpalReader.OpalSensor;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Represent a switch implemented accessible within a local OpalRT.
 */
public class OpalSwitch extends Switch<Float[]> implements OpalSensor<Switch.State> {

    private int[] indexes;

    public OpalSwitch(String label, float[] position, JSONObject properties) {
        super(label, position);
        JSONArray jIndexes = properties.getJSONArray("indexes");
        this.indexes = new int[jIndexes.length()];
        for (int i = 0; i < jIndexes.length(); ++i) {
            this.indexes[i] = (jIndexes.getInt(i));
        }
        OpalReader.registerDevice(this);
    }

    public OpalSwitch(String label, float[] position, JSONObject properties, int[] indexes) {
        super(label, position);
        this.indexes = indexes;
    }

    @Override
    public void sensed(Float[] values) {
        // setValue(sensedValue(value));
        System.out.println("Switch state is " + this.state);
    }

    @Override
    public int[] getIndexes() {
        return this.indexes;
    }

    @Override
    protected State sensedValue(float input) {
        return input > 0.5f ? State.ON : State.OFF;
    }

    @Override
    public void setValue(State value) {
        switch (value) {
            case ON:
                turnON();
                break;
            case OFF:
                turnOFF();
                break;
        }
    }

    private void turnON() {
        this.state = State.ON;
        System.out.println("Turning switch ON");
    }

    private void turnOFF() {
        this.state = State.OFF;
        System.out.println("Turning switch OFF");
    }

}
