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

import es.bsc.hp2c.edge.generic.Generator;
import org.json.JSONObject;

import static es.bsc.hp2c.edge.utils.CommUtils.BytesToFloatArray;

/**
 * Digital twin Generator.
 */
public class VirtualGenerator extends Generator<Float[]> {
    /**
     * Creates a new instance of VirtualGenerator.
     *
     * @param label device label
     * @param position device position
     * @param properties JSONObject representing device properties
     * */
    public VirtualGenerator(String label, float[] position, JSONObject properties) {
        super(label, position);
    }

    /**
     * Receive a new measurement and preprocess it for storage as the current
     * device state.
     */
    @Override
    public void sensed(Float[] values) {
        super.setValues(sensedValues(values));
        System.out.println("Device " + getLabel() + " voltage set point is " + values[0] + " V");
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

