package it.mazz.isw2;

import com.opencsv.CSVWriter;
import it.mazz.isw2.entities.Commit;
import it.mazz.isw2.entities.Features;
import it.mazz.isw2.entities.Ticket;
import it.mazz.isw2.entities.Version;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

public class DatasetGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatasetGenerator.class);

    private static final Util util = Util.getInstance();

    private static DatasetGenerator instance = null;

    private DatasetGenerator() {
    }

    public static DatasetGenerator getInstance() {
        if (instance == null)
            instance = new DatasetGenerator();
        return instance;
    }

    public void generateDataset(String projName) {
        LOGGER.info("Project: {}", projName);

        LOGGER.info("Delete old local repository");
        File repo = checkAndDeleteLocalRepo(projName);
        if (repo == null) {
            LOGGER.error("Old repository delete error");
            return;
        }

        LOGGER.info("Checkout latest Project Revision");
        Git git = checkoutProjGit(projName);
        if (git == null) {
            LOGGER.error("Git checkout error");
            return;
        }

        LOGGER.info("Retrieve Versions from Jira");
        List<Version> versions = getJiraVersions(projName);
        LOGGER.info("Version list size: {}", versions.size());

        LOGGER.info("Merge Jira Versions and ref/tags to take commits");
        getReleaseCommit(versions, git);

        List<Commit> commits;
        try (Repository repository = git.getRepository()) {
            LOGGER.info("Get All Commits");
            commits = getAllCommits(repository, git, versions);
        }

        LOGGER.info("Retrieve Tickets from Jira");
        List<Ticket> tickets = getTickets(projName, versions);
        LOGGER.info("Ticket list size: {}", tickets.size());


        LOGGER.info("Add commits with java files to tickets");
        addCommitToTickets(projName, tickets, commits);
        removeTicketsWithoutCommits(tickets);
        LOGGER.info("Ticket list new size: {}", tickets.size());

        LOGGER.info("Remove invalid and newer versions");
        removeInvalidVersions(versions);
        LOGGER.info("Version list size: {}", versions.size());

        for (Version version : versions)
            LOGGER.info("Version {} Commits list size: {}", version.getName(), version.getCommits().size());

        LOGGER.info("Get all file instance for every version");
        List<Features> featuresList = getAllFeatures(projName, git, versions, tickets);
        LOGGER.info("Features list size: {}", featuresList.size());

        LOGGER.info("Create walk-forward dataset files");
        String dirPath = "./output/" + projName + "-datasets/";

        try {
            Path path = Paths.get(dirPath);
            Files.createDirectories(path);
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
            return;
        }

        createDatasets(projName, dirPath, versions, featuresList);

        if (repo.exists()) {
            try {
                FileUtils.deleteDirectory(repo);
            } catch (IOException e) {
                LOGGER.warn(e.getMessage());
            }
        }

        LOGGER.info("END");
    }

    private void createDatasets(String projName, String path, List<Version> versions, List<Features> featuresList) {
        String arffHeader = "@relation " + projName + "\n\n" +
                "@attribute LOC numeric\n" +
                "@attribute LOC_touched numeric\n" +
                "@attribute NR numeric\n" +
                "@attribute NFix numeric\n" +
                "@attribute NAuth numeric\n" +
                "@attribute LOC_added numeric\n" +
                "@attribute MAX_LOC_added numeric\n" +
                "@attribute AVG_LOC_added numeric\n" +
                "@attribute Churn numeric\n" +
                "@attribute MAX_Churn numeric\n" +
                "@attribute AVG_Churn numeric\n" +
                "@attribute ChgSetSize numeric\n" +
                "@attribute MAX_ChgSet numeric\n" +
                "@attribute AVG_ChgSet numeric\n" +
                "@attribute Buggy {no, yes}\n\n" +
                "@data\n";

        String header = "Version,Filename,LOC,LOC_touched,NR,NFix,NAuth,LOC_added,MAX_LOC_added,AVG_LOC_added," +
                "Churn,MAX_Churn,AVG_Churn,ChgSetSize,MAX_ChgSet,AVG_ChgSet,Buggy\n";


        for (int i = 0; i < versions.size(); i++) {
            LOGGER.info("Write data for version {} of {}", i + 1, versions.size());

            String versionNum = String.valueOf(i);
            if (i < 10) {
                versionNum = "0".concat(String.valueOf(i));
            }

            File testFile = new File(path + projName + "-Run_" + versionNum + "_test" + ".arff");
            File trainFile = new File(path + projName + "-Run_" + versionNum + "_train" + ".arff");


            try (FileWriter outputTestFile = new FileWriter(testFile)) {
                outputTestFile.write(arffHeader);
                writeVersion(outputTestFile, versions.get(i).getIncremental(), featuresList);
            } catch (IOException e) {
                LOGGER.error(e.getMessage());
                return;
            }

            try (FileWriter outputTrainFile = new FileWriter(trainFile)) {
                outputTrainFile.write(arffHeader);
                writeVersions(outputTrainFile, versions.get(i).getIncremental(), featuresList);
            } catch (IOException e) {
                LOGGER.error(e.getMessage());
                return;
            }
        }

        File dataset = new File(("./" + projName + "-dataset.csv"));
        try (FileWriter outputTrainFile = new FileWriter(dataset)) {
            outputTrainFile.write(header);
            writeFullDataset(outputTrainFile, featuresList);
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
        }
    }

    private void writeFullDataset(FileWriter outputFile, List<Features> featuresList) throws IOException {
        try (CSVWriter writer = new CSVWriter(outputFile)) {
            for (Features features : featuresList) {
                writer.writeNext(features.toStringArrayForCSV());
            }
        }
    }

    private void writeVersions(FileWriter outputFile, int i, List<Features> featuresList) throws IOException {
        try (CSVWriter writer = new CSVWriter(outputFile)) {
            for (Features features : featuresList) {
                if (features.getVersion() < i) {
                    writer.writeNext(features.toStringArrayForArff());
                } else {
                    break;
                }
            }
        }
    }

    private void writeVersion(FileWriter outputFile, int i, List<Features> featuresList) throws IOException {
        try (CSVWriter writer = new CSVWriter(outputFile)) {
            for (Features features : featuresList) {
                if (features.getVersion() == i)
                    writer.writeNext(features.toStringArrayForArff());
            }
        }
    }

    private List<Features> getAllFeatures(String projName, Git git, List<Version> versions, List<Ticket> tickets) {
        List<Features> featuresList = new LinkedList<>();
        for (Version version : versions) {
            try {
                git.checkout().setName(version.getSha()).setCreateBranch(false).call();
            } catch (GitAPIException e) {
                LOGGER.warn(e.getMessage());
                return Collections.emptyList();
            }
            List<File> files = new ArrayList<>();
            util.listFiles("./" + projName.toLowerCase(), files);
            for (File f : files) {
                boolean java = Pattern.compile(Pattern.quote(".java"),
                        Pattern.CASE_INSENSITIVE).matcher(f.getName()).find();
                boolean testName = Pattern.compile(Pattern.quote("test"),
                        Pattern.CASE_INSENSITIVE).matcher(f.getName()).find();
                boolean testPath = Pattern.compile(Pattern.quote("test"),
                        Pattern.CASE_INSENSITIVE).matcher(f.getPath()).find();
                if (!java || testName || testPath) continue;
                featuresList.add(getFeatures(f, version, projName, tickets, git));
            }
        }
        return featuresList;
    }

    private List<Commit> getAllCommits(Repository repository, Git git, List<Version> versions) {
        List<Commit> commits = new LinkedList<>();
        RevWalk revWalk = util.getRevWalkForAllCommits(repository);
        for (RevCommit revCommit : revWalk) {
            Commit commit = new Commit(revCommit, repository, git);
            if (commit.javaFileInCommit()) {
                for (Version version : versions) {
                    try{
                    if (commit.getDate().after(version.getPreviousVersionReleaseDate()) && commit.getDate().before(version.getReleaseDate())) {
                        version.addCommit(commit);
                        break;
                    }}
                    catch (NullPointerException e){
                        e.printStackTrace();
                    }
                }
                commits.add(commit);
            }
        }
        return commits;
    }

    private void removeInvalidVersions(List<Version> jiraVersions) {
        int i = 0, size = jiraVersions.size();
        while (i < jiraVersions.size()) {
            Version version = jiraVersions.get(i);
            //Remove invalid and most recent versions
            if (version.getSha() == null || version.getReleaseDate() == null || i >= ((size / 2) - 1)) {
                jiraVersions.remove(i);
            } else {
                i++;
            }
        }
    }

    private void removeTicketsWithoutCommits(List<Ticket> tickets) {
        tickets.removeIf(ticket -> ticket.getCommits().isEmpty());
    }

    private File checkAndDeleteLocalRepo(String projName) {
        File repo = new File("./" + projName.toLowerCase());
        if (repo.exists()) {
            try {
                FileUtils.deleteDirectory(repo);
            } catch (IOException e) {
                LOGGER.error(e.getMessage());
                return null;
            }
        }
        return repo;
    }

    private Git checkoutProjGit(String projName) {
        Git git;
        try {
            git = Git.cloneRepository().setURI("https://github.com/apache/" + projName.toLowerCase() + ".git").call();
        } catch (GitAPIException e) {
            LOGGER.error(e.getMessage());
            return null;
        }
        return git;
    }

    private List<Version> getJiraVersions(String projName) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        JSONObject jiraVersionsJson = util.readJsonFromUrl(
                "https://issues.apache.org/jira/rest/api/2/project/" + projName + "/version");
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
            if (i - 1 >= 0)
                version.setPreviousVersionReleaseDate(jiraVersions.get(i - 1).getReleaseDate());
            else {
                version.setPreviousVersionReleaseDate(new GregorianCalendar(1900, Calendar.JANUARY, 1).getTime());
            }
        }
        return jiraVersions;
    }

    private List<Ticket> getTickets(String projName, List<Version> jiraVersions) {
        int j;
        int i = 0;
        int total;
        List<Ticket> tickets = new LinkedList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        //Get JSON API for closed bugs w/ AV in the project
        do {
            //Only gets a max of 1000 at a time, so must do this multiple times if bugs >1000
            j = i + 1000;
            String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22" + projName +
                    "%22AND%22issueType%22=%22Bug%22AND" +
                    "%28%22resolution%22%3D%22fixed%22OR%22resolution%22%3D%22done%22%29AND" +
                    "%28%22status%22%3D%22closed%22OR%22status%22%3D%22resolved%22OR%22status%22%3D%22done%22%29" +
                    "AND%22resolution%22=%22fixed%22&fields=" + "key,resolutiondate,versions,created&startAt=" + i
                    + "&maxResults=" + j;
            JSONObject json = util.readJsonFromUrl(url);
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
                    LOGGER.warn(e.getMessage());
                }
                url = "https://issues.apache.org/jira/rest/api/2/issue/" + ticket.getKey();
                JSONObject issue = util.readJsonFromUrl(url);
                JSONObject issueFields = (JSONObject) issue.get("fields");
                List<Version> fixedVersions = getVersionsList(issueFields, "fixVersions", jiraVersions);
                List<Version> affectedVersions = getVersionsList(issueFields, "versions", jiraVersions);
                affectedVersions.removeAll(fixedVersions);
                ticket.setFixedVersions(fixedVersions);
                ticket.setAffectedVersions(affectedVersions);
                ticket.setOpeningVersion(jiraVersions);
                if (ticket.getFixedVersions().isEmpty() || ticket.getOpeningVersion() == null)
                    continue;
                tickets.add(ticket);
            }
        } while (i < total);
        consistencyReviewTickets(tickets, jiraVersions);
        return tickets;
    }

    private void addCommitToTickets(String projName, List<Ticket> tickets, List<Commit> commits) {
        for (Commit commit : commits) {
            if (!commit.getMessage().contains(projName)) continue;
            for (Ticket ticket : tickets) {
                if (commit.getMessage().contains(ticket.getKey())) {
                    ticket.addCommit(commit);
                    break;
                }
            }
        }
    }

    private void getReleaseCommit(List<Version> jiraVersions, Git git) {
        List<Ref> refs;
        try {
            refs = git.tagList().call();
        } catch (GitAPIException e) {
            LOGGER.error(e.getMessage());
            return;
        }

        for (Version version : jiraVersions) {
            for (Ref ref : refs) {
                if (ref != null && ref.toString().contains(version.getName())) {
                    ObjectId objId = new ObjectId(0, 0, 0, 0, 0);
                    try {
                        objId = ref.getObjectId();
                        if (objId == null) continue;
                    } catch (NullPointerException e) {
                        LOGGER.warn(e.getMessage());
                    }
                    version.setSha(objId.getName());
                }
            }
        }
    }

    private void consistencyReviewTickets(List<Ticket> tickets, List<Version> jiraVersions) {
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

    private void calculateInjectedVersion(Ticket t, List<Float> pList, List<Version> jiraVersions) {
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

    private List<Version> getVersionsList(JSONObject issueFields, String fieldName, List<Version> jiraVersions) {
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

    private Features getFeatures(File f, Version version, String projName, List<Ticket> tickets, Git git) {
        Features features = new Features(version.getIncremental(), f.getPath());
        features.setLoc(f);
        String path = f.getPath().substring(f.getPath().indexOf(projName.toLowerCase()) + projName.length() + 1);
        calculateFeaturesByCommits(features, git, version.getCommits(), path);
        features.setFixes(version, tickets, path);
        features.setBuggy(version, tickets, path);
        return features;
    }

    public void calculateFeaturesByCommits(Features features, Git git, List<Commit> commits, String path) {
        Set<PersonIdent> authorsList = new HashSet<>();

        for (Commit commit : commits) {
            boolean isFileInCommit = commit.isFileInCommit(path);
            if (isFileInCommit) authorsList.add(commit.getAuthor());
            int[] stats = getCommitStats(git, commit, path);
            features.addLocTouched(stats[1]);
            features.addLocTouched(stats[2]);
            features.addLocAdded(stats[1]);
            features.addChurn(stats[1] - stats[2]);
            features.incrementRevisions();
            features.addChgSetSize(stats[0]);
            features.setMaxChgSet(stats[0]);
            features.setMaxLocAdded(stats[1]);
            features.setMaxChurn(stats[1] - stats[2]);

        }

        features.setAuthors(authorsList.size());
        if (features.getRevisions() > 0) {
            features.setAvgLocAdded((double) features.getLocAdded() / (double) features.getRevisions());
            features.setAvgChurn((double) features.getChurn() / (double) features.getRevisions());
            features.setAvgChgSet((double) features.getChgSetSize() / (double) features.getRevisions());
        }
    }

    private int[] getCommitStats(Git git, Commit commit, String path) {
        int filesChanged = 0;
        int currLocAdded = 0;
        int currLocDeleted = 0;
        try (Repository repository = git.getRepository()) {
            RevCommit currCommit = util.getRevCommit(commit.getSha(), repository);
            RevCommit parent = currCommit.getParent(0);
            DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
            df.setRepository(repository);
            df.setDiffComparator(RawTextComparator.DEFAULT);
            //df.setDetectRenames(true);
            List<DiffEntry> diffs;
            //diffs = df.scan(parent.getTree(), currCommit.getTree());
            diffs = df.scan(parent, currCommit);
            //filesChanged = diffs.size();
            for (DiffEntry diff : diffs) {
                if (diff.getNewPath().endsWith(".java") && !diff.getNewPath().contains("tests"))
                    filesChanged++;
                if (diff.getNewPath().equals(path) || diff.getOldPath().equals(path)) {
                    for (Edit edit : df.toFileHeader(diff).toEditList()) {
                        currLocAdded += edit.getEndB() - edit.getBeginB();
                        currLocDeleted += edit.getEndA() - edit.getBeginA();
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn(e.getMessage());
        }
        return new int[]{filesChanged, currLocAdded, currLocDeleted};
    }
}
