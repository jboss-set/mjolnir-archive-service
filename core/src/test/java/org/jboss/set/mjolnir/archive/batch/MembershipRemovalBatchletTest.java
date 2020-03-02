package org.jboss.set.mjolnir.archive.batch;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.assertj.core.groups.Tuple;
import org.jboss.set.mjolnir.archive.domain.GitHubOrganization;
import org.jboss.set.mjolnir.archive.domain.RemovalStatus;
import org.jboss.set.mjolnir.archive.domain.UserRemoval;
import org.jboss.set.mjolnir.archive.util.TestUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(CdiTestRunner.class)
public class MembershipRemovalBatchletTest {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8089);

    @Inject
    private EntityManager em;

    @Inject
    private MembershipRemovalBatchlet batchlet;

    @Before
    public void setup() throws Exception {
        // create two sample removals

        em.getTransaction().begin();

        UserRemoval userRemoval = new UserRemoval();
        userRemoval.setUsername("thofman");
        em.persist(userRemoval);

        userRemoval = new UserRemoval();
        userRemoval.setUsername("lvydra");
        em.persist(userRemoval);

        em.getTransaction().commit();
    }

    @Test
    public void testRemovalsMarked() throws Exception {
        // verify there are two fresh removals present in the database
        TypedQuery<UserRemoval> findRemovalsQuery = em.createNamedQuery(UserRemoval.FIND_REMOVALS_TO_PROCESS, UserRemoval.class);
        List<UserRemoval> removals = findRemovalsQuery.getResultList();
        assertThat(removals.size()).isEqualTo(2);

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
        assertThat(orgs.get(0).getTeams())
                .extracting("name")
                .containsOnly("Test Team", "Other Team");
    }

    @Test
    public void testFindGitHubUsername() {
        String ghUsername = batchlet.findGitHubUsername("thofman");
        assertThat(ghUsername).isEqualTo("TomasHofman");
    }

    @Test
    public void testBatchlet() throws Exception {
        TestUtils.setupGitHubApiStubs();

        String result = batchlet.process();
        em.clear();
        assertThat(result).isEqualTo("DONE");

        // verify that all removals has been marked as processed
        TypedQuery<UserRemoval> findRemovalsQuery = em.createNamedQuery(UserRemoval.FIND_REMOVALS_TO_PROCESS, UserRemoval.class);
        List<UserRemoval> removalsToProcess = findRemovalsQuery.getResultList();
        assertThat(removalsToProcess.size()).isEqualTo(0);

        // verify the removal state
        List<UserRemoval> removals = em.createQuery("SELECT r FROM UserRemoval r where r.username = :username", UserRemoval.class)
                .setParameter("username", "thofman")
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

        // TODO: verify that repositories were archived
    }

}
