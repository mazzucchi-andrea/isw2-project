package it.mazz.isw2;

import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
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

    public RevWalk getRevWalkForAllCommits(Repository repository) {
        Collection<Ref> allRefs;
        try {
            allRefs = repository.getRefDatabase().getRefs();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        try (RevWalk revWalk = new RevWalk(repository)) {
            for (Ref ref : allRefs) {
                revWalk.markStart(revWalk.parseCommit(ref.getObjectId()));
            }
            return revWalk;
        } catch (IOException e) {
            return null;
        }
    }

    public RevCommit getRevCommit(String sha, Repository repository) throws IOException {
        ObjectId objectId = repository.resolve(sha);
        if (objectId != null) {
            try (RevWalk revWalk = new RevWalk(repository)) {
                return revWalk.parseCommit(objectId);
            }
        }
        return null;
    }
}
