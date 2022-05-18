package it.mazz.isw2;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class Ticket {
    private String key;
    private Version openingVersion;
    private List<Version> affectedVersions;
    private List<Version> fixedVersions;
    private Version injectedVersion;
    private Date created;
    private Date resolved;
    private List<Commit> commits = new LinkedList<>();
    private final List<String> affectedFiles = new LinkedList<>();

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Version getOpeningVersion() {
        return openingVersion;
    }

    public void setOpeningVersion(List<Version> versions) {
        if (this.affectedVersions.size() > 1) {
            for (int i = this.affectedVersions.size() - 1; i >= 0; i--) {
                if (this.created.compareTo(this.affectedVersions.get(i).getReleaseDate()) > 0) {
                    this.openingVersion = this.affectedVersions.get(i);
                    break;
                }
            }
        } else {
            for (int j = 0; j < versions.size(); j++) {
                if (this.created.compareTo(versions.get(j).getReleaseDate()) > 0) {
                    this.openingVersion = versions.get(j - 1);
                    break;
                }
            }
        }
    }

    public List<Version> getAffectedVersions() {
        return affectedVersions;
    }

    public void setAffectedVersions(List<Version> affectedVersions) {
        this.affectedVersions = affectedVersions;
    }

    public List<Version> getFixedVersions() {
        return fixedVersions;
    }

    public void setFixedVersions(List<Version> fixedVersions) {
        this.fixedVersions = fixedVersions;
    }

    public Version getInjectedVersion() {
        return injectedVersion;
    }

    public void setInjectedVersion(Version injectedVersion) {
        this.injectedVersion = injectedVersion;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Date getResolved() {
        return resolved;
    }

    public void setResolved(Date resolved) {
        this.resolved = resolved;
    }

    public List<Commit> getCommits() {
        return commits;
    }

    public void setCommits(List<Commit> commits) {
        this.commits = commits;
    }

    public void addCommit(Commit commit) {
        this.commits.add(commit);
    }

    public List<String> getAffectedFiles() {
        return affectedFiles;
    }

    public void addAffectedFiles(List<String> fixedFiles) {
        this.affectedFiles.addAll(fixedFiles);
    }

    public boolean consistencyCheckAffectedVersion() {
        if (affectedVersions.isEmpty()) {
            return false;
        } else if (fixedVersions.isEmpty()) {
            return false;
        } else return affectedVersions.get(affectedVersions.size() - 1).getIncremental() <= fixedVersions.get(0).getIncremental();
    }
}
