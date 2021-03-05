package org.jboss.set.mjolnir.archive;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.egit.github.core.User;
import org.jboss.logging.Logger;
import org.jboss.set.mjolnir.archive.domain.GitHubOrganization;
import org.jboss.set.mjolnir.archive.domain.GitHubTeam;
import org.jboss.set.mjolnir.archive.domain.RegisteredUser;
import org.jboss.set.mjolnir.archive.domain.RemovalLog;
import org.jboss.set.mjolnir.archive.domain.UserRemoval;
import org.jboss.set.mjolnir.archive.domain.repositories.RegisteredUserRepositoryBean;
import org.jboss.set.mjolnir.archive.github.GitHubMembershipBean;
import org.jboss.set.mjolnir.archive.ldap.LdapClientBean;

import javax.inject.Inject;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Discovers users that left the company and creates their removal records.
 *
 * This implementation works by querying LDAP database. It should eventually be replaced by an implementation
 * relying on JMS messages.
 */
@SuppressWarnings("UnnecessaryLocalVariable")
public class UserDiscoveryBean {

    private final Logger logger = Logger.getLogger(getClass());

    @Inject
    private EntityManager em;

    @Inject
    private LdapClientBean ldapClientBean;

    @Inject
    private GitHubMembershipBean gitHubMembershipBean;

    @Inject
    private RegisteredUserRepositoryBean userRepositoryBean;


    public void createRemovalsForUsersWithoutLdapAccount() {
        try {
            doCreateRemovalsForUsersWithoutLdapAccount();
        } catch (IOException | NamingException e) {
            logger.error("Failed to create user removals", e);
            RemovalLog log = new RemovalLog();
            log.setStackTrace(e);
            log.setMessage("Failed to create user removals");

            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            em.persist(log);
            transaction.commit();
        }

    }

    void doCreateRemovalsForUsersWithoutLdapAccount() throws IOException, NamingException {
        logger.infof("Starting job to create user removals");

        // get users without ldap account
        Collection<String> usersWithoutLdapAccount = findAllUsersWithoutLdapAccount();

        // create removal records
        createUserRemovals(usersWithoutLdapAccount);
    }

    /**
     * Finds all registered users who are members of monitored GitHub organizations or teams, and do not have an
     * active LDAP account.
     *
     * @return LDAP usernames
     */
    public Collection<String> findAllUsersWithoutLdapAccount() throws IOException, NamingException {
        // collect members of all teams and organizations
        HashMap<String, List<GitHubTeam>> allTeamsMembers = getAllTeamsMembers();
        HashMap<String, List<GitHubOrganization>> allOrganizationsMembers = getAllOrganizationsMembers();

        HashSet<String> githubUsernames = new HashSet<>();
        githubUsernames.addAll(allTeamsMembers.keySet());
        githubUsernames.addAll(allOrganizationsMembers.keySet());
        logger.infof("Found %d members of all organizations teams.", githubUsernames.size());

        return findUsersWithoutLdapAccount(githubUsernames).values();
    }

