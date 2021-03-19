package org.jboss.set.mjolnir.archive.github;

import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.OrganizationService;
import org.jboss.logging.Logger;
import org.jboss.set.mjolnir.archive.domain.GitHubOrganization;
import org.jboss.set.mjolnir.archive.domain.GitHubTeam;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides user membership management capabilities.
 */
public class GitHubMembershipBean {

    private final Logger logger = Logger.getLogger(getClass());

    private final CustomizedTeamService teamService;
    private final OrganizationService organizationService;

    @Inject
    public GitHubMembershipBean(GitHubClient client) {
        teamService = new CustomizedTeamService(client);
        organizationService = new OrganizationService(client);
    }

    /**
     * Find teams for given organization filter ones in which user got membership and then removes users membership.
     *
     * @param gitHubTeam github team
     * @param gitHubUsername   github username
     */
    public void removeUserFromTeam(GitHubTeam gitHubTeam, String gitHubUsername) throws IOException {
        if (isMember(gitHubUsername, gitHubTeam)) {
            logger.infof("Removing membership of user %s in team %s", gitHubUsername, gitHubTeam.getName());
            teamService.removeMember(gitHubTeam.getGithubId(), gitHubUsername);
        } else {
            logger.infof("User %s is not a member of team %s", gitHubUsername, gitHubTeam.getName());
        }
    }

    /**
     * Removes user's membership in given organization.
     *
     * @param organization organization to remove user from
     * @param gitHubUsername github username
     */
    public void removeUserFromOrganization(GitHubOrganization organization, String gitHubUsername) throws IOException {
        logger.infof("Removing user %s from organization %s", gitHubUsername, organization.getName());
        if (organizationService.isMember(organization.getName(), gitHubUsername)) {
            organizationService.removeMember(organization.getName(), gitHubUsername);
        }
    }

    /**
     * Retrieves members of all organization teams.
     */
    public Map<GitHubTeam, List<User>> getAllTeamsMembers(GitHubOrganization organization) throws IOException {
        HashMap<GitHubTeam, List<User>> map = new HashMap<>();
        for (GitHubTeam team : organization.getTeams()) {
            List<User> members = teamService.getMembers(organization.getName(), team.getGithubId());
            if (members.size() > 0) {
                map.put(team, members);
            }
        }
        return map;
    }

    /**
     * Retrieves members of a GH team.
     */
    public List<User> getTeamsMembers(GitHubTeam team) throws IOException {
        return teamService.getMembers(team.getOrganization().getName(), team.getGithubId());
    }

    /**
     * Retrieves members of an organization.
     */
    public Collection<User> getOrganizationMembers(GitHubOrganization organization) throws IOException {
        return organizationService.getMembers(organization.getName());
    }

    public boolean isMember(String githubUser, GitHubTeam team) throws IOException {
        return teamService.isMember(team.getGithubId(), githubUser);
    }

}
