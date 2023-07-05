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

package es.bsc.hp2c.devices.generic;

import es.bsc.hp2c.devices.types.Device;
import es.bsc.hp2c.devices.types.Sensor;

/**
 * Sensor measuring the intensity of the network.
 */
public abstract class Ammeter extends Device implements Sensor<Float> {

    private float value = 0.0f;

    private final SensorProcessing[] processors = new SensorProcessing[]{
        new SensorProcessing() {

            @Override
            public void sensed(float... values) {
                Ammeter.this.value = sensedValue(values[0]);
                System.out.println("Sensed "+Ammeter.this.value + "amps");
            }

        }
    };

    protected Ammeter(String label, float[] position){
        super(label, position);
    }
    
    /**
     * Converts a the sensed input to a known value;
     * @param input input value sensed
     * @return corresponding known value
     */
    protected abstract float sensedValue(float input);
    
    @Override
    public final Float getCurrentValue() {
        return this.value;
    }
    
    @Override
    public final SensorProcessing[] getProcessors() {
        return processors;
    }
    
    
    @Override
    public final boolean isActionable() {
        return false;
    }


}
