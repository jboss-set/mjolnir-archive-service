package org.jboss.set.mjolnir.archive.batch;

import org.eclipse.egit.github.core.Repository;
import org.jboss.logging.Logger;
import org.jboss.set.mjolnir.archive.ArchivingBean;
import org.jboss.set.mjolnir.archive.github.GitHubDiscoveryBean;
import org.jboss.set.mjolnir.archive.github.GitHubTeamServiceBean;
import org.jboss.set.mjolnir.archive.configuration.Configuration;
import org.jboss.set.mjolnir.archive.domain.GitHubOrganization;
import org.jboss.set.mjolnir.archive.domain.RemovalLog;
import org.jboss.set.mjolnir.archive.domain.RemovalStatus;
import org.jboss.set.mjolnir.archive.domain.RepositoryFork;
import org.jboss.set.mjolnir.archive.domain.RegisteredUser;
import org.jboss.set.mjolnir.archive.domain.UserRemoval;

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
 */
@Named
public class MembershipRemovalBatchlet extends AbstractBatchlet {

    private Logger logger = Logger.getLogger(getClass());

    @Inject
    private EntityManager em;

    @Inject
    private Configuration configuration;

    @Inject
    private GitHubDiscoveryBean discoveryBean;

    @Inject
    private ArchivingBean archivingBean;

    @Inject
    private GitHubTeamServiceBean userRemovalBean;

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
                logError(removal, "Failed to process user: " + removal.getUsername(), e);

                removal.setStatus(RemovalStatus.FAILED);
                em.persist(removal);
            }
            em.flush();
        }

        transaction.commit();

        if (successful) {
            return "DONE";
        } else {
            return "DONE_WITH_ERRORS";
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

        // determine user's github username

        String gitHubUsername = findGitHubUsername(removal.getUsername());
        if (gitHubUsername == null) {
            logMessage(removal, "Ignoring removal request for user " + removal.getUsername());

            removal.setStatus(RemovalStatus.UNKNOWN_USER);
            em.persist(removal);
            return true;
        }
        logger.infof("Found GitHub username for user %s", removal.getUsername());


        // obtain list of monitored GitHub organizations & teams

        List<GitHubOrganization> organizations = loadOrganizations();

        for (GitHubOrganization organization : organizations) {

            // find user's repositories

            Set<Repository> repositoriesToArchive;
            try {
                logger.infof("Looking for repositories belonging to user %s that are forks of organization %s repositories.",
                        removal.getUsername(), organization.getName());
                repositoriesToArchive = discoveryBean.getRepositoriesToArchive(organization.getName(), gitHubUsername);
                logger.infof("Found following repositories to archive: %s",
                        repositoriesToArchive.stream().map(Repository::generateId).collect(Collectors.toList()));
            } catch (IOException e) {
                logError(removal, "Couldn't obtain repositories for user " + gitHubUsername, e);

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
                } catch (Exception e) {
                    logError(removal, "Couldn't archive repository: " + repository.getCloneUrl(), e);

                    removal.setStatus(RemovalStatus.FAILED);
                    em.persist(removal);

                    return false;
                }
            }

            // remove team memberships

            if (configuration.isUnsubscribeUsers()) {
                try {
                    userRemovalBean.removeUserFromTeams(organization.getName(), gitHubUsername);
                } catch (IOException e) {
                    logError(removal, "Couldn't remove user membership from GitHub teams: " + removal.getUsername(), e);

                    removal.setStatus(RemovalStatus.FAILED);
                    em.persist(removal);

                    return false;
                }
            } else {
                logger.infof("User membership is not removed for user %s", removal.getUsername());
            }

        }

        removal.setStatus(RemovalStatus.COMPLETED);
        em.persist(removal);
        logger.infof("Removal batchlet completed successfully.");
        return true;
    }

    private void logMessage(UserRemoval removal, String message) {
        logger.infof(message);

        RemovalLog log = new RemovalLog();
        log.setUserRemoval(removal);
        log.setMessage(message);
        em.persist(log);
    }

    private void logError(UserRemoval removal, String message, Throwable t) {
        logger.errorf(t, message, removal.getUsername());

        RemovalLog log = new RemovalLog();
        log.setUserRemoval(removal);
        log.setMessage(message);
        if (t != null) {
            log.setStackTrace(t);
        }
        em.persist(log);
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
