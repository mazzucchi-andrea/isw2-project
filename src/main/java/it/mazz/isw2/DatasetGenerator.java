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

        LOGGER.info("Delete previous results");
        File repo = deletePreviousResults(projName);
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
        VersionsHandler.getVersionsFromJira(projName);

        LOGGER.info("Merge Jira Versions and ref/tags to take commits");
        VersionsHandler.setReleaseCommit(git);
        try (Repository repository = git.getRepository()) {
            VersionsHandler.addCommitsToVersions(repository, git);
        }

        if (VersionsHandler.getListSize() < 3) {
            LOGGER.error("Jira versions list size less than 3");
            return;
        }
        LOGGER.info("Version list size: {}", VersionsHandler.getListSize());

        LOGGER.info("Retrieve Tickets from Jira");
        List<Ticket> tickets = getTickets(projName, VersionsHandler.getVersions());
        LOGGER.info("Ticket list size: {}", tickets.size());

        LOGGER.info("Get All Commits");
        List<Commit> commits = getAllCommits(git);
        if (commits.isEmpty())
            return;

        LOGGER.info("Add commits to tickets");
        addCommitToTickets(projName, tickets, commits);
        removeTicketsWithoutCommits(tickets);
        LOGGER.info("Ticket list new size: {}", tickets.size());

        LOGGER.info("Get all file instance for every version");
        List<Features> featuresList = getAllFeatures(projName, git, VersionsHandler.getVersions(), tickets);
        LOGGER.info("Features list size: {}", featuresList.size());

        LOGGER.info("Remove newer versions");
        VersionsHandler.removeHalfVersions();
        LOGGER.info("Version list size: {}", VersionsHandler.getListSize());

        for (Version version : VersionsHandler.getVersions()) {
            LOGGER.info("Version {} Commits list size: {}", version.getName(), version.getCommits().size());
        }

        LOGGER.info("Create walk-forward dataset files");
        String dirPath = String.format("./output/%s/%s-datasets/", projName, projName);

        try {
            Path path = Paths.get(dirPath);
            Files.createDirectories(path);
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
            return;
        }

        createDatasets(projName, dirPath, VersionsHandler.getVersions(), featuresList);

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

        File dataset = new File(String.format("./output/%s/%s-datasets.csv/", projName, projName));
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
                continue;
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
                boolean exampleName = Pattern.compile(Pattern.quote("example"),
                        Pattern.CASE_INSENSITIVE).matcher(f.getPath()).find();
                if (!java || testName || testPath || exampleName) continue;
                featuresList.add(getFeatures(f, version, projName, tickets, git));
            }
        }
        return featuresList;
    }

    private List<Commit> getAllCommits(Git git) {
        Map<String, Commit> commits = new HashMap<>();
        try (Repository repository = git.getRepository()) {
            RevWalk revWalk = new RevWalk(repository);
            List<Ref> allRefs;
            try {
                allRefs = git.branchList().call();
                allRefs.addAll(git.tagList().call());
            } catch (GitAPIException e) {
                LOGGER.error(e.getMessage());
                return Collections.emptyList();
            }

            for (Ref ref : allRefs) {
                // Resolve the reference to an object ID
                ObjectId refObjectId = ref.getObjectId();
                if (refObjectId == null) continue;

                // Mark the start of the walk for this reference
                try {
                    revWalk.markStart(revWalk.parseCommit(refObjectId));
                } catch (IOException e) {
                    LOGGER.error(e.getMessage());
                    return Collections.emptyList();
                }
            }

            // Traverse all commits reachable from the references
            for (RevCommit revCommit : revWalk) {
                Commit commit = new Commit(revCommit, repository, git);
                commits.put(commit.getSha(), commit);
            }
        }
        return new ArrayList<>(commits.values());
    }

    private void removeTicketsWithoutCommits(List<Ticket> tickets) {
        tickets.removeIf(ticket -> ticket.getCommits().isEmpty());
    }

    private File deletePreviousResults(String projName) {
        File repo = new File("./" + projName.toLowerCase());
        File output = new File("./output/" + projName);
        try {
            if (repo.exists())
                FileUtils.deleteDirectory(repo);
            if (output.exists())
                FileUtils.deleteDirectory(output);
        } catch (IOException e) {
            LOGGER.error(e.getMessage());
            return null;
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

    private List<Ticket> getTickets(String projName, List<Version> versions) {
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
                List<Version> affectedVersions = getVersionByFieldName(issueFields, "versions", versions);
                List<Version> fixedVersions = getVersionByFieldName(issueFields, "fixVersions", versions);
                if (!fixedVersions.isEmpty()) {
                    fixedVersions.sort(Comparator.comparing(Version::getReleaseDate));
                    ticket.setFixedVersion(fixedVersions.get(fixedVersions.size() - 1));
                } else {
                    ticket.setFixedVersion(VersionsHandler.getVersionByDate(ticket.getResolved()));
                }
                affectedVersions.remove(ticket.getFixedVersion());
                ticket.setAffectedVersions(affectedVersions);
                ticket.setOpeningVersion(VersionsHandler.getVersionByDate(ticket.getCreated()));
                if (ticket.getOpeningVersion() == null)
                    continue;
                tickets.add(ticket);
            }
        } while (i < total);
        tickets.sort(Comparator.comparing(Ticket::getCreated));
        consistencyReviewTickets(tickets, versions);
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

    private void consistencyReviewTickets(List<Ticket> tickets, List<Version> jiraVersions) {
        int i = 0;
        int n = 0;
        float p = 0F;
        while (i < tickets.size()) {
            Ticket t = tickets.get(i);
            if (t.consistencyCheckAffectedVersion()) {
                t.setInjectedVersion(t.getAffectedVersions().get(0));
                float fv = t.getFixedVersion().getIncremental();
                float iv = t.getInjectedVersion().getIncremental();
                float ov = t.getOpeningVersion().getIncremental();
                if (fv != ov) {
                    p += (fv - iv) / (fv - ov);
                    n++;
                }
            } else {
                t.setAffectedVersions(new ArrayList<>());
                if (p == 0) { // use SIMPLE to set the injected version if Proportion is not initialized
                    t.addAffectedVersions(VersionsHandler.getVersionBetween(t.getOpeningVersion().getIncremental(), t.getFixedVersion().getIncremental()));
                    if (t.getOpeningVersion().getIncremental() < t.getFixedVersion().getIncremental()) {
                        t.addAffectedVersions(t.getOpeningVersion());
                    }
                    t.setInjectedVersion(t.getOpeningVersion());
                } else {
                    calculateInjectedVersion(t, p, n, jiraVersions);
                }
            }
            i++;
        }
    }

    private void calculateInjectedVersion(Ticket t, Float p, int n, List<Version> jiraVersions) {
        float fv = t.getFixedVersion().getIncremental();
        float ov = t.getOpeningVersion().getIncremental();
        int iv = (int) (fv - (fv - ov) * (p / n));
        t.setAffectedVersions(new LinkedList<>());
        for (Version version : jiraVersions) {
            if (version.getIncremental() <= t.getFixedVersion().getIncremental() && version.getIncremental() >= iv)
                t.addAffectedVersions(version);
            if (version.getIncremental().equals(iv)) {
                t.setInjectedVersion(version);
                break;
            }
        }

    }

    private List<Version> getVersionByFieldName(JSONObject issueFields, String fieldName, List<Version> jiraVersions) {
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
        calculateFeaturesByCommits(features, git, new ArrayList<>(version.getCommits().values()), path);
        features.setFixes(version, tickets, path);
        features.setBuggy(version, tickets, path);
        return features;
    }

    public void calculateFeaturesByCommits(Features features, Git git, List<Commit> commits, String path) {
        Set<PersonIdent> authorsList = new HashSet<>();

        for (Commit commit : commits) {
            boolean isFileInCommit = commit.isFileInCommit(path);
            if (isFileInCommit) {
                authorsList.add(commit.getAuthor());
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
            ObjectId objectId = repository.resolve(commit.getSha());
            RevCommit currCommit;
            if (objectId != null) {
                RevWalk revWalk = new RevWalk(repository);
                currCommit = revWalk.parseCommit(objectId);
            } else {
                return new int[]{filesChanged, currLocAdded, currLocDeleted};
            }
            if (currCommit.getParentCount() == 0)
                return new int[]{filesChanged, currLocAdded, currLocDeleted};
            RevCommit parent = currCommit.getParent(0);
            DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
            df.setRepository(repository);
            df.setDiffComparator(RawTextComparator.DEFAULT);
            List<DiffEntry> diffs;
            diffs = df.scan(parent, currCommit);
            for (DiffEntry diff : diffs) {
                if (diff.getNewPath().endsWith(".java") && !diff.getNewPath().contains("tests") && !diff.getNewPath().contains("benchmark"))
                    filesChanged++;
                if (diff.getNewPath().equals(path) || diff.getOldPath().equals(path)) {
                    for (Edit edit : df.toFileHeader(diff).toEditList()) {
                        currLocAdded += edit.getEndB() - edit.getBeginB();
                        currLocDeleted += edit.getEndA() - edit.getBeginA();
                    }
                }
            }
        } catch (NullPointerException | IOException e) {
            LOGGER.warn(e.getMessage());
        }
        return new int[]{filesChanged, currLocAdded, currLocDeleted};
    }
}
