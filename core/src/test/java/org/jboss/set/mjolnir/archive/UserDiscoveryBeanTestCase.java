package org.jboss.set.mjolnir.archive;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.deltaspike.core.util.ArraysUtils;
import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.assertj.core.groups.Tuple;
import org.jboss.set.mjolnir.archive.domain.GitHubOrganization;
import org.jboss.set.mjolnir.archive.domain.GitHubTeam;
import org.jboss.set.mjolnir.archive.domain.RegisteredUser;
import org.jboss.set.mjolnir.archive.domain.UserRemoval;
import org.jboss.set.mjolnir.archive.ldap.LdapClientBean;
import org.jboss.set.mjolnir.archive.util.MockitoAnswers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import javax.inject.Inject;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.set.mjolnir.archive.util.TestUtils.readSampleResponse;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doReturn;

@RunWith(CdiTestRunner.class)
public class UserDiscoveryBeanTestCase {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8089);

    @Inject
    private EntityManager em;

    @Inject
    private UserDiscoveryBean userDiscoveryBean;

    @Inject
    private LdapClientBean ldapClientBeanMock;

    @Before
    public void setup() throws IOException, URISyntaxException, NamingException {
        // clear data before each test

        clearData();


        // stubs for GitHub API endpoints

        stubFor(get(urlPathEqualTo("/api/v3/orgs/testorg/team/1/members"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(readSampleResponse("responses/gh-orgs-team-1-members-response.json"))));

        stubFor(get(urlPathEqualTo("/api/v3/orgs/testorg/team/2/members"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(readSampleResponse("responses/gh-orgs-team-2-members-response.json"))));

        stubFor(get(urlPathEqualTo("/api/v3/orgs/testorg/team/3/members"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(readSampleResponse("responses/empty-list-response.json"))));

        stubFor(get(urlPathEqualTo("/api/v3/teams/1/members/bob"))
                .willReturn(aResponse()
                        .withStatus(204)));

        stubFor(get(urlPathEqualTo("/api/v3/teams/2/members/bob"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody(readSampleResponse("responses/gh-orgs-members-not-found-response.json"))));

        stubFor(get(urlPathEqualTo("/api/v3/teams/3/members/bob"))
                .willReturn(aResponse()
                        .withStatus(204)));

        stubFor(get(urlPathEqualTo("/api/v3/orgs/testorg/members"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(readSampleResponse("responses/gh-orgs-testorg-members-response.json"))));


        // mock LdapDiscoveryBean behaviour
        Mockito.reset(ldapClientBeanMock);
        doReturn(false).when(ldapClientBeanMock).checkUserExists("bobNonExisting");
        doReturn(true).when(ldapClientBeanMock).checkUserExists("jimExisting");
        doReturn(false).when(ldapClientBeanMock).checkUserExists("ben");
        doReturn(false).when(ldapClientBeanMock).checkUserExists("bob");

        HashMap<Object, Object> ldapUsersMap = new HashMap<>();
        ldapUsersMap.put("ben", false);
        ldapUsersMap.put("bob", false);
        doReturn(ldapUsersMap).when(ldapClientBeanMock).checkUsersExists(ArraysUtils.asSet("ben", "bob"));
    }

    @After
    public void tearDown() {
        // don't let a transaction open outside of a test method
        if (em.getTransaction().isActive()) {
            em.getTransaction().rollback();
        }
    }

    @Test
    public void testCreateUserRemovals() {
        TypedQuery<UserRemoval> query = em.createNamedQuery(UserRemoval.FIND_REMOVALS_TO_PROCESS, UserRemoval.class);
        Assert.assertTrue(query.getResultList().isEmpty());

        // create removals
        userDiscoveryBean.createUserRemovals(Arrays.asList("ben", "bob"));

        // check removals were created
        List<UserRemoval> removals = query.getResultList();
        assertThat(removals)
                .extracting("ldapUsername")
                .containsOnly("ben", "bob");

        // create the same removals again
        userDiscoveryBean.createUserRemovals(Arrays.asList("ben", "bob"));

        // check that duplicates were not created
        removals = query.getResultList();
        assertThat(removals.size()).isEqualTo(2);
        assertThat(removals)
                .extracting("ldapUsername")
                .containsOnly("ben", "bob");
    }

    @Test
    public void testAllOrganizationMembers() throws IOException {
        HashMap<String, List<GitHubTeam>> members = userDiscoveryBean.getAllTeamsMembers();
        assertThat(members.keySet()).containsOnly("bob", "ben");
        assertThat(members.get("bob")).extracting("name").containsOnly("Team 1");
        assertThat(members.get("ben")).extracting("name").containsOnly("Team 2");
    }

    @Test
    public void testGetUnregisteredTeamsMembers() throws IOException {
        createRegisteredUser(null, "bob", false);

        Map<String, List<GitHubTeam>> members = userDiscoveryBean.findUnregisteredTeamsMembers();
        assertThat(members.keySet()).containsOnly("ben");
        assertThat(members.get("ben")).extracting("name").containsOnly("Team 2");
    }

    @Test
    public void testGetUnregisteredTeamsMembersCaseInsensitive() throws IOException {
        createRegisteredUser(null, "Bob", false);

        Map<String, List<GitHubTeam>> members = userDiscoveryBean.findUnregisteredTeamsMembers();
        assertThat(members.keySet()).containsOnly("ben");
        assertThat(members.get("ben")).extracting("name").containsOnly("Team 2");
    }

    @Test
    public void testUnregisteredOrganizationMembers() throws IOException {
        createRegisteredUser(null, "bob", false);

        Map<String, List<GitHubOrganization>> members = userDiscoveryBean.findUnregisteredOrganizationsMembers();
        assertThat(members.keySet()).containsOnly("ben");
        assertThat(members.get("ben")).extracting("name").containsOnly("testorg");
    }

    @Test
    public void testUnregisteredOrganizationMembersCaseInsensitive() throws IOException {
        createRegisteredUser(null, "Bob", false);

        Map<String, List<GitHubOrganization>> members = userDiscoveryBean.findUnregisteredOrganizationsMembers();
        assertThat(members.keySet()).containsOnly("ben");
        assertThat(members.get("ben")).extracting("name").containsOnly("testorg");
    }

    @Test
    public void testFindAllUsersWithoutLdapAccount() throws IOException, NamingException {
        createRegisteredUser("bob", "Bob", false);
        createRegisteredUser("ben", "ben", false);

        HashMap<String, Boolean> usersLdapMap = new HashMap<>();
        usersLdapMap.put("ben", false);
        usersLdapMap.put("bob", true);
        Mockito.when(ldapClientBeanMock.checkUsersExists(anyCollection())).thenReturn(usersLdapMap);

        Collection<String> users = userDiscoveryBean.findAllUsersWithoutLdapAccount();
        assertThat(users).containsOnly("ben");
    }

    @Test
    public void testFindOrganizationsMembersWithoutLdapAccount() throws IOException, NamingException {
        createRegisteredUser("bob", "Bob", false);
        createRegisteredUser("ben", "ben", false);

        HashMap<String, Boolean> usersLdapMap = new HashMap<>();
        usersLdapMap.put("ben", false);
        usersLdapMap.put("bob", true);
        Mockito.when(ldapClientBeanMock.checkUsersExists(anyCollection())).thenReturn(usersLdapMap);

        Map<String, List<GitHubOrganization>> users = userDiscoveryBean.findOrganizationsMembersWithoutLdapAccount();

        assertThat(users.keySet()).containsOnly("ben");
        assertThat(users.get("ben")).extracting("name").containsOnly("testorg");
    }

    @Test
    public void testFindOrganizationsMembersWithoutLdapAccountWhenRemovalExists() throws IOException, NamingException {
        createRegisteredUser("bob", "Bob", false);
        createRegisteredUser("ben", "ben", false);
        createUserRemoval("ben");

        HashMap<String, Boolean> usersLdapMap = new HashMap<>();
        usersLdapMap.put("ben", false);
        usersLdapMap.put("bob", true);
        Mockito.when(ldapClientBeanMock.checkUsersExists(anyCollection())).thenReturn(usersLdapMap);

        Map<String, List<GitHubOrganization>> users = userDiscoveryBean.findOrganizationsMembersWithoutLdapAccount();

        // user shouldn't be included because a removal record exists
        assertThat(users.keySet()).isEmpty();
    }

    @Test
    public void testFindOrganizationsMembersWithoutLdapAccountWhenOldRemovalExists() throws IOException, NamingException {
        createRegisteredUser("bob", "Bob", false);
        createRegisteredUser("ben", "ben", false);
        UserRemoval removal = createUserRemoval("ben");
        // move created timestamp 2 days back
        em.getTransaction().begin();
        int updatedRecords = em.createNativeQuery("update user_removals set created = CURRENT_TIMESTAMP - 2 where id = " + removal.getId()).executeUpdate();
        assertThat(updatedRecords).isEqualTo(1);
        em.getTransaction().commit();
        em.clear();

        HashMap<String, Boolean> usersLdapMap = new HashMap<>();
        usersLdapMap.put("ben", false);
        usersLdapMap.put("bob", true);
        Mockito.when(ldapClientBeanMock.checkUsersExists(anyCollection())).thenReturn(usersLdapMap);

        Map<String, List<GitHubOrganization>> users = userDiscoveryBean.findOrganizationsMembersWithoutLdapAccount();

        // removal record is older than a day, user should be reported
        assertThat(users.keySet()).containsOnly("ben");
        assertThat(users.get("ben")).extracting("name").containsOnly("testorg");
    }

    @Test
    public void testFindTeamsMembersWithoutLdapAccount() throws IOException, NamingException {
        createRegisteredUser("bob", "Bob", false);
        createRegisteredUser("ben", "ben", false);

        HashMap<String, Boolean> usersLdapMap = new HashMap<>();
        usersLdapMap.put("ben", false);
        usersLdapMap.put("bob", true);
        Mockito.when(ldapClientBeanMock.checkUsersExists(anyCollection())).thenReturn(usersLdapMap);

        Map<String, List<GitHubTeam>> users = userDiscoveryBean.findTeamsMembersWithoutLdapAccount();

        assertThat(users.keySet()).containsOnly("ben");
        assertThat(users.get("ben")).extracting("name").containsOnly("Team 2");
    }

    @Test
    public void testFindTeamsMembersWithoutLdapAccountWhenRemovalExists() throws IOException, NamingException {
        createRegisteredUser("bob", "Bob", false);
        createRegisteredUser("ben", "ben", false);
        createUserRemoval("ben");

        HashMap<String, Boolean> usersLdapMap = new HashMap<>();
        usersLdapMap.put("ben", false);
        usersLdapMap.put("bob", true);
        Mockito.when(ldapClientBeanMock.checkUsersExists(anyCollection())).thenReturn(usersLdapMap);

        Map<String, List<GitHubTeam>> users = userDiscoveryBean.findTeamsMembersWithoutLdapAccount();

        // removal record exists, user should not be reported
        assertThat(users.keySet()).isEmpty();
    }

    @Test
    public void testFindTeamsMembersWithoutLdapAccountWhenOldRemovalExists() throws IOException, NamingException {
        createRegisteredUser("bob", "Bob", false);
        createRegisteredUser("ben", "ben", false);
        UserRemoval removal = createUserRemoval("ben");
        // move created timestamp 2 days back
        em.getTransaction().begin();
        int updatedRecords = em.createNativeQuery("update user_removals set created = CURRENT_TIMESTAMP - 2 where id = " + removal.getId()).executeUpdate();
        assertThat(updatedRecords).isEqualTo(1);
        em.getTransaction().commit();
        em.clear();

        HashMap<String, Boolean> usersLdapMap = new HashMap<>();
        usersLdapMap.put("ben", false);
        usersLdapMap.put("bob", true);
        Mockito.when(ldapClientBeanMock.checkUsersExists(anyCollection())).thenReturn(usersLdapMap);

        Map<String, List<GitHubTeam>> users = userDiscoveryBean.findTeamsMembersWithoutLdapAccount();

        // removal record is older than a day, user should be reported
        assertThat(users.keySet()).containsOnly("ben");
        assertThat(users.get("ben")).extracting("name").containsOnly("Team 2");
    }

    @Test
    public void testWhitelistedUsers() {
        createRegisteredUser("bobNonExisting", "bob", true);
        createRegisteredUser("jimExisting", "jim", true);
        createRegisteredUser(null, "ben", true);
        createRegisteredUser(null, "joe", false);

        List<RegisteredUser> members = userDiscoveryBean.getAllowedUsersList();
        assertThat(members)
                .extracting("githubName")
                .containsOnly("ben", "bob", "jim");
    }

    @Test
    public void testWhitelistedUsersCaseInsensitive() {
        createRegisteredUser("bobNonExisting", "BOB", true);
        createRegisteredUser("jimExisting", "JIM", "responsible guy", true);
        createRegisteredUser(null, "BEN", true);
        createRegisteredUser(null, "JOE", false);

        List<RegisteredUser> members = userDiscoveryBean.getAllowedUsersList();
        assertThat(members)
                .extracting("githubName", "responsiblePerson")
                .containsOnly(
                        Tuple.tuple("JIM", "responsible guy"),
                        Tuple.tuple("BOB", null),
                        Tuple.tuple("BEN", null)
                );
    }

    @Test
    public void testAllUsersTeams() throws IOException {
        List<GitHubTeam> teams = userDiscoveryBean.getAllUsersTeams("bob");
        assertThat(teams)
                .extracting("name")
                .containsOnly("Team 1", "Team 3");
    }

    @Test
    public void testCreateRemovalsForUsersWithoutLdapAccount() throws NamingException {
        createRegisteredUser("bob", "bob", false);
        createRegisteredUser("ben", "ben", false);
        Mockito.when(ldapClientBeanMock.checkUsersExists(anyCollection()))
                .thenAnswer(new MockitoAnswers.UsersNotInLdapAnswer());

        userDiscoveryBean.createRemovalsForUsersWithoutLdapAccount();

        TypedQuery<UserRemoval> query = em.createNamedQuery(UserRemoval.FIND_REMOVALS_TO_PROCESS, UserRemoval.class);
        List<UserRemoval> removals = query.getResultList();
        assertThat(removals)
                .extracting("ldapUsername")
                .containsOnly("ben", "bob");
    }

    @Test
    public void testCreateRemovalsForUsersWithoutLdapAccountCaseInsensitive() throws NamingException {
        createRegisteredUser("bob", "BOB", false);
        createRegisteredUser("ben", "BEN", false);
        Mockito.when(ldapClientBeanMock.checkUsersExists(anyCollection()))
                .thenAnswer(new MockitoAnswers.UsersNotInLdapAnswer());

        userDiscoveryBean.createRemovalsForUsersWithoutLdapAccount();

        TypedQuery<UserRemoval> query = em.createNamedQuery(UserRemoval.FIND_REMOVALS_TO_PROCESS, UserRemoval.class);
        List<UserRemoval> removals = query.getResultList();
        assertThat(removals)
                .extracting("ldapUsername")
                .containsOnly("ben", "bob");
    }

    @Test
    public void testDontCreateDuplicateRemovals() throws NamingException {
        createRegisteredUser("bob", "bob", false);
        createRegisteredUser("ben", "ben", false);
        // create already existing removal
        createUserRemoval("bob");
        Mockito.when(ldapClientBeanMock.checkUsersExists(anyCollection()))
                .thenAnswer(new MockitoAnswers.UsersNotInLdapAnswer());

        userDiscoveryBean.createRemovalsForUsersWithoutLdapAccount();

        // verify that the removal for bob is not duplicated
        TypedQuery<UserRemoval> query = em.createNamedQuery(UserRemoval.FIND_REMOVALS_TO_PROCESS, UserRemoval.class);
        List<UserRemoval> removals = query.getResultList();
        assertThat(removals.size()).isEqualTo(2);
        assertThat(removals)
                .extracting("ldapUsername")
                .containsOnly("ben", "bob");
    }

    private void createRegisteredUser(String username, String githubName, boolean whitelisted) {
        createRegisteredUser(username, githubName, null, whitelisted);
    }

    private void createRegisteredUser(String username, String githubName, String responsiblePerson, boolean whitelisted) {
        RegisteredUser registeredUser = new RegisteredUser();
        registeredUser.setGithubName(githubName);
        registeredUser.setKerberosName(username);
        registeredUser.setResponsiblePerson(responsiblePerson);
        registeredUser.setWhitelisted(whitelisted);

        em.getTransaction().begin();
        em.persist(registeredUser);
        em.getTransaction().commit();
    }

    private UserRemoval createUserRemoval(String username) {
        UserRemoval userRemoval = new UserRemoval();
        userRemoval.setLdapUsername(username);

        return createUserRemoval(userRemoval);
    }

    private UserRemoval createUserRemoval(UserRemoval userRemoval) {
        em.getTransaction().begin();
        em.persist(userRemoval);
        em.getTransaction().commit();

        return userRemoval;
    }

    private void clearData() {
        em.getTransaction().begin();
        em.createQuery("delete from UserRemoval").executeUpdate();
        em.createQuery("delete from RegisteredUser").executeUpdate();
        em.getTransaction().commit();
    }
}