    /**
     * Finds all registered users who are members of monitored GitHub organizations, and do not have an active
     * LDAP account.
     *
     * @return map LDAP username => list of organizations
     */
    public Map<String, List<GitHubOrganization>> findOrganizationsMembersWithoutLdapAccount()
            throws IOException, NamingException {
        // gh username => list of orgs map
        HashMap<String, List<GitHubOrganization>> allOrganizationsMembers = getAllOrganizationsMembers();
        // gh username => ldap username map
        Map<String, String> usersWithoutLdapAccount = findUsersWithoutLdapAccount(allOrganizationsMembers.keySet());
        return usersWithoutLdapAccount.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, entry -> allOrganizationsMembers.get(entry.getKey())));
    }

    /**
     * Finds all registered users who are members of monitored GitHub teams, and do not have an active
     * LDAP account.
     *
     * @return map LDAP username => list of teams
     */
    public Map<String, List<GitHubTeam>> findTeamsMembersWithoutLdapAccount()
            throws IOException, NamingException {
        // gh username => list of teams map
        HashMap<String, List<GitHubTeam>> allTeamsMembers = getAllTeamsMembers();
        // gh username => ldap username map
        Map<String, String> usersWithoutLdapAccount = findUsersWithoutLdapAccount(allTeamsMembers.keySet());
        return usersWithoutLdapAccount.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, entry -> allTeamsMembers.get(entry.getKey())));
    }

    /**
     * Finds users who are registered in Mjolnir database but do not have an active LDAP account.
     *
     * @param githubUsernames GitHub usernames to check
     * @return map GitHub username => LDAP username
     */
    Map<String, String> findUsersWithoutLdapAccount(Collection<String> githubUsernames) throws NamingException {
        // retrieve kerberos names of collected users (those that we know and are not whitelisted)
        HashMap<String, String> githubToLdapUsernames = new HashMap<>();
        githubUsernames.forEach(githubUsername -> {
            Optional<RegisteredUser> registeredUser = userRepositoryBean.findByGitHubUsername(githubUsername);
            registeredUser.ifPresent(user -> {
                if (user.isWhitelisted()) {
                    logger.infof("Skipping whitelisted user %s.", user.getGithubName());
                } else if (StringUtils.isBlank(user.getKerberosName())) {
                    logger.warnf("Skipping user %s because of unknown LDAP name.", user.getGithubName());
                } else {
                    githubToLdapUsernames.put(githubUsername, user.getKerberosName());
                }
            });
        });
        logger.infof("Out of all members, %d are registered users.", githubToLdapUsernames.size());

        // search for users that do not have active LDAP account
        Map<String, Boolean> usersLdapMap = ldapClientBean.checkUsersExists(githubToLdapUsernames.values());
        Map<String, String> result = githubToLdapUsernames.entrySet().stream()
                .filter(entry -> !usersLdapMap.get(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        logger.infof("Detected %d users that do not have active LDAP account.", result.size());
        return result;
    }

    public List<RegisteredUser> getWhitelistedUsers() {
        return em.createNamedQuery(RegisteredUser.FIND_WHITELISTED, RegisteredUser.class).getResultList();
    }

    /**
     * Collects members of all monitored GH teams.
     *
     * @return map GitHub username => list of GH teams
     */
    HashMap<String, List<GitHubTeam>> getAllTeamsMembers() throws IOException {
        List<GitHubOrganization> organizations =
                em.createNamedQuery(GitHubOrganization.FIND_ALL, GitHubOrganization.class).getResultList();

        HashMap<String, List<GitHubTeam>> usersMap = new HashMap<>();
        for (GitHubOrganization organization : organizations) {
            for (GitHubTeam team: organization.getTeams()) {
                for (User user: gitHubMembershipBean.getTeamsMembers(team)) {
                    List<GitHubTeam> usersTeams = usersMap.computeIfAbsent(user.getLogin(), u -> new ArrayList<>());
                    usersTeams.add(team);
                }
            }
        }

        return usersMap;
    }

    /**
     * Collects members of all monitored GH organizations.
     *
     * @return map GitHub username => list of GH orgs
     */
    HashMap<String, List<GitHubOrganization>> getAllOrganizationsMembers() throws IOException {
        List<GitHubOrganization> organizations =
                em.createNamedQuery(GitHubOrganization.FIND_ALL, GitHubOrganization.class).getResultList();

        HashMap<String, List<GitHubOrganization>> usersMap = new HashMap<>();
        for (GitHubOrganization organization : organizations) {
            if (organization.isUnsubscribeUsersFromOrg()) {
                for (User user: gitHubMembershipBean.getOrganizationMembers(organization)) {
                    List<GitHubOrganization> usersOrgs = usersMap.computeIfAbsent(user.getLogin(), u -> new ArrayList<>());
                    usersOrgs.add(organization);
                }
            }
        }

        return usersMap;
    }

    /**
     * Finds all monitored GitHub teams members who are not registered in the Mjolnir database.
     *
     * @return list of GitHub usernames
     */
    public Map<String, List<GitHubTeam>> findUnregisteredTeamsMembers() throws IOException {
        HashMap<String, List<GitHubTeam>> allMembers = getAllTeamsMembers();
        List<RegisteredUser> registeredUsers = em.createNamedQuery(RegisteredUser.FIND_ALL, RegisteredUser.class).getResultList();

        Map<String, List<GitHubTeam>> unregisteredMembersMap = allMembers.entrySet().stream()
                .filter(entry -> !containsRegisteredUser(entry.getKey(), registeredUsers))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return unregisteredMembersMap;
    }

    /**
     * Finds all monitored GitHub organizations members who are not registered in the Mjolnir database.
     *
     * @return list of GitHub usernames
     */
    public Map<String, List<GitHubOrganization>> findUnregisteredOrganizationsMembers() throws IOException {
        HashMap<String, List<GitHubOrganization>> allMembers = getAllOrganizationsMembers();
        List<RegisteredUser> registeredUsers = em.createNamedQuery(RegisteredUser.FIND_ALL, RegisteredUser.class).getResultList();

        Map<String, List<GitHubOrganization>> unregisteredMembersMap = allMembers.entrySet().stream()
                .filter(entry -> !containsRegisteredUser(entry.getKey(), registeredUsers))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return unregisteredMembersMap;
    }

    /**
     * Finds all teams that a GitHub user is member of.
     *
     * @deprecated The users-teams/orgs relationships can be obtained straight away via `findUnregisteredTeamsMembers()`
     *   and `getUnregisteredOrganizationsMembers()`.
     */
    public List<GitHubTeam> getAllUsersTeams(String gitHubUser) throws IOException {
        List<GitHubTeam> memberTeams = new ArrayList<>();

        List<GitHubOrganization> organizations =
                em.createNamedQuery(GitHubOrganization.FIND_ALL, GitHubOrganization.class).getResultList();

        List<GitHubTeam> allTeams = new ArrayList<>();
        for (GitHubOrganization organization : organizations) {
            allTeams.addAll(organization.getTeams());
        }

        for (GitHubTeam team : allTeams) {
            if (gitHubMembershipBean.isMember(gitHubUser, team))
                memberTeams.add(team);
        }

        return memberTeams;
    }

    private static boolean containsRegisteredUser(String member, List<RegisteredUser> registeredUsers) {
        for (RegisteredUser registeredUser : registeredUsers) {
            if (registeredUser.getGithubName() != null
                    && member.toLowerCase().equals(registeredUser.getGithubName().toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates and persists UserRemoval objects for given list of usernames.
     */
    void createUserRemovals(Collection<String> krbNames) {
        em.getTransaction().begin();

        Set<String> existingUserNamesToProcess = getExistingUserNamesToProcess();

        krbNames.forEach(username -> createUniqueUserRemoval(existingUserNamesToProcess, username));

        em.getTransaction().commit();
    }

    public void createUserRemoval(String krbName) {
        Set<String> existingUserNamesToProcess = getExistingUserNamesToProcess();
        createUniqueUserRemoval(existingUserNamesToProcess, krbName);
    }

    private Set<String> getExistingUserNamesToProcess() {
        List<UserRemoval> existingRemovalsToProcess =
                em.createNamedQuery(UserRemoval.FIND_REMOVALS_TO_PROCESS, UserRemoval.class).getResultList();
        return existingRemovalsToProcess.stream().map(UserRemoval::getLdapUsername).collect(Collectors.toSet());
    }

    private void createUniqueUserRemoval(Set<String> existingUserNamesToProcess, String userName) {
        if (existingUserNamesToProcess.contains(userName)) {
            logger.infof("Removal record for user %s already exists", userName);
        } else {
            logger.infof("Creating removal record for user %s", userName);
            UserRemoval removal = new UserRemoval();
            removal.setLdapUsername(userName);
            em.persist(removal);
        }
    }
}
