package it.mazz.isw2.entities;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Version {
    private final Date releaseDate;
    Map<String, Commit> commits;
    private Integer incremental;
    private String name;
    private boolean released;
    private String sha;

    public Version(Integer incremental, String name, boolean released, Date releaseDate) {
        this.incremental = incremental + 1;
        this.name = name;
        this.released = released;
        this.releaseDate = releaseDate;
        commits = new HashMap<>();
    }

    public Integer getIncremental() {
        return incremental;
    }

    public void setIncremental(Integer incremental) {
        this.incremental = incremental;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isReleased() {
        return released;
    }

    public void setReleased(boolean released) {
        this.released = released;
    }

    public Date getReleaseDate() {
        return releaseDate;
    }

    public void addCommit(Commit commit) {
        commits.put(commit.getSha(), commit);
    }

    public String getSha() {
        return sha;
    }

    public void setSha(String sha) {
        this.sha = sha;
    }

    public Map<String, Commit> getCommits() {
        return commits;
    }

    public boolean containsCommit(Commit commit) {
        return commits.get(commit.getSha()) != null;
    }
}
