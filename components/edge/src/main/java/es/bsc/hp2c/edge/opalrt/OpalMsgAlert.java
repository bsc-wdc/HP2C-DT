package es.bsc.hp2c.edge.opalrt;

import es.bsc.hp2c.common.generic.MsgAlert;
import es.bsc.hp2c.common.generic.Switch;
import es.bsc.hp2c.edge.opalrt.OpalComm.OpalActuator;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Represents a message alert system implemented within a local OpalRT environment.
 * This device receives a string and prints it as an alert message.
 */
public class OpalMsgAlert extends MsgAlert implements OpalActuator<String> {

    /**
     * Creates an OpalMsgAlert instance.
     *
     * @param label device label
     * @param position device position
     */
    public OpalMsgAlert(String label, float[] position, JSONObject jProperties, JSONObject jGlobalProperties) {
        super(label, position);
        if (jGlobalProperties.getBoolean("executeOpalComm")){
            OpalComm.registerActuator(this);
            OpalComm.init(jGlobalProperties);
        }
    }

    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public void actuate(String message) throws IOException {
        if (message == null || message.isEmpty()) {
            throw new IOException("OpalMsgAlert received an empty message.");
        }
        System.out.println("[OpalMsgAlert] Received alert: " + message);
    }

    @Override
    public void actuate(byte[] byteValues) throws IOException {
        actuate(decodeValuesActuator(byteValues));
    }

    @Override
    public void actuate(String[] values) throws IOException {
        if (values == null || values.length == 0) {
            throw new IOException("OpalMsgAlert received an empty message.");
        }
        actuate(values[0]);
    }

    @Override
    public String decodeValuesActuator(byte[] messageBytes) {
        return new String(messageBytes, StandardCharsets.UTF_8);
    }

    @Override
    public JSONObject getDataTypes(){
        JSONObject result = new JSONObject();
        result.put("actuator", String.class.getSimpleName());
        return result;
    }

    @Override
    public int[] getIndexes() {
        return new int[0];
    }
}

