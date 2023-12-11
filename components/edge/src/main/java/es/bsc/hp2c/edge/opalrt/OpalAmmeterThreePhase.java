package es.bsc.hp2c.edge.opalrt;

import es.bsc.hp2c.edge.generic.ThreePhaseSensor;
import es.bsc.hp2c.edge.opalrt.OpalComm.OpalSensor;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * OpalAmmeterThreePhase ---- Three-phase Opal-RT voltmeter
 */
public class OpalAmmeterThreePhase extends ThreePhaseSensor<Float[], OpalAmmeter>
        implements OpalSensor<Float[]> {
    private int[] indexes;

    /*
     * Creates a new instance of OpalAmmeterThreePhase when the device is declared in the JSON file. If an Opal device
     * is used by the edge, OpalComm.init() initializes ports and ips for communications according to the data in
     * jGlobalProperties.
     *
     * @param label device label
     * @param position device position
     * @param jProperties JSONObject representing device properties
     * @param jGlobalProperties JSONObject representing the global properties of the edge
     * */
    public OpalAmmeterThreePhase(String label, float[] position, JSONObject properties, JSONObject jGlobalProperties) {
        super(label, position);
        JSONArray jIndexes = properties.getJSONArray("indexes");
        this.indexes = new int[jIndexes.length()];
        for (int i = 0; i < jIndexes.length(); ++i) {
            this.indexes[i] = (jIndexes.getInt(i));
        }
        OpalComm.registerSensor(this);
        subSensors = new OpalAmmeter[super.getNPhases()];
        for (int i = 0; i < super.getNPhases(); i++) {
            String subLabel = label + "." + i;
            int[] indexes = { jIndexes.getInt(i) };
            subSensors[i] = new OpalAmmeter(subLabel, position, indexes);
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

    @Override
    public int[] getIndexes() {
        return this.indexes;
    }
}
