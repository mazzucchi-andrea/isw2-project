package it.mazz.isw2;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class Commit {
    private String author;
    private int authorId;
    private Date date;
    private String message;
    private List<FileStat> fileStatList;

    public Commit() {
        fileStatList = new LinkedList<>();
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public int getAuthorId() {
        return authorId;
    }

    public void setAuthorId(int authorId) {
        this.authorId = authorId;
    }

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

    public boolean isFileInCommit(String path) {
        for (FileStat fileStat : fileStatList) {
            if (fileStat.getFilePath().equals(path))
                return true;
        }
        return false;
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

    public List<String> getFilePathList() {
        List<String> paths = new LinkedList<>();
        for (FileStat fileStat : fileStatList) {
            paths.add(fileStat.getFilePath());
        }
        return paths;
    }
}
