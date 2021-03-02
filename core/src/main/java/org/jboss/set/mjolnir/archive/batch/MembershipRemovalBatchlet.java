package org.jboss.set.mjolnir.archive.batch;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.egit.github.core.Repository;
import org.jboss.logging.Logger;
import org.jboss.set.mjolnir.archive.ArchivingBean;
import org.jboss.set.mjolnir.archive.configuration.Configuration;
import org.jboss.set.mjolnir.archive.domain.GitHubOrganization;
import org.jboss.set.mjolnir.archive.domain.GitHubTeam;
import org.jboss.set.mjolnir.archive.domain.RegisteredUser;
import org.jboss.set.mjolnir.archive.domain.RemovalStatus;
import org.jboss.set.mjolnir.archive.domain.RepositoryFork;
import org.jboss.set.mjolnir.archive.domain.RepositoryForkStatus;
import org.jboss.set.mjolnir.archive.domain.UserRemoval;
import org.jboss.set.mjolnir.archive.domain.repositories.RemovalLogRepositoryBean;
import org.jboss.set.mjolnir.archive.github.GitHubDiscoveryBean;
import org.jboss.set.mjolnir.archive.github.GitHubTeamServiceBean;

import javax.batch.api.AbstractBatchlet;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.TypedQuery;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Batchlet that handles the user removal process.
 *
 * Gets fresh removal records from db (those records are created by a MDB listening for employee offboarding messages),
 * archives their private repositories, and removes their access to our GitHub Teams.
 */
@Named
public class MembershipRemovalBatchlet extends AbstractBatchlet {

    private final Logger logger = Logger.getLogger(getClass());

    @Inject
    private EntityManager em;

    @Inject
    private Configuration configuration;

    @Inject
    private GitHubDiscoveryBean discoveryBean;

    @Inject
    private ArchivingBean archivingBean;

    @Inject
    private GitHubTeamServiceBean teamServiceBean;

    @Inject
    private RemovalLogRepositoryBean logRepositoryBean;

    @Override
    public String process() {
        // obtain list of users we want to remove the access rights from
        // (this is going to run in separate transaction)
        List<UserRemoval> removals = loadRemovalsToProcess();
        logger.infof("Found %d user removal requests.", removals.size());

        EntityTransaction transaction = em.getTransaction();
        transaction.begin();

        boolean successful = true;
        for (UserRemoval removal : removals) {
            try {
                successful &= processRemoval(removal);
            } catch (Exception e) {
                successful = false;
                
                // log error to db
                logRepositoryBean.logError(removal, "Failed to process removal: " + removal.toString(), e);

                removal.setStatus(RemovalStatus.FAILED);
                em.persist(removal);
            }
            em.flush();
        }

        transaction.commit();

        if (successful) {
            return Constants.DONE;
        } else {
            return Constants.DONE_WITH_ERRORS;
        }
    }

    List<UserRemoval> loadRemovalsToProcess() {
        // perform in transaction to avoid removals being loaded by two parallel executions
        EntityTransaction transaction = em.getTransaction();
        transaction.begin();

        TypedQuery<UserRemoval> findRemovalsQuery = em.createNamedQuery(UserRemoval.FIND_REMOVALS_TO_PROCESS, UserRemoval.class);
        List<UserRemoval> removals = findRemovalsQuery.getResultList();

        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        for (UserRemoval removal : removals) {
            removal.setStarted(timestamp);
            removal.setStatus(RemovalStatus.STARTED);
            em.persist(removal);
        }

        transaction.commit();

        return removals;
    }

    List<GitHubOrganization> loadOrganizations() {
        return em.createNamedQuery(GitHubOrganization.FIND_ALL, GitHubOrganization.class).getResultList();
    }

    String findGitHubUsername(String krbName) {
        List<RegisteredUser> resultList = em.createNamedQuery(RegisteredUser.FIND_BY_KRB_NAME, RegisteredUser.class)
                .setParameter("krbName", krbName)
                .setMaxResults(1)
                .getResultList();
        if (resultList.size() == 1) {
            return resultList.get(0).getGithubName();
        } else if (resultList.size() > 1) {
            throw new IllegalStateException("Expected only single user with given kerberos name.");
        }
        return null;
    }

