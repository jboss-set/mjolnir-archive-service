package org.jboss.set.mjolnir.archive.umb;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.jboss.set.mjolnir.archive.domain.RegisteredUser;
import org.jboss.set.mjolnir.archive.domain.UserRemoval;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(CdiTestRunner.class)
public class EmployeeOffBoardEventsMDBTestCase {

    @Inject
    private EntityManager em;

    @Inject
    private EmployeeOffBoardEventsMDB employeeOffBoardEventsMDB;

    @Before
    public void setup() {
        em.getTransaction().begin();

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
    public void testProcessMessage() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method method = EmployeeOffBoardEventsMDB.class.getDeclaredMethod("processMessage", new Class[] {String.class});
        method.setAccessible(true);

        JSONObject eIAMMessage = new JSONObject();
        eIAMMessage.put("event", "offboard");
        eIAMMessage.put("key", "22");
        JSONObject person = new JSONObject();
        person.put("uid", "lvydra");
        person.put("dn", "lvydra");
        eIAMMessage.put("person", person);

        method.invoke(employeeOffBoardEventsMDB, eIAMMessage.toString());

        eIAMMessage = new JSONObject();
        eIAMMessage.put("event", "offboard");
        eIAMMessage.put("key", "22");
        person = new JSONObject();
        person.put("uid", "joe");
        person.put("dn", "joe");
        eIAMMessage.put("person", person);

        method.invoke(employeeOffBoardEventsMDB, eIAMMessage.toString());

        eIAMMessage = new JSONObject();
        eIAMMessage.put("event", "update");
        eIAMMessage.put("key", "22");
        person = new JSONObject();
        person.put("uid", "bruno");
        person.put("dn", "bruno");
        eIAMMessage.put("person", person);

        method.invoke(employeeOffBoardEventsMDB, eIAMMessage.toString());

        TypedQuery<UserRemoval> findRemovalsQuery = em.createNamedQuery(UserRemoval.FIND_REMOVALS_TO_PROCESS, UserRemoval.class);
        List<UserRemoval> removals = findRemovalsQuery.getResultList();
        assertThat(removals.size()).isEqualTo(1);
        assertThat(removals)
                .extracting("username")
                .containsOnly("lvydra");
    }
}
