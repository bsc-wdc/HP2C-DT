package es.bsc.hp2c.opalSimulator;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/*
 * Runs nEdges TCP servers in order to receive actuations, nEdges TCP clients to update the values on the edges
 * (just of TCP sensors), and nEdges UDP servers with the aim of sending UDP sensors values. The OpalSimulator also
 * keeps track of the state of every TCP sensor within each edge by maintaining a map where the second digit of the
 * ports serves as the key (unique for each edge), and a Float array represents the corresponding values.
 *
 */
public class OpalSimulator {
    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static int BASE_TCP_SENSORS_PORT;
    private static int BASE_TCP_ACTUATORS_PORT;
    private static int runClient = 0;
    private static Map<Integer, Float[]> devices = new HashMap<>();
    private static int BASE_UDP_PORT;
    private static final double frequency = 1.0 / 20.0;
    private static int[] tcpIndexesPerEdge;
    private static int[] udpIndexesPerEdge;
    private static int nEdges;


    /*
    * Runs nEdges TCP servers in order to receive actuations, nEdges TCP clients to update the values on the edges
    * (just of TCP sensors), and nEdges UDP servers with the aim of sending UDP sensors values.
    *
    * @param args User should input the deployment directory (name of the directory in path "hp2cdt/deployments")
    * */
    public static void main(String[] args) throws InterruptedException, FileNotFoundException {
        if (args.length > 0){
            parseJSON(args[0], false);
        } else {
            System.out.println("User must input as argument the deployment directory");
            parseJSON("testbed", true);
        }

        // while each of the columns represents the indexes within the edge.
        for (int i = 0; i < nEdges; ++i){
            Float[] edgeI = new Float[tcpIndexesPerEdge[i]];
            Arrays.fill(edgeI, Float.NEGATIVE_INFINITY);
            devices.put(i+1, edgeI);
        }

        startActuatorsServer();
        startTCPSensors();
        startUDPSensors();
    }

    //=======================================
    // UDP
    //=======================================

    private static void startUDPSensors() {
        for (int i = 0; i < nEdges; ++i){
            int finalI = i;
            new Thread(() -> {
                int udpPort = BASE_UDP_PORT + (finalI * 1000);
                System.out.println("Starting UDP communication in port " + udpPort + " ip " + SERVER_ADDRESS);
                startUDPClient(udpPort, (udpPort / 1000) % 10);
            }).start();
        }
    }


