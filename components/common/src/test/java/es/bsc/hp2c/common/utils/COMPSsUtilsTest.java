package es.bsc.hp2c.common.utils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

class COMPSsUtilsTest {
    private static String INPUT_PATH = "inputs/input_test_set_resources.json";
    private static String OUTPUT_PATH = "outputs/output_test_set_resources.xml";
    private static String TEMPLATE_PATH = "templates/resources_template.xml";

    private String readResource(String resourcePath) {
        InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
        assertNotNull(is, "Resource not found: " + resourcePath);
        try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name())) {
            return scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
        }
    }

    @Test
    void testGenerateResourceXML() {
        String inputJsonStr = readResource(INPUT_PATH);
        String expectedXml = readResource(OUTPUT_PATH).trim();
        String templateXml = readResource(TEMPLATE_PATH);

        JSONObject input = new JSONObject(inputJsonStr);
        JSONArray resources = input.getJSONObject("compss").getJSONArray("resources");

        JSONObject jResource = resources.getJSONObject(0);
        String actualXml = COMPSsUtils.generateResourcesXML(templateXml, jResource).trim();

        assertEquals(expectedXml, actualXml);
    }
}