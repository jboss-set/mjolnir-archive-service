package org.jboss.set.mjolnir.archive.github;

import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.client.GitHubRequest;
import org.eclipse.egit.github.core.service.UserService;

import java.io.IOException;

import static org.eclipse.egit.github.core.client.IGitHubConstants.SEGMENT_USER;

/**
 * UserService extension that implement the get-user-by-id API method (undocumented).
 */
public class ExtendedUserService extends UserService {

    public ExtendedUserService(GitHubClient client) {
        super(client);
    }

    public User getUserById(Integer id) throws IOException {
        if (id == null)
            throw new IllegalArgumentException("ID cannot be null"); //$NON-NLS-1$

        GitHubRequest request = createRequest();
        StringBuilder uri = new StringBuilder(SEGMENT_USER);
        uri.append('/').append(id);
        request.setUri(uri);
        request.setType(User.class);
        return (User) client.get(request).getBody();
    }
}
