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
package es.bsc.hp2c.edge.generic;

import java.io.IOException;
import java.util.ArrayList;

import es.bsc.hp2c.edge.types.Actuator;
import es.bsc.hp2c.edge.types.Device;
import es.bsc.hp2c.edge.types.Sensor;

/**
 * This class interacts with a switch of the electrical network. It has a property (states), representing device's
 * states (switch state defined as ON/OFF)
 */
public abstract class Switch<R> extends Device implements Sensor<R, Switch.State[]>, Actuator<Switch.State[]> {

    public enum State {
        ON,
        OFF
    }

    protected State[] states;

    private ArrayList<Runnable> onReadFunctions;

    /**
     * Creates a new instance of switch;
     *
     * @param label device label
     * @param position device position
     * @param size device number of phases
     */
    protected Switch(String label, float[] position, int size) {
        super(label, position);
        this.onReadFunctions = new ArrayList<>();
        this.states = new State[size];
        for (int i = 0; i < size; ++i){
            this.states[i] = State.ON;
        }
    }

    @Override
    public abstract void sensed(R values);

    @Override
    public abstract void actuate(State[] values) throws IOException;

    /**
     * Adds a runnable to devices "onRead" functions;
     *
     * @param action runnable implementing the action
     */
    public void addOnReadFunction(Runnable action) {
        this.onReadFunctions.add(action);
    }

    /**
     * Calls actions to be performed in case of a new read;
     *
     */
    public void onRead() {
        for (Runnable action : this.onReadFunctions) {
            action.run();
        }
    }

    /**
     * Converts the sensed input to a known value;
     *
     * @param input input value sensed
     * @return corresponding known value
     */
    protected abstract State[] sensedValues(R input);

    protected abstract Float[] actuateValues(State[] values);

    @Override
    public final State[] getCurrentValues() {
        return this.states;
    }

    protected void setValues(State[] values) { this.states = values; }

    @Override
    public boolean isActionable() {
        return true;
    }

    @Override
    public final boolean isSensitive() {
        return true;
    }
}
