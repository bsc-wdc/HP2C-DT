package es.bsc.hp2c.opalSimulator.utils;

import es.bsc.hp2c.common.types.Device;

import java.util.Arrays;

public class DeviceWrapper {
    private String label;
    private String protocol;
    private int[] indexes;
    private Device device;
    private float[] values;


    public DeviceWrapper(String label, String protocol, int[] indexes) {
        this.label = label;
        this.protocol = protocol;
        this.indexes = indexes;
        this.values = new float[indexes.length];
        Arrays.fill(values, Float.NEGATIVE_INFINITY);
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public float[] getValues() {
        return values;
    }

    public void setValues(float[] values) {
        this.values = values;
    }

    public void setValue(float value, int index) {
        this.values[index] = value;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public int[] getIndexes() {
        return indexes;
    }

    public void setIndexes(int[] indexes) {
        this.indexes = indexes;
    }

    public Device getDevice() {
        return device;
    }

    public void setDevice(Device device) {
        this.device = device;
    }
}
