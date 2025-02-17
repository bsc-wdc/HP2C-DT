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

import es.bsc.hp2c.common.generic.Generator;
import es.bsc.hp2c.server.device.VirtualComm.VirtualActuator;
import es.bsc.hp2c.server.device.VirtualComm.VirtualSensor;
import es.bsc.hp2c.common.utils.CommUtils;
import es.bsc.hp2c.server.modules.AmqpManager;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;

import static es.bsc.hp2c.HP2CServerContext.getAmqp;
import static es.bsc.hp2c.common.utils.CommUtils.isNumeric;


/**
 * Digital twin Generator.
 */
public class VirtualGenerator extends Generator<Float[]> implements VirtualSensor<Float[]>, VirtualActuator<Float[]> {
    private final String edgeLabel;
    private final int size;
    private String aggregate;
    private Object units;

    /**
     * Creates a new instance of VirtualGenerator.
     *
     * @param label device label
     * @param position device position
     * @param properties JSONObject representing device properties
     * @param jGlobalProperties JSONObject representing the global properties of the edge
     * */
    public VirtualGenerator(String label, float[] position, JSONObject properties, JSONObject jGlobalProperties) {
        super(label, position, properties, jGlobalProperties);
        this.edgeLabel = jGlobalProperties.getString("label");
        this.size = 2;
        this.aggregate = "";
    }

    /**
     * Receive a new measurement and preprocess it for storage as the current
     * device state.
     */
    @Override
    public void sensed(Float[] values, Instant timestamp) {
        super.setValues(sensedValues(values), timestamp);
    }

    @Override
    public void actuate(Float[] values) throws IOException {
        byte[] byteValues = encodeValuesActuator(values);
        AmqpManager amqp = getAmqp();
        amqp.virtualActuate(this, edgeLabel, byteValues);
    }

    @Override
    public void actuate(String[] stringValues) throws IOException {
        Float[] values = new Float[stringValues.length];
        for (int i = 0; i < stringValues.length; i++) {
            if (stringValues[i].equalsIgnoreCase("null") || stringValues[i].equalsIgnoreCase("none")) {
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
    public int getSize() { return this.size; }

    @Override
    public boolean isCategorical() {
        return false;
    }

    @Override
    public ArrayList<String> getCategories() {
        return null;
    }

    @Override
    public String getAggregate() {
        return this.aggregate;
    }

    @Override
    public void setAggregate(String aggregate) {
        this.aggregate = aggregate;
    }

    @Override
    public void setUnits(Object units){
        this.units = units;
    }

    @Override
    public Object getUnits() {
        return units;
    }

    @Override
    public JSONObject getDataTypes(){
        JSONObject result = new JSONObject();
        JSONObject sensorTypes = new JSONObject();
        sensorTypes.put("human-readable", Float[].class.getTypeName());
        sensorTypes.put("raw", Float[].class.getTypeName());
        result.put("sensor", sensorTypes);
        result.put("actuator", Float[].class.getTypeName());
        return result;
    }
}

