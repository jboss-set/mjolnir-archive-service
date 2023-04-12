package org.jboss.set.mjolnir.archive;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.jboss.logging.Logger;
import org.jboss.set.mjolnir.archive.domain.GitHubOrganization;
import org.jboss.set.mjolnir.archive.domain.GitHubTeam;
import org.jboss.set.mjolnir.archive.domain.RegisteredUser;
import org.jboss.set.mjolnir.archive.domain.RemovalLog;
import org.jboss.set.mjolnir.archive.domain.UserRemoval;
import org.jboss.set.mjolnir.archive.domain.repositories.RegisteredUserRepositoryBean;
import org.jboss.set.mjolnir.archive.github.ExtendedUserService;
import org.jboss.set.mjolnir.archive.github.GitHubMembershipBean;
import org.jboss.set.mjolnir.archive.ldap.LdapClientBean;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * <p>
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

    @Inject
    private GitHubClient gitHubClient;

    private ExtendedUserService userService;

    @PostConstruct
    void init() {
        userService = new ExtendedUserService(gitHubClient);
    }

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
        return findUsersWithoutLdapAccount(findAllGitHubUsers()).values();
    }

    Set<String> findAllGitHubUsers() throws IOException {
        // collect members of all teams and organizations
        HashMap<String, List<GitHubTeam>> allTeamsMembers = getAllTeamsMembers();
        logger.infof("Found %d members of all monitored teams.", allTeamsMembers.size());
        HashMap<String, List<GitHubOrganization>> allOrganizationsMembers = getAllOrganizationsMembers();
        logger.infof("Found %d members of all monitored organizations.", allOrganizationsMembers.size());

        HashSet<String> githubUsernames = new HashSet<>();
        githubUsernames.addAll(allTeamsMembers.keySet());
        githubUsernames.addAll(allOrganizationsMembers.keySet());
        logger.infof("Found %d members of all monitored organizations and teams.", githubUsernames.size());

        return githubUsernames;
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
        List<UserRemoval> existingRemovalsToProcess =
                em.createNamedQuery(UserRemoval.FIND_REMOVALS_TO_PROCESS, UserRemoval.class).getResultList();
        Timestamp dayAgo = Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS));
        List<String> ldapUsernamesWaitingForRemoval = existingRemovalsToProcess.stream()
                .filter(removal -> removal.getCreated().after(dayAgo))
                .map(UserRemoval::getLdapUsername)
                .collect(Collectors.toList());
        logger.infof("Following LDAP usernames are still waiting for removal and won't be reported: %s",
                ldapUsernamesWaitingForRemoval);

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
                .filter(entry -> !ldapUsernamesWaitingForRemoval.contains(entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        logger.infof("Detected %d users that do not have active LDAP account.", result.size());
        return result;
    }

    /**
     * Returns registered users whose GH usernames do not exist or do not match their GH ID.
     *
     * @return list of RegisteredUser instances
     */
    public List<RegisteredUser> findInvalidGithubUsers() {
        // find all users registered in our db
        List<RegisteredUser> allRegisteredUsers = userRepositoryBean.getAllUsers();
        // find all users whose GH usernames do not exist or do not match their GH ID
        List<RegisteredUser> invalidUsers = allRegisteredUsers.stream()
                .filter(registeredUser -> StringUtils.isNotBlank(registeredUser.getGithubName()))
                .filter(registeredUser -> {
                    String ghUsername = registeredUser.getGithubName();
                    try {
                        User user = userService.getUserIfExists(ghUsername);
                        if (user == null || !Integer.valueOf(user.getId()).equals(registeredUser.getGithubId())) {
                            return true;
                        }
                        return false;
                    } catch (IOException e) {
                        logger.errorf("Unable to verify existence of GH user '%s': %s", ghUsername, e.getMessage());
                        return false; // Do not list the user as invalid.
                    }
                })
                .collect(Collectors.toList());
        return invalidUsers;
    }

    /**
     * Looks up GH username for given GH ID.
     *
     * @param githubId GH user ID
     * @return GH username
     */
    public String findGithubLoginForID(Integer githubId) {
        if (githubId == null) {
            return null;
        }
        try {
            User user = userService.getUserById(githubId);
            return user.getLogin();
        } catch (IOException e) {
            logger.warnf("Can't find GH user for ID %d", githubId);
            return null;
        }
    }

    /**
     * Checks whether LDAP account exists.
     *
     * @param ldapUsername LDAP username
     * @return Boolean signifying if LDAP account was found or not, or null if LDAP query failed.
     */
    public Boolean hasActiveLdapAccount(String ldapUsername) {
        try {
            return ldapClientBean.checkUserExists(ldapUsername);
        } catch (NamingException e) {
            logger.errorf(e, "Can't query LDAP for user %s", ldapUsername);
            return null;
        }
    }

    public List<RegisteredUser> getAllowedUsersList() {
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
                List<User> teamMembers = gitHubMembershipBean.getTeamsMembers(team);
                logger.infof("Discovered %d members of team %s",
                        teamMembers.size(), team.getOrganization().getName() + " / " + team.getName());
                for (User user: teamMembers) {
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
                Collection<User> organizationMembers = gitHubMembershipBean.getOrganizationMembers(organization);
                logger.infof("Discovered %d members of organization %s",
                        organizationMembers.size(), organization.getName());
                for (User user: organizationMembers) {
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
    @Deprecated
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
