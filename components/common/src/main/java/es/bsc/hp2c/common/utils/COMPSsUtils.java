package es.bsc.hp2c.common.utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static es.bsc.hp2c.common.utils.FileUtils.getJsonObject;

public class COMPSsUtils {

    public static void setResources(String setupFile) throws IOException {
        File dockerEnvFile = new File("/.dockerenv");
        if (!dockerEnvFile.isFile()) {
            return;
        }

        // Load XML template
        String templatePath = "/data/resources_template.xml";
        String xmlTemplate;
        try {
            xmlTemplate = new String(Files.readAllBytes(Paths.get(templatePath)));
        } catch (IOException e) {
            throw new IOException("Failed to read XML template from " + templatePath, e);
        }

        JSONObject object = getJsonObject(setupFile);
        JSONObject compss = object.optJSONObject("compss");
        if (compss == null) return;
        JSONArray jResources = compss.optJSONArray("resources");
        if (jResources == null) return;

        for (Object jo : jResources) {
            JSONObject jResource = (JSONObject) jo;
            String ip = jResource.optString("ip");
            if (ip.isEmpty()) throw new IllegalArgumentException("IP must be declared for the resource");

            String port = jResource.optString("port");
            if (port.isEmpty()) throw new IllegalArgumentException("Port must be declared for the resource " + ip);

            String cpu = jResource.optString("cpu", "1");
            String arc = jResource.optString("arch", "[unassigned]");

            // Replace placeholders in the template
            String xmlData = xmlTemplate
                    .replace("{{IP}}", ip)
                    .replace("{{ARCH}}", arc)
                    .replace("{{CPU}}", cpu)
                    .replace("{{PORT}}", port);

            String restPort = System.getenv("REST_AGENT_PORT");
            String command = "curl -s -X PUT http://127.0.0.1:" + restPort + "/COMPSs/addResources "
                    + "-H 'content-type: application/xml' "
                    + "-d '" + xmlData + "'";

            ProcessBuilder processBuilder = new ProcessBuilder("bash", "-c", command);
            processBuilder.inheritIO();
            processBuilder.start();
        }
    }
}