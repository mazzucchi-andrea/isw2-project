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
                if (this.affectedVersions.get(i).getReleaseDate() != null &&
                        this.created.getTime() > this.affectedVersions.get(i).getReleaseDate().getTime()) {
                    this.openingVersion = this.affectedVersions.get(i);
                    break;
                }
            }
        } else if (openingVersion == null) {
            this.affectedVersions = new LinkedList<>();
            for (int j = 0; j < versions.size(); j++) {
                if (versions.get(j).getReleaseDate() != null &&
                        j + 1 < versions.size() &&
                        versions.get(j + 1).getReleaseDate() != null &&
                        this.created.getTime() > versions.get(j).getReleaseDate().getTime() &&
                        this.created.getTime() < versions.get(j + 1).getReleaseDate().getTime()) {
                    this.openingVersion = versions.get(j);
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

    public boolean consistencyCheckAffectedVersion() {
        if (affectedVersions.isEmpty()) {
            return false;
        } else if (fixedVersions.isEmpty()) {
            return false;
        } else
            return affectedVersions.get(affectedVersions.size() - 1).getIncremental() <= fixedVersions.get(0).getIncremental();
    }

    public void addAffectedVersions(Version version) {
        this.affectedVersions.add(version);
    }
}
