package es.bsc.hp2c;

import com.rabbitmq.client.Channel;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import static es.bsc.hp2c.common.utils.FileUtils.getJsonObject;

public class HP2CEdgeContext {
    private static String edgeLabel;
    private static Channel channel;
    private static final String EXCHANGE_NAME = "measurements";

    public static void setEdgeLabel(String e){
        edgeLabel = e;
    }

    public static void setChannel(Channel c){
        channel = c;
    }

    public static String getEdgeLabel() {
        return edgeLabel;
    }

    public static Channel getChannel() {
        return channel;
    }

    public static String getExchangeName() {
        return EXCHANGE_NAME;
    }

    public static void setResources(String setupFile) throws IOException {
        JSONObject object = getJsonObject(setupFile);
        JSONArray jResources = object.optJSONArray("resources");
        if (jResources == null) return;

        for (Object jo : jResources) {
            JSONObject jResource = (JSONObject) jo;
            String ip = jResource.optString("ip");
            if (ip.isEmpty()) throw new IllegalArgumentException("IP must be declared for the resource");

            String port = jResource.optString("port");
            if (port.isEmpty()) throw new IllegalArgumentException("Port must be declared for the resource " + ip);

            String cpu = jResource.optString("cpu", "1");
            String arc = jResource.optString("arch", "[unassigned]");

            String xmlData = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<newResource>"
                    + "<externalResource>"
                    + "<name>" + ip + "</name>"
                    + "<description>"
                    + "<processors>"
                    + "<processor>"
                    + "<name>MainProcessor</name>"
                    + "<type>CPU</type>"
                    + "<architecture>" + arc + "</architecture>"
                    + "<computingUnits>" + cpu + "</computingUnits>"
                    + "<internalMemory>-1.0</internalMemory>"
                    + "<propName>[unassigned]</propName>"
                    + "<propValue>[unassigned]</propValue>"
                    + "<speed>-1.0</speed>"
                    + "</processor>"
                    + "</processors>"
                    + "<memorySize>-1</memorySize>"
                    + "<memoryType>[unassigned]</memoryType>"
                    + "<storageSize>-1.0</storageSize>"
                    + "<storageType>[unassigned]</storageType>"
                    + "<operatingSystemDistribution>[unassigned]</operatingSystemDistribution>"
                    + "<operatingSystemType>[unassigned]</operatingSystemType>"
                    + "<operatingSystemVersion>[unassigned]</operatingSystemVersion>"
                    + "<pricePerUnit>-1.0</pricePerUnit>"
                    + "<priceTimeUnit>-1</priceTimeUnit>"
                    + "<value>0.0</value>"
                    + "<wallClockLimit>-1</wallClockLimit>"
                    + "</description>"
                    + "<adaptor>es.bsc.compss.agent.comm.CommAgentAdaptor</adaptor>"
                    + "<resourceConf xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:type=\"ResourcesExternalAdaptorProperties\">"
                    + "<Property>"
                    + "<Name>Port</Name>"
                    + "<Value>" + port + "</Value>"
                    + "</Property>"
                    + "</resourceConf>"
                    + "</externalResource>"
                    + "</newResource>";

            String command = "curl -s -X PUT http://127.0.0.1:46101/COMPSs/addResources "
                    + "-H 'content-type: application/xml' "
                    + "-d '" + xmlData + "'";

            ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", command);
            processBuilder.inheritIO();
            processBuilder.start();
        }
    }
}
