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
import es.bsc.hp2c.server.device.VirtualComm;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static es.bsc.hp2c.common.utils.FileUtils.loadDevices;

/**
 * Edge representation in the Server.
 * It contains a map of devices and other properties such as availability.
 */
public class VirtualEdge {
    private final String label;
    private final Map<String, VirtualComm.VirtualDevice> devices;
    private boolean isAvailable;
    private long lastHeartbeat;
    private float x;
    private float y;
    private ArrayList<String> connections;
    private boolean modified;

    /**
     * Basic constructor when passing all the essential parameters explicitly.
     * @param label Edge label
     * @param devices Map of devices
     * @param currentTime Current time in milliseconds
     */
    public VirtualEdge(String label, Map<String, VirtualComm.VirtualDevice> devices, Long currentTime) {
        this.label = label;
        this.devices = devices;
        this.isAvailable = true;
        this.lastHeartbeat = currentTime;
    }

    /**
     * When passing the full JSON object, the constructor parses all the essential information from it.
     * @param jEdgeSetup Original edge JSON setup plus the "heartbeat" and "available" fields
     */
    public VirtualEdge(JSONObject jEdgeSetup) {
        // Collect main data
        JSONObject jGlobalProps = jEdgeSetup.getJSONObject("global-properties");
        this.label = jGlobalProps.getString("label");
        Map<String, Device> devicesMap = loadDevices(jEdgeSetup, "driver-dt", false);
        devices = new HashMap<>();
        for (String d : devicesMap.keySet()){
            devices.put(d, (VirtualComm.VirtualDevice) devicesMap.get(d));
        }

        // Set devices' availability
        JSONArray jDevices = jEdgeSetup.getJSONArray("devices");
        for (Object device : jDevices){
            JSONObject jDevice = (JSONObject) device;
            String deviceLabel = jDevice.getString("label").replace(" ", "").replace("-","");
            boolean availability = jDevice.getBoolean("availability");
            this.setDeviceAvailability(deviceLabel, availability);
        }

        this.isAvailable = jGlobalProps.getBoolean("available");
        this.lastHeartbeat = jGlobalProps.getLong("heartbeat");
        // Collect edge geospatial data
        JSONObject jGeoData = jGlobalProps.getJSONObject("geo-data");
        this.x = jGeoData.getJSONObject("position").getFloat("x");
        this.y = jGeoData.getJSONObject("position").getFloat("y");
        this.connections = new ArrayList<>();
        JSONArray jConnections = jGeoData.getJSONArray("connections");
        for (int i = 0; i < jConnections.length(); i++) {
            this.connections.add(jConnections.getString(i));
        }
        this.modified = true;
    }

    public boolean equals(VirtualEdge oldEdge){
        return this.label.equals(oldEdge.label) && this.devices.keySet().equals(oldEdge.devices.keySet()) &&
                this.isAvailable == oldEdge.isAvailable() && this.x == oldEdge.x && this.y == oldEdge.y &&
                this.connections.equals(oldEdge.connections);
    }

    public boolean containsDevice(String deviceLabel) {
        return devices.containsKey(deviceLabel);
    }

    /**
     * Returns a JSONObject for its use in REST API messaging.
     */
    public JSONObject getEdgeGeoInfo() {
        JSONObject jEdgeInfo = new JSONObject();
        jEdgeInfo.put(
                "position", new JSONObject()
                        .put("x", x)
                        .put("y", y)
        );
        jEdgeInfo.put("connections", connections);
        jEdgeInfo.put("show", isAvailable);
        return jEdgeInfo;
    }

    /**
     * Get a breakdown of the edge's devices in JSON format.
     */
    public JSONObject getDevicesInfo() {
        JSONObject jDevicesInfo = new JSONObject();

        for (Map.Entry<String, VirtualComm.VirtualDevice> entry : devices.entrySet()) {
            String deviceLabel = entry.getKey();
            Device device = (Device) entry.getValue();
            JSONObject jDevice = new JSONObject();
            boolean isActionable = false;
            if (device.isActionable()) {
                VirtualComm.VirtualActuator<?> actuator = (VirtualComm.VirtualActuator<?>) device;
                isActionable = true;
                boolean isCategorical = actuator.isCategorical();
                jDevice.put("isCategorical", isCategorical);
                jDevice.put("size", actuator.getSize());
                if (isCategorical) {
                    jDevice.put("categories", actuator.getCategories());
                }
            }
            else{
                VirtualComm.VirtualSensor<?> sensor = (VirtualComm.VirtualSensor<?>) device;
                jDevice.put("size", sensor.getSize());
            }
            jDevice.put("isActionable", isActionable);
            jDevicesInfo.put(deviceLabel, jDevice);
        }
        return jDevicesInfo;
    }

    public void setDeviceAvailability(String deviceLabel, boolean availability){
        devices.get(deviceLabel).setAvailability(availability);
    }

    public boolean getDeviceAvailability(String deviceLabel){
        return devices.get(deviceLabel).isAvailable();
    }

    public String getLabel() {
        return label;
    }

    public VirtualComm.VirtualDevice getDevice(String deviceLabel) {
        return devices.get(deviceLabel);
    }

    public ArrayList<String> getDeviceLabels() {
        return new ArrayList<>(devices.keySet());
    }

    public ArrayList<VirtualComm.VirtualDevice> getDevices() {
        return new ArrayList<>(devices.values());
    }

    public void setModified(boolean b){
        this.modified = b;
    }

    public boolean getModified(){
        return this.modified;
    }

    public void setLastHeartbeat(long lastHeartbeat) {
        this.lastHeartbeat = lastHeartbeat;
    }

    public long getLastHeartbeat() {
        return lastHeartbeat;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public void setAvailable(boolean available) {
        isAvailable = available;
    }

    public Map<String, VirtualComm.VirtualDevice> getDeviceMap(){
        return devices;
    }

    public String toString() {
        return "VirtualEdge{" +
                "label='" + label + '\'' +
                ", devices=" + devices +
                ", isAvailable=" + isAvailable +
                ", lastHeartbeat=" + lastHeartbeat +
                ", x=" + x +
                ", y=" + y +
                ", connections=" + connections +
                '}';
    }
}
