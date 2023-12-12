package es.bsc.hp2c.edge.utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public final class FileUtils {
    private FileUtils(){}

    /**
     * Read the provided JSON file to return the edge label.
     * @param setupFile Path of the setup file in String format
     * @return the name of the edge
     */
    public static String readEdgeLabel(String setupFile) {
        InputStream is = null;
        try {
            is = new FileInputStream(setupFile);
        } catch (FileNotFoundException e) {
            System.err.println("Error loading file " + setupFile + ": " + e.getMessage());
            throw new RuntimeException(e);
        }
        JSONTokener tokener = new JSONTokener(is);
        JSONObject object = new JSONObject(tokener);
        JSONObject jGlobProp = object.getJSONObject("global-properties");
        String edgeLabel = jGlobProp.optString("label", "");
        if (edgeLabel.isEmpty()){
            throw new JSONException("Malformed JSON. edge label must be specified inside global-properties");
        }
        return edgeLabel;
    }
}
