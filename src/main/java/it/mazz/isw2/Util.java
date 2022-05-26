package it.mazz.isw2;

import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.Unirest;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
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

    private List<String> listDiff(Repository repository, Git git, String oldCommit, String newCommit) throws GitAPIException, IOException {
        List<String> files = new LinkedList<>();
        final List<DiffEntry> diffs = git.diff()
                .setOldTree(prepareTreeParser(repository, oldCommit))
                .setNewTree(prepareTreeParser(repository, newCommit))
                .call();
        for (DiffEntry diff : diffs)
            files.add(diff.getNewPath());

        return files;
    }

    private AbstractTreeIterator prepareTreeParser(Repository repository, String objectId) throws IOException {
        // from the commit we can build the tree which allows us to construct the TreeParser
        //noinspection Duplicates
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(repository.resolve(objectId));
            RevTree tree = walk.parseTree(commit.getTree().getId());

            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }

            walk.dispose();

            return treeParser;
        }
    }

    public List<String> getCommitFiles(Repository repository, Git git, String sha) {
        ObjectId objectIdCommit = null;
        try {
            objectIdCommit = repository.resolve(sha);
        } catch (IOException e) {
            e.printStackTrace();

        }
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit child = revWalk.parseCommit(objectIdCommit);
            RevCommit parent = getParent(child, repository);

            return listDiff(repository, git, parent.getName(), child.getName());
        } catch (IOException | GitAPIException e) {
            return Collections.emptyList();
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

    public RevCommit getParent(RevCommit child, Repository repository) throws IOException {
        RevCommit parent;
        try {
            parent = child.getParent(0);
        } catch (ArrayIndexOutOfBoundsException e) {
            try (RevWalk revWalk = new RevWalk(repository)) {
                parent = revWalk.parseCommit(repository.resolve("4b825dc642cb6eb9a060e54bf8d69288fbee4904"));
            }
        }
        return parent;
    }

    public Commit createCommit(RevCommit revCommit, Repository repository, Git git) {
        List<String> files = getCommitFiles(repository, git, revCommit.getName());
        return new Commit(
                revCommit.getName(),
                revCommit.getCommitterIdent().getWhen(),
                revCommit.getCommitterIdent(),
                revCommit.getFullMessage(),
                files);
    }
}
