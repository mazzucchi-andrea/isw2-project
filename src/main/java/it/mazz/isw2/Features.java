package it.mazz.isw2;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class Features {
    private final Integer version;
    private final String fileName;
    private Integer loc;
    private Integer locTouched;
    private Integer revisions;
    private Integer fixes;
    private Integer authors;
    private Integer locAdded;
    private Integer maxLocAdded;
    private Double avgLocAdded;
    private Integer churn;
    private Integer maxChurn;
    private Double avgChurn;
    private Integer chgSetSize;
    private Integer maxChgSet;
    private Double avgChgSet;
    private String buggy;

    public Features(Integer version, String fileName) {
        this.version = version;
        this.fileName = fileName;
        this.loc = 0;
        this.locTouched = 0;
        this.revisions = 0;
        this.fixes = 0;
        this.authors = 0;
        this.locAdded = 0;
        this.maxLocAdded = 0;
        this.avgLocAdded = 0D;
        this.churn = 0;
        this.maxChurn = 0;
        this.avgChurn = 0D;
        this.chgSetSize = 0;
        this.maxChgSet = 0;
        this.avgChgSet = 0D;
        this.buggy = "no";
    }

    private static List<String> listDiff(Repository repository, Git git, String oldCommit, String newCommit) throws GitAPIException, IOException {
        List<String> files = new LinkedList<>();
        final List<DiffEntry> diffs = git.diff()
                .setOldTree(prepareTreeParser(repository, oldCommit))
                .setNewTree(prepareTreeParser(repository, newCommit))
                .call();
        for (DiffEntry diff : diffs)
            files.add(diff.getNewPath());

        return files;
    }

    private static AbstractTreeIterator prepareTreeParser(Repository repository, String objectId) throws IOException {
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

    public void calculateFeaturesByCommits(Git git, String path, Long currVersionReleaseDate, Long prevVersionReleaseDate) {
        Set<PersonIdent> authorsList = new HashSet<>();
        Collection<Ref> allRefs;
        Repository repository = git.getRepository();
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
                Long commitDate = revCommit.getCommitTime() * 1000L;
                if (currVersionReleaseDate > commitDate) continue;
                boolean isValidCommit = isFileInCommit(repository, git, revCommit.getName(), path);
                if (isValidCommit) authorsList.add(revCommit.getAuthorIdent());
                if (isValidCommit && prevVersionReleaseDate < commitDate) {
                    Commit commit = Util.getInstance().getCommit(revCommit.getName());
                    FileStat fileStat = commit.getFileStat(path);
                    if (fileStat != null) {
                        this.locTouched += fileStat.getLocAdded();
                        this.locTouched += fileStat.getLocDeleted();
                        this.locTouched += fileStat.getLocModified();
                        this.locAdded += fileStat.getLocAdded();
                        this.churn += (fileStat.getLocAdded() - fileStat.getLocDeleted());
                        this.revisions++;
                        this.chgSetSize += commit.getFileStatList().size();
                        if (commit.getFileStatList().size() > this.maxChgSet)
                            this.maxChgSet = commit.getFileStatList().size();
                        if (fileStat.getLocAdded() > this.maxLocAdded)
                            this.maxLocAdded = fileStat.getLocAdded();
                        if ((fileStat.getLocAdded() - fileStat.getLocDeleted()) > this.maxChurn)
                            this.maxChurn = (fileStat.getLocAdded() - fileStat.getLocDeleted());
                    }
                }
            }
            this.authors = authorsList.size();
            this.avgLocAdded = ((double) this.locAdded / (double) this.revisions);
            this.avgChurn = ((double) this.churn / (double) this.revisions);
            this.avgChgSet = ((double) this.chgSetSize / (double) this.revisions);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Count physical LOC (no comments/blanks)
    public void setLoc(File f) {
        this.loc = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            String line = reader.readLine();
            while (line != null) {
                line = line.replaceAll("\\s+", "");
                if ("".equals(line) ||
                        line.startsWith("/*") ||
                        line.startsWith("*") ||
                        line.startsWith("//")) {
                    line = reader.readLine();
                    continue;
                }
                line = reader.readLine();
                this.loc++;
            }
        } catch (IOException e) {
            e.printStackTrace();
            this.loc = 0;
        }
    }

    public void setFixes(Version version, List<Ticket> tickets, String path) {
        int count = 0;
        for (Ticket ticket : tickets) {
            for (Version fixedVersion : ticket.getFixedVersions()) {
                if (version.getIncremental() <= fixedVersion.getIncremental()) {
                    for (Commit commit : ticket.getCommits()) {
                        if (commit.isFileInCommit(path)) count++;
                    }
                }
            }
        }
        this.fixes = count;
    }

    public void setBuggy(Version version, List<Ticket> tickets, String path) {
        for (Ticket ticket : tickets) {
            for (Version affectedVersion : ticket.getAffectedVersions()) {
                if (version.getIncremental().equals(affectedVersion.getIncremental())) {
                    for (Commit commit : ticket.getCommits()) {
                        if (commit.isFileInCommit(path)) {
                            this.buggy = "yes";
                            return;
                        }
                    }
                }
            }
        }
        this.buggy = "no";
    }

    public String[] toStringArray() {
        return new String[]{
                version.toString(),
                fileName,
                loc.toString(),
                locTouched.toString(),
                revisions.toString(),
                fixes.toString(),
                authors.toString(),
                locAdded.toString(),
                maxLocAdded.toString(),
                avgLocAdded.toString(),
                churn.toString(),
                maxChurn.toString(),
                avgChurn.toString(),
                chgSetSize.toString(),
                maxChgSet.toString(),
                avgChgSet.toString(),
                buggy};
    }

    private boolean isFileInCommit(Repository repository, Git git, String sha, String path) {
        ObjectId objectIdCommit;
        try {
            objectIdCommit = repository.resolve(sha);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit child = revWalk.parseCommit(objectIdCommit);
            RevCommit parent;
            parent = child.getParent(0);
            List<String> files = listDiff(repository, git, parent.getName(), child.getName());
            for (String f : files) {
                if (f.equals(path))
                    return true;
            }
        } catch (IOException | GitAPIException | ArrayIndexOutOfBoundsException e) {
            return false;
        }
        return false;
    }
}
