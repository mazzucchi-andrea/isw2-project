package it.mazz.isw2;

import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;

public class Util {

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

        JSONObject jsonObject = readJsonFromUrl("https://api.github.com/repos/apache/openjpa/commits/" + sha, true);

        JSONObject jsonObjectAuthor;
        try {
            jsonObjectAuthor = jsonObject.getJSONObject("author");
        } catch (JSONException e) {
            try {
                jsonObjectAuthor = jsonObject.getJSONObject("committer");
            } catch (JSONException je) {
                jsonObjectAuthor = new JSONObject("{\"login\":\"unknown\", \"id\":99999}");
            }
        }
        commit.setAuthor(jsonObjectAuthor.getString("login"));
        commit.setAuthorId(jsonObjectAuthor.getInt("id"));

        try {
            commit.setMessage(jsonObject.getJSONObject("commit").getString("message"));
        } catch (JSONException e) {
            e.printStackTrace();
            System.out.println(jsonObject.toString(2));
            return null;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        try {
            commit.setDate(sdf.parse(jsonObject.getJSONObject("commit").getJSONObject("author").getString("date")));
        } catch (ParseException e) {
            e.printStackTrace();
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
