package org.jboss.set.mjolnir.archive.mail.report;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.deltaspike.core.util.ArraysUtils;
import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.jboss.set.mjolnir.archive.domain.RegisteredUser;
import org.jboss.set.mjolnir.archive.ldap.LdapClientBean;
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

import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

@RunWith(CdiTestRunner.class)
public class InvalidResponsiblePersonTableTestCase {

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8089);

    @Inject
    private EntityManager em;

    @Inject
    private InvalidResponsiblePersonTable invalidResponsiblePersonTable;

    @Inject
    private LdapClientBean ldapClientBeanMock;

    @Before
    public void setup() throws NamingException {

        HashMap<Object, Object> ldapUsersMap = new HashMap<>();
        ldapUsersMap.put("Responsible guy", false);
        ldapUsersMap.put("Responsible guy1", false);
        ldapUsersMap.put("ben", true);
        doReturn(ldapUsersMap).when(ldapClientBeanMock).checkUsersExists(ArraysUtils.asSet("Responsible guy", "Responsible guy1", "ben"));
        em.getTransaction().begin();

        RegisteredUser registeredUser = new RegisteredUser();
        registeredUser.setGithubName("bruno");
        registeredUser.setKerberosName("bruno");
        registeredUser.setResponsiblePerson("Responsible guy");
        em.persist(registeredUser);

        registeredUser = new RegisteredUser();
        registeredUser.setGithubName("joe");
        registeredUser.setKerberosName("joe");
        registeredUser.setResponsiblePerson("Responsible guy1");
        em.persist(registeredUser);

        registeredUser = new RegisteredUser();
        registeredUser.setGithubName("sam");
        registeredUser.setKerberosName("samExisting");
        registeredUser.setResponsiblePerson("ben");
        registeredUser.setWhitelisted(true);
        em.persist(registeredUser);

        em.getTransaction().commit();

    }

    @Test
    public void testComposeTableBody() throws NamingException {

        List<RegisteredUser> listOfInvalidResponsiblePerson = invalidResponsiblePersonTable.getInvalidLdapUser();
        assertThat(listOfInvalidResponsiblePerson).extracting(RegisteredUser::getKerberosName).containsOnly("joe", "bruno");

        String messageBody = invalidResponsiblePersonTable.composeTable();
        Document doc = Jsoup.parse(messageBody);

        assertThat(doc.select("tr").size()).isEqualTo(listOfInvalidResponsiblePerson.size() + 1);

        Elements elements = doc.select("td");
        assertThat(elements.size()).isEqualTo(listOfInvalidResponsiblePerson.size() * 3);
        assertThat(elements.get(2).text()).contains("Responsible guy");
        assertThat(elements.get(5).text()).contains("Responsible guy1");

    }
}
