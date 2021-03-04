package org.jboss.set.mjolnir.archive.ldap;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.deltaspike.core.util.ArraysUtils;
import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.assertj.core.groups.Tuple;
import org.jboss.set.mjolnir.archive.domain.GitHubTeam;
import org.jboss.set.mjolnir.archive.domain.RegisteredUser;
import org.jboss.set.mjolnir.archive.domain.UserRemoval;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.set.mjolnir.archive.util.TestUtils.readSampleResponse;
import static org.mockito.Mockito.doReturn;

@RunWith(CdiTestRunner.class)
public class LdapScanningBeanTestCase {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8089);

    @Inject
    private EntityManager em;

    @Inject
    private LdapScanningBean ldapScanningBean;

    @Inject
    private LdapDiscoveryBean ldapDiscoveryBeanMock;

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


        // mock LdapDiscoveryBean behaviour

        doReturn(false).when(ldapDiscoveryBeanMock).checkUserExists("bobNonExisting");
        doReturn(true).when(ldapDiscoveryBeanMock).checkUserExists("jimExisting");
        doReturn(false).when(ldapDiscoveryBeanMock).checkUserExists("ben");
        doReturn(false).when(ldapDiscoveryBeanMock).checkUserExists("bob");

        HashMap<Object, Object> ldapUsersMap = new HashMap<>();
        ldapUsersMap.put("ben", false);
        ldapUsersMap.put("bob", false);
        doReturn(ldapUsersMap).when(ldapDiscoveryBeanMock).checkUsersExists(ArraysUtils.asSet("ben", "bob"));
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
        ldapScanningBean.createUserRemovals(Arrays.asList("ben", "bob"));

        // check removals were created
        List<UserRemoval> removals = query.getResultList();
        assertThat(removals)
                .extracting("ldapUsername")
                .containsOnly("ben", "bob");

        // create the same removals again
        ldapScanningBean.createUserRemovals(Arrays.asList("ben", "bob"));

        // check that duplicates were not created
        removals = query.getResultList();
        assertThat(removals.size()).isEqualTo(2);
        assertThat(removals)
                .extracting("ldapUsername")
                .containsOnly("ben", "bob");
    }

    @Test
    public void testAllOrganizationMembers() throws IOException {
        Set<String> members = ldapScanningBean.getAllTeamsMembers();
        assertThat(members).containsOnly("bob", "ben");
    }

    @Test
    public void testUnregisteredOrganizationMembers() throws IOException {
        createRegisteredUser(null, "bob", false);

        Set<String> members = ldapScanningBean.getUnregisteredOrganizationMembers();
        assertThat(members).containsOnly("ben");
    }

    @Test
    public void testUnregisteredOrganizationMembersCaseInsensitive() throws IOException {
        createRegisteredUser(null, "Bob", false);

        Set<String> members = ldapScanningBean.getUnregisteredOrganizationMembers();
        assertThat(members).containsOnly("ben");
    }

    @Test
    public void testWhitelistedUsers() {
        createRegisteredUser("bobNonExisting", "bob", true);
        createRegisteredUser("jimExisting", "jim", true);
        createRegisteredUser(null, "ben", true);
        createRegisteredUser(null, "joe", false);

        List<RegisteredUser> members = ldapScanningBean.getWhitelistedUsers();
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

        List<RegisteredUser> members = ldapScanningBean.getWhitelistedUsers();
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
        List<GitHubTeam> teams = ldapScanningBean.getAllUsersTeams("bob");
        assertThat(teams)
                .extracting("name")
                .containsOnly("Team 1", "Team 3");
    }

    @Test
    public void testCreateRemovalsForUsersWithoutLdapAccount() {
        createRegisteredUser("bob", "bob", false);
        createRegisteredUser("ben", "ben", false);

        ldapScanningBean.createRemovalsForUsersWithoutLdapAccount();

        TypedQuery<UserRemoval> query = em.createNamedQuery(UserRemoval.FIND_REMOVALS_TO_PROCESS, UserRemoval.class);
        List<UserRemoval> removals = query.getResultList();
        assertThat(removals)
                .extracting("ldapUsername")
                .containsOnly("ben", "bob");
    }

    @Test
    public void testCreateRemovalsForUsersWithoutLdapAccountCaseInsensitive() {
        createRegisteredUser("bob", "BOB", false);
        createRegisteredUser("ben", "BEN", false);

        ldapScanningBean.createRemovalsForUsersWithoutLdapAccount();

        TypedQuery<UserRemoval> query = em.createNamedQuery(UserRemoval.FIND_REMOVALS_TO_PROCESS, UserRemoval.class);
        List<UserRemoval> removals = query.getResultList();
        assertThat(removals)
                .extracting("ldapUsername")
                .containsOnly("ben", "bob");
    }

    @Test
    public void testDontCreateDuplicateRemovals() {
        createRegisteredUser("bob", "bob", false);
        createRegisteredUser("ben", "ben", false);
        // create already existing removal
        createUserRemoval("bob");

        ldapScanningBean.createRemovalsForUsersWithoutLdapAccount();

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

    private void createUserRemoval(String username) {
        UserRemoval userRemoval = new UserRemoval();
        userRemoval.setLdapUsername(username);

        em.getTransaction().begin();
        em.persist(userRemoval);
        em.getTransaction().commit();
    }

    private void clearData() {
        em.getTransaction().begin();
        em.createQuery("delete from UserRemoval").executeUpdate();
        em.createQuery("delete from RegisteredUser").executeUpdate();
        em.getTransaction().commit();
    }
}
