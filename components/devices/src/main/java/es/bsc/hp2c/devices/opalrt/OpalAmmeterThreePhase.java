package es.bsc.hp2c.devices.opalrt;

import es.bsc.hp2c.devices.generic.ThreePhaseSensor;
import es.bsc.hp2c.devices.opalrt.OpalReader.ThreePhaseOpalSensor;
import org.json.JSONObject;

/**
 * OpalAmmeterThreePhase ---- Three-phase Opal-RT voltmeter
 */
public class OpalAmmeterThreePhase extends ThreePhaseSensor<Float[], OpalAmmeter>
    implements ThreePhaseOpalSensor<Float[]> {
    private final int index;

    public OpalAmmeterThreePhase(String label, float[] position, JSONObject properties) {
        super(label, position);
        this.index = properties.optInt("index", 0);
        OpalReader.registerThreePhaseDevice(this);
        subSensors = new OpalAmmeter[super.getNPhases()];
        for (int i = 0; i < super.getNPhases(); i++) {
            String subLabel = label + "." + i;
            subSensors[i] = new OpalAmmeter(subLabel, position, properties);
        }
    }

    @Override
    public void sensed(Float[] values) {
        Float[] sensedValues = sensedValue(values);
        for (int i = 0; i < subSensors.length; i++) {
            subSensors[i].sensed(sensedValues[i]);
        }
    }

    @Override
    protected Float[] sensedValue(Float[] input) {
        return input;
    }

    @Override
    public int getIndex() {
        return this.index;
    }
}
