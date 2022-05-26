package it.mazz.isw2;

import org.eclipse.jgit.lib.PersonIdent;

import java.util.Date;
import java.util.List;

public class Commit {

    private final List<String> files;
    private String sha;
    private PersonIdent author;
    private Date date;
    private String message;

    public Commit(String sha, Date date, PersonIdent committerIdent, String fullMessage, List<String> files) {
        this.sha = sha;
        this.date = date;
        this.author = committerIdent;
        this.message = fullMessage;
        this.files = files;
    }

    public String getSha() {
        return sha;
    }

    public void setSha(String sha) {
        this.sha = sha;
    }

    public PersonIdent getAuthor() {
        return author;
    }

    public void setAuthor(PersonIdent author) {
        this.author = author;
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

    public List<String> getFiles() {
        return files;
    }

    public boolean javaFileInCommit() {
        for (String path : this.files) {
            if (path.contains(".java")) return true;
        }
        return false;
    }

    public boolean isFileInCommit(String path) {
        for (String s : this.files) {
            if (s.equals(path)) return true;
        }
        return false;
    }
}
