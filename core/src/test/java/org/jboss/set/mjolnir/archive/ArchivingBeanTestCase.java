package org.jboss.set.mjolnir.archive;

import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.User;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.jboss.set.mjolnir.archive.configuration.Configuration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class ArchivingBeanTestCase {

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private Repository userRepository;
    @SuppressWarnings("FieldCanBeLocal")
    private Repository archiveRepo;
    private File sourceRepositoryDir;
    private File forkedRepositoryDir;
    private File archiveDir;
    private Configuration configuration;

    @Before
    public void setup() throws Exception {
        sourceRepositoryDir = tempDir.newFolder("sourceRepository");
        forkedRepositoryDir = tempDir.newFolder("forkedRepository");
        archiveDir = tempDir.newFolder("archive");

        Assert.assertTrue(new File(sourceRepositoryDir, "readme.txt").createNewFile());

        // create source repo with "master" branch
        try (Git sourceRepo = Git.init().setDirectory(sourceRepositoryDir).call()) {
            sourceRepo.add().addFilepattern(".").call();
            sourceRepo.commit().setMessage("Initial commit").call();
        }

        // create fork repo with "master" and "feature" branches
        try (Git forkedRepo = Git.init().setDirectory(forkedRepositoryDir).call()) {
            forkedRepo.remoteAdd().setName("origin").setUri(new URIish(sourceRepositoryDir.getAbsolutePath())).call();
            forkedRepo.pull().setRemote("origin").setRemoteBranchName("master").call();
            forkedRepo.checkout().setName("feature").setCreateBranch(true).call();
        }

        // prepare Repository object with source repo and owner set

        User githubOrg = new User();
        githubOrg.setLogin("testorg");

        Repository sourceRepository = new Repository();
        sourceRepository.setName("testrepo");
        sourceRepository.setCloneUrl(sourceRepositoryDir.getAbsolutePath());
        sourceRepository.setOwner(githubOrg);

        User githubUser = new User();
        githubUser.setLogin("TomasHofman");

        userRepository = new Repository();
        userRepository.setCloneUrl(forkedRepositoryDir.getAbsolutePath());
        userRepository.setName("testrepo");
        userRepository.setOwner(githubUser);
        userRepository.setSource(sourceRepository);

        archiveRepo = new Repository();
        archiveRepo.setCloneUrl(archiveDir.getAbsolutePath());
        archiveRepo.setName("testarchiverepo");
        archiveRepo.setOwner(githubUser);
        archiveRepo.setSource(sourceRepository);

        configuration = new Configuration.ConfigurationBuilder()
                .setGitHubToken("token")
                .setRepositoryArchiveRoot(archiveDir.getAbsolutePath())
                .build();

    }

    @Test
    public void testCreateRepositoryMirror() throws Exception {
        ArchivingBean bean = new ArchivingBean(configuration);
        bean.createRepositoryMirror(userRepository);

        // verify archived repository
        URIish sourceRepoUri = new URIish(sourceRepositoryDir.getAbsolutePath());
        URIish forkedRepoUri = new URIish(forkedRepositoryDir.getAbsolutePath());
        try (Git archivedRepository = Git.open(new File(archiveDir, "testorg/testrepo"))) {

            // verify origins

            assertThat(archivedRepository.remoteList().call()).satisfies(remotes -> {
                assertThat(remotes)
                        .extracting("name")
                        .containsOnly("origin", "TomasHofman");
                assertThat(findRemoteConfigByName(remotes, "origin")).satisfies(remote ->
                        assertThat(remote.getURIs()).contains(sourceRepoUri));
                assertThat(findRemoteConfigByName(remotes, "TomasHofman")).satisfies(remote ->
                        assertThat(remote.getURIs()).contains(forkedRepoUri));
            });

            // verify that all branches are present

            assertThat(archivedRepository.branchList().setListMode(ListBranchCommand.ListMode.ALL).call())
                    .extracting("name")
                    .contains("refs/heads/master",
                            "refs/remotes/TomasHofman/master",
                            "refs/remotes/TomasHofman/feature");

            // verify that all branches are deleted

            List<Ref> refs = archivedRepository.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
            Git git = Git.open(new File(archiveDir, "testorg/testrepo"));
            refs.forEach(ref -> {
                try {
                    if (ref.getName().contains("TomasHofman/")) {
                        assertThat(git.branchDelete().setBranchNames(ref.getName()).setForce(true).call());
                    }
                } catch (GitAPIException e) {
                    e.printStackTrace();
                }
            });

        }
    }

    @Test
    public void testCreateRepositoryMirrorInExistingArchive() throws Exception {
        // create a preexisting archived repository
        try (Git archiveRepo = Git.init().setDirectory(new File(archiveDir, "testorg/testrepo")).call()) {
            archiveRepo.remoteAdd().setName("origin").setUri(new URIish(sourceRepositoryDir.getAbsolutePath())).call();
            archiveRepo.pull().setRemote("origin").setRemoteBranchName("master").call();
        }

        testCreateRepositoryMirror();
    }

    private RemoteConfig findRemoteConfigByName(List<? extends RemoteConfig> remotes, String name) {
        Optional<? extends RemoteConfig> first = remotes.stream().filter(r -> name.equals(r.getName())).findFirst();
        Assert.assertTrue(first.isPresent());
        return first.get();
    }


}