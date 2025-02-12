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

package es.bsc.hp2c.edge.local;

import es.bsc.hp2c.common.generic.MsgAlert;
import org.json.JSONObject;

import java.io.IOException;

/**
 *
 */
public class Console extends MsgAlert {

    public Console(String label, float[] position, JSONObject properties) {
        super(label, position);
    }

    @Override
    public void actuate(String message) {
        System.out.println(message);
    }

    @Override
    public void actuate(byte[] byteValues) throws IOException { }

    @Override
    public String decodeValuesActuator(byte[] messageBytes) { return null; }

    @Override
    public JSONObject getDeviceInfo() {
        return null;
    }

    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public JSONObject getDataTypes() {
        return null;
    }
}
