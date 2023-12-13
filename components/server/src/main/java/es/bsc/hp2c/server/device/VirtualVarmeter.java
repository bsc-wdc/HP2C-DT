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

package es.bsc.hp2c.server.device;

import es.bsc.hp2c.edge.generic.Varmeter;
import org.json.JSONObject;

import static es.bsc.hp2c.edge.utils.CommUtils.BytesToFloatArray;

/**
 * Digital twin Varmeter.
 */
public class VirtualVarmeter extends Varmeter<Float[]> {
    /**
     * Creates a new instance of VirtualVarmeter.
     *
     * @param label device label
     * @param position device position
     * @param properties JSONObject representing device properties
     * @param jGlobalProperties JSONObject representing the global properties of the edge
     */
    public VirtualVarmeter(String label, float[] position, JSONObject properties, JSONObject jGlobalProperties) {
        super(label, position);
    }

    @Override
    public void sensed(Float[] values) {
        super.setValues(sensedValues(values));
        System.out.println("Device " + getLabel() + " sensed " + values[0] + " VAR");
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
