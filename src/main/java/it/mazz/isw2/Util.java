package it.mazz.isw2;

import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

public class Util {

    private static Util instance = null;

    private Util() {
    }

    public static Util getInstance() {
        if (instance == null)
            instance = new Util();
        return instance;
    }

    public void listFiles(String directoryName, List<File> files) {
        File directory = new File(directoryName);

        // Get all files from a directory.
        File[] fList = directory.listFiles();
        if (fList != null)
            for (File file : fList) {
                if (file.isFile()) {
                    files.add(file);
                } else if (file.isDirectory()) {
                    listFiles(file.getAbsolutePath(), files);
                }
            }
    }

    public JSONObject readJsonFromUrl(String url) throws JSONException {
        HttpResponse<JsonNode> resp = Unirest.get(url).asJson();
        return new JSONObject(resp.getBody().toString());
    }
}
