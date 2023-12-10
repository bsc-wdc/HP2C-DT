/**
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

package es.bsc.hp2c.server.device;

import es.bsc.hp2c.edge.generic.Ammeter;
import org.json.JSONObject;

import static es.bsc.hp2c.edge.utils.CommUtils.BytesToFloatArray;

/**
 * Digital Twin Ammeter.
 */
public class VirtualAmmeter extends Ammeter<Float[]> {
    /**
    * Creates a new instance of VirtualAmmeter.
    *
    * @param label device label
    * @param position device position
    * @param properties JSONObject representing device properties
    * */
    public VirtualAmmeter(String label, float[] position, JSONObject properties) {
        super(label, position);
    }

    /**
     * Receive a new measurement and preprocess it for storage as the current
     * device state.
     */
    @Override
    public void sensed(Float[] values) {
        super.setValues(sensedValues(values));
        for (Float value : values) {
            System.out.println("Device " + getLabel() + " sensed " + value + " A");
        }
    }

    /**
     * Preprocess a raw measurement.
     */
    @Override
    protected Float[] sensedValues(Float[] input) {
        return input;
    }

    @Override
    public final Float[] decodeValues(byte[] message) {
        Float[] rawValues = BytesToFloatArray(message);
        return sensedValues(rawValues);
    }
}
