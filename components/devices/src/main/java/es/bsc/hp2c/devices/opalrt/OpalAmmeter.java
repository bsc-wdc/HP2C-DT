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

import es.bsc.hp2c.devices.generic.Ammeter;
import es.bsc.hp2c.devices.opalrt.OpalReader.OpalSensor;
import org.json.JSONObject;

/**
 * Ammeter simulated on an Opal-RT.
 */
public class OpalAmmeter extends Ammeter<Float> implements OpalSensor<Float> {

    private final int index;

    public OpalAmmeter(String label, float[] position, JSONObject properties) {
        super(label, position);
        this.index = properties.optInt("index",0);
        OpalReader.registerDevice(this);
    }
    
    @Override
    public int getIndex() {
        return this.index;
    }

    @Override
    public void sensed(Float value) {
        super.setValue(sensedValue(value));
        System.out.println("Sensor " + getLabel() + " sensed " + super.getCurrentValue() + " A");
    }

    @Override
    protected float sensedValue(float input) {
        return input;
    }


}
