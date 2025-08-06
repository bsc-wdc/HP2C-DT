package es.bsc.hp2c.common.funcs;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.*;

import static es.bsc.hp2c.common.funcs.FuncUtils.transformFuncToServerFormat;
import static org.junit.jupiter.api.Assertions.*;

class TransformUtilsTest {

    private JSONObject loadJSON(String filename) throws IOException {
        String resourcePath = "inputs/" + filename;
        InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (is == null) {
            throw new FileNotFoundException("Resource not found: " + resourcePath);
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line);
        }
        reader.close();

        return new JSONObject(content.toString());
    }



    @Test
    void testBasicTransformation() throws IOException {
        JSONObject func = loadJSON("basic_transformation.json");
        transformFuncToServerFormat(func, "edge1");

        JSONObject sensors = func.getJSONObject("parameters").getJSONObject("sensors");
        JSONObject actuators = func.getJSONObject("parameters").getJSONObject("actuators");
        JSONObject triggerSensors = func.getJSONObject("trigger").getJSONObject("parameters").getJSONObject("trigger-sensor");

        assertEquals("Voltmeter Gen1", sensors.getJSONArray("edge1").getString(0));
        assertEquals("Three-Phase Switch Gen1", actuators.getJSONArray("edge1").getString(0));
        assertEquals("Voltmeter Gen1", triggerSensors.getJSONArray("edge1").getString(0));
    }

    @Test
    void testEmptyActuatorsArray() throws IOException {
        JSONObject func = loadJSON("empty_actuators.json");
        transformFuncToServerFormat(func, "edge2");

        JSONObject sensors = func.getJSONObject("parameters").getJSONObject("sensors");
        JSONObject actuators = func.getJSONObject("parameters").getJSONObject("actuators");

        assertEquals(2, sensors.getJSONArray("edge2").length());
        assertEquals(0, actuators.getJSONArray("edge2").length());
    }

    @Test
    void testInvalidTriggerSensorType() throws IOException {
        JSONObject func = loadJSON("invalid_trigger_sensor.json");

        Exception exception = assertThrows(IllegalArgumentException.class,
                () -> transformFuncToServerFormat(func, "edge3"));

        assertTrue(exception.getMessage().contains("Expected 'trigger-sensor' to be a JSONArray"));
    }

    @Test
    void testInvalidSensorArray() throws IOException {
        JSONObject func = loadJSON("invalid_sensor_array.json");

        Exception exception = assertThrows(IllegalArgumentException.class,
                () -> transformFuncToServerFormat(func, "edge4"));

        assertTrue(exception.getMessage().contains("Expected sensors to be a JSONArray"));
    }
}
