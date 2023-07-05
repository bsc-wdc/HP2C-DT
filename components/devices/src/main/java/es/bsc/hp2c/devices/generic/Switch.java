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


import es.bsc.hp2c.devices.types.Actuator;
import es.bsc.hp2c.devices.types.Device;
import es.bsc.hp2c.devices.types.Sensor;

/**
 * This class interacts with a switch of the electrical network.
 */
public abstract class Switch extends Device implements Sensor<Switch.State>, Actuator<Switch.State> {

    public enum State {
        ON,
        OFF
    }

    private State state = State.OFF;

    private final SensorProcessing[] processors = new SensorProcessing[]{
        new SensorProcessing() {

            @Override
            public void sensed(float... values) {
                Switch.this.state = sensedValue(values[0]);

            }
        }
    };

    protected Switch(String label, float[] position) {
        super(label, position);
    }

    @Override
    public SensorProcessing[] getProcessors() {
        return this.processors;
    }

    /**
     * Converts a the sensed input to a known value;
     *
     * @param input input value sensed
     * @return corresponding known value
     */
    protected abstract State sensedValue(float input);

    @Override
    public final State getCurrentValue() {
        return this.state;
    }
    
    @Override
    public boolean isActionable() {
        return true;
    }
}
