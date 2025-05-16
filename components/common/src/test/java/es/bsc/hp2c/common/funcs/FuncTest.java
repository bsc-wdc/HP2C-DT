package es.bsc.hp2c.common.funcs;

import org.json.JSONObject;

import static es.bsc.hp2c.common.funcs.Func.transformFuncToServerFormat;
import static org.junit.jupiter.api.Assertions.*;

class TransformUtilsTest {

    @org.junit.jupiter.api.Test
    void testBasicTransformation() {
        JSONObject func = new JSONObject(
                "{" +
                        "\"label\": \"VoltLimitation\"," +
                        "\"lang\": \"Python\"," +
                        "\"parameters\": {" +
                        "\"sensors\": [\"Voltmeter Gen1\"]," +
                        "\"actuators\": [\"Three-Phase Switch Gen1\"]," +
                        "\"other\": {}" +
                        "}," +
                        "\"trigger\": {" +
                        "\"type\": \"onRead\"," +
                        "\"parameters\": {" +
                        "\"trigger-sensor\": [\"Voltmeter Gen1\"]," +
                        "\"interval\": 1" +
                        "}" +
                        "}" +
                        "}"
        );

        transformFuncToServerFormat(func, "edge1");

        JSONObject params = func.getJSONObject("parameters");
        JSONObject sensors = params.getJSONObject("sensors");
        JSONObject actuators = params.getJSONObject("actuators");
        JSONObject triggerSensors = func.getJSONObject("trigger").getJSONObject("parameters")
                .getJSONObject("trigger-sensor");

        // Assert that every noted device is within "edge1"
        assertEquals("Voltmeter Gen1", sensors.getJSONArray("edge1").getString(0));
        assertEquals("Three-Phase Switch Gen1", actuators.getJSONArray("edge1").getString(0));
        assertEquals("Voltmeter Gen1", triggerSensors.getJSONArray("edge1").getString(0));
    }

    @org.junit.jupiter.api.Test
    void testEmptyActuatorsArray() {
        JSONObject func = new JSONObject(
                "{" +
                        "\"label\": \"CalcPower\"," +
                        "\"lang\": \"Java\"," +
                        "\"type\": \"workflow\"," +
                        "\"method-name\": \"es.bsc.hp2c.edge.funcs.CalcPower\"," +
                        "\"parameters\": {" +
                        "\"sensors\": [\"Voltmeter Gen1\", \"Ammeter Gen1\"]," +
                        "\"actuators\": []," +
                        "\"other\": {}" +
                        "}," +
                        "\"trigger\": {" +
                        "\"type\": \"onFrequency\"," +
                        "\"parameters\": {" +
                        "\"frequency\": 2000" +
                        "}" +
                        "}" +
                        "}"
        );

        transformFuncToServerFormat(func, "edge2");

        JSONObject sensors = func.getJSONObject("parameters").getJSONObject("sensors");
        JSONObject actuators = func.getJSONObject("parameters").getJSONObject("actuators");

        assertEquals(2, sensors.getJSONArray("edge2").length());
        assertEquals(0, actuators.getJSONArray("edge2").length());
    }


    @org.junit.jupiter.api.Test
    void testInvalidTriggerSensorType() {
        JSONObject func = new JSONObject(
                "{" +
                        "\"label\": \"FaultyFunc\"," +
                        "\"parameters\": {" +
                        "\"sensors\": [\"Sensor1\"]," +
                        "\"actuators\": [\"Actuator1\"]" +
                        "}," +
                        "\"trigger\": {" +
                        "\"type\": \"onRead\"," +
                        "\"parameters\": {" +
                        "\"trigger-sensor\": \"Not an array\"" +
                        "}" +
                        "}" +
                        "}"
        );

        Exception exception = assertThrows(IllegalArgumentException.class,
                () -> transformFuncToServerFormat(func, "edge3"));

        assertTrue(exception.getMessage().contains("Expected 'trigger-sensor' to be a JSONArray"));
    }

    @org.junit.jupiter.api.Test
    void testInvalidSensorArray() {
        JSONObject func = new JSONObject(
                "{" +
                        "\"label\": \"EmptySensors\"," +
                        "\"lang\": \"Java\"," +
                        "\"parameters\": {" +
                        "\"sensors\": \"Not an array\"," +
                        "\"actuators\": [\"Actuator1\"]" +
                        "}," +
                        "\"trigger\": {" +
                        "\"type\": \"onRead\"," +
                        "\"parameters\": {" +
                        "\"trigger-sensor\": [\"Sensor1\"]" +
                        "}" +
                        "}" +
                        "}"
        );

        Exception exception = assertThrows(IllegalArgumentException.class,
                () -> transformFuncToServerFormat(func, "edge4"));

        assertTrue(exception.getMessage().contains("Expected sensors to be a JSONArray"));
    }

}
