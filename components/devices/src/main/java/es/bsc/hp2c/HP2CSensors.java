package es.bsc.hp2c;

import es.bsc.hp2c.devices.types.Device;
import es.bsc.hp2c.devices.types.Device.DeviceInstantiationException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 *
 */
public class HP2CSensors {

    public static void main(String[] args) throws Exception {
        String setupFile = args[0];
        //String setupFile="/home/flordan/projects/HP2C-DT/development/testbed/setup/device1.json";
        List<Device> devices = loadDevices(setupFile);
        
    }
    
    
    private static  List<Device> loadDevices(String setupFile) throws FileNotFoundException {
        InputStream is = new FileInputStream(setupFile);
        JSONTokener tokener = new JSONTokener(is);
        JSONObject object = new JSONObject(tokener);
        JSONArray jDevices = object.getJSONArray("devices");
        ArrayList<Device> devices = new ArrayList<>();
        for (Object jo : jDevices) {
            JSONObject jDevice = (JSONObject) jo;
            try {
                Device d = Device.parseJSON(jDevice);
                devices.add(d);
            } catch (DeviceInstantiationException | ClassNotFoundException | JSONException e) {
                System.err.println("Error loading device " + jDevice + ". Ignoring it.");
            }
        }
        return devices;
    }
}
