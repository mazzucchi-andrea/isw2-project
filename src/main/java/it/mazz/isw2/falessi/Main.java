package it.mazz.isw2.falessi;

import com.opencsv.CSVWriter;
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class Main {

    public static void main(String[] args) throws IOException, GitAPIException, ParseException {
        String projName = "OPENJPA";
        String username = "";
        String token = "";

        //Get bug tickets from Jira
        //List<Ticket> tickets = getTickets(projName);


        //Open Git Repo
        Git git = null;
        if (projName.equals("OPENJPA")){
            File repo = new File("./openjpa");
            if (repo.exists()){
                FileUtils.deleteDirectory(repo);
            }
            git = Git.cloneRepository().setURI("https://github.com/apache/openjpa.git").call();
        }

        // Get GitHub tags with related commit from GitHub API
        List<Tag> gitHubTags = getGitHubTags(projName, username, token);

        //Get versions from JIRA https://issues.apache.org/jira/rest/api/2/project/{projName}/version
        List<Version> jiraVersions = getJiraVersions(projName);

        //Get only jiraVersions with matching commit
        int i = 0;
        while (i < jiraVersions.size()){
            Version version = jiraVersions.get(i);
            for (Tag tag: gitHubTags){
                if (version.getName().equals(tag.getName())){
                    version.setCommit(tag.getCommitID());
                    if (version.getReleaseDate() == null)
                        version.setReleaseDate(tag.getDate());
                }
            }
            //No version detected for OPENJPA
//            if (version.getCommit() == null){
//                Iterable<RevCommit> commits = git.log().call();
//                for (RevCommit commit : commits) {
//                    Map<ObjectId, String> namedCommits = git.nameRev().addPrefix("refs/tags/").add(commit).call();
//                    if (namedCommits.containsKey(commit.getId()) &&
//                            namedCommits.get(commit.getId()).equals(version.getName())) {
//                        version.setCommit(commit.getName());
//                    }
//                }
//            }
            if (version.getCommit() == null) {
                jiraVersions.remove(i);
            } else {
                i++;
            }
        }
        File file = new File("./" +projName +".csv");
        FileWriter outputfile = new FileWriter(file);
        CSVWriter writer = new CSVWriter(outputfile);
        String[] header =
                {"Version", "File_Name", "LOC", "LOC_touched",	"NR", "NFix", "NAuth", "LOC_added", "MAX_LOC_added",
                        "AVG_LOC_added", "Churn", "MAX_Churn", "AVG_Churn", "ChgSetSize", "MAX_ChgSet", "AVG_ChgSet",
                        "Age", "WeightedAge",	"Buggy" };
        writer.writeNext(header);
        for (Version version : jiraVersions) {
            git.checkout().setName(version.getCommit()).setCreateBranch(false).call();
            List<File> files = new ArrayList<>();
            listf("./" +projName.toLowerCase(), files);
            for (File f : files){
                if (!f.getName().contains("java") || f.getName().contains("test")) continue;
                String file_name = f.getName();
                long LOC = countLOC(f);
            }
        }
        writer.close();
        return;
    }

    //Count physical LOC
    private static long countLOC(File f) {
        long lines = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            String line = reader.readLine();
            while (line != null) {
                if ("".equals(line) ||
                        line.startsWith("/*") ||
                        line.startsWith(" *"))
                    continue;
                line = reader.readLine();
                lines++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lines;
    }

    private static void listf(String directoryName, List<File> files) {
        File directory = new File(directoryName);

        // Get all files from a directory.
        File[] fList = directory.listFiles();
        if(fList != null)
            for (File file : fList) {
                if (file.isFile()) {
                    files.add(file);
                } else if (file.isDirectory()) {
                    listf(file.getAbsolutePath(), files);
                }
            }
    }

    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    private static JSONObject readJsonFromUrl(String url, String username, String token) throws JSONException {
        HttpResponse<JsonNode> resp;
        if (username == null && token == null){
            resp = Unirest.get(url).asJson();
        } else {
            resp = Unirest.get(url).basicAuth(username, token).asJson();
        }
        return  new JSONObject(resp.getBody().toString());
    }

    private static JSONArray readJsonArrayFromUrl(String url, String username, String token) throws JSONException {
        HttpResponse<JsonNode> resp;
        if (username == null && token == null){
            resp = Unirest.get(url).asJson();
        } else {
            resp = Unirest.get(url).basicAuth(username, token).asJson();
        }
        return new JSONArray(resp.getBody().toString());
    }

    private static List<Ticket> getTickets(String projName) throws JSONException, ParseException {
        int j;
        int i = 0;
        int total;
        List<Ticket> tickets = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        //Get JSON API for closed bugs w/ AV in the project
        do {
            //Only gets a max of 1000 at a time, so must do this multiple times if bugs >1000
            j = i + 1000;
            String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22"
                    + projName + "%22AND%22issueType%22=%22Bug%22AND(%22status%22=%22closed%22OR"
                    + "%22status%22=%22resolved%22)AND%22resolution%22=%22fixed%22&fields=key,resolutiondate,versions,created&startAt="
                    + i + "&maxResults=" + j;
            JSONObject json = readJsonFromUrl(url, null, null);
            JSONArray issues = json.getJSONArray("issues");
            total = json.getInt("total");
            for (; i < total && i < j; i++) {
                //Iterate through each bug
                JSONObject jsonTicket = issues.getJSONObject(i % 1000);
                JSONObject ticketFields = (JSONObject) jsonTicket.get("fields");
                Ticket ticket = new Ticket("Bug");
                ticket.setKey(jsonTicket.get("key").toString());
                ticket.setCreated(sdf.parse(ticketFields.get("created").toString()));
                ticket.setResolved(sdf.parse(ticketFields.get("resolutiondate").toString()));
                url = "https://issues.apache.org/jira/rest/api/2/issue/" + ticket.getKey();
                JSONObject issue = readJsonFromUrl(url, null, null);
                JSONObject issueFields = (JSONObject) issue.get("fields");
                ticket.setFixedVersions(getVersionsList(issueFields, "fixVersions"));
                ticket.setAffectedVersions(getVersionsList(issueFields, "versions"));
                tickets.add(ticket);
            }
        } while (i < total);
        return tickets;
    }

    private static List<String> getVersionsList(JSONObject issueFields, String fieldName) {
        List<String> versions = new ArrayList<>();
        JSONArray versionJsonArray = (JSONArray) issueFields.get(fieldName);
        for (int k = 0; k < versionJsonArray.length(); k++) {
            JSONObject fixedVersion = (JSONObject) (versionJsonArray.get(k));
            versions.add(fixedVersion.get("name").toString());
        }
        return versions;
    }

    private static List<Version> getJiraVersions(String projName) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        JSONObject jiraVersionsJson = readJsonFromUrl("https://issues.apache.org/jira/rest/api/2/project/" +
                projName + "/version", null, null);
        JSONArray jiraVersionsArray = jiraVersionsJson.optJSONArray("values");
        List<Version> jiraVersions = new ArrayList<>();
        for (int i = 0; i < jiraVersionsArray.length(); i++){
            JSONObject jiraVersion = jiraVersionsArray.getJSONObject(i);
            Integer id = jiraVersion.getInt("id");
            String name = (String) jiraVersion.get("name");
            boolean archived = jiraVersion.getBoolean("archived");
            boolean released = jiraVersion.getBoolean("released");
            Date releaseDate;
            try {
                releaseDate = sdf.parse(jiraVersion.get("releaseDate").toString());
            } catch (Exception e){
                System.out.println("Missing releaseDate version: " + name);
                releaseDate = null;
            }

            Version version = new Version(id, name, archived, released, releaseDate);
            jiraVersions.add(version);
        }
        return jiraVersions;
    }

    private static List<Tag> getGitHubTags(String projName, String username, String token) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        List<Tag> gitHubVersions = new ArrayList<>();

        JSONArray githubTags = readJsonArrayFromUrl(
                "https://api.github.com/repos/apache/"
                + projName.toLowerCase() +
                        "/git/refs/tags", username, token);

        for (int i = 0; i < githubTags.length(); i++){
            JSONObject tagRef = githubTags.getJSONObject(i).getJSONObject("object");
            JSONObject tagJson = readJsonFromUrl(
                    "https://api.github.com/repos/apache/" +
                            projName.toLowerCase() +
                            "/git/tags/"+tagRef.get("sha"), username, token);
            Tag tag = new Tag((String) tagJson.get("tag"), (String) tagJson.getJSONObject("object").get("sha"));
            tag.setDate(sdf.parse(tagJson.getJSONObject("tagger").get("date").toString()));
            gitHubVersions.add(tag);
        }
        return gitHubVersions;
    }
}
