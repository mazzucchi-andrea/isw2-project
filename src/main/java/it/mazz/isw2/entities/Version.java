package it.mazz.isw2.entities;

import java.util.Date;

public class Version {
    private final Integer incremental;
    private Integer id;
    private String name;
    private boolean released;
    private Date releaseDate;

    private String commit;

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

    public String getCommit() {
        return commit;
    }

    public void setCommit(String commit) {
        this.commit = commit;
    }

}
