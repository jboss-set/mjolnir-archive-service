package org.jboss.set.mjolnir.archive;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.URIish;
import org.jboss.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

/**
 * Defines basic operations for fetching remote repositories.
 */
public class GitArchiveRepository {

    private final Logger logger = Logger.getLogger(getClass());

    private final Git git;

    GitArchiveRepository(Git git) {
        this.git = git;
    }

    /**
     * Opens a local repository clone.
     *
     * @param repositoryDirectory an existing repository dir
     * @return a GitArchiveRepository instance
     */
    public static GitArchiveRepository open(File repositoryDirectory) throws IOException {
        return new GitArchiveRepository(Git.open(repositoryDirectory));
    }

    /**
     * Creates a local repository clone.
     *
     * @param targetDirectory a repository dir where the repository should be cloned to
     * @param originUrl a URL of the repository that will be cloned
     * @param credentialsProvider credentials
     * @return a GitArchiveRepository instance
     */
    public static GitArchiveRepository clone(File targetDirectory, String originUrl, CredentialsProvider credentialsProvider)
            throws GitAPIException {
        Git git = Git.cloneRepository()
                .setCredentialsProvider(credentialsProvider)
                .setURI(originUrl)
                .setMirror(true)
                .setDirectory(targetDirectory)
                .call();
        return new GitArchiveRepository(git);
    }

    /**
     * Adds a remote to a local repository.
     */
    public void addRemote(String remoteName, String repositoryUrl) throws URISyntaxException, GitAPIException {
        git.remoteAdd()
                .setName(remoteName)
                .setUri(new URIish(repositoryUrl))
                .call();
    }

    /**
     * Fetches changes from a remote.
     */
    public void fetch(String remoteName, CredentialsProvider credentialsProvider) throws GitAPIException {
        git.fetch()
                .setCredentialsProvider(credentialsProvider)
                .setRemote(remoteName)
                .setTagOpt(TagOpt.FETCH_TAGS)
                .call();
    }

    public void removeRemoteBranches(String remoteName) throws GitAPIException {
        List<Ref> refs = git.branchList()
                .setListMode(ListBranchCommand.ListMode.REMOTE)
                .call();
        for (Ref ref : refs) {
            if (ref.getName().startsWith("refs/remotes/" + remoteName + "/")) {
                logger.infof("Removing branch %s in repo %s", ref.getName(),
                        git.getRepository().getDirectory().getAbsolutePath());
                git.branchDelete().setBranchNames(ref.getName()).setForce(true).call();
            }
        }
    }
}
