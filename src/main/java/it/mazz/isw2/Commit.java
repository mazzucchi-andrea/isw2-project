package it.mazz.isw2;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class Commit {

    private final List<FileStat> fileStatList = new LinkedList<>();
    private Date date;
    private String message;

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<FileStat> getFileStatList() {
        return fileStatList;
    }

    public void addFileStat(FileStat fileStat) {
        this.fileStatList.add(fileStat);
    }

    public boolean javaFileInCommit() {
        for (FileStat fileStat : fileStatList) {
            if (fileStat.getFilePath().contains(".java"))
                return true;
        }
        return false;
    }

    public FileStat getFileStat(String path) {
        for (FileStat fileStat : fileStatList) {
            if (fileStat.getFilePath().equals(path))
                return fileStat;
        }
        return null;
    }

    public boolean isFileInCommit(String path) {
        for (FileStat fileStat : fileStatList) {
            if (fileStat.getFilePath().equals(path))
                return true;
        }
        return false;
    }
}
