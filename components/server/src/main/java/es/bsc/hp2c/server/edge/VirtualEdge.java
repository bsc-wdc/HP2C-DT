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
package es.bsc.hp2c.server.edge;

import es.bsc.hp2c.common.types.Device;

import java.util.ArrayList;
import java.util.Map;

/**
 * Edge representation in the Server.
 * It contains a map of devices and other properties such as availability.
 */
public class VirtualEdge {
    private final Map<String, Device> devices;
    private boolean isAvailable;

    public VirtualEdge(Map<String, Device> devices) {
        this.devices = devices;
        this.isAvailable = true;
    }

    public boolean containsDevice(String deviceLabel) {
        return devices.containsKey(deviceLabel);
    }

    public Device getDevice(String deviceLabel) {
        return devices.get(deviceLabel);
    }

    public ArrayList<String> getDeviceLabels() {
        return new ArrayList<>(devices.keySet());
    }

    public ArrayList<Device> getDevices() {
        return new ArrayList<>(devices.values());
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public void setAvailable(boolean available) {
        isAvailable = available;
    }

    public Map<String, Device> getDeviceMap(){
        return devices;
    }
}
