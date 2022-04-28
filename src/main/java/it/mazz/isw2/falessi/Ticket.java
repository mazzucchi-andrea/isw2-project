package it.mazz.isw2.falessi;

import java.util.Date;
import java.util.List;

public class Ticket {

    private String key;
    private String type;
    private String openingVersion;
    private List<String> affectedVersions;
    private List<String> fixedVersions;
    private Date created;
    private Date resolved;

    public Ticket(String type) {
        this.type = type;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getOpeningVersion() {
        return openingVersion;
    }

    public void setOpeningVersion(String openingVersion) {
        this.openingVersion = openingVersion;
    }

    public List<String> getAffectedVersions() {
        return affectedVersions;
    }

    public void setAffectedVersions(List<String> affectedVersions) {
        this.affectedVersions = affectedVersions;
    }

    public List<String> getFixedVersions() {
        return fixedVersions;
    }

    public void setFixedVersions(List<String> fixedVersions) {
        this.fixedVersions = fixedVersions;
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
}
