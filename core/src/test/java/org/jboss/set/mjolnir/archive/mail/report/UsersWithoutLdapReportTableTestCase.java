package org.jboss.set.mjolnir.archive.mail.report;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.jboss.set.mjolnir.archive.domain.RegisteredUser;
import org.jboss.set.mjolnir.archive.ldap.LdapClientBean;
import org.jboss.set.mjolnir.archive.UserDiscoveryBean;
import org.jboss.set.mjolnir.archive.util.MockitoAnswers;
import org.jboss.set.mjolnir.archive.util.TestUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import javax.inject.Inject;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;

@RunWith(CdiTestRunner.class)
public class UsersWithoutLdapReportTableTestCase {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8089);

    @Inject
    private EntityManager em;

    @Inject
    private UserDiscoveryBean userDiscoveryBean;

    @Inject
    private LdapClientBean ldapClientBeanMock;

    @Inject
    private UsersWithoutLdapReportTable usersWithoutLdapReportTable;

    @Before
    public void setup() throws IOException, URISyntaxException, NamingException {
        TestUtils.setupGitHubApiStubs();

        Mockito.reset(ldapClientBeanMock);
        Mockito.when(ldapClientBeanMock.checkUsersExists(anyCollection()))
                .thenAnswer(new MockitoAnswers.UsersNotInLdapAnswer());

        em.getTransaction().begin();

        RegisteredUser registeredUser = new RegisteredUser();
        registeredUser.setGithubName("bob");
        registeredUser.setKerberosName("bob");
        registeredUser.setWhitelisted(false);
        em.persist(registeredUser);

        registeredUser = new RegisteredUser();
        registeredUser.setGithubName("ben");
        registeredUser.setKerberosName("ben");
        registeredUser.setWhitelisted(false);
        em.persist(registeredUser);

        em.getTransaction().commit();
    }

    @Test
    public void testComposeTableBody() throws IOException, NamingException {
        Collection<String> users = userDiscoveryBean.findAllUsersWithoutLdapAccount();
        assertThat(users).containsOnly("ben", "bob");

        List<String> usersList = new ArrayList<>(users);
        usersList.sort(String::compareToIgnoreCase);

        String messageBody = usersWithoutLdapReportTable.composeTable();
        Document doc = Jsoup.parse(messageBody);

        assertThat(doc.select("tr")).satisfies(elements -> {
            assertThat(elements.size()).isEqualTo(5);
            assertThat(elements.get(1).html()).satisfies(text -> {
                assertThat(text.contains("ben")).isTrue();
                assertThat(text.contains("testorg")).isTrue();
            });
            assertThat(elements.get(2).html()).satisfies(text -> {
                assertThat(text.contains("bob")).isTrue();
                assertThat(text.contains("testorg")).isTrue();
            });
            assertThat(elements.get(3).html()).satisfies(text -> {
                assertThat(text.contains("ben")).isTrue();
                assertThat(text.contains("testorg/Team 2")).isTrue();
            });
            assertThat(elements.get(4).html()).satisfies(text -> {
                assertThat(text.contains("bob")).isTrue();
                assertThat(text.contains("testorg/Team 1")).isTrue();
            });
        });
    }

}
