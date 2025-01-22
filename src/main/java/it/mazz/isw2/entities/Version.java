package it.mazz.isw2.entities;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class Version {
    List<Commit> commits = new LinkedList<>();
    private Integer incremental;
    private Integer id;
    private String name;
    private boolean released;
    private Date releaseDate;
    private String sha;
    private Date previousVersionReleaseDate;

    public Version(Integer id, Integer incremental, String name, boolean released, Date releaseDate) {
        this.id = id;
        this.incremental = incremental;
        this.name = name;
        this.released = released;
        this.releaseDate = releaseDate;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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

    public void setReleaseDate(Date releaseDate) {
        this.releaseDate = releaseDate;
    }

    public Date getPreviousVersionReleaseDate() {
        return previousVersionReleaseDate;
    }

    public void setPreviousVersionReleaseDate(Date previousVersionReleaseDate) {
        this.previousVersionReleaseDate = previousVersionReleaseDate;
    }

    public void addCommit(Commit commit) {
        commits.add(commit);
    }

    public String getSha() {
        return sha;
    }

    public void setSha(String sha) {
        this.sha = sha;
    }

    public List<Commit> getCommits() {
        return commits;
    }
}
