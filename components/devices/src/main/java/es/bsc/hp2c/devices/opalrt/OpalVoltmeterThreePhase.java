package es.bsc.hp2c.devices.opalrt;

import es.bsc.hp2c.devices.generic.ThreePhaseSensor;
import es.bsc.hp2c.devices.opalrt.OpalReader.ThreePhaseOpalSensor;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * OpalVoltmeterThreePhase ---- Three-phase Opal-RT voltmeter
 */
public class OpalVoltmeterThreePhase extends ThreePhaseSensor<Float[], OpalVoltmeter>
        implements ThreePhaseOpalSensor<Float[]> {
    private int[] indexes;

    public OpalVoltmeterThreePhase(String label, float[] position, JSONObject properties) {
        super(label, position);
        JSONArray jIndexes = properties.getJSONArray("indexes");
        this.indexes = new int[jIndexes.length()];
        for (int i = 0; i < jIndexes.length(); ++i) {
            this.indexes[i] = (jIndexes.getInt(i));
        }
        OpalReader.registerThreePhaseDevice(this);
        subSensors = new OpalVoltmeter[super.getNPhases()];
        for (int i = 0; i < super.getNPhases(); i++) {
            String subLabel = label + "." + i;
            int[] indexes = { jIndexes.getInt(i) };
            subSensors[i] = new OpalVoltmeter(subLabel, position, properties, indexes);
        }
        this.setSubSensors(subSensors);
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

    public int[] getIndexes() {
        return this.indexes;
    }

}
