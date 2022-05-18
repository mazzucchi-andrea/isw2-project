package it.mazz.isw2;

public class FileStat {

    private String filePath;
    private int locAdded;
    private int locModified;
    private int locDeleted;

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getLocAdded() {
        return locAdded;
    }

    public void setLocAdded(int locAdded) {
        this.locAdded = locAdded;
    }

    public int getLocModified() {
        return locModified;
    }

    public void setLocModified(int locModified) {
        this.locModified = locModified;
    }

    public int getLocDeleted() {
        return locDeleted;
    }

    public void setLocDeleted(int locDeleted) {
        this.locDeleted = locDeleted;
    }
}
