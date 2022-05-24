package it.mazz.isw2;

import com.opencsv.CSVWriter;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;

public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    private static final Util util = Util.getInstance();

    public static void main(String[] args) {
        String projName = "OPENJPA";
        LOGGER.info("Project: {}", projName);

        if (args.length < 2) {
            LOGGER.error("username and token are mandatory");
            return;
        }
        String username = args[0];
        String token = args[1];

        util.setUsername(username);
        util.setToken(token);

        LOGGER.info("Delete old local repository");
        File repo = checkAndDeleteLocalRepo(projName);
        if (repo == null) return;

        LOGGER.info("Checkout latest Project Revision");
        Git git = checkoutProjGit(projName);
        if (git == null) return;

        LOGGER.info("Retrieve versions tags from GitHub");
        List<Tag> gitHubTags = getGitHubTags(projName);
        LOGGER.info("GitHub version tags list size {}", gitHubTags.size());

        LOGGER.info("Retrieve Versions from Jira");
        List<Version> jiraVersions = getJiraVersions(projName);
        LOGGER.info("Version list size: {}", jiraVersions.size());

        LOGGER.info("Merge GitHub tags, Jira Versions and ref/tags to take commits and missing release date");
        getReleaseCommit(jiraVersions, gitHubTags, git);

        LOGGER.info("Retrieve Tickets from Jira");
        List<Ticket> tickets = getTickets(projName, jiraVersions);
        LOGGER.info("Ticket list size: {}", tickets.size());

        try (Repository repository = git.getRepository()) {
            LOGGER.info("Add commits with java files to tickets");
            addCommitToTickets(projName, tickets, repository);
            removeTicketsWithoutCommits(tickets);
            LOGGER.info("Ticket list new size: {}", tickets.size());
        }

        LOGGER.info("Remove invalid and newer versions");
        removeInvalidVersions(jiraVersions, projName);
        LOGGER.info("Version list size: {}", jiraVersions.size());

        //Dataset generation
        File file = new File("./" + projName + ".csv");
        FileWriter outputFile;
        try {
            outputFile = new FileWriter(file);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        try (CSVWriter writer = new CSVWriter(outputFile)) {
            String[] header =
                    {"Version", "File_Name", "LOC", "LOC_touched", "NR", "NFix", "NAuth", "LOC_added", "MAX_LOC_added",
                            "AVG_LOC_added", "Churn", "MAX_Churn", "AVG_Churn", "ChgSetSize", "MAX_ChgSet",
                            "AVG_ChgSet", "Buggy"};
            writer.writeNext(header);
            writeData(writer, projName, git, jiraVersions, tickets);
        } catch (IOException | GitAPIException e) {
            e.printStackTrace();
        }

        if (repo.exists()) {
            try {
                FileUtils.deleteDirectory(repo);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void removeInvalidVersions(List<Version> jiraVersions, String projName) {
        int i = 0;
        while (i < jiraVersions.size()) {
            Version version = jiraVersions.get(i);
            //Remove invalid and most recent versions
            if (version.getCommit() == null || version.getReleaseDate() == null ||
                    (projName.equals("OPENJPA") && version.getReleaseDate().getTime() > 1451602800000L)) {
                jiraVersions.remove(i);
            } else {
                i++;
            }
        }

    }

    private static void removeTicketsWithoutCommits(List<Ticket> tickets) {
        tickets.removeIf(ticket -> ticket.getCommits().isEmpty());
    }

    private static File checkAndDeleteLocalRepo(String projName) {
        File repo = new File("./" + projName.toLowerCase());
        if (repo.exists()) {
            try {
                FileUtils.deleteDirectory(repo);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
        return repo;
    }

    private static Git checkoutProjGit(String projName) {
        Git git;
        try {
            git = Git.cloneRepository().setURI("https://github.com/apache/" + projName.toLowerCase() + ".git").call();
        } catch (GitAPIException e) {
            e.printStackTrace();
            return null;
        }
        return git;
    }

    private static List<Tag> getGitHubTags(String projName) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        List<Tag> gitHubVersions = new ArrayList<>();

        JSONArray githubTags = util.readJsonArrayFromUrl(
                "https://api.github.com/repos/apache/"
                        + projName.toLowerCase() +
                        "/git/refs/tags", true);

        for (int i = 0; i < githubTags.length(); i++) {
            JSONObject tagRef = githubTags.getJSONObject(i).getJSONObject("object");
            JSONObject tagJson = util.readJsonFromUrl(
                    "https://api.github.com/repos/apache/" +
                            projName.toLowerCase() +
                            "/git/tags/" + tagRef.get("sha"), true);
            Tag tag = new Tag((String) tagJson.get("tag"), (String) tagJson.getJSONObject("object").get("sha"));
            try {
                tag.setDate(sdf.parse(tagJson.getJSONObject("tagger").get("date").toString()));
            } catch (ParseException e) {
                e.printStackTrace();
            }
            gitHubVersions.add(tag);
        }
        return gitHubVersions;
    }

    private static List<Version> getJiraVersions(String projName) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        JSONObject jiraVersionsJson = util.readJsonFromUrl(
                "https://issues.apache.org/jira/rest/api/2/project/" +
                        projName + "/version", false);
        JSONArray jiraVersionsArray = jiraVersionsJson.optJSONArray("values");
        List<Version> jiraVersions = new ArrayList<>();
        for (int i = 0; i < jiraVersionsArray.length(); i++) {
            JSONObject jiraVersion = jiraVersionsArray.getJSONObject(i);
            Integer id = jiraVersion.getInt("id");
            String name = (String) jiraVersion.get("name");
            boolean released = jiraVersion.getBoolean("released");
            Date releaseDate;
            try {
                releaseDate = sdf.parse(jiraVersion.get("releaseDate").toString());
            } catch (Exception e) {
                releaseDate = null;
            }
            Version version = new Version(id, i, name, released, releaseDate);
            jiraVersions.add(version);
        }
        return jiraVersions;
    }

    private static List<Ticket> getTickets(String projName, List<Version> jiraVersions) {
        int j;
        int i = 0;
        int total;
        List<Ticket> tickets = new LinkedList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        //Get JSON API for closed bugs w/ AV in the project
        do {
            //Only gets a max of 1000 at a time, so must do this multiple times if bugs >1000
            j = i + 1000;
            String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22"
                    + projName + "%22AND%22issueType%22=%22Bug%22AND(%22status%22=%22closed%22OR"
                    + "%22status%22=%22resolved%22)AND%22resolution%22=%22fixed%22&fields=" +
                    "key,resolutiondate,versions,created&startAt=" + i + "&maxResults=" + j;
            JSONObject json = util.readJsonFromUrl(url, false);
            JSONArray issues = json.getJSONArray("issues");
            total = json.getInt("total");
            for (; i < total && i < j; i++) {
                //Iterate through each bug
                JSONObject jsonTicket = issues.getJSONObject(i % 1000);
                JSONObject ticketFields = (JSONObject) jsonTicket.get("fields");
                Ticket ticket = new Ticket();
                ticket.setKey(jsonTicket.get("key").toString());
                try {
                    ticket.setCreated(sdf.parse(ticketFields.get("created").toString()));
                    ticket.setResolved(sdf.parse(ticketFields.get("resolutiondate").toString()));
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                url = "https://issues.apache.org/jira/rest/api/2/issue/" + ticket.getKey();
                JSONObject issue = util.readJsonFromUrl(url, false);
                JSONObject issueFields = (JSONObject) issue.get("fields");
                List<Version> fixedVersions = getVersionsList(issueFields, "fixVersions", jiraVersions);
                List<Version> affectedVersions = getVersionsList(issueFields, "versions", jiraVersions);
                affectedVersions.removeAll(fixedVersions);
                ticket.setFixedVersions(fixedVersions);
                ticket.setAffectedVersions(affectedVersions);
                ticket.setOpeningVersion(jiraVersions);
                if (ticket.getFixedVersions().isEmpty() || ticket.getOpeningVersion() == null) continue;
                tickets.add(ticket);
            }
        } while (i < total);
        consistencyReviewTickets(tickets, jiraVersions);
        return tickets;
    }

    private static void addCommitToTickets(String projName, List<Ticket> tickets, Repository repository) {
        Collection<Ref> allRefs;
        try {
            allRefs = repository.getRefDatabase().getRefs();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        try (RevWalk revWalk = new RevWalk(repository)) {
            for (Ref ref : allRefs) {
                revWalk.markStart(revWalk.parseCommit(ref.getObjectId()));
            }
            for (RevCommit revCommit : revWalk) {
                if (!revCommit.getFullMessage().contains(projName)) continue;
                for (Ticket ticket : tickets) {
                    if (revCommit.getFullMessage().contains(ticket.getKey()) &&
                            revCommit.getCommitTime() < ticket.getResolved().getTime()) {
                        Commit commit = util.getCommit(revCommit.getName());
                        if (commit != null && commit.javaFileInCommit()) {
                            ticket.addCommit(commit);
                            break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void getReleaseCommit(List<Version> jiraVersions, List<Tag> gitHubTags, Git git) {
        for (Version version : jiraVersions) {
            //Get release commit and missing release date  in gitHubTags
            for (Tag tag : gitHubTags) {
                if (version.getName().equals(tag.getName())) {
                    version.setCommit(tag.getSha());
                    version.setReleaseDate(tag.getDate());
                }
            }

            if (version.getCommit() == null)
                findVersionInRepoLog(version, git);
        }
    }

    private static void consistencyReviewTickets(List<Ticket> tickets, List<Version> jiraVersions) {
        int i = 0;
        List<Float> pList = new ArrayList<>();
        while (i < tickets.size()) {
            Ticket t = tickets.get(i);
            if (t.consistencyCheckAffectedVersion()) {
                t.setInjectedVersion(t.getAffectedVersions().get(0));
                Float fv = Float.valueOf(t.getFixedVersions().get(t.getFixedVersions().size() - 1).getIncremental());
                Float iv = Float.valueOf(t.getInjectedVersion().getIncremental());
                Float ov = Float.valueOf(t.getOpeningVersion().getIncremental());
                pList.add((fv - iv) / (fv - ov));
            } else {
                if (pList.isEmpty()) {
                    tickets.remove(i);
                    continue;
                }
                calculateInjectedVersion(t, pList, jiraVersions);
            }
            i++;
        }
    }

    private static void calculateInjectedVersion(Ticket t, List<Float> pList, List<Version> jiraVersions) {
        Float fv = Float.valueOf(t.getFixedVersions().get(t.getFixedVersions().size() - 1).getIncremental());
        Float ov = Float.valueOf(t.getOpeningVersion().getIncremental());
        Float p = 0F;
        for (Float f : pList)
            p += f;
        p = p / pList.size();
        Integer iv = Math.round(fv - (fv - ov) * p);
        t.setAffectedVersions(new LinkedList<>());
        for (Version version : jiraVersions) {
            if (version.getIncremental() <= t.getOpeningVersion().getIncremental() && version.getIncremental() >= iv)
                t.addAffectedVersions(version);
            if (version.getIncremental().equals(iv)) {
                t.setInjectedVersion(version);
                break;
            }
        }

    }

    private static List<Version> getVersionsList(JSONObject issueFields, String fieldName, List<Version> jiraVersions) {
        List<Version> versions = new ArrayList<>();
        JSONArray versionJsonArray = (JSONArray) issueFields.get(fieldName);
        for (int k = 0; k < versionJsonArray.length(); k++) {
            JSONObject version = (JSONObject) (versionJsonArray.get(k));
            for (Version v : jiraVersions) {
                if (v.getName().equals(version.get("name").toString())) {
                    versions.add(v);
                    break;
                }
            }
        }
        return versions;
    }

    //Get release commit on repo
    private static void findVersionInRepoLog(Version version, Git git) {
        Iterable<RevCommit> commits;
        try {
            commits = git.log().call();
            for (RevCommit commit : commits) {
                Map<ObjectId, String> namedCommits = git.nameRev().addPrefix("refs/tags/").add(commit).call();
                if (namedCommits.containsKey(commit.getId()) &&
                        namedCommits.get(commit.getId()).contains(version.getName())) {
                    version.setCommit(commit.getName());
                    if (version.getReleaseDate() == null)
                        version.setReleaseDate(Date.from(Instant.ofEpochSecond(commit.getCommitTime())));
                }
                if (namedCommits.size() > 0 && version.getName().compareTo(namedCommits.get(commit.getId())) < 0) break;
            }
        } catch (GitAPIException | MissingObjectException e) {
            e.printStackTrace();
        }
    }

    private static String[] getDatasetFeatures(File f, Version currVersion, Version prevVersion,
                                               String projName, List<Ticket> tickets, Git git) {
        Features features = new Features(currVersion.getIncremental(), f.getName());
        features.setLoc(f);
        String path = f.getPath().substring(f.getPath().indexOf(projName.toLowerCase()) + projName.length() + 1);
        long currVersionReleaseDate = currVersion.getReleaseDate().getTime();
        if (prevVersion == null) {
            features.calculateFeaturesByCommits(git, path, currVersionReleaseDate, 0L);
        } else {
            long prevVersionReleaseDate = prevVersion.getReleaseDate().getTime();
            features.calculateFeaturesByCommits(git, path, currVersionReleaseDate, prevVersionReleaseDate);
        }
        features.setFixes(currVersion, tickets, path);
        features.setBuggy(currVersion, tickets, path);
        return features.toStringArray();
    }

    private static void writeData(CSVWriter writer, String projName, Git git, List<Version> jiraVersions,
                                  List<Ticket> tickets) throws GitAPIException {
        for (int i = 0; i < jiraVersions.size(); i++) {
            Version currVersion = jiraVersions.get(i);
            Version prevVersion = null;
            if (i - 1 > 0) {
                prevVersion = jiraVersions.get(i - 1);
            }
            git.checkout().setName(currVersion.getCommit()).setCreateBranch(false).call();
            List<File> files = new ArrayList<>();
            util.listf("./" + projName.toLowerCase(), files);
            for (File f : files) {
                if (!f.getName().contains(".java") ||
                        f.getName().contains("test") || f.getPath().contains("test")) continue;
                writer.writeNext(getDatasetFeatures(f, currVersion, prevVersion, projName, tickets, git));
            }
            writer.flushQuietly();
        }
    }
}
