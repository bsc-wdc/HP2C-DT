package es.bsc.hp2c.common.utils;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;

public class AlarmHandler {
    private static String alarmFilePath;
    private static JSONObject alarms;

    static {
        // Check if the server is running in docker
        File server = new File("/data/server");
        if (server.exists()) {
            alarmFilePath = "/tmp/alarms.json"; //docker path
        } else {
            String cwd = Paths.get("").toAbsolutePath().toString();
            System.out.println("CWD: " + cwd);
            alarmFilePath = cwd + "/alarms.json"; //local path
        }

        // Initialize alarms JSON object
        alarms = new JSONObject();
    }

    public static void addNewAlarm(String funcLabel) {
        try{
            JSONObject newAlarm = new JSONObject();
            newAlarm.put("alarm", false);
            newAlarm.put("location", new JSONObject());
            alarms.put(funcLabel, newAlarm);
            writeToFile();
        } catch (Exception e){
            System.err.println(e);
        }
    }

    public static void writeAlarm(String funcLabel, String edge, String device) {
        if (!alarms.has(funcLabel)) {
            System.out.println("[Error] Function label does not exist: " + funcLabel);
            return;
        }

        JSONObject funcAlarm = alarms.getJSONObject(funcLabel);
        funcAlarm.put("alarm", true);
        JSONObject location = funcAlarm.getJSONObject("location");
        location.put(edge, device);
        funcAlarm.put("location", location);
        alarms.put(funcLabel, funcAlarm);
        writeToFile();
    }

    private static void writeToFile() {
        try (FileWriter fileWriter = new FileWriter(alarmFilePath)) {
            fileWriter.write(alarms.toString(4));
            System.out.println("[AlarmHandler] Alarm written to " + alarmFilePath);
        } catch (IOException e) {
            System.err.println("[AlarmHandler] Failed to write alarm to file: " + e.getMessage());
        }
    }
}
