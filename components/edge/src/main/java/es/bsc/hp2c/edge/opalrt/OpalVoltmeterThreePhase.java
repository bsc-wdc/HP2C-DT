package es.bsc.hp2c.edge.opalrt;

import es.bsc.hp2c.edge.generic.ThreePhaseSensor;
import es.bsc.hp2c.edge.opalrt.OpalComm.OpalSensor;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * OpalVoltmeterThreePhase ---- Three-phase Opal-RT voltmeter
 */
public class OpalVoltmeterThreePhase extends ThreePhaseSensor<Float[], OpalVoltmeter>
        implements OpalSensor<Float[]> {
    private int[] indexes;

    /*
     * Creates a new instance of OpalVoltmeterThreePhase.
     *
     * @param label device label
     * @param position device position
     * @param properties JSONObject representing device properties
     */
    public OpalVoltmeterThreePhase(String label, float[] position, JSONObject properties) {
        super(label, position);
        JSONArray jIndexes = properties.getJSONArray("indexes");
        this.indexes = new int[jIndexes.length()];
        for (int i = 0; i < jIndexes.length(); ++i) {
            this.indexes[i] = (jIndexes.getInt(i));
        }
        OpalComm.registerSensor(this);
        subSensors = new OpalVoltmeter[super.getNPhases()];
        for (int i = 0; i < super.getNPhases(); i++) {
            String subLabel = label + "." + i;
            int[] indexes = { jIndexes.getInt(i) };
            subSensors[i] = new OpalVoltmeter(subLabel, position, indexes);
        }
    }

    /*
     * Sends received values to the corresponding sensors
     *
     * @param values received
     */
    @Override
    public void sensed(Float[] values) {
        Float[] sensedValues = sensedValues(values);
        for (int i = 0; i < subSensors.length; i++) {
            Float[] sensedValuesSensor = { sensedValues[i] };
            subSensors[i].sensed(sensedValuesSensor);
        }
    }

    @Override
    public Float[] getCurrentValues() {
        Float[] values = new Float[subSensors.length];
        for (int i = 0; i < subSensors.length; i++) {
            values[i] = subSensors[i].getCurrentValues()[0];
        }
        return values;
    }

    @Override
    protected Float[] sensedValues(Float[] input) {
        return input;
    }

    public int[] getIndexes() {
        return this.indexes;
    }

}
