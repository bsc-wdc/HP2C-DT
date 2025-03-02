package es.bsc.hp2c.server.device;

import es.bsc.hp2c.common.generic.MsgAlert;
import es.bsc.hp2c.server.modules.AmqpManager;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static es.bsc.hp2c.HP2CServerContext.getAmqp;

public class VirtualMsgAlert extends MsgAlert implements VirtualComm.VirtualActuator<String> {
    private final String edgeLabel;

    public VirtualMsgAlert(String label, float[] position, JSONObject properties, JSONObject jGlobalProperties) {
        super(label, position);
        this.edgeLabel = jGlobalProperties.getString("label");
    }

    @Override
    public void actuate(String values) throws IOException {
        byte[] byteValues = encodeValuesActuator(values);
        AmqpManager amqp = getAmqp();
        amqp.virtualActuate(this, edgeLabel, byteValues);
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
    public boolean isCategorical() {
        return false;
    }

    @Override
    public ArrayList<String> getCategories() {
        return null;
    }

    @Override
    public String getEdgeLabel() {
        return this.edgeLabel;
    }

    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public JSONObject getDataTypes(){
        JSONObject result = new JSONObject();
        result.put("actuator", String.class.getSimpleName());
        return result;
    }
}
