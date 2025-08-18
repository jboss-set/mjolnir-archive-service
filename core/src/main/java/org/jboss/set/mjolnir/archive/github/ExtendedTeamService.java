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
     * <p>
     * EGit implementation uses deprecated endpoint, this method uses new endpoint.
     * <p>
     * See <a href="https://developer.github.com/v3/teams/members/#list-team-members">#list-team-members</a>
     * vs <a href="https://developer.github.com/v3/teams/members/#list-team-members-legacy">#list-team-members-legacy</a>
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

    /**
     * @deprecated see <a href="https://docs.github.com/en/rest/teams/members?apiVersion=2022-11-28#remove-team-member-legacy">#remove-team-member-legacy</a>, use {@link #removeMembership(String, String, String)} instead.
     */
    @Override
    public void removeMember(int id, String user) throws IOException {
        throw new RuntimeException("Deprecated method");
    }

    /**
     * @deprecated see <a href="https://docs.github.com/rest/teams/members#remove-team-membership-for-a-user-legacy">#remove-team-membership-for-a-user-legacy</a>, use {@link #removeMembership(String, String, String)} instead.
     */
    @Override
    public void removeMembership(int id, String user) throws IOException {
        throw new RuntimeException("Deprecated method");
    }

    /**
     * Remove team member.
     * <p>
     * See <a href="https://docs.github.com/en/rest/teams/members?apiVersion=2022-11-28#remove-team-membership-for-a-user">#remove-team-membership-for-a-user</a>.
     */
    public void removeMembership(String organization, String teamSlug, String user) throws IOException {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        } else if (user.isEmpty()) {
            throw new IllegalArgumentException("User cannot be empty");
        } else if (organization == null) {
            throw new IllegalArgumentException("Organization cannot be null");
        } else if (organization.isEmpty()) {
            throw new IllegalArgumentException("Organization cannot be null");
        }

        StringBuilder uri = new StringBuilder("/orgs");
        uri.append("/").append(organization);
        uri.append("/teams/").append(teamSlug);
        uri.append("/memberships/").append(user);

        this.client.delete(uri.toString());
    }

}
