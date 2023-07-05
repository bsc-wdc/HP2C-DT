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
import org.json.JSONObject;

/**
 * Represent a switch implemented accessible within a local OpalRT.
 */
public class OpalSwitch extends Switch implements OpalSensor<Switch.State>{

    private final int index;

    public OpalSwitch(String label, float[] position, JSONObject properties) {
        super(label, position);
        this.index = properties.optInt("index", 0);
        OpalReader.registerDevice(this);
    }

    
    @Override
    public int getIndex() {
        return this.index;
    }

    @Override
    protected State sensedValue(float input) {
        return input > 0.5f ? State.ON : State.OFF;
    }

    @Override
    public void setValue(State value) throws Exception {
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
        System.out.println("Li diem al switch que ha de deixar passar electricitat");
    }

    private void turnOFF() {
        System.out.println("Li diem al switch que NO ha de deixar passar electricitat");
    }

}
