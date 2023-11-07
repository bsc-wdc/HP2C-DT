package es.bsc.hp2c.devices.opalrt;

import es.bsc.hp2c.devices.generic.ThreePhaseSensor;
import es.bsc.hp2c.devices.opalrt.OpalReader.OpalSensor;

import java.util.ArrayList;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * OpalAmmeterThreePhase ---- Three-phase Opal-RT voltmeter
 */
public class OpalAmmeterThreePhase extends ThreePhaseSensor<Float[], OpalAmmeter>
        implements OpalSensor<Float[]> {
    private int[] indexes;

    public OpalAmmeterThreePhase(String label, float[] position, JSONObject properties) {
        super(label, position);
        JSONArray jIndexes = properties.getJSONArray("indexes");
        this.indexes = new int[jIndexes.length()];
        for (int i = 0; i < jIndexes.length(); ++i) {
            this.indexes[i] = (jIndexes.getInt(i));
        }
        OpalReader.registerDevice(this);
        subSensors = new OpalAmmeter[super.getNPhases()];
        for (int i = 0; i < super.getNPhases(); i++) {
            String subLabel = label + "." + i;
            int[] indexes = { jIndexes.getInt(i) };
            subSensors[i] = new OpalAmmeter(subLabel, position, indexes);
        }
    }

    @Override
    public void sensed(Float[] values) {
        Float[] sensedValues = sensedValues(values);
        for (int i = 0; i < subSensors.length; i++) {
            Float[] sensedValuesSensor = { sensedValues[i] };
            subSensors[i].sensed(sensedValuesSensor);
        }
    }

    @Override
    protected Float[] sensedValues(Float[] input) {
        return input;
    }

    @Override
    public int[] getIndexes() {
        return this.indexes;
    }
}
