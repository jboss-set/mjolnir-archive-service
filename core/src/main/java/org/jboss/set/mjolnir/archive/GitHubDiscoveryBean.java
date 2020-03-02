package org.jboss.set.mjolnir.archive;

import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.RepositoryService;

import javax.inject.Inject;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides discovery of user repositories.
 */
public class GitHubDiscoveryBean {

    private RepositoryService repositoryService;

    @Inject
    public GitHubDiscoveryBean(GitHubClient client) {
        repositoryService = new RepositoryService(client);
    }

    /**
     * Find forks of private repositories in given organization that belongs to given user.
     *
     * @param organisation github organization
     * @param githubUser   github username
     * @return list of private repositories
     */
    public Set<Repository> getRepositoriesToArchive(String organisation, String githubUser) throws IOException {
        List<Repository> orgRepositories = repositoryService.getOrgRepositories(organisation);

        List<Repository> privateRepositories = orgRepositories.stream()
                .filter(Repository::isPrivate)
                .collect(Collectors.toList());

        Set<Repository> userRepositories = new HashSet<>();
        for (Repository sourceRepository : privateRepositories) {
            List<Repository> forks = repositoryService.getForks(sourceRepository);
            forks.stream()
                    .filter(fork -> githubUser.equals(fork.getOwner().getLogin()))
                    .peek(repository -> repository.setSource(sourceRepository)) // set organization's repository as the source repository
                    .forEach(userRepositories::add);
        }

        return userRepositories;
    }

}
