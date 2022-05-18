package it.mazz.isw2;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class Features {
    private Integer version;
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

    public void calculateFeaturesByCommits(List<Commit> commits, String path, long currVersionReleaseDate, long prevVersionReleaseDate) {
        List<Integer> authorsList = new LinkedList<>();
        int currChgSetSize = 0;
        for (Commit commit : commits){
            FileStat fileStat = commit.getFileStat(path);
            if (commit.isFileInCommit(path) && commit.getDate().getTime() < currVersionReleaseDate)
                authorsList.add(commit.getAuthorId());
            else {
                continue;
            }
            if (commit.isFileInCommit(path) &&
                    commit.getDate().getTime() < currVersionReleaseDate &&
                    commit.getDate().getTime() > prevVersionReleaseDate) {
                this.locTouched += fileStat.getLocAdded();
                this.locTouched += fileStat.getLocDeleted();
                this.locTouched += fileStat.getLocModified();
                this.locAdded += fileStat.getLocAdded();
                this.churn += (fileStat.getLocAdded() - fileStat.getLocDeleted());
                this.revisions++;
                currChgSetSize += commit.getFileStatList().size();
                this.chgSetSize += commit.getFileStatList().size();
                if (currChgSetSize > this.maxChgSet) this.maxChgSet = this.chgSetSize;
                if (fileStat.getLocAdded() > this.maxLocAdded) this.maxLocAdded = fileStat.getLocAdded();
                if (this.churn > this.maxChurn) this.maxChurn = this.churn;
            }
        }


        this.authors = authorsList.size();
        try {
            this.avgLocAdded = ((double) this.locAdded / (double) this.revisions);
            this.avgChurn = ((double) this.churn / (double) this.revisions);
            this.avgChgSet = ((double) this.chgSetSize / (double) this.revisions);
        } catch (NullPointerException e) {
            e.printStackTrace();
            this.avgLocAdded = 0D;
            this.avgChurn = 0D;
        }
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
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

    public Integer getAuthors() {
        return authors;
    }

    public void setAuthors(Integer authors) {
        this.authors = authors;
    }

    public void setFixes(Version version, List<Ticket> tickets, String path) {
        int count = 0;
        for (Ticket ticket : tickets) {
            for (Version fixedVersion : ticket.getFixedVersions()) {
                if (version.getIncremental() <= fixedVersion.getIncremental()) {
                    for (String f : ticket.getAffectedFiles()) {
                        if (f.equals(path)) count++;
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
                    for (String f : ticket.getAffectedFiles()) {
                        if (f.equals(path)) {
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
}
