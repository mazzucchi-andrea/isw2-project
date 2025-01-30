package it.mazz.isw2.entities;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class Commit {

    private static final Logger LOGGER = LoggerFactory.getLogger(Commit.class);
    private final String sha;
    private final PersonIdent author;
    private final String message;
    private List<String> files;

    public Commit(RevCommit revCommit, Repository repository, Git git) {
        files = new LinkedList<>();
        ObjectId objectIdCommit = null;
        try {
            objectIdCommit = repository.resolve(revCommit.getName());
        } catch (IOException e) {
            LOGGER.error("Error resolving commit {}", revCommit.getName(), e);
        }
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit child = revWalk.parseCommit(objectIdCommit);
            RevCommit parent = child.getParent(0);

            final List<DiffEntry> diffs = git.diff()
                    .setOldTree(prepareTreeParser(repository, parent.getName()))
                    .setNewTree(prepareTreeParser(repository, child.getName()))
                    .call();
            for (DiffEntry diff : diffs)
                if (diff.getNewPath().contains(".java"))
                    files.add(diff.getNewPath());
        } catch (ArrayIndexOutOfBoundsException | IOException | GitAPIException e) {
            files = Collections.emptyList();
        }
        this.sha = revCommit.getName();
        this.author = revCommit.getCommitterIdent();
        this.message = revCommit.getFullMessage();
    }

    public String getSha() {
        return sha;
    }

    public PersonIdent getAuthor() {
        return author;
    }

    public String getMessage() {
        return message;
    }

    public boolean javaFileInCommit() {
        for (String path : this.files) {
            if (path.contains(".java")) return true;
        }
        return false;
    }

    public boolean isFileInCommit(String path) {
        for (String s : this.files) {
            if (s.equals(path))
                return true;
        }
        return false;
    }

    private AbstractTreeIterator prepareTreeParser(Repository repository, String objectId) throws IOException {
        // from the commit we can build the tree which allows us to construct the TreeParser
        //noinspection Duplicates
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(repository.resolve(objectId));
            RevTree tree = walk.parseTree(commit.getTree().getId());

            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }

            walk.dispose();

            return treeParser;
        }
    }
}
