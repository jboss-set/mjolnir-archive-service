package org.jboss.set.mjolnir.archive.batch;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.assertj.core.groups.Tuple;
import org.eclipse.egit.github.core.Repository;
import org.jboss.set.mjolnir.archive.ArchivingBean;
import org.jboss.set.mjolnir.archive.domain.GitHubOrganization;
import org.jboss.set.mjolnir.archive.domain.RemovalStatus;
import org.jboss.set.mjolnir.archive.domain.UnsubscribeStatus;
import org.jboss.set.mjolnir.archive.domain.UnsubscribedUserFromOrg;
import org.jboss.set.mjolnir.archive.domain.UnsubscribedUserFromTeam;
import org.jboss.set.mjolnir.archive.domain.UserRemoval;
import org.jboss.set.mjolnir.archive.util.TestUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.sql.Date;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(CdiTestRunner.class)
public class MembershipRemovalBatchletTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8089);

    @Inject
    private EntityManager em;

    @Inject
    private MembershipRemovalBatchlet batchlet;

    @Inject
    private ArchivingBean archivingBeanMock;

    @Before
    public void setup() {
        Mockito.reset(archivingBeanMock);

        // create sample removals

        em.getTransaction().begin();

        em.createNativeQuery("delete from repository_forks").executeUpdate();
        em.createNativeQuery("delete from unsubscribed_users_from_orgs").executeUpdate();
        em.createNativeQuery("delete from unsubscribed_users_from_teams").executeUpdate();
        em.createNativeQuery("delete from user_removals").executeUpdate();
        em.clear();

        UserRemoval userRemoval = new UserRemoval();
        userRemoval.setLdapUsername("thofman");
        em.persist(userRemoval);

        userRemoval = new UserRemoval();
        userRemoval.setLdapUsername("lvydra");
        userRemoval.setRemoveOn(new Date(System.currentTimeMillis() - (24 * 3600 * 1000))); // removal date 1 day in past
        em.persist(userRemoval);

        userRemoval = new UserRemoval();
        userRemoval.setLdapUsername("future");
        userRemoval.setRemoveOn(new Date(System.currentTimeMillis() + (24 * 3600 * 1000))); // removal date 1 day in future
        em.persist(userRemoval);

        userRemoval = new UserRemoval();
        userRemoval.setGithubUsername("GitHubUsername");
        em.persist(userRemoval);

        em.getTransaction().commit();
    }

    @Test
    public void testRemovalsMarked() {
        // verify there are two fresh removals present in the database
        TypedQuery<UserRemoval> findRemovalsQuery = em.createNamedQuery(UserRemoval.FIND_REMOVALS_TO_PROCESS, UserRemoval.class);
        List<UserRemoval> removals = findRemovalsQuery.getResultList();
        assertThat(removals)
                .extracting("ldapUsername", "githubUsername")
                .containsOnly(Tuple.tuple("thofman", null),
                        Tuple.tuple("lvydra", null),
                        Tuple.tuple(null, "GitHubUsername"));
        assertThat(removals.size()).isEqualTo(3);

        // let the batchlet load the removals
        batchlet.loadRemovalsToProcess();

        // verify removals are not available anymore
        removals = findRemovalsQuery.getResultList();
        assertThat(removals).isEmpty();
    }

    @Test
    public void testLoadOrganizations() {
        List<GitHubOrganization> orgs = batchlet.loadOrganizations();
        assertThat(orgs.size()).isEqualTo(1);
        assertThat(orgs.get(0).getName()).isEqualTo("testorg");
        assertThat(orgs.get(0).isUnsubscribeUsersFromOrg()).isEqualTo(false);
        assertThat(orgs.get(0).getTeams())
                .extracting("name")
                .containsOnly("Team 1", "Team 2", "Team 3");
    }

    @Test
    public void testFindGitHubUsername() {
        String ghUsername = batchlet.findGitHubUsername("thofman");
        assertThat(ghUsername).isEqualTo("TomasHofman");
    }

    @Test
    public void testBatchlet() throws Exception {
        setUnsubscribeFromOrganization(false);
        verifyBatchletProcessing(false);
    }

    @Test
    public void testBatchlet_removeFromOrg() throws Exception {
        setUnsubscribeFromOrganization(true);
        verifyBatchletProcessing(true);
    }

    private void setUnsubscribeFromOrganization(boolean enable) {
        em.getTransaction().begin();
        GitHubOrganization org = em.find(GitHubOrganization.class, 1L);
        org.setUnsubscribeUsersFromOrg(enable);
        em.persist(org);
        em.getTransaction().commit();
    }

    private void verifyBatchletProcessing(boolean removeFromOrg) throws Exception {
        TestUtils.setupGitHubApiStubs();

        String result = batchlet.process();
        em.clear();
        assertThat(result).isEqualTo("DONE");

        // verify that all removals has been marked as processed
        TypedQuery<UserRemoval> findRemovalsQuery = em.createNamedQuery(UserRemoval.FIND_REMOVALS_TO_PROCESS, UserRemoval.class);
        List<UserRemoval> removalsToProcess = findRemovalsQuery.getResultList();
        assertThat(removalsToProcess.size()).isEqualTo(0);

        // verify processed removal state
        List<UserRemoval> removals = em.createQuery("SELECT r FROM UserRemoval r where r.ldapUsername = :ldapUsername", UserRemoval.class)
                .setParameter("ldapUsername", "thofman")
                .getResultList();
        assertThat(removals.size()).isEqualTo(1);
        assertThat(removals.get(0)).satisfies(removal -> {
            em.refresh(removal);
            assertThat(removal.getStatus()).isEqualTo(RemovalStatus.COMPLETED);
            assertThat(removal.getStarted()).isNotNull();
            assertThat(removal.getCompleted()).isNotNull();

            // verify that repository forks has been saved
            assertThat(removal.getForks())
                    .extracting("sourceRepositoryName", "repositoryName")
                    .containsOnly(
                            Tuple.tuple("testorg/aphrodite", "TomasHofman/aphrodite"),
                            Tuple.tuple("testorg/activemq-artemis", "TomasHofman/activemq-artemis")
                    );
        });

        // verify unprocessed removal state (no github name registered)
        removals = em.createQuery("SELECT r FROM UserRemoval r where r.ldapUsername = :ldapUsername", UserRemoval.class)
                .setParameter("ldapUsername", "lvydra")
                .getResultList();
        assertThat(removals.size()).isEqualTo(1);
        assertThat(removals.get(0)).satisfies(removal -> {
            em.refresh(removal);
            assertThat(removal.getStatus()).isEqualTo(RemovalStatus.UNKNOWN_USER);
            assertThat(removal.getLogs())
                    .extracting("message")
                    .contains("Ignoring removal request for user lvydra");
        });

        // verify processed removal state
        removals = em.createQuery("SELECT r FROM UserRemoval r where r.githubUsername = :githubUsername", UserRemoval.class)
                .setParameter("githubUsername", "GitHubUsername")
                .getResultList();
        assertThat(removals.size()).isEqualTo(1);
        assertThat(removals.get(0)).satisfies(removal -> {
            em.refresh(removal);
            assertThat(removal.getStatus()).isEqualTo(RemovalStatus.COMPLETED);
            assertThat(removal.getStarted()).isNotNull();
            assertThat(removal.getCompleted()).isNotNull();
        });

        // verify that repositories were archived (ArchivingBean is mocked)
        ArgumentCaptor<Repository> repositoryArgumentCaptor = ArgumentCaptor.forClass(Repository.class);
        verify(archivingBeanMock, times(2))
                .createRepositoryMirror(repositoryArgumentCaptor.capture());
        assertThat(repositoryArgumentCaptor.getAllValues())
                .extracting("cloneUrl")
                .containsOnly("https://github.com/TomasHofman/aphrodite.git",
                        "https://github.com/TomasHofman/activemq-artemis.git");

        // verify that user was removed from GitHub teams
        WireMock.verify(getRequestedFor(urlEqualTo("/api/v3/teams/1/members/TomasHofman")));
        WireMock.verify(getRequestedFor(urlEqualTo("/api/v3/teams/2/members/TomasHofman")));
        WireMock.verify(getRequestedFor(urlEqualTo("/api/v3/teams/3/members/TomasHofman")));
        WireMock.verify(deleteRequestedFor(urlEqualTo("/api/v3/teams/1/members/TomasHofman")));
        WireMock.verify(0, deleteRequestedFor(urlEqualTo("/api/v3/teams/2/members/TomasHofman")));
        WireMock.verify(0, deleteRequestedFor(urlEqualTo("/api/v3/teams/3/members/TomasHofman")));

        if (removeFromOrg) {
            // verify that user was removed from GitHub organization
            WireMock.verify(getRequestedFor(urlEqualTo("/api/v3/orgs/testorg/members/TomasHofman")));
            WireMock.verify(deleteRequestedFor(urlEqualTo("/api/v3/orgs/testorg/members/TomasHofman")));
        } else {
            // verify that user was not removed from GitHub organization
            WireMock.verify(0, getRequestedFor(urlEqualTo("/api/v3/orgs/testorg/members/TomasHofman")));
            WireMock.verify(0, deleteRequestedFor(urlEqualTo("/api/v3/orgs/testorg/members/TomasHofman")));
        }
    }
    
    @Test
    public void testProcessRemoval_auditLog() throws Exception {
        // configure that users should be unsubscribed from testorg
        setUnsubscribeFromOrganization(true);
        // configure GH API stubs
        TestUtils.setupGitHubApiStubs();

        // get a single removal to process
        UserRemoval removal = em.createQuery("select r from UserRemoval r where ldapUsername = 'thofman'", UserRemoval.class)
                .getSingleResult();
        assertThat(removal.getStatus()).isNull();

        // the #processRemoval() method doesn't open a transaction by itself, so need to do it here
        em.getTransaction().begin();

        // let the removal be processed
        batchlet.processRemoval(removal);
        em.getTransaction().commit();

        // verify the processing ended successfully
        em.refresh(removal);
        assertThat(removal.getStatus()).isEqualTo(RemovalStatus.COMPLETED);

        // verify audit log for unsubscribed orgs
        List<UnsubscribedUserFromOrg> unsubscribedUsers =
                em.createQuery("select u from UnsubscribedUserFromOrg u", UnsubscribedUserFromOrg.class)
                        .getResultList();
        assertThat(unsubscribedUsers).extracting("userRemoval.id", "githubUsername", "githubOrgName", "status")
                .containsOnly(Tuple.tuple(removal.getId(), "TomasHofman", "testorg", UnsubscribeStatus.COMPLETED));

        // verify audit log for unsubscribed teams
        List<UnsubscribedUserFromTeam> unsubscribedUserFromTeam = em.createQuery("select u from UnsubscribedUserFromTeam u", UnsubscribedUserFromTeam.class)
                .getResultList();
        assertThat(unsubscribedUserFromTeam).extracting("userRemoval.id", "githubUsername","githubTeamName", "githubOrgName", "status")
                .contains(Tuple.tuple(removal.getId(), "TomasHofman", "Team 1", "testorg", UnsubscribeStatus.COMPLETED));
        
    }

}
