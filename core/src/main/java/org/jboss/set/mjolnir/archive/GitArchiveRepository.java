package org.jboss.set.mjolnir.archive;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.URIish;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

/**
 * Defines basic operations for fetching remote repositories.
 */
public class GitArchiveRepository {

    private Git git;

    GitArchiveRepository(Git git) {
        this.git = git;
    }

    public GitArchiveRepository(File repositoryDirectory) throws IOException {
        this.git = Git.open(repositoryDirectory);
    }

    /**
     * Creates a local repository clone.
     *
     * @return Returns instance of this class which can be used for further work with the cloned repository.
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
        for (Ref ref: refs) {
            if (ref.getName().startsWith("ref/remote/" + remoteName + "/")) {
                git.branchDelete().setBranchNames(ref.getName()).setForce(true).call();
            }
        }
    }
 }
