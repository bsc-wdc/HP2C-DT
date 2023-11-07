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

package es.bsc.hp2c.devices.local;

import es.bsc.hp2c.devices.generic.MsgAlert;
import org.json.JSONObject;

/**
 *
 */
public class Console extends MsgAlert {

    public Console(String label, float[] position, JSONObject properties) {
        super(label, position);
    }


    @Override
    public void setValues(String message) throws Exception {
        System.out.println(message);
    }

}
