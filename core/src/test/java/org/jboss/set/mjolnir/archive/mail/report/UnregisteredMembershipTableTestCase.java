package org.jboss.set.mjolnir.archive.mail.report;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.jboss.set.mjolnir.archive.domain.RegisteredUser;
import org.jboss.set.mjolnir.archive.util.TestUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.io.IOException;
import java.net.URISyntaxException;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(CdiTestRunner.class)
public class UnregisteredMembershipTableTestCase {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8089);

    @Inject
    private EntityManager em;

    @Inject
    private UnregisteredMembersReportTable unregisteredMembersReportTable;

    @Before
    public void setup() throws IOException, URISyntaxException {
        TestUtils.setupGitHubApiStubs();

        em.getTransaction().begin();

        RegisteredUser registeredUser = new RegisteredUser();
        registeredUser.setGithubName("ben");
        em.persist(registeredUser);

        em.getTransaction().commit();
    }

    @Test
    public void testComposeTableBody() throws IOException {
        String messageBody = unregisteredMembersReportTable.composeTable();

        assertThat(messageBody.contains("bob")).isTrue();
        assertThat(messageBody.contains("testorg")).isTrue();
        assertThat(messageBody.contains("testorg/Team 1")).isTrue();

        assertThat(messageBody.contains("ben")).isFalse();
    }
}
