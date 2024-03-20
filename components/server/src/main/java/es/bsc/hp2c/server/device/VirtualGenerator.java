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
import es.bsc.hp2c.server.device.VirtualComm.VirtualActuator;
import es.bsc.hp2c.server.device.VirtualComm.VirtualSensor;
import es.bsc.hp2c.common.utils.CommUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

import static es.bsc.hp2c.common.utils.CommUtils.isNumeric;
import static es.bsc.hp2c.server.modules.AmqpManager.virtualActuate;

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
    public void actuate(Float[] values) throws IOException {
        byte[] byteValues = encodeValuesActuator(values);
        virtualActuate(this, edgeLabel, byteValues);
    }

    @Override
    public void actuate(String[] stringValues) throws IOException {
        Float[] values = new Float[stringValues.length];
        for (int i = 0; i < stringValues.length; i++) {
            if (stringValues[i].toLowerCase().equals("null") || stringValues[i].toLowerCase().equals("none")) {
                values[i] = Float.NEGATIVE_INFINITY;
            } else if (isNumeric(stringValues[i])){
                values[i] = Float.parseFloat(stringValues[i]);
            } else {
                throw new IOException("Values passed to Generator " +
                        "(" + edgeLabel + "." + getLabel() + ") must be numeric or null/none.");
            }
        }
        actuate(values);
    }

    @Override
    protected Float[] sensedValues(Float[] input) {
        return input;
    }

    @Override
    public Float[] actuatedValues(Float[] values){
        return values;
    }

    @Override
    public final Float[] decodeValuesSensor(byte[] message) {
        return CommUtils.BytesToFloatArray(message);
    }
    @Override
    public final Float[] decodeValuesActuator(byte[] message) {
        return CommUtils.BytesToFloatArray(message);
    }

    @Override
    public String getEdgeLabel() {
        return this.edgeLabel;
    }

    @Override
    public boolean isCategoric() {
        return false;
    }

    @Override
    public ArrayList<String> getCategories() {
        return null;
    }
}

