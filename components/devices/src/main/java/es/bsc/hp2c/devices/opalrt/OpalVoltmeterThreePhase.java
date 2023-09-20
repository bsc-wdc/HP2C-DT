package es.bsc.hp2c.devices.opalrt;

import es.bsc.hp2c.devices.generic.ThreePhaseSensor;
import es.bsc.hp2c.devices.opalrt.OpalReader.ThreePhaseOpalSensor;
import org.json.JSONObject;

/**
 * OpalVoltmeterThreePhase ---- Three-phase Opal-RT voltmeter
 */
public class OpalVoltmeterThreePhase extends ThreePhaseSensor<Float[], OpalVoltmeter>
    implements ThreePhaseOpalSensor<Float[]> {
    private final int index;

    public OpalVoltmeterThreePhase(String label, float[] position, JSONObject properties) {
        super(label, position);
        this.index = properties.optInt("index",0);
        System.out.println("Registering device " + label);
        OpalReader.registerThreePhaseDevice(this);
        subSensors = new OpalVoltmeter[super.getNPhases()];
        for (int i = 0; i < super.getNPhases(); i++){
            String subLabel = label + "." + i;
            System.out.println("Storing in " + label + " subsensor" + subLabel);
            subSensors[i] = new OpalVoltmeter(subLabel, position, properties);
        }
    }

    @Override
    public void sensed(Float[] values) {
        Float[] sensedValues = sensedValue(values);
        for (int i = 0; i < subSensors.length; i++) {
            subSensors[i].sensed(sensedValues[i]);
        }
        System.out.println("Sensed " + super.getCurrentValue() + " V");
    }

    @Override
    protected Float[] sensedValue(Float[] input) {
        return input;
    }

    public int getIndex() {
        return this.index;
    }
}
