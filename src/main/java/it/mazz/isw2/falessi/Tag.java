package it.mazz.isw2.falessi;

import java.util.Date;

public class Tag {

    private String name;
    private String commitID;

    private Date date;

    public Tag(String name, String commitID) {
        this.name = name;
        this.commitID = commitID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCommitID() {
        return commitID;
    }

    public void setCommitID(String commitID) {
        this.commitID = commitID;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }
}
