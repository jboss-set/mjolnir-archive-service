package org.jboss.set.mjolnir.archive.github;

import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.OrganizationService;
import org.jboss.logging.Logger;
import org.jboss.set.mjolnir.archive.domain.GitHubOrganization;
import org.jboss.set.mjolnir.archive.domain.GitHubTeam;

import javax.inject.Inject;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
     * @param organization github organization
     * @param gitHubUsername   github username
     */
    public void removeUserFromTeams(GitHubOrganization organization, String gitHubUsername) throws IOException {
        logger.infof("Removing user %s from organization %s teams", gitHubUsername, organization.getName());

        List<GitHubTeam> teams = organization.getTeams();

        for (GitHubTeam team : teams) {
            if (isMember(gitHubUsername, team)) {
                logger.infof("Removing membership of user %s in team %s", gitHubUsername, team.getName());
                teamService.removeMember(team.getGithubId(), gitHubUsername);
            } else {
                logger.infof("User %s is not a member of team %s", gitHubUsername, team.getName());
            }
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
    public Set<User> getAllTeamsMembers(GitHubOrganization organization) throws IOException {
        Set<User> members = new HashSet<>();
        for (GitHubTeam team : organization.getTeams()) {
            members.addAll(teamService.getMembers(organization.getName(), team.getGithubId()));
        }
        return members;
    }

    public boolean isMember(String githubUser, GitHubTeam team) throws IOException {
        return teamService.isMember(team.getGithubId(), githubUser);
    }

}
