package org.jboss.set.mjolnir.archive.mail.report;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.jboss.set.mjolnir.archive.domain.RegisteredUser;
import org.jboss.set.mjolnir.archive.ldap.LdapDiscoveryBean;
import org.jboss.set.mjolnir.archive.ldap.LdapScanningBean;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@RunWith(CdiTestRunner.class)
public class WhitelistedUsersReportTableTestCase {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8089);

    @Inject
    private EntityManager em;

    @Inject
    private LdapScanningBean ldapScanningBean;

    @Inject
    private WhitelistedUsersReportTable whitelistedUsersReportTable;

    @Before
    public void setup() {
        em.getTransaction().begin();

        RegisteredUser registeredUser = new RegisteredUser();
        registeredUser.setGithubName("bob");
        registeredUser.setKerberosName("bobNonExisting");
        registeredUser.setWhitelisted(true);
        em.persist(registeredUser);

        registeredUser = new RegisteredUser();
        registeredUser.setGithubName("jim");
        registeredUser.setKerberosName("jimExisting");
        registeredUser.setResponsiblePerson("Responsible guy");
        registeredUser.setWhitelisted(true);
        em.persist(registeredUser);

        registeredUser = new RegisteredUser();
        registeredUser.setGithubName("carl");
        registeredUser.setKerberosName("carlExisting");
        registeredUser.setResponsiblePerson("Responsible guy");
        registeredUser.setWhitelisted(true);
        em.persist(registeredUser);

        registeredUser = new RegisteredUser();
        registeredUser.setGithubName("sam");
        registeredUser.setKerberosName("samExisting");
        registeredUser.setResponsiblePerson("Responsible guy");
        registeredUser.setWhitelisted(true);
        em.persist(registeredUser);

        registeredUser = new RegisteredUser();
        registeredUser.setGithubName("bruno");
        registeredUser.setWhitelisted(true);
        em.persist(registeredUser);

        registeredUser = new RegisteredUser();
        registeredUser.setGithubName("joe");
        registeredUser.setWhitelisted(false);
        em.persist(registeredUser);

        em.getTransaction().commit();
    }

    @Test
    public void testComposeTableBody() throws NamingException, NoSuchFieldException, IllegalAccessException {
        LdapDiscoveryBean ldapDiscoveryBean = mock(LdapDiscoveryBean.class);
        doReturn(false).when(ldapDiscoveryBean).checkUserExists("bobNonExisting");
        doReturn(true).when(ldapDiscoveryBean).checkUserExists("jimExisting");
        doReturn(true).when(ldapDiscoveryBean).checkUserExists("carlExisting");
        doReturn(true).when(ldapDiscoveryBean).checkUserExists("samExisting");

        Field ldapDiscoveryBeanField = LdapScanningBean.class.getDeclaredField("ldapDiscoveryBean");
        ldapDiscoveryBeanField.setAccessible(true);
        ldapDiscoveryBeanField.set(ldapScanningBean, ldapDiscoveryBean);

        whitelistedUsersReportTable.ldapScanningBean = ldapScanningBean;

        List<RegisteredUser> users = ldapScanningBean.getWhitelistedUsers();
        List<RegisteredUser> usersList = new ArrayList<>(users);
        usersList.sort(new WhitelistedUsersReportTable.GitHubNameComparator());

        String messageBody = whitelistedUsersReportTable.composeTable();
        Document doc = Jsoup.parse(messageBody);

        assertThat(doc.select("tr").size()).isEqualTo(users.size() + 1);

        Elements elements = doc.select("td");
        assertThat(elements.size()).isEqualTo(users.size() * 2);

        int i = 0;
        for (RegisteredUser user : usersList) {
            assertThat(elements.get(i).childNode(0).toString()).isEqualTo(user.getGithubName());
            if (elements.get(i + 1).childNodeSize() == 0) {
                assertThat(user.getResponsiblePerson()).isNullOrEmpty();
            } else {
                assertThat(elements.get(i + 1).childNode(0).toString()).isEqualTo(user.getResponsiblePerson());
            }
            i += 2;
        }
    }
}
