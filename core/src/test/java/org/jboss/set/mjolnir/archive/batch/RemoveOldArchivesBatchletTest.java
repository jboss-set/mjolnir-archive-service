package org.jboss.set.mjolnir.archive.batch;

import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.assertj.core.groups.Tuple;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.jboss.set.mjolnir.archive.configuration.Configuration;
import org.jboss.set.mjolnir.archive.domain.RepositoryFork;
import org.jboss.set.mjolnir.archive.domain.RepositoryForkStatus;
import org.jboss.set.mjolnir.archive.domain.UserRemoval;
import org.jboss.set.mjolnir.archive.util.GitRepositoryUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(CdiTestRunner.class)
public class RemoveOldArchivesBatchletTest {

    private static final LocalDate TWO_MONTHS_AGO = LocalDate.now().minusDays(60);
    private static final LocalDate THREE_MONTHS_AGO = LocalDate.now().minusDays(90);
    private static final LocalDate FOUR_MONTHS_AGO = LocalDate.now().minusDays(120);
    private static final LocalDate TODAY = LocalDate.now();
    private static final LocalDate YESTERDAY = LocalDate.now().minusDays(1);

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    @Inject
    private EntityManager em;

    @Inject
    private RemoveOldArchivesBatchlet batchlet;

    @Before
    public void setup() throws GitAPIException, IOException, URISyntaxException {
        /*
        Following repository_fork records are created here:
        - 1. user1/repo1, created 90 days ago,
        - 2. user2/repo1, created 120 days ago,
        - 3. user2/repo1, created 90 days ago,
        - 4. user2/repo1, created 60 days ago,
        - 5. user2/repo2, created 90 days ago, deleted 2 days ago
         */


        em.getTransaction().begin();

        // clear tables to avoid object accumulation
        em.createNativeQuery("delete from repository_forks;").executeUpdate();
        em.createNativeQuery("delete from user_removals;").executeUpdate();

        // create one removal record since RepositoryForks must reference it
        UserRemoval userRemoval = new UserRemoval();
        userRemoval.setLdapUsername("thofman");
        em.persist(userRemoval);


        // create some sample repository records

        RepositoryFork fork = new RepositoryFork();
        fork.setUserRemoval(userRemoval);
        fork.setRepositoryName("user1/repo1");
        fork.setSourceRepositoryName("upstream/repo1");
        fork.setStatus(RepositoryForkStatus.ARCHIVED);
        em.persist(fork);

        // insert three duplicate records (same repo name), with different creation dates
        RepositoryFork oldestFork = fork = new RepositoryFork();
        fork.setUserRemoval(userRemoval);
        fork.setRepositoryName("user2/repo1");
        fork.setSourceRepositoryName("upstream/repo1");
        fork.setStatus(RepositoryForkStatus.ARCHIVED);
        em.persist(fork);

        fork = new RepositoryFork();
        fork.setUserRemoval(userRemoval);
        fork.setRepositoryName("user2/repo1");
        fork.setSourceRepositoryName("upstream/repo1");
        fork.setStatus(RepositoryForkStatus.ARCHIVED);
        em.persist(fork);

        RepositoryFork newestFork = fork = new RepositoryFork();
        fork.setUserRemoval(userRemoval);
        fork.setRepositoryName("user2/repo1");
        fork.setSourceRepositoryName("upstream/repo1");
        fork.setStatus(RepositoryForkStatus.ARCHIVED);
        em.persist(fork);

        // this record already has a deleted date, so should never be loaded
        fork = new RepositoryFork();
        fork.setUserRemoval(userRemoval);
        fork.setRepositoryName("user2/repo2");
        fork.setSourceRepositoryName("upstream/repo2");
        fork.setDeleted(Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS))); // processed yesterday
        fork.setStatus(RepositoryForkStatus.DELETION_FAILED);
        em.persist(fork);

        // clear entity cache, so that the entities are loaded anew
        em.flush();
        em.clear();

        // reset created timestamps of all records three months back
        em.createNativeQuery("update repository_forks set created = now() - interval 90 day")
                .executeUpdate();
        // set created timestamp for the first duplicate record four months back
        em.createNativeQuery("update repository_forks set created = now() - interval 120 day " +
                "where id = :id")
                .setParameter("id", oldestFork.getId())
                .executeUpdate();
        // set created timestamp for the third duplicate record two months back
        em.createNativeQuery("update repository_forks set created = now() - interval 60 day " +
                "where id = :id")
                .setParameter("id", newestFork.getId())
                .executeUpdate();

        em.getTransaction().commit();


        // prepare sample git repositories

        // user's repository with some branches
        File userRepoDir = tempDir.newFolder("userRepo");
        try (Git userRepository = GitRepositoryUtils.initializeRepository(userRepoDir, true)) {
            userRepository.branchCreate().setName("branch1").call();
            userRepository.branchList().call();
        }

        // archive repository
        File archiveRepoDir = tempDir.newFolder("upstream/repo1");
        try (Git archiveRepository = GitRepositoryUtils.initializeRepository(archiveRepoDir, false)) {
            GitRepositoryUtils.addRemoteAndFetch(archiveRepository, "user1", userRepoDir);
            GitRepositoryUtils.addRemoteAndFetch(archiveRepository, "user2", userRepoDir);

            // verify the branches are as intended
            List<Ref> refs = archiveRepository.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call();
            assertThat(refs).extracting("name")
                    .containsOnly(
                            "refs/remotes/user1/master",
                            "refs/remotes/user1/branch1",
                            "refs/remotes/user2/master",
                            "refs/remotes/user2/branch1"
                    );
        }
    }

    /**
     * Checks that loadRepositoryMethod loads correct records.
     */
    @Test
    public void testLoadRepositoryForks() {
        Map<String, List<RepositoryFork>> forks = batchlet.loadRepositoryForks();

        assertThat(forks.keySet()).containsOnly(
                "user1/repo1",
                "user2/repo1"
        );
        assertThat(forks.get("user1/repo1"))
                .extracting("repositoryName", "created.toLocalDateTime.toLocalDate")
                .containsOnly(Tuple.tuple("user1/repo1", THREE_MONTHS_AGO));
        assertThat(forks.get("user2/repo1"))
                .extracting("repositoryName", "created.toLocalDateTime.toLocalDate")
                .containsOnly(
                        Tuple.tuple("user2/repo1", FOUR_MONTHS_AGO),
                        Tuple.tuple("user2/repo1", THREE_MONTHS_AGO),
                        Tuple.tuple("user2/repo1", TWO_MONTHS_AGO)
                );
    }

    /**
     * Checks that the updateRecordStatus() method correctly sets deleted timestamp.
     */
    @Test
    public void testUpdateRecordStatus() {
        RepositoryFork fork = em.createQuery("SELECT f FROM RepositoryFork f WHERE repositoryName = 'user1/repo1'", RepositoryFork.class)
                .getSingleResult();
        assertThat(fork.getDeleted()).isNull();

        em.getTransaction().begin();
        batchlet.updateRecordStatus(fork, RepositoryForkStatus.SUPERSEDED);
        em.getTransaction().commit();

        em.refresh(fork);
        assertThat(fork.getDeleted().toLocalDateTime().toLocalDate()).isEqualTo(LocalDate.now());
        assertThat(fork.getStatus()).isEqualTo(RepositoryForkStatus.SUPERSEDED);
    }

    /**
     * Tests the complete batchlet workflow.
     */
    @Test
    public void testBatchlet_removalEnabled() throws IOException, GitAPIException {
        batchlet.configuration = new Configuration.ConfigurationBuilder()
                .setRepositoryArchiveRoot(tempDir.getRoot().getAbsolutePath())
                .setRemoveArchives(true)
                .build();

        String result = batchlet.process();

        assertThat(result).isEqualTo(Constants.DONE);

        // check db records
        em.clear();
        List<RepositoryFork> forks = em.createQuery("SELECT f FROM RepositoryFork f ORDER BY f.id", RepositoryFork.class)
                .getResultList();
        assertThat(forks)
                .extracting("repositoryName",
                        "created.toLocalDateTime.toLocalDate",
                        "deleted.toLocalDateTime.toLocalDate",
                        "status")
                .containsOnly(
                        Tuple.tuple("user1/repo1", THREE_MONTHS_AGO, TODAY, RepositoryForkStatus.DELETED),
                        Tuple.tuple("user2/repo1", FOUR_MONTHS_AGO, TODAY, RepositoryForkStatus.SUPERSEDED),
                        Tuple.tuple("user2/repo1", THREE_MONTHS_AGO, TODAY, RepositoryForkStatus.SUPERSEDED),
                        Tuple.tuple("user2/repo1", TWO_MONTHS_AGO, null, RepositoryForkStatus.ARCHIVED), // too young for deletion
                        Tuple.tuple("user2/repo2", THREE_MONTHS_AGO, YESTERDAY, RepositoryForkStatus.DELETION_FAILED)
                );

        // check that user1's branches were deleted in the git repo
        try (Git git = Git.open(new File(tempDir.getRoot(), "upstream/repo1"))) {
            List<Ref> refs = git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call();
            assertThat(refs).extracting("name")
                    .containsOnly(
                            "refs/remotes/user2/master",
                            "refs/remotes/user2/branch1"
                    );
        }
    }

    @Test
    public void testBatchlet_removalDisabled() throws IOException, GitAPIException {
        batchlet.configuration = new Configuration.ConfigurationBuilder()
                .setRepositoryArchiveRoot(tempDir.getRoot().getAbsolutePath())
                .setRemoveArchives(false)
                .build();

        String result = batchlet.process();

        assertThat(result).isEqualTo(Constants.ARCHIVES_PRUNING_DISABLED);

        // basically everything should remain in the original state

        // check db records
        em.clear();
        List<RepositoryFork> forks = em.createQuery("SELECT f FROM RepositoryFork f ORDER BY f.id", RepositoryFork.class)
                .getResultList();
        assertThat(forks)
                .extracting("repositoryName",
                        "created.toLocalDateTime.toLocalDate",
                        "deleted.toLocalDateTime.toLocalDate",
                        "status")
                .containsOnly(
                        Tuple.tuple("user1/repo1", THREE_MONTHS_AGO, null, RepositoryForkStatus.ARCHIVED),
                        Tuple.tuple("user2/repo1", FOUR_MONTHS_AGO, null, RepositoryForkStatus.ARCHIVED),
                        Tuple.tuple("user2/repo1", THREE_MONTHS_AGO, null, RepositoryForkStatus.ARCHIVED),
                        Tuple.tuple("user2/repo1", TWO_MONTHS_AGO, null, RepositoryForkStatus.ARCHIVED),
                        Tuple.tuple("user2/repo2", THREE_MONTHS_AGO, YESTERDAY, RepositoryForkStatus.DELETION_FAILED)
                );

        // check git repo, should still contain all branches
        try (Git git = Git.open(new File(tempDir.getRoot(), "upstream/repo1"))) {
            List<Ref> refs = git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call();
            assertThat(refs).extracting("name")
                    .containsOnly(
                            "refs/remotes/user1/master",
                            "refs/remotes/user1/branch1",
                            "refs/remotes/user2/master",
                            "refs/remotes/user2/branch1"
                    );
        }
    }
}
