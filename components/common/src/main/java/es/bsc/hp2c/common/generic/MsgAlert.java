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

import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Device;
import org.json.JSONObject;

import java.io.IOException;

/**
 * This class interacts with a switch of the electrical network.
 */
public abstract class MsgAlert extends Device implements Actuator<String> {

    protected MsgAlert(String label, float[] position) {
        super(label, position);
    }

    @Override
    public final boolean isActionable() {
        return true;
    }

    @Override
    public final boolean isSensitive() {
        return false;
    }

    @Override
    public abstract void actuate(String values) throws IOException;

    @Override
    public final byte[] encodeValuesActuator(String values) {
        return values.getBytes();
    }

    @Override
    public JSONObject getDeviceInfo(){
        JSONObject result = new JSONObject();
        int size = this.getSize();
        String className = this.getClass().getSimpleName();
        JSONObject types = getDataTypes();
        result.put("size", size);
        result.put("class-name", className);
        result.put("types", types);
        return result;
    }
}
