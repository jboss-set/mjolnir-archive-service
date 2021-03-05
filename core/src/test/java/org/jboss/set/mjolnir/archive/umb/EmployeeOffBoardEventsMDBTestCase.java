package org.jboss.set.mjolnir.archive.umb;

import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.jboss.set.mjolnir.archive.domain.RegisteredUser;
import org.jboss.set.mjolnir.archive.domain.UserRemoval;
import org.jboss.set.mjolnir.archive.ldap.LdapClientBean;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(CdiTestRunner.class)
public class EmployeeOffBoardEventsMDBTestCase {

    @Inject
    private EntityManager em;

    @Inject
    private EmployeeOffBoardEventsMDB employeeOffBoardEventsMDB;

    @Inject
    private LdapClientBean ldapClientBeanMock;

    @Before
    public void setup() throws Exception {
        // mock ldap bean to return two UIDs for each user (would be current and prior UID)
        Mockito.when(ldapClientBeanMock.findAllUserUids(Mockito.anyString()))
                .thenAnswer((Answer<List<String>>) invocation -> {
                    ArrayList<String> uids = new ArrayList<>();
                    uids.add(invocation.getArgument(0));
                    uids.add(invocation.getArgument(0) + "_prior");
                    return uids;
                });

        em.getTransaction().begin();

        em.createNativeQuery("delete from users").executeUpdate();
        em.createNativeQuery("delete from user_removals").executeUpdate();

        RegisteredUser registeredUser = new RegisteredUser();
        registeredUser.setGithubName("lvydra");
        registeredUser.setKerberosName("lvydra");
        registeredUser.setWhitelisted(false);
        em.persist(registeredUser);

        registeredUser = new RegisteredUser();
        registeredUser.setGithubName("joe");
        registeredUser.setKerberosName("joe");
        registeredUser.setWhitelisted(true);
        em.persist(registeredUser);

        registeredUser = new RegisteredUser();
        registeredUser.setGithubName("bruno");
        registeredUser.setKerberosName("bruno");
        registeredUser.setWhitelisted(true);
        em.persist(registeredUser);

        em.getTransaction().commit();
    }

    @Test
    public void testProcessMessage() {
        JSONObject eIAMMessage = new JSONObject();
        eIAMMessage.put("event", "offboard");
        eIAMMessage.put("key", "22");
        JSONObject person = new JSONObject();
        person.put("uid", "lvydra");
        person.put("dn", "uid=lvydra,dc=test");
        eIAMMessage.put("person", person);

        em.getTransaction().begin();
        employeeOffBoardEventsMDB.processMessage(eIAMMessage.toString());
        em.getTransaction().commit();

        eIAMMessage = new JSONObject();
        eIAMMessage.put("event", "offboard");
        eIAMMessage.put("key", "22");
        person = new JSONObject();
        person.put("uid", "joe");
        person.put("dn", "uid=joe,dc=test");
        eIAMMessage.put("person", person);

        em.getTransaction().begin();
        employeeOffBoardEventsMDB.processMessage(eIAMMessage.toString());
        em.getTransaction().commit();

        eIAMMessage = new JSONObject();
        eIAMMessage.put("event", "update");
        eIAMMessage.put("key", "22");
        person = new JSONObject();
        person.put("uid", "bruno");
        person.put("dn", "uid=bruno,dc=test");
        eIAMMessage.put("person", person);

        em.getTransaction().begin();
        employeeOffBoardEventsMDB.processMessage(eIAMMessage.toString());
        em.getTransaction().commit();

        TypedQuery<UserRemoval> findRemovalsQuery = em.createNamedQuery(UserRemoval.FIND_REMOVALS_TO_PROCESS, UserRemoval.class);
        List<UserRemoval> removals = findRemovalsQuery.getResultList();
        assertThat(removals.size()).isEqualTo(1);
        assertThat(removals)
                .extracting("ldapUsername")
                .containsOnly("lvydra");
    }

    /**
     * Tests the case when the UID given in the UMB event corresponds to two different registered users.
     *
     * (UMB event contains the current UID, LDAP query shows that the user has another prior UID, and there are
     * registered users for both the current and the prior UID. This is an edge case where we currently do not want
     * any removal to be generated,)
     */
    @Test
    public void testProcessMessageTwoRegisteredUsers() {
        em.getTransaction().begin();
        RegisteredUser registeredUser = new RegisteredUser();
        registeredUser.setGithubName("lvydra_prior");
        registeredUser.setKerberosName("lvydra_prior");
        registeredUser.setWhitelisted(false);
        em.persist(registeredUser);
        em.getTransaction().commit();

        JSONObject eIAMMessage = new JSONObject();
        eIAMMessage.put("event", "offboard");
        eIAMMessage.put("key", "22");
        JSONObject person = new JSONObject();
        person.put("uid", "lvydra");
        person.put("dn", "uid=lvydra,dc=test");
        eIAMMessage.put("person", person);

        em.getTransaction().begin();
        employeeOffBoardEventsMDB.processMessage(eIAMMessage.toString());
        em.getTransaction().commit();

        TypedQuery<UserRemoval> findRemovalsQuery = em.createNamedQuery(UserRemoval.FIND_REMOVALS_TO_PROCESS, UserRemoval.class);
        List<UserRemoval> removals = findRemovalsQuery.getResultList();
        assertThat(removals.size()).isEqualTo(0);
    }

    /**
     * Tests the case where a user has been registered with his prior UID, but UMB event contains his new UID.
     */
    @Test
    public void testProcessMessagePriorUid() {
        // remove user 'lvydra' and register 'lvydra_prior' instead
        em.getTransaction().begin();

        em.createNativeQuery("delete from users where krb_name = 'lvydra'").executeUpdate();

        RegisteredUser registeredUser = new RegisteredUser();
        registeredUser.setGithubName("lvydra_prior");
        registeredUser.setKerberosName("lvydra_prior");
        registeredUser.setWhitelisted(false);
        em.persist(registeredUser);

        em.getTransaction().commit();


        JSONObject eIAMMessage = new JSONObject();
        eIAMMessage.put("event", "offboard");
        eIAMMessage.put("key", "22");
        JSONObject person = new JSONObject();
        person.put("uid", "lvydra");
        person.put("dn", "uid=lvydra,dc=test");
        eIAMMessage.put("person", person);

        em.getTransaction().begin();
        employeeOffBoardEventsMDB.processMessage(eIAMMessage.toString());
        em.getTransaction().commit();

        TypedQuery<UserRemoval> findRemovalsQuery = em.createNamedQuery(UserRemoval.FIND_REMOVALS_TO_PROCESS, UserRemoval.class);
        List<UserRemoval> removals = findRemovalsQuery.getResultList();
        assertThat(removals.size()).isEqualTo(1);
        assertThat(removals.get(0).getLdapUsername()).isEqualTo("lvydra_prior");
    }

}
