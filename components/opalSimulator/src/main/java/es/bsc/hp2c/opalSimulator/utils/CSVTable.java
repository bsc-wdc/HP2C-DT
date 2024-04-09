package es.bsc.hp2c.opalSimulator.utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CSVTable {
    private List<String> edgeNames;
    private List<String> deviceNames;
    private List<List<Float>> data;

    public CSVTable(String csvFilePath) {
        edgeNames = new ArrayList<>();
        deviceNames = new ArrayList<>();
        data = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvFilePath))) {
            // Read the first line to get edge names
            String[] edges = br.readLine().split(",");
            for (int i = 1; i < edges.length; i++) {
                edgeNames.add(edges[i].replaceAll("\\s", ""));
            }

            // Read the second line to get device names
            String[] devices = br.readLine().split(",");
            for (int i = 1; i < devices.length; i++) {
                deviceNames.add(devices[i].replaceAll("\\s", ""));
            }

            // Read remaining lines to populate data
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) {
                    return;
                }
                String[] values = line.split(",", -1);
                List<Float> rowData = new ArrayList<>();
                for (int i = 1; i < values.length; i++) {
                    String val = values[i].replaceAll("\\s", "");
                    Float value = Float.NEGATIVE_INFINITY;
                    if (!val.isEmpty()) value = Float.parseFloat(val);
                    rowData.add(value);
                }
                data.add(rowData);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<String> getEdgeNames() {
        return edgeNames;
    }

    public List<String> getDeviceNames() {
        return deviceNames;
    }

    public List<List<Float>> getData() {
        return data;
    }

    public List<Float> getRow(int rowIndex) {
        return data.get(rowIndex);
    }

    public void printTable() {
        // Print edge names, device names, and data
        for (int i = 0; i < edgeNames.size(); i++) {
            System.out.print(edgeNames.get(i) + "\t" + deviceNames.get(i) + "\t");
            for (int j = 0; j < data.get(i).size(); j++) {
                System.out.print(data.get(i).get(j) + "\t");
            }
            System.out.println();
        }
    }
}
