package org.jboss.set.mjolnir.archive.github;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.jboss.set.mjolnir.archive.domain.GitHubOrganization;
import org.jboss.set.mjolnir.archive.domain.GitHubTeam;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.set.mjolnir.archive.util.TestUtils.readSampleResponse;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GitHubMembershipBeanTestCase {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8089);

    private final GitHubClient client = new GitHubClient("localhost", 8089, "http");

    @Before
    public void setup() throws IOException, URISyntaxException {
        stubFor(get(urlPathEqualTo("/api/v3/teams/1/members/lvydra"))
                .willReturn(aResponse()
                        .withStatus(204)));

        stubFor(get(urlPathEqualTo("/api/v3/teams/2/members/lvydra"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody(readSampleResponse("responses/gh-orgs-members-not-found-response.json"))));

        stubFor(get(urlPathEqualTo("/api/v3/teams/3/members/lvydra"))
                .willReturn(aResponse()
                        .withStatus(204)));

        stubFor(delete(urlPathEqualTo("/api/v3/teams/1/members/lvydra"))
                .willReturn(aResponse()
                        .withStatus(204)));

        stubFor(delete(urlPathEqualTo("/api/v3/teams/3/members/lvydra"))
                .willReturn(aResponse()
                        .withStatus(204)));

        stubFor(get(urlPathEqualTo("/api/v3/orgs/testorg/team/1/members"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(readSampleResponse("responses/gh-orgs-team-1-members-response.json"))));

        stubFor(get(urlPathEqualTo("/api/v3/orgs/testorg/team/2/members"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(readSampleResponse("responses/gh-orgs-team-2-members-response.json"))));

        stubFor(get(urlPathEqualTo("/api/v3/orgs/testorg/team/3/members"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(readSampleResponse("responses/empty-list-response.json"))));

        stubFor(get(urlPathEqualTo("/api/v3/orgs/testorg/members/thofman"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(readSampleResponse("responses/empty-list-response.json"))));

        stubFor(delete(urlPathEqualTo("/api/v3/orgs/testorg/members/thofman"))
                .willReturn(aResponse()
                        .withStatus(204)));

    }

    @Test
    public void testWiremockResponses() throws Exception {
        CustomizedTeamService teamService = new CustomizedTeamService(client);

        assertTrue(teamService.isMember(1, "lvydra"));
        assertFalse(teamService.isMember(2, "lvydra"));
        assertTrue(teamService.isMember(3, "lvydra"));

        assertThat(teamService.getMembers("testorg", 1))
                .extracting("login")
                .containsOnly("bob");
        assertThat(teamService.getMembers("testorg", 2))
                .extracting("login")
                .containsOnly("ben");
        assertThat(teamService.getMembers("testorg", 3))
                .isEmpty();
    }

    @Test
    public void testRemoveUsersFromTeam() throws Exception {
        GitHubMembershipBean bean = new GitHubMembershipBean(client);

        bean.removeUserFromTeams(createOrganizationEntity(), "lvydra");

        verify(deleteRequestedFor(urlEqualTo("/api/v3/teams/1/members/lvydra")));
        verify(exactly(0), deleteRequestedFor(urlEqualTo("/api/v3/teams/2/members/lvydra")));
        verify(deleteRequestedFor(urlEqualTo("/api/v3/teams/3/members/lvydra")));
    }

    @Test
    public void testRemoveUsersFromOrganization() throws Exception {
        GitHubMembershipBean bean = new GitHubMembershipBean(client);

        bean.removeUserFromOrganization(createOrganizationEntity(), "thofman");

        verify(deleteRequestedFor(urlEqualTo("/api/v3/orgs/testorg/members/thofman")));
    }

    @Test
    public void testGetAllTeamsMembers() throws Exception {
        GitHubMembershipBean bean = new GitHubMembershipBean(client);

        Set<User> members = bean.getAllTeamsMembers(createOrganizationEntity());
        assertThat(members).extracting("login")
                .containsOnly("bob", "ben");
    }

    private GitHubOrganization createOrganizationEntity() {
        GitHubTeam team1 = new GitHubTeam();
        team1.setGithubId(1);
        team1.setName("Team 1");

        GitHubTeam team2 = new GitHubTeam();
        team2.setGithubId(2);
        team2.setName("Team 2");

        GitHubTeam team3 = new GitHubTeam();
        team3.setGithubId(3);
        team3.setName("Team 3");

        GitHubOrganization org = new GitHubOrganization();
        org.setName("testorg");
        org.setTeams(Arrays.asList(team1, team2, team3));

        return org;
    }
}
