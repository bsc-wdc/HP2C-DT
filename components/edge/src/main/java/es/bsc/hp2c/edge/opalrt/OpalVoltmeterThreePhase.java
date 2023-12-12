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
     * Creates a new instance of OpalVoltmeterThreePhase when the device is declared in the JSON file. If an Opal device
     * is used by the edge, OpalComm.init() initializes ports and ips for communications according to the data in
     * jGlobalProperties.
     *
     * @param label device label
     * @param position device position
     * @param jProperties JSONObject representing device properties
     * @param jGlobalProperties JSONObject representing the global properties of the edge
     * */
    public OpalVoltmeterThreePhase(String label, float[] position, JSONObject jProperties, JSONObject jGlobalProperties) {
        super(label, position);
        JSONArray jIndexes = jProperties.getJSONArray("indexes");
        this.indexes = new int[jIndexes.length()];
        for (int i = 0; i < jIndexes.length(); ++i) {
            this.indexes[i] = (jIndexes.getInt(i));
        }
        OpalComm.registerSensor(this);
        OpalComm.init(jGlobalProperties);
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
