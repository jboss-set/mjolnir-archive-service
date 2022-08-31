package org.jboss.set.mjolnir.archive.github;

import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.OrganizationService;
import org.jboss.logging.Logger;
import org.jboss.set.mjolnir.archive.domain.GitHubOrganization;
import org.jboss.set.mjolnir.archive.domain.GitHubTeam;
import org.jboss.set.mjolnir.archive.domain.UnsubscribeStatus;
import org.jboss.set.mjolnir.archive.domain.UnsubscribedUserFromOrg;
import org.jboss.set.mjolnir.archive.domain.UnsubscribedUserFromTeam;
import org.jboss.set.mjolnir.archive.domain.UserRemoval;

import javax.inject.Inject;
import javax.persistence.EntityManager;
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

    private final ExtendedTeamService teamService;
    private final OrganizationService organizationService;
    private final EntityManager em;

    @Inject
    public GitHubMembershipBean(GitHubClient client, EntityManager em) {
        teamService = new ExtendedTeamService(client);
        organizationService = new OrganizationService(client);
        this.em = em;
    }

    /**
     * Find teams for given organization filter ones in which user got membership and then removes users membership.
     *
     * @param gitHubTeam github team
     * @param gitHubUsername   github username
     */
    public void removeUserFromTeam(UserRemoval removal, GitHubTeam gitHubTeam, String gitHubUsername)
            throws IOException {
        if (isMember(gitHubUsername, gitHubTeam)) {
            logger.infof("Removing membership of user %s in team %s", gitHubUsername, gitHubTeam.getName());
            try {
                teamService.removeMember(gitHubTeam.getGithubId(), gitHubUsername);
                logUnsubscribedTeam(removal, gitHubUsername, gitHubTeam, UnsubscribeStatus.COMPLETED);
            } catch (IOException e) {
                logUnsubscribedTeam(removal, gitHubUsername, gitHubTeam, UnsubscribeStatus.FAILED);
                throw e;
            }
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
    public void removeUserFromOrganization(UserRemoval removal, GitHubOrganization organization, String gitHubUsername)
            throws IOException {
        logger.infof("Removing user %s from organization %s", gitHubUsername, organization.getName());
        if (organizationService.isMember(organization.getName(), gitHubUsername)) {
            try {
                organizationService.removeMember(organization.getName(), gitHubUsername);
                logUnsubscribedOrg(removal, gitHubUsername, organization, UnsubscribeStatus.COMPLETED);
            } catch (IOException e) {
                logUnsubscribedOrg(removal, gitHubUsername, organization, UnsubscribeStatus.FAILED);
                throw e;
            }
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

    private void logUnsubscribedTeam(UserRemoval removal, String gitHubUsername, GitHubTeam team, UnsubscribeStatus status) {
        UnsubscribedUserFromTeam unsubscribedUserFromTeam = new UnsubscribedUserFromTeam();
        unsubscribedUserFromTeam.setUserRemoval(removal);
        unsubscribedUserFromTeam.setGithubUsername(gitHubUsername);
        unsubscribedUserFromTeam.setGithubTeamName(team.getName());
        unsubscribedUserFromTeam.setGithubOrgName(team.getOrganization().getName());
        unsubscribedUserFromTeam.setStatus(status);
        em.persist(unsubscribedUserFromTeam);
    }

    private void logUnsubscribedOrg(UserRemoval removal, String gitHubUsername, GitHubOrganization org, UnsubscribeStatus status) {
        UnsubscribedUserFromOrg unsubscribedUserFromOrg = new UnsubscribedUserFromOrg();
        unsubscribedUserFromOrg.setUserRemoval(removal);
        unsubscribedUserFromOrg.setGithubUsername(gitHubUsername);
        unsubscribedUserFromOrg.setGithubOrgName(org.getName());
        unsubscribedUserFromOrg.setStatus(status);
        em.persist(unsubscribedUserFromOrg);
    }
}
