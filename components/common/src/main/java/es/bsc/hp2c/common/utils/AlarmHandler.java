package es.bsc.hp2c.common.utils;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.Duration;

public class AlarmHandler {

    private static String alarmFilePath;
    private static JSONObject alarms = new JSONObject();
    private static float timeout = 60f; // timeout to turn off an alarm (in seconds)

    static {
        // Check if the server is running in Docker
        File server = new File("/data/server");
        if (server.exists()) {
            alarmFilePath = "/tmp/alarms.json"; // Docker path
        } else {
            String cwd = Paths.get("").toAbsolutePath().toString();
            alarmFilePath = cwd + "/alarms.json"; // Local path
        }
    }

    public static void addNewAlarm(String funcLabel) {
        try {
            if (!alarms.has(funcLabel)) {
                JSONObject newAlarm = new JSONObject();
                newAlarm.put("alarm", false);
                newAlarm.put("location", new JSONObject());
                alarms.put(funcLabel, newAlarm);
                writeToFile();
            }
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    public static void writeAlarm(String funcLabel, String edge, String device, String infoMessage) {
        if (!alarms.has(funcLabel)) {
            System.out.println("[Error] Function label does not exist: " + funcLabel);
            return;
        }

        JSONObject funcAlarm = alarms.getJSONObject(funcLabel);
        funcAlarm.put("alarm", true);

        // Handle the edge-device pair (if edge and device are not null)
        if (edge != null && device != null) {
            JSONObject location = funcAlarm.optJSONObject("location");
            if (location == null) {
                location = new JSONObject();
            }

            JSONObject edgeData = location.optJSONObject(edge);
            if (edgeData == null) {
                edgeData = new JSONObject();
            }

            // Add or update the device with the current timestamp
            edgeData.put(device, Instant.now().toString());
            location.put(edge, edgeData);
            funcAlarm.put("location", location);
        } else {
            funcAlarm.put("time", Instant.now());
        }

        // Add the info message (if not null)
        if (infoMessage != null) {
            funcAlarm.put("info", infoMessage);
        }

        // Update the alarms attribute and write to file
        alarms.put(funcLabel, funcAlarm);
        writeToFile();
    }


    public static void updateAlarm(String funcLabel, String edge, String device) {
        if (!alarms.has(funcLabel)) {
            System.out.println("[Error] Function label does not exist: " + funcLabel);
            return;
        }

        JSONObject funcAlarm = alarms.getJSONObject(funcLabel);
        if (!funcAlarm.getBoolean("alarm")){
            return;
        }
        JSONObject location = funcAlarm.optJSONObject("location");

        if (edge == null && device == null) {
            // Check the "time" key for timestamp
            if (!funcAlarm.has("time")) {
                System.out.println("[Update] No global timestamp found for function: " + funcLabel);
                return;
            }

            Instant alarmTime = (Instant) funcAlarm.get("time");
            Instant now = Instant.now();

            if (Duration.between(alarmTime, now).getSeconds() >= timeout) {
                // Clear the alarm entirely if the timeout has expired
                funcAlarm.put("alarm", false);
                funcAlarm.remove("time");
                funcAlarm.remove("info");
                writeToFile();
            }
        } else {
            // Handle edge and device-specific case
            if (location == null || !location.has(edge)) {
                return;
            }

            JSONObject edgeData = location.getJSONObject(edge);
            if (!edgeData.has(device)) {
                System.out.println("[Update] Device not found in edge: " + edge);
                return;
            }

            // Check the timestamp for the device
            Instant alarmTime = (Instant) funcAlarm.get(device);
            Instant now = Instant.now();

            if (Duration.between(alarmTime, now).getSeconds() >= timeout) {
                // Remove the device if the timeout has passed
                edgeData.remove(device);
                if (edgeData.isEmpty()) {
                    location.remove(edge);
                }
                if (location.isEmpty()) {
                    funcAlarm.put("alarm", false);
                }
                writeToFile();
            }
        }
    }


    private static void writeToFile() {
        try (FileWriter fileWriter = new FileWriter(alarmFilePath)) {
            fileWriter.write(alarms.toString(4));
            System.out.println("[AlarmHandler] Alarm written to " + alarmFilePath);
        } catch (IOException e) {
            System.err.println("[AlarmHandler] Failed to write alarm to file: " + e.getMessage());
        }
    }

    public static void setTimeout(String timeValue) {
        try {
            if (timeValue == null || timeValue.isEmpty()) {
                throw new IllegalArgumentException("Time value cannot be null or empty");
            }

            String unit;
            float value;

            // Check if the time string ends with "ms" (milliseconds)
            if (timeValue.endsWith("ms")) {
                unit = "ms";
                value = Float.parseFloat(timeValue.substring(0, timeValue.length() - 2));
            } else {
                unit = timeValue.substring(timeValue.length() - 1); // Last character for unit
                value = Float.parseFloat(timeValue.substring(0, timeValue.length() - 1));
            }

            switch (unit) {
                case "ms": // Milliseconds
                    timeout = value / 1000; // Convert to seconds
                    break;
                case "s": // Seconds
                    timeout = value;
                    break;
                case "m": // Minutes
                    timeout = value * 60;
                    break;
                case "h": // Hours
                    timeout = value * 3600;
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported time unit: " + unit);
            }
            System.out.println("[AlarmHandler] Timeout set to " + timeout + " seconds");
        } catch (NumberFormatException e) {
            System.err.println("[AlarmHandler] Invalid numeric value in time string: " + timeValue);
        } catch (IllegalArgumentException e) {
            System.err.println("[AlarmHandler] " + e.getMessage());
        }
    }
}

