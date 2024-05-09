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

import es.bsc.hp2c.common.generic.Ammeter;
import es.bsc.hp2c.server.device.VirtualComm.VirtualSensor;
import es.bsc.hp2c.common.utils.CommUtils;
import org.json.JSONObject;

/**
 * Digital Twin Ammeter.
 */
public class VirtualAmmeter extends Ammeter<Float[]> implements VirtualSensor<Float[]> {
    private final String edgeLabel;
    private final int size;

    /**
    * Creates a new instance of VirtualAmmeter.
    *
    * @param label device label
    * @param position device position
    * @param properties JSONObject representing device properties
    * @param jGlobalProperties JSONObject representing the global properties of the edge
    * */
    public VirtualAmmeter(String label, float[] position, JSONObject properties, JSONObject jGlobalProperties) {
        super(label, position);
        this.edgeLabel = jGlobalProperties.getString("label");
        this.size = properties.getJSONArray("indexes").length();
    }

    /**
     * Receive a new measurement and preprocess it for storage as the current
     * device state.
     */
    @Override
    public void sensed(Float[] values) {
        super.setValues(sensedValues(values));
    }

    /**
     * Preprocess a raw measurement.
     */
    @Override
    protected Float[] sensedValues(Float[] input) {
        return input;
    }

    @Override
    public final Float[] decodeValuesSensor(byte[] message) {
        return CommUtils.BytesToFloatArray(message);
    }

    @Override
    public String getEdgeLabel() {
        return this.edgeLabel;
    }

    @Override
    public int getSize() {
        return this.size;
    }
}
