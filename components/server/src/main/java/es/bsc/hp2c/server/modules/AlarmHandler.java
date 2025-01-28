package es.bsc.hp2c.server.modules;

import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.Duration;

import static es.bsc.hp2c.common.utils.FileUtils.getJsonObject;

public class AlarmHandler {

    private String alarmFilePath;
    private JSONObject alarms = new JSONObject();
    private float timeout = 60f; // timeout to turn off an alarm (in seconds)
    private DatabaseHandler db;

    public AlarmHandler (String pathToSetup, DatabaseHandler database){
        db = database;

        // Check if the server is running in Docker
        File server = new File("/data/server");
        if (server.exists()) {
            alarmFilePath = "/tmp/alarms.json"; // Docker path
        } else {
            String cwd = Paths.get("").toAbsolutePath().toString();
            alarmFilePath = cwd + "/alarms.json"; // Local path
        }
        try {
            // Load setup file
            JSONObject object = getJsonObject(pathToSetup);
            String timeoutValue = object.getJSONObject("global-properties").optString("alarm-timeout");
            if(timeoutValue != null){
                setTimeout(timeoutValue);
            }
        } catch (Exception e){
            System.err.println("Error loading server json from: " + pathToSetup);
        }
    }

    public void addNewAlarm(String funcLabel) {
        try {
            if (!alarms.has(funcLabel)) {
                JSONObject newAlarm = new JSONObject();
                alarms.put(funcLabel, newAlarm);
                writeToFile();
            }
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    public void writeAlarm(String funcLabel, String edge, String device, String infoMessage, boolean alarmStatus) {
        if (!alarms.has(funcLabel)) {
            System.out.println("[Error] Function label does not exist: " + funcLabel);
            return;
        }

        JSONObject funcAlarm = alarms.getJSONObject(funcLabel);
        funcAlarm.put("alarm", alarmStatus);

        if (alarmStatus) {
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

                JSONObject jDevice = new JSONObject();
                jDevice.put("time", Instant.now());
                if (infoMessage != null){
                    jDevice.put("info", infoMessage);
                }

                // Add or update the device with the current timestamp
                edgeData.put(device, jDevice);
                location.put(edge, edgeData);
                funcAlarm.put("location", location);

            } else {
                funcAlarm.put("time", Instant.now());
                if (infoMessage != null){
                    funcAlarm.put("info", infoMessage);
                }
            }
        } else {
            // Handle the case when alarmStatus is false
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

                JSONObject jDevice = edgeData.getJSONObject(device);

                // Check the timestamp for the device
                Instant alarmTime = (Instant) jDevice.get("time");

                if (Duration.between(alarmTime, Instant.now()).getSeconds() >= timeout) {
                    // Remove the device if the timeout has passed
                    edgeData.remove(device);
                    if (edgeData.isEmpty()) {
                        location.remove(edge);
                    }
                    if (location.isEmpty()) {
                        funcAlarm.put("alarm", false);
                    }
                }
            }
        }

        // Update the alarms attribute and write to file
        alarms.put(funcLabel, funcAlarm);
        writeToFile();
        db.writeAlarmDB(Instant.now(), funcLabel, edge, device, alarmStatus, infoMessage);
    }


    private void writeToFile() {
        try (FileWriter fileWriter = new FileWriter(alarmFilePath)) {
            fileWriter.write(alarms.toString(4));
            System.out.println("[AlarmHandler] Alarm written to " + alarmFilePath);
        } catch (IOException e) {
            System.err.println("[AlarmHandler] Failed to write alarm to file: " + e.getMessage());
        }
    }

    public void setTimeout(String timeValue) {
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

