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

package es.bsc.hp2c.common.generic;

import es.bsc.hp2c.common.types.Device;
import es.bsc.hp2c.common.types.Sensor;
import es.bsc.hp2c.common.utils.CommUtils;

/**
 * Represents a smoke sensor belonging to the network.
 */
public abstract class SmokeSensor<R> extends Device implements Sensor<R, SmokeSensor.Smoke> {

    public static enum Smoke {
        NO_SMOKE,
        SMOKE
    }

    private Smoke status = null;

    protected SmokeSensor(String label, float[] position) {
        super(label, position);
    }

    protected abstract Smoke sensedSmoke(float val);

    @Override
    public Smoke getCurrentValues() {
        return this.status;
    }

    public abstract Smoke sensedValues(R value);

    @Override
    public final byte[] encodeValues() {
        Smoke state = this.getCurrentValues();
        Float[] values = new Float[1];
        values[0] = (float) ((state == Smoke.SMOKE) ? 1.0 : 0.0);
        return CommUtils.FloatArrayToBytes(values);
    }

    @Override
    public abstract R decodeValuesRaw(byte[] message);

    @Override
    public boolean isActionable() {
        return false;
    }

    @Override
    public final boolean isSensitive() {
        return true;
    }

    @Override
    public abstract void sensed(R value);

    @Override
    public void sensed(byte[] messageBytes) {
        sensed(decodeValuesRaw(messageBytes));
    }
}
