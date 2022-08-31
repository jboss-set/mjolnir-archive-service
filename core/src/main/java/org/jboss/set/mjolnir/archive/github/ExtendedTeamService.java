package org.jboss.set.mjolnir.archive.github;

import com.google.gson.reflect.TypeToken;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.client.PagedRequest;
import org.eclipse.egit.github.core.service.TeamService;

import java.io.IOException;
import java.util.List;

/**
 * TeamService extension that implements the new list-team-members API method.
 */
public class ExtendedTeamService extends TeamService {

    public ExtendedTeamService(GitHubClient client) {
        super(client);
    }

    /**
     * Retrieve team members.
     *
     * EGit implementation uses deprecated endpoint, this method uses new endpoint.
     *
     * See https://developer.github.com/v3/teams/members/#list-team-members
     * vs https://developer.github.com/v3/teams/members/#list-team-members-legacy
     */
    public List<User> getMembers(String organization, int teamId) throws IOException {
        StringBuilder uri = new StringBuilder("/orgs");
        uri.append("/").append(organization);
        uri.append("/team/").append(teamId);
        uri.append("/members");
        PagedRequest<User> request = this.createPagedRequest();
        request.setUri(uri);
        request.setType((new TypeToken<List<User>>() {
        }).getType());
        return this.getAll(request);
    }

}
