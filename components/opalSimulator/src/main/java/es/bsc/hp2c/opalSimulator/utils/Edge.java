package es.bsc.hp2c.opalSimulator.utils;

import es.bsc.hp2c.opalSimulator.utils.DeviceWrapper;

import java.util.ArrayList;

public class Edge {
    private final String label;
    private final int tcpSensorsPort;
    private final int tcpActuatorsPort;
    private final int udpSensorsPort;
    private ArrayList<DeviceWrapper> devices;

    public Edge(String label, int tcpSensorsPort, int tcpActuatorsPort, int udpSensorsPort) {
        this.label = label;
        this.tcpSensorsPort = tcpSensorsPort;
        this.tcpActuatorsPort = tcpActuatorsPort;
        this.udpSensorsPort = udpSensorsPort;
        this.devices = new ArrayList<>();
    }

    public String getLabel() {
        return label;
    }

    public int getTcpSensorsPort() {
        return tcpSensorsPort;
    }

    public int getTcpActuatorsPort() {
        return tcpActuatorsPort;
    }

    public int getUdpSensorsPort() {
        return udpSensorsPort;
    }

    public ArrayList<DeviceWrapper> getDevices() {
        return devices;
    }

    public void setDevices(ArrayList<DeviceWrapper> devices) {
        this.devices = devices;
    }

    public void addDevice(DeviceWrapper device) {
        devices.add(device);
    }
}
