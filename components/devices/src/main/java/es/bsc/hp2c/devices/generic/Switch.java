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

import java.util.ArrayList;

import es.bsc.hp2c.devices.types.Actuator;
import es.bsc.hp2c.devices.types.Device;
import es.bsc.hp2c.devices.types.Sensor;

/**
 * This class interacts with a switch of the electrical network.
 */
public abstract class Switch<T> extends Device implements Sensor<T, Switch.State>, Actuator<Switch.State> {

    public enum State {
        ON,
        OFF
    }
    private boolean readFlag;

    private ArrayList<Runnable> onReadFunctions;

    protected State state = State.ON;

    protected Switch(String label, float[] position) {
        super(label, position);
        this.onReadFunctions = new ArrayList<>();
    }

    @Override
    public abstract void sensed(T value);

    public void addOnReadFunction(Runnable action) {
        this.onReadFunctions.add(action);
    }

    public void onRead() {
        for (Runnable action : this.onReadFunctions) {
            action.run();
        }
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
    public abstract void setValue(Switch.State value) throws Exception;

    @Override
    public boolean isActionable() {
        return true;
    }

    @Override
    public final boolean isSensitive() {
        return true;
    }
}
