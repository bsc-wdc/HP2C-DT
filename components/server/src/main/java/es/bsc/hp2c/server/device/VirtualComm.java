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

import es.bsc.hp2c.common.types.Actuator;
import es.bsc.hp2c.common.types.Sensor;

import java.io.IOException;
import java.util.ArrayList;

public class VirtualComm {

    protected interface VirtualDevice {
        String getEdgeLabel();
        int getSize();
    }
    public interface VirtualSensor<V> extends Sensor<Float[], V>, VirtualDevice {}
    public interface VirtualActuator<V> extends Actuator<V>, VirtualDevice {
        void actuate(String[] stringValues) throws IOException;
        boolean isCategorical();
        ArrayList<String> getCategories();
    }
}
