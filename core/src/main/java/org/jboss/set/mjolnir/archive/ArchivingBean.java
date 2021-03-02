package org.jboss.set.mjolnir.archive;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.jboss.logging.Logger;
import org.jboss.set.mjolnir.archive.configuration.Configuration;

import javax.inject.Inject;
import java.io.File;
import java.net.URISyntaxException;

public class ArchivingBean {
  
    private Logger logger = Logger.getLogger(getClass());

    private UsernamePasswordCredentialsProvider credentialsProvider;
    private Configuration configuration;

    @Inject
    public ArchivingBean(Configuration configuration) {
        this.configuration = configuration;
        this.credentialsProvider = new UsernamePasswordCredentialsProvider("token", configuration.getGitHubToken());
    }

    /**
     * Create remote repository for given repository object
     *
     * @param repository object representing repository for archiving
     */
    public void createRepositoryMirror(Repository repository) throws GitAPIException, URISyntaxException {
        logger.infof("Archiving repository %s", repository.getCloneUrl());

        if (repository.getSource() == null || repository.getSource().getOwner() == null) {
            throw new IllegalArgumentException("Source repository and it's owner has to be set.");
        }

        String parentUrl = repository.getSource().getCloneUrl();

        File archiveRoot;
        if (configuration.getRepositoryArchiveRoot() != null) {
            archiveRoot = new File(configuration.getRepositoryArchiveRoot());
        } else {
            archiveRoot = new File(System.getProperty("user.home"));
        }

        String sourceOrganizationName = repository.getSource().getOwner().getLogin();
        if (StringUtils.isBlank(sourceOrganizationName)) {
            throw new IllegalArgumentException("Source organization name has to be non blank.");
        }

        File organizationDirectory = new File(archiveRoot, sourceOrganizationName);
        File repositoryDirectory = new File(organizationDirectory, repository.getName());

        GitArchiveRepository gitArchive = GitArchiveRepository.clone(repositoryDirectory, parentUrl, credentialsProvider);
        gitArchive.addRemote(repository.getOwner().getLogin(), repository.getCloneUrl());
        gitArchive.fetch(repository.getOwner().getLogin(), credentialsProvider);
    }
}
