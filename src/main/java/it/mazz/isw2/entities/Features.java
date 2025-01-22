package it.mazz.isw2.entities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

public class Features {
    private final Integer version;

    private final String filename;
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

    public Features(Integer version, String filename) {
        this.version = version;
        this.filename = filename;
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

    public Integer getVersion() {
        return version;
    }

    //Count physical LOC (no comments/blanks)
    public void setLoc(File f) {
        this.loc = 0;
        boolean inBlockComment = false;
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            String line;

            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();

                // Skip blank lines
                if (trimmedLine.isEmpty()) {
                    continue;
                }

                // Handle block comments
                if (inBlockComment) {
                    if (trimmedLine.contains("*/")) {
                        inBlockComment = false;
                    }
                    continue;
                }

                // Start of a block comment
                if (trimmedLine.startsWith("/*")) {
                    inBlockComment = true;
                    continue;
                }

                // Skip single-line comments
                if (trimmedLine.startsWith("//")) {
                    continue;
                }

                // Count valid code lines
                loc++;
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

    public String[] toStringArrayForArff() {
        return new String[]{
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

    public String[] toStringArrayForCSV() {
        return new String[]{
                version.toString(),
                filename,
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

    public void addLocTouched(int locTouched) {
        this.locTouched += locTouched;
    }

    public Integer getLocAdded() {
        return locAdded;
    }

    public void addLocAdded(int locAdded) {
        this.locAdded += locAdded;
    }

    public Integer getChurn() {
        return churn;
    }

    public void addChurn(int churn) {
        this.churn += churn;
    }

    public Integer getRevisions() {
        return revisions;
    }

    public void incrementRevisions() {
        this.revisions++;
    }

    public Integer getChgSetSize() {
        return chgSetSize;
    }

    public void addChgSetSize(int filesChanged) {
        this.chgSetSize += filesChanged;
    }

    public void setMaxChgSet(Integer chgSetSize) {
        if (this.maxChgSet < chgSetSize)
            this.maxChgSet = chgSetSize;
    }

    public void setMaxLocAdded(Integer locAdded) {
        if (this.maxLocAdded < locAdded)
            this.maxLocAdded = locAdded;
    }

    public void setMaxChurn(Integer churn) {
        if (this.maxChurn < churn)
            this.maxChurn = churn;
    }

    public void setAuthors(Integer authors) {
        this.authors = authors;
    }

    public void setAvgLocAdded(Double avgLocAdded) {
        this.avgLocAdded = avgLocAdded;
    }

    public void setAvgChurn(Double avgChurn) {
        this.avgChurn = avgChurn;
    }

    public void setAvgChgSet(Double avgChgSet) {
        this.avgChgSet = avgChgSet;
    }

}
