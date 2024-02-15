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

import es.bsc.hp2c.common.generic.Generator;
import es.bsc.hp2c.common.generic.Switch;
import es.bsc.hp2c.server.device.VirtualComm.VirtualActuator;
import es.bsc.hp2c.server.device.VirtualComm.VirtualSensor;
import es.bsc.hp2c.common.utils.CommUtils;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Digital twin Generator.
 */
public class VirtualGenerator extends Generator<Float[]> implements VirtualSensor<Float[]>, VirtualActuator<Float[]> {
    private String edgeLabel;

    /**
     * Creates a new instance of VirtualGenerator.
     *
     * @param label device label
     * @param position device position
     * @param properties JSONObject representing device properties
     * @param jGlobalProperties JSONObject representing the global properties of the edge
     * */
    public VirtualGenerator(String label, float[] position, JSONObject properties, JSONObject jGlobalProperties) {
        super(label, position);
        this.edgeLabel = jGlobalProperties.getString("label");
    }

    /**
     * Receive a new measurement and preprocess it for storage as the current
     * device state.
     */
    @Override
    public void sensed(Float[] values) {
        super.setValues(sensedValues(values));
    }

    @Override
    public void actuate(Float[] value) throws IOException {

    }

    @Override
    protected Float[] sensedValues(Float[] input) {
        return input;
    }

    @Override
    public Float[] actuateValues(Float[] values){
        return values;
    }

    @Override
    public final Float[] decodeValuesRaw(byte[] message) {
        return CommUtils.BytesToFloatArray(message);
    }
    @Override
    public final Float[] decodeValues(byte[] message) {
        return CommUtils.BytesToFloatArray(message);
    }

    @Override
    public String getEdgeLabel() {
        return this.edgeLabel;
    }
}