    /**
     * Processes removal of a single user.
     *
     * @param removal removal to process
     * @return processed successfully?
     */
    boolean processRemoval(UserRemoval removal) {

        // validate that either ldap username or github username is specified
        if (StringUtils.isBlank(removal.getGithubUsername()) && StringUtils.isBlank(removal.getLdapUsername())) {
            logger.warnf("Ignoring removal #%d, neither GitHub username or LDAP username were specified.", removal.getId());
            removal.setStatus(RemovalStatus.INVALID);
            em.persist(removal);
            return false;
        }

        // determine user's github username
        String gitHubUsername;

        if (StringUtils.isNotBlank(removal.getGithubUsername())) {
            gitHubUsername = removal.getGithubUsername();
            logger.infof("Processing removal of GitHub user %s", gitHubUsername);
        } else {
            gitHubUsername = findGitHubUsername(removal.getLdapUsername());
            if (gitHubUsername == null) {
                logRepositoryBean.logMessage(removal, "Ignoring removal request for user " + removal.getLdapUsername());

                removal.setStatus(RemovalStatus.UNKNOWN_USER);
                em.persist(removal);
                return true;
            }
            logger.infof("Processing removal of LDAP user %s, GitHub username %s",
                    removal.getLdapUsername(), gitHubUsername);
        }


        // obtain list of monitored GitHub organizations & teams

        List<GitHubOrganization> organizations = loadOrganizations();

        for (GitHubOrganization organization : organizations) {

            // find user's repositories

            Set<Repository> repositoriesToArchive;
            try {
                logger.infof("Looking for repositories belonging to user %s that are forks of organization %s repositories.",
                        gitHubUsername, organization.getName());
                repositoriesToArchive = discoveryBean.getRepositoriesToArchive(organization.getName(), gitHubUsername);
                logger.infof("Found following repositories to archive: %s",
                        repositoriesToArchive.stream().map(Repository::generateId).collect(Collectors.toList()));
            } catch (IOException e) {
                logRepositoryBean.logError(removal, "Couldn't obtain repositories for user " + gitHubUsername, e);

                removal.setStatus(RemovalStatus.FAILED);
                em.persist(removal);

                return false;
            }

            // archive repositories

            for (Repository repository : repositoriesToArchive) {

                // persist repository record

                RepositoryFork repositoryFork = createRepositoryFork(repository);
                repositoryFork.setUserRemoval(removal);
                em.persist(repositoryFork);


                // archive

                logger.infof("Archiving repository %s", repository.generateId());
                try {
                    archivingBean.createRepositoryMirror(repository);

                    repositoryFork.setStatus(RepositoryForkStatus.ARCHIVED);
                    em.persist(repositoryFork);
                } catch (Exception e) {
                    logRepositoryBean.logError(removal, "Couldn't archive repository: " + repository.getCloneUrl(), e);

                    repositoryFork.setStatus(RepositoryForkStatus.ARCHIVAL_FAILED);
                    em.persist(repositoryFork);

                    removal.setStatus(RemovalStatus.FAILED);
                    em.persist(removal);

                    return false;
                }
            }

            // remove team memberships
            logger.infof("User is about to be removed from following teams: %s",
                    organization.getTeams().stream().map(GitHubTeam::getName).collect(Collectors.joining(", ")));
            if (configuration.isUnsubscribeUsers()) {
                try {
                    teamServiceBean.removeUserFromTeams(organization, gitHubUsername);
                } catch (IOException e) {
                    logRepositoryBean.logError(removal, "Couldn't remove user membership from GitHub teams: " + gitHubUsername, e);

                    removal.setStatus(RemovalStatus.FAILED);
                    em.persist(removal);

                    return false;
                }
            } else {
                logger.infof("Membership removal is disabled, membership has not been removed.", gitHubUsername);
            }
        }

        removal.setStatus(RemovalStatus.COMPLETED);
        em.persist(removal);
        logger.infof("Removal batchlet completed successfully.");
        return true;
    }

    static RepositoryFork createRepositoryFork(Repository repository) {
        RepositoryFork fork = new RepositoryFork();
        fork.setRepositoryName(repository.generateId());
        fork.setRepositoryUrl(repository.getCloneUrl());
        fork.setSourceRepositoryName(repository.getSource().generateId());
        fork.setSourceRepositoryUrl(repository.getSource().getCloneUrl());
        return fork;
    }
}
