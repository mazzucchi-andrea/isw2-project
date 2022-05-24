package it.mazz.isw2;

import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;

public class Util {

    private static final Logger LOGGER = LoggerFactory.getLogger(Util.class);
    private static Util instance = null;
    private String username;
    private String token;

    private Util() {
    }

    public static Util getInstance() {
        if (instance == null)
            instance = new Util();
        return instance;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Commit getCommit(String sha) {
        Commit commit = new Commit();

        JSONObject jsonObject = null;
        while (jsonObject == null) {
            try {
                jsonObject = readJsonFromUrl("https://api.github.com/repos/apache/openjpa/commits/" + sha, true);
            } catch (UnirestException e) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }

        try {
            commit.setMessage(jsonObject.getJSONObject("commit").getString("message"));
        } catch (JSONException e) {
            e.printStackTrace();
            LOGGER.error(jsonObject.toString(2));
            return null;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        try {
            commit.setDate(sdf.parse(jsonObject.getJSONObject("commit").getJSONObject("author").getString("date")));
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }

        JSONArray jsonArrayFiles = jsonObject.getJSONArray("files");
        for (int i = 0; i < jsonArrayFiles.length(); i++) {
            JSONObject jsonFile = jsonArrayFiles.getJSONObject(i);
            FileStat fileStat = new FileStat();
            fileStat.setFilePath(jsonFile.getString("filename"));
            fileStat.setLocAdded(jsonFile.getInt("additions"));
            fileStat.setLocDeleted(jsonFile.getInt("deletions"));
            fileStat.setLocModified(jsonFile.getInt("changes"));
            commit.addFileStat(fileStat);
        }

        return commit;
    }

    public void listf(String directoryName, List<File> files) {
        File directory = new File(directoryName);

        // Get all files from a directory.
        File[] fList = directory.listFiles();
        if (fList != null)
            for (File file : fList) {
                if (file.isFile()) {
                    files.add(file);
                } else if (file.isDirectory()) {
                    listf(file.getAbsolutePath(), files);
                }
            }
    }

    public JSONObject readJsonFromUrl(String url, boolean github) throws JSONException {
        HttpResponse<JsonNode> resp;
        if (github) {
            resp = Unirest.get(url).basicAuth(username, token).asJson();
        } else {
            resp = Unirest.get(url).asJson();
        }
        return new JSONObject(resp.getBody().toString());
    }

    public JSONArray readJsonArrayFromUrl(String url, boolean github) throws JSONException {
        HttpResponse<JsonNode> resp;
        if (github) {
            resp = Unirest.get(url).basicAuth(username, token).asJson();
        } else {
            resp = Unirest.get(url).asJson();
        }
        return new JSONArray(resp.getBody().toString());
    }
}
