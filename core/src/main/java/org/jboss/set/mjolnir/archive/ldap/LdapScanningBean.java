package org.jboss.set.mjolnir.archive.ldap;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.egit.github.core.Team;
import org.eclipse.egit.github.core.User;
import org.jboss.logging.Logger;
import org.jboss.set.mjolnir.archive.configuration.Configuration;
import org.jboss.set.mjolnir.archive.domain.GitHubOrganization;
import org.jboss.set.mjolnir.archive.domain.RegisteredUser;
import org.jboss.set.mjolnir.archive.domain.RemovalLog;
import org.jboss.set.mjolnir.archive.domain.UserRemoval;
import org.jboss.set.mjolnir.archive.domain.repositories.RegisteredUserRepositoryBean;
import org.jboss.set.mjolnir.archive.github.GitHubTeamServiceBean;

import javax.inject.Inject;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Discovers users that left the company and creates their removal records.
 *
 * This implementation works by querying LDAP database. It should eventually be replaced by an implementation
 * relying on JMS messages.
 */
@SuppressWarnings("UnnecessaryLocalVariable")
public class LdapScanningBean {

    private final Logger logger = Logger.getLogger(getClass());

    @Inject
    private EntityManager em;

    @Inject
    private LdapDiscoveryBean ldapDiscoveryBean;

    @Inject
    private GitHubTeamServiceBean gitHubBean;

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
        Set<String> usersWithoutLdapAccount = getUsersWithoutLdapAccount();

        // create removal records
        createUserRemovals(usersWithoutLdapAccount);
    }

    public Set<String> getUsersWithoutLdapAccount() throws IOException, NamingException {
        // collect members of all teams
        Set<String> allMembers = getAllOrganizationsMembers();
        logger.infof("Found %d members of all organizations teams.", allMembers.size());

        // retrieve kerberos names of collected users (those that we know and are not whitelisted)
        HashSet<String> krbNames = new HashSet<>();
        allMembers.forEach(member -> {
            Optional<RegisteredUser> registeredUser = userRepositoryBean.findByGitHubUsername(member);
            registeredUser.ifPresent(user -> {
                if (user.isWhitelisted()) {
                    logger.infof("Skipping whitelisted user %s.", user.getGithubName());
                } else if (StringUtils.isBlank(user.getKerberosName())) {
                    logger.warnf("Skipping user %s because of unknown LDAP name.", user.getGithubName());
                } else {
                    krbNames.add(user.getKerberosName());
                }
            });
        });
        logger.infof("Out of all members, %d are registered users.", krbNames.size());

        // search for users that do not have active LDAP account
        Map<String, Boolean> usersLdapMap = ldapDiscoveryBean.checkUsersExists(krbNames);
        SortedSet<String> usersWithoutLdapAccount = usersLdapMap.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(TreeSet::new));
        logger.infof("Detected %d users that do not have active LDAP account.", usersWithoutLdapAccount.size());

        return usersWithoutLdapAccount;
    }

    public Set<RegisteredUser> getWhitelistedUsersWithoutLdapAccount() throws NamingException {
        SortedSet<RegisteredUser> whitelistedUsersWithoutLdapAccount = new TreeSet<>((firstUser, secondUser) -> firstUser.getGithubName().compareToIgnoreCase(secondUser.getGithubName()));
        for (RegisteredUser whitelistedUser : getWhitelistedUsers()) {
            if (StringUtils.isBlank(whitelistedUser.getKerberosName()) || !ldapDiscoveryBean.checkUserExists(whitelistedUser.getKerberosName()))
                whitelistedUsersWithoutLdapAccount.add(whitelistedUser);
        }

        return whitelistedUsersWithoutLdapAccount;
    }

    public Set<RegisteredUser> getWhitelistedUsersWithLdapAccount() throws NamingException {
        SortedSet<RegisteredUser> whitelistedUsersWithLdapAccount = new TreeSet<>((firstUser, secondUser) -> firstUser.getGithubName().compareToIgnoreCase(secondUser.getGithubName()));
        for (RegisteredUser whitelistedUser : getWhitelistedUsers()) {
            if (!StringUtils.isBlank(whitelistedUser.getKerberosName()) && ldapDiscoveryBean.checkUserExists(whitelistedUser.getKerberosName()))
                whitelistedUsersWithLdapAccount.add(whitelistedUser);
        }

        return whitelistedUsersWithLdapAccount;
    }

    public List<RegisteredUser> getWhitelistedUsers() {
        return em.createNamedQuery(RegisteredUser.FIND_WHITELISTED, RegisteredUser.class).getResultList();
    }

    /**
     * Collects members of all teams of all registered GitHub organizations.
     */
    Set<String> getAllOrganizationsMembers() throws IOException {
        List<GitHubOrganization> organizations =
                em.createNamedQuery(GitHubOrganization.FIND_ALL, GitHubOrganization.class).getResultList();

        HashSet<User> users = new HashSet<>();
        for (GitHubOrganization organization : organizations) {
            users.addAll(gitHubBean.getAllTeamsMembers(organization.getName()));
        }

        return users.stream()
                .map(User::getLogin)
                .collect(Collectors.toSet());
    }

    public Set<String> getUnregisteredOrganizationMembers() throws IOException {
        Set<String> allMembers = getAllOrganizationsMembers();
        List<RegisteredUser> registeredUsers = em.createNamedQuery(RegisteredUser.FIND_ALL, RegisteredUser.class).getResultList();

        Set<String> unregisteredMembers = allMembers.stream()
                .filter(user -> !containsRegisteredUser(user, registeredUsers))
                .collect(Collectors.toSet());

        return unregisteredMembers;
    }

    public List<Team> getAllUsersTeams(String gitHubUser) throws IOException {
        List<Team> memberTeams = new ArrayList<>();

        List<GitHubOrganization> organizations =
                em.createNamedQuery(GitHubOrganization.FIND_ALL, GitHubOrganization.class).getResultList();

        List<Team> allTeams = new ArrayList<>();
        for (GitHubOrganization organization : organizations) {
            allTeams.addAll(gitHubBean.getTeams(organization.getName()));
        }

        for (Team team : allTeams) {
            if (gitHubBean.isMember(gitHubUser, team))
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

        krbNames.forEach(username -> {
            createUniqueUserRemoval(existingUserNamesToProcess, username);
        });

        em.getTransaction().commit();
    }

    public void createUserRemoval(String krbName) {
        em.getTransaction().begin();

        Set<String> existingUserNamesToProcess = getExistingUserNamesToProcess();
        createUniqueUserRemoval(existingUserNamesToProcess, krbName);

        em.getTransaction().commit();
    }

    private Set<String> getExistingUserNamesToProcess() {
        List<UserRemoval> existingRemovalsToProcess =
                em.createNamedQuery(UserRemoval.FIND_REMOVALS_TO_PROCESS, UserRemoval.class).getResultList();
        return existingRemovalsToProcess.stream().map(UserRemoval::getUsername).collect(Collectors.toSet());
    }

    private void createUniqueUserRemoval(Set<String> existingUserNamesToProcess, String userName) {
        if (existingUserNamesToProcess.contains(userName)) {
            logger.infof("Removal record for user %s already exists", userName);
        } else {
            logger.infof("Creating removal record for user %s", userName);
            UserRemoval removal = new UserRemoval();
            removal.setUsername(userName);
            em.persist(removal);
        }
    }
}
