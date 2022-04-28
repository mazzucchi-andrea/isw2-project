package it.mazz.isw2.falessi;

import java.util.Date;

public class Version {
    private Integer id;
    private String name;
    private boolean archived;
    private boolean released;
    private Date releaseDate;

    private String commit;

    public Version(Integer id, String name, boolean archived, boolean released, Date releaseDate) {
        this.id = id;
        this.name = name;
        this.archived = archived;
        this.released = released;
        this.releaseDate = releaseDate;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
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
