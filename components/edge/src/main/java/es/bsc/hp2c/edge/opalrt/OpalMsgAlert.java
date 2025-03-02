package es.bsc.hp2c.edge.opalrt;

import es.bsc.hp2c.common.generic.MsgAlert;
import es.bsc.hp2c.edge.opalrt.OpalComm.OpalActuator;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

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

        try {
            long originalTimestamp = Long.parseLong(message);
            long timestampNanos = Instant.now().getEpochSecond() * 1_000_000_000L
                    + Instant.now().getNano();
            String currentTimestamp = Long.toString(timestampNanos);
            System.out.println("[OpalMsgAlert] Original timestamp: " + message);
            System.out.println("[OpalMsgAlert] Current timestamp: " + currentTimestamp);
            System.out.println("[OpalMsgAlert] Timestamp difference: " + (timestampNanos - originalTimestamp));
        } catch (NumberFormatException e) {
            System.out.println("[OpalMsgAlert] Received message: " + message);
        }
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

