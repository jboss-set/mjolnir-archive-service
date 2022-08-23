package org.jboss.set.mjolnir.archive.batch;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
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
import org.jboss.set.mjolnir.archive.github.ExtendedUserService;
import org.jboss.set.mjolnir.archive.github.GitHubMembershipBean;
import org.jboss.set.mjolnir.archive.github.GitHubRepositoriesBean;

import javax.annotation.PostConstruct;
import javax.batch.api.AbstractBatchlet;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.TypedQuery;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
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
    private GitHubRepositoriesBean discoveryBean;

    @Inject
    private ArchivingBean archivingBean;

    @Inject
    private GitHubMembershipBean membershipBean;

    @Inject
    private RemovalLogRepositoryBean logRepositoryBean;

    @Inject
    private GitHubClient gitHubClient;

    private ExtendedUserService userService;

    @PostConstruct
    void init() {
        userService = new ExtendedUserService(gitHubClient);
    }

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
                logger.infof("Processing removal #%d", removal.getId());
                RemovalStatus removalStatus = processRemoval(removal);

                logger.infof("Status of removal #%d is %s", removal.getId(), removalStatus.name());
                removal.setStatus(removalStatus);
                em.persist(removal);

                successful &= RemovalStatus.COMPLETED.equals(removalStatus);
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
            logger.infof("Removal batchlet completed successfully.");
            return Constants.DONE;
        } else {
            logger.infof("Removal batchlet completed with errors.");
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

    /**
     * Retrieves registered user from DB by his LDAP name.
     */
    RegisteredUser findRegisteredUser(String krbName) {
        List<RegisteredUser> resultList = em.createNamedQuery(RegisteredUser.FIND_BY_KRB_NAME, RegisteredUser.class)
                .setParameter("krbName", krbName)
                .getResultList();
        if (resultList.size() == 1) {
            return resultList.get(0);
        } else if (resultList.size() > 1) {
            throw new IllegalStateException("Expected only single user with given kerberos name.");
        }
        return null;
    }

    /**
     * Retrieves current GH username from GH API, based on user's GH ID.
     */
    String findUsersGitHubName(RegisteredUser registeredUser) {
        Integer githubId = registeredUser.getGithubId();
        Objects.requireNonNull(githubId, "The GitHub ID for user '%s' in unknown.");

        try {
            User githubUser = userService.getUserById(githubId);
            logger.infof("Discovered GH username for user '%s', GH ID %d: %s", registeredUser.getKerberosName(),
                    registeredUser.getGithubId(), githubUser.getLogin());
            return githubUser.getLogin();
        } catch (IOException e) {
            throw new RuntimeException(
                    String.format("Unable to obtain user information from GitHub for user id %d, username '%s'",
                            githubId, registeredUser.getGithubName()), e);
        }
    }

    /**
     * Processes removal of a single user.
     *
     * @param removal removal to process
     * @return processed successfully?
     */
    RemovalStatus processRemoval(UserRemoval removal) {

        // validate that either ldap username or github username is specified
        if (StringUtils.isBlank(removal.getGithubUsername()) && StringUtils.isBlank(removal.getLdapUsername())) {
            logRepositoryBean.logError(removal, String.format("Ignoring removal #%d, neither GitHub username or LDAP username were specified.", removal.getId()));
            return RemovalStatus.INVALID;
        } else if (StringUtils.isNotBlank(removal.getGithubUsername()) && StringUtils.isNotBlank(removal.getLdapUsername())) {
            logRepositoryBean.logError(removal, String.format("Ignoring removal #%d, only one of GitHub username and LDAP username can be specified.", removal.getId()));
            return RemovalStatus.INVALID;
        }

        // determine user's github username
        String gitHubUsername;

        if (StringUtils.isNotBlank(removal.getGithubUsername())) {
            gitHubUsername = removal.getGithubUsername();
            logger.infof("Processing removal of GitHub user '%s'", gitHubUsername);
        } else {
            RegisteredUser registeredUser = findRegisteredUser(removal.getLdapUsername());
            if (registeredUser == null) {
                logRepositoryBean.logMessage(removal, "Ignoring removal request for user " + removal.getLdapUsername());
                return RemovalStatus.UNKNOWN_USER;
            }

            gitHubUsername = findUsersGitHubName(registeredUser);

            logger.infof("Processing removal of LDAP user '%s', GitHub username '%s'",
                    removal.getLdapUsername(), gitHubUsername);
        }


        // obtain list of monitored GitHub organizations & teams

        List<GitHubOrganization> organizations = loadOrganizations();

        for (GitHubOrganization organization : organizations) {
            // archive user repositories
            try {
                archiveUserRepositories(removal, organization, gitHubUsername);
            } catch (Exception e) {
                logRepositoryBean.logError(removal, "Couldn't archive repositories of user " + gitHubUsername, e);
                return RemovalStatus.FAILED;
            }

            // if this is enabled in app configuration, unsubscribe user on github
            if (configuration.isUnsubscribeUsers()) {
                logger.infof("Removing user '%s' from following teams: %s", gitHubUsername,
                        organization.getTeams().stream().map(GitHubTeam::getName).collect(Collectors.joining(", ")));

                // remove team memberships
                for (GitHubTeam team : organization.getTeams()) {
                    try {
                        membershipBean.removeUserFromTeam(removal, team, gitHubUsername);
                    } catch (IOException e) {
                        logRepositoryBean.logError(removal, String.format("Couldn't remove user '%s' membership from GitHub team '%s'",
                                        gitHubUsername, team.getName()), e);
                        return RemovalStatus.FAILED;
                    }
                }

                // if this is enabled for given organization, remove organization membership
                if (organization.isUnsubscribeUsersFromOrg()) {
                    try {
                        membershipBean.removeUserFromOrganization(removal, organization, gitHubUsername);
                    } catch (IOException e) {
                        logRepositoryBean.logError(removal, String.format("Couldn't remove user '%s' membership from GitHub organization '%s'",
                                gitHubUsername, organization), e);
                        return RemovalStatus.FAILED;
                    }
                }
            } else {
                logger.infof("Membership removal is disabled, memberships has not been removed.");
            }
        }

        return RemovalStatus.COMPLETED;
    }

    private void archiveUserRepositories(UserRemoval removal, GitHubOrganization organization, String gitHubUsername)
            throws Exception {

        // find user's repositories

        Set<Repository> repositoriesToArchive;
        try {
            logger.infof("Looking for repositories belonging to user '%s' that are forks of organization '%s' repositories.",
                    gitHubUsername, organization.getName());
            repositoriesToArchive = discoveryBean.getRepositoriesToArchive(organization.getName(), gitHubUsername);
            logger.infof("Found following repositories to archive: %s",
                    repositoriesToArchive.stream().map(Repository::generateId).collect(Collectors.toList()));
        } catch (IOException e) {
            logRepositoryBean.logError(removal, "Couldn't obtain repositories for user " + gitHubUsername, e);

            removal.setStatus(RemovalStatus.FAILED);
            em.persist(removal);

            throw e;
        }

        // archive repositories

        for (Repository repository : repositoriesToArchive) {

            // persist repository record
            RepositoryFork repositoryFork = createRepositoryFork(repository);
            repositoryFork.setUserRemoval(removal);
            em.persist(repositoryFork);

            // archive
            logger.infof("Archiving repository '%s'", repository.generateId());
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

                throw e;
            }
        }
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