    private static void startUDPClient(int udpPort, int edgeNumber) {
        try (DatagramSocket udpSocket = new DatagramSocket()) {
            InetAddress address = InetAddress.getByName(SERVER_ADDRESS);
            while (true) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(udpIndexesPerEdge[edgeNumber-1]* Float.BYTES);
                float[] values = genSineValues(udpIndexesPerEdge[edgeNumber-1]);
                for (float value: values) {
                    // float value = (float) Math.random();
                    byteBuffer.putFloat(value);
                }
                byte[] buffer = byteBuffer.array();
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, udpPort);
                udpSocket.send(packet);
                System.out.println("Sent UDP packet.");
                Thread.sleep(5000);
            }
        } catch (Exception e) {
            System.err.println("Error sending data through UDP.");
        }
    }

    //=======================================
    // TCP-Sensors
    //=======================================

    private static void startTCPSensors() {
        for (int i = 0; i < nEdges; ++i){
            int tcpPort = BASE_TCP_SENSORS_PORT + (i * 1000);
            new Thread(() -> {
                System.out.println("Starting TCP communication in port " + tcpPort + " ip " + SERVER_ADDRESS);
                startTCPClient(tcpPort, (tcpPort / 1000) % 10);
            }).start();
        }
    }


    /*
     * This method starts a TCP client to update the value on the corresponding edge. To do so, we check start of message
     * with a readInt() that checks the expected length (number of floats) of the message, then get the buffer with that
     * messageLength, and lastly the end of line (EoL) character.
     * */
    private static void startTCPClient(int tcpPort, int edgeNumber) {
        if (devices.get(edgeNumber).length < 1){ return; }
        try {
            Socket tcpSocket = new Socket(SERVER_ADDRESS, tcpPort);
            while (true) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(devices.get(edgeNumber).length * Float.BYTES + Integer.BYTES + Character.BYTES);
                float[] values = new float[devices.get(edgeNumber).length];
                for (int i = 0; i < devices.get(edgeNumber).length; i++) {
                    Float[] aux = devices.get(edgeNumber);
                    values[i] = aux[i];
                }
                byteBuffer.putInt(devices.get(edgeNumber).length);
                System.out.println("Length of the message: " + devices.get(edgeNumber).length);
                for (float value : values) {
                    byteBuffer.putFloat(value);
                    System.out.println("Prepared TCP value: " + value);
                }
                byteBuffer.putChar('\n');

                try {
                    DataOutputStream outputStream = new DataOutputStream(tcpSocket.getOutputStream());
                    byte[] buffer = byteBuffer.array();
                    outputStream.write(buffer);
                    System.out.println("Sent TCP packet.\n");
                } catch (IOException e) {
                    System.err.println("Error sending data through TCP: " + e.getMessage());
                    break;
                }
                Thread.sleep(5000);
            }
        } catch (Exception e) {
            System.err.println("Error connecting to TCP server: " + e.getMessage());
        }
    }

    //=======================================
    // TCP-Actuators
    //=======================================

    private static void startActuatorsServer() {
        for (int i = 0; i < nEdges; ++i){
            int port = BASE_TCP_ACTUATORS_PORT + (i * 1000);
            new Thread(() -> {
                try {
                    ServerSocket server = new ServerSocket(port, 0, InetAddress.getByName("0.0.0.0"));
                    System.out.println("Server running in port " + port + ". Waiting for client requests...");
                    Socket clientSocket = null;
                    clientSocket = server.accept();
                    runClient += 1;
                    System.out.println("Accepted connection from: " + clientSocket.getInetAddress().getHostAddress());
                    handleActuateClient(clientSocket, (port / 1000) % 10);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }
        System.out.println("");
        System.out.println("Waiting for " + nEdges + " edges to be connected...");
        System.out.println("");
        while (runClient < nEdges){
            try { Thread.sleep(1000); } catch (InterruptedException e) { throw new RuntimeException(e); }
        }
    }

    /*
    * This method uses a TCP socket to receive actuations and update the corresponding values in "devices" map.
    *
    * @param clientSocket Socket through which we will receive the messages.
    * @param edgeNumber Map key where value must be updated.
    * */
    private static void handleActuateClient(Socket clientSocket, int edgeNumber) {
        try {
            if (devices.get(edgeNumber).length < 1){ return; }
            DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
            while (true) {
                byte[] buffer = new byte[devices.get(edgeNumber).length * Float.BYTES];
                dis.readFully(buffer);
                System.out.println("Message Received from " + clientSocket.getInetAddress().getHostAddress());

                ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);

                int index = 0;
                while (byteBuffer.remaining() > 0) {
                    Float[] aux = devices.get(edgeNumber);
                    Float newFloat = byteBuffer.getFloat();
                    if (newFloat != Float.NEGATIVE_INFINITY){
                        aux[index] = newFloat;
                    }
                    devices.put(edgeNumber, aux);
                    index += 1;
                }
                System.out.println("    Message is: " + Arrays.toString(devices.get(edgeNumber)) +
                        " for edge " + edgeNumber);
            }
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    }

    //=======================================
    // UTILS
    //=======================================

    public static float[] genSineValues(int size) {
        float[] values = new float[size];
        long currentTimeMillis = System.currentTimeMillis();
        double time = currentTimeMillis / 1000.0; // Convert milliseconds to seconds
        double angularFrequency = 2 * Math.PI * frequency;

        for (int i = 0; i < size; i++) {
            double shift = i * (2 * Math.PI / 3);
            values[i] = (float) Math.sin(angularFrequency * time + shift);
            // Modify voltages (0, 1, 2)
            values[i] *= (float) Math.sqrt(2) * 230;
        }
        return values;
    }


    public static void parseJSON(String deployment, boolean local) throws FileNotFoundException {
        String deploymentFile = "../../deployments/" + deployment + "/setup/";
        System.out.println("Path to deployment setup: " + deploymentFile);
        File directory = new File(deploymentFile);

        //get the number of files in the directory and set nEdges. If the execution is local, use only 1 edge
        if (local) { nEdges = 1; }
        else { nEdges = directory.listFiles().length; }

        tcpIndexesPerEdge = new int[nEdges];
        udpIndexesPerEdge = new int[nEdges];

        //set up comms base ports from edge1
        setUpComms(deploymentFile);

        //for every edge in the deployment directory, get the number of TCP and UDP indexes
        getCommIndexes(deploymentFile);
    }


    private static void getCommIndexes(String deploymentFile) throws FileNotFoundException {
        for (int i = 1; i <= nEdges; ++i){
            String setupFile = deploymentFile + "/edge" + i + ".json";
            InputStream is = new FileInputStream(setupFile);
            JSONTokener tokener = new JSONTokener(is);
            JSONObject object = new JSONObject(tokener);
            JSONArray jDevices = object.getJSONArray("devices");
            int nIndexesUDP = 0;
            int nIndexesTCP = 0;
            for (Object jo : jDevices){
                JSONObject jDevice = (JSONObject) jo;
                JSONObject jDProperties = jDevice.getJSONObject("properties");
                if (jDProperties.getString("comm-type").equals("opal-tcp")){
                    nIndexesTCP += jDProperties.getJSONArray("indexes").length();
                } else{
                    nIndexesUDP += jDProperties.getJSONArray("indexes").length();
                }
            }
            udpIndexesPerEdge[i-1] = nIndexesUDP;
            tcpIndexesPerEdge[i-1] = nIndexesTCP;
        }
    }


    private static void setUpComms(String deploymentFile) throws FileNotFoundException {
        String setupFile = deploymentFile + "/edge1.json";
        InputStream is = new FileInputStream(setupFile);
        JSONTokener tokener = new JSONTokener(is);
        JSONObject object = new JSONObject(tokener);
        JSONObject jGlobProp = object.getJSONObject("global-properties");
        JSONObject jComms = jGlobProp.getJSONObject("comms");

        JSONObject jUDP = jComms.getJSONObject("opal-udp");
        JSONObject jTCP= jComms.getJSONObject("opal-tcp");

        JSONObject jUDPSensors = jUDP.getJSONObject("sensors");
        BASE_UDP_PORT = jUDPSensors.getInt("port");

        JSONObject jTCPSensors = jTCP.getJSONObject("sensors");
        BASE_TCP_SENSORS_PORT = jTCPSensors.getInt("port");

        JSONObject jTCPActuators = jTCP.getJSONObject("actuators");
        BASE_TCP_ACTUATORS_PORT = jTCPActuators.getInt("port");
    }
}
