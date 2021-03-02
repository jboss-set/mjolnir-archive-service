package org.jboss.set.mjolnir.archive.util;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.URIish;

import java.io.File;
import java.net.URISyntaxException;

/**
 * Utilities to create sample testing git repos.
 */
public final class GitRepositoryUtils {

    private GitRepositoryUtils() {
    }

    public static Git initializeRepository(File directory, boolean createInitialCommit) throws GitAPIException {
        Git repo = Git.init().setDirectory(directory).call();
        if (createInitialCommit) {
            repo.add().addFilepattern(".").call();
            repo.commit().setMessage("Initial commit").call();
        }
        return repo;
    }

    public static void addRemoteAndFetch(Git repo, String remoteName, File remoteRepositoryDir)
            throws URISyntaxException, GitAPIException {
        repo.remoteAdd().setName(remoteName).setUri(new URIish(remoteRepositoryDir.getAbsolutePath())).call();
        repo.fetch().setRemote(remoteName).call();
    }
}
