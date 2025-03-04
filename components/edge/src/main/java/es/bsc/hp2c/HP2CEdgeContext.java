package es.bsc.hp2c;

import com.rabbitmq.client.Channel;

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


}
