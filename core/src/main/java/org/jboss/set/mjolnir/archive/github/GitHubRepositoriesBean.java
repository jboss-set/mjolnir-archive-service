package org.jboss.set.mjolnir.archive.github;

import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.client.IGitHubConstants;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides discovery of user repositories.
 */
public class GitHubRepositoriesBean {

    private static final Logger LOG = Logger.getLogger(GitHubRepositoriesBean.class);

    private final RepositoryService repositoryService;

    @Inject
    public GitHubRepositoriesBean(GitHubClient client) {
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
        SocketTimeoutException timeoutException = null;

        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                List<Repository> orgRepositories = repositoryService.getOrgRepositories(organisation);

                List<Repository> privateRepositories = orgRepositories.stream()
                        .filter(Repository::isPrivate)
                        .collect(Collectors.toList());

                Set<Repository> userRepositories = new HashSet<>();
                for (Repository sourceRepository : privateRepositories) {
                    List<Repository> forks = repositoryService.getForks(sourceRepository);
                    forks.stream()
                            .filter(fork -> githubUser.toLowerCase().equals(fork.getOwner().getLogin().toLowerCase()))
                            .peek(repository -> repository.setSource(sourceRepository)) // set organization's repository as the source repository
                            .forEach(userRepositories::add);
                }

                return userRepositories;
            } catch (SocketTimeoutException e) {
                timeoutException = e;
                // try to report GitHub API IP address, to make it possible to rule out egress firewall issue
                try {
                    InetAddress addr = InetAddress.getByName(IGitHubConstants.HOST_API);
                    LOG.warnf(e, "GitHub API connection timeout. %s resolves to %s",
                            IGitHubConstants.HOST_API, addr.getHostAddress());
                } catch (UnknownHostException e2) {
                    LOG.warnf(e, "Failed to resolve GitHub API host");
                }
            }
        }
        throw timeoutException;
    }

}
