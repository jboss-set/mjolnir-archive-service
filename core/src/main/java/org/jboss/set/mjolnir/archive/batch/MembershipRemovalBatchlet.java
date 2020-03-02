package org.jboss.set.mjolnir.archive.batch;

import org.eclipse.egit.github.core.Repository;
import org.jboss.logging.Logger;
import org.jboss.set.mjolnir.archive.ArchivingBean;
import org.jboss.set.mjolnir.archive.GitHubDiscoveryBean;
import org.jboss.set.mjolnir.archive.domain.GitHubOrganization;
import org.jboss.set.mjolnir.archive.domain.RemovalStatus;
import org.jboss.set.mjolnir.archive.domain.RepositoryFork;
import org.jboss.set.mjolnir.archive.domain.User;
import org.jboss.set.mjolnir.archive.domain.UserRemoval;

import javax.batch.api.AbstractBatchlet;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Named
public class MembershipRemovalBatchlet extends AbstractBatchlet {

    private Logger logger = Logger.getLogger(getClass());

    @PersistenceContext
    @Inject
    private EntityManager em;

    @Inject
    private GitHubDiscoveryBean discoveryBean;

    @Inject
    private ArchivingBean archivingBean;

    @Override
    public String process() {
        // obtain list of users we want to remove the access rights from
        List<UserRemoval> removals = loadRemovalsToProcess();

        boolean successful = true;
        for (UserRemoval removal : removals) {
            try {
                successful &= processRemoval(removal);
            } catch (Exception e) {
                logger.errorf(e, "Removal processing of user %s failed.", removal.getUsername());
                // TODO: log error to db
                removal.setStatus(RemovalStatus.FAILED);
                em.persist(removal);
                flush();
                throw e;
            }
            flush();
        }

        if (successful) {
            return "DONE";
        } else {
            return "DONE_WITH_ERRORS";
        }
    }

    List<UserRemoval> loadRemovalsToProcess() {
        // perform in transaction to avoid removals being loaded by two parallel executions
        em.getTransaction().begin();

        Query findRemovalsQuery = em.createNamedQuery(UserRemoval.FIND_REMOVALS_TO_PROCESS);
        //noinspection unchecked
        List<UserRemoval> removals = findRemovalsQuery.getResultList();

        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        for (UserRemoval removal : removals) {
            removal.setStarted(timestamp);
            removal.setStatus(RemovalStatus.STARTED);
            em.persist(removal);
        }

        em.getTransaction().commit();

        return removals;
    }

    List<GitHubOrganization> loadOrganizations() {
        return em.createNamedQuery(GitHubOrganization.FIND_ALL, GitHubOrganization.class).getResultList();
    }

    String findGitHubUsername(String krbName) {
        List<User> resultList = em.createNamedQuery(User.FIND_BY_KRB_NAME, User.class)
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
            logger.infof("GitHub username not known for user %s", removal.getUsername());

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
                logger.infof("Looking for repositories belonging to user %s in organization %s",
                        removal.getUsername(), organization.getName());
                repositoriesToArchive = discoveryBean.getRepositoriesToArchive(organization.getName(), gitHubUsername);
                logger.infof("Found following repositories to archive: %s",
                        repositoriesToArchive.stream().map(Repository::generateId).collect(Collectors.toList()));
            } catch (IOException e) {
                logger.errorf(e, "Couldn't obtain repositories for user %s", gitHubUsername);
                removal.setStatus(RemovalStatus.FAILED);
                // TODO: log error to db
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
                    archivingBean.createRepositoryMirror(repository.getCloneUrl());
                } catch (Exception e) { // TODO: change this to a checked exception(s) when archivingBean is ready?
                    logger.errorf(e, "Couldn't archive repository %s", repository.getCloneUrl());
                    removal.setStatus(RemovalStatus.FAILED);
                    // TODO: log error to db
                    em.persist(removal);

                    return false;
                }
            }

            // TODO: remove team memberships

        }

        removal.setStatus(RemovalStatus.COMPLETED);
        removal.setCompleted(new Timestamp(System.currentTimeMillis()));
        em.persist(removal);
        return true;
    }

    private void flush() {
        em.getTransaction().begin();
        em.getTransaction().commit();
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
