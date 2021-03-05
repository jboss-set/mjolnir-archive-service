package org.jboss.set.mjolnir.archive.umb;

import org.jboss.ejb3.annotation.ResourceAdapter;
import org.jboss.logging.Logger;
import org.jboss.set.mjolnir.archive.domain.RegisteredUser;
import org.jboss.set.mjolnir.archive.domain.repositories.RegisteredUserRepositoryBean;
import org.jboss.set.mjolnir.archive.domain.repositories.RemovalLogRepositoryBean;
import org.jboss.set.mjolnir.archive.ldap.LdapClientBean;
import org.jboss.set.mjolnir.archive.UserDiscoveryBean;
import org.json.JSONObject;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import javax.naming.NamingException;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@MessageDriven(name = "EmployeeOffBoardEventsMDB", activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(propertyName = "useJndi", propertyValue = "true"),
        @ActivationConfigProperty(propertyName = "destination", propertyValue = "java:/queue/EmployeeEventsQueue"), // JNDI name
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")})
@ResourceAdapter(value = "activemq-rar.rar")
public class EmployeeOffBoardEventsMDB implements MessageListener {

    private static final String OFF_BOARD_EVENT = "offboard";

    private final Logger logger = Logger.getLogger(getClass());

    @Inject
    private RegisteredUserRepositoryBean userRepositoryBean;

    @Inject
    private UserDiscoveryBean userDiscoveryBean;

    @Inject
    private RemovalLogRepositoryBean logRepositoryBean;

    @Inject
    private LdapClientBean ldapClientBean;

    @Inject
    private EntityManager em;

    public void onMessage(Message rcvMessage) {
        EntityTransaction transaction = null;

        try {
            transaction = em.getTransaction();
            transaction.begin();

            try {
                if (rcvMessage instanceof TextMessage) {
                    TextMessage textMessage = (TextMessage) rcvMessage;
                    processMessage(textMessage.getText());
                } else {
                    logger.warnf("Message of wrong type: %s", rcvMessage.getClass().getName());
                }
            } catch (JMSException e) {
                throw new RuntimeException(e);
            }

            transaction.commit();
        } finally {
            if (transaction != null && transaction.isActive()) {
                transaction.commit();
            }
            em.close();
        }
    }

    void processMessage(String message) {
        JSONObject jsonObject = new JSONObject(message);
        String event = jsonObject.getString("event");

        if (OFF_BOARD_EVENT.equals(event)) {
            JSONObject person = jsonObject.getJSONObject("person");
            String currentUid = person.getString("uid");

            logger.infof("Received offboard event for user %s.", currentUid);

            HashSet<String> uids = new HashSet<>();
            uids.add(currentUid);
            try {
                List<String> allUserUids = ldapClientBean.findAllUserUids(currentUid);
                uids.addAll(allUserUids);
            } catch (NamingException e) {
                logger.errorf("Couldn't obtain user UIDs from LDAP for user %s", currentUid);
            }

            List<RegisteredUser> registeredUsers = userRepositoryBean.findByKrbUsernames(uids);
            if (registeredUsers.size() == 1) {
                RegisteredUser user = registeredUsers.get(0);
                if (user.isWhitelisted()) {
                    logger.infof("Skipping whitelisted user %s.", user.getKerberosName());
                    logRepositoryBean.logMessage(String.format("Offboard event: Skipping whitelisted user %s.",
                            user.getKerberosName()));
                } else {
                    logger.infof("Creating removal for user %s.", user.getKerberosName());
                    logRepositoryBean.logMessage(String.format("Offboard event: Creating removal for user %s.",
                            user.getKerberosName()));
                    userDiscoveryBean.createUserRemoval(user.getKerberosName());
                }
            } else if (registeredUsers.size() == 0){
                logger.infof("User %s is not registered.", currentUid);
                logRepositoryBean.logMessage(String.format("Offboard event: User %s is not registered.", currentUid));
            } else {
                // Several registered users found => do nothing, let the situation be verified by an admin.

                // Hopefully corporate auth system doesn't allow users to pick UID that previously belonged to
                // a different user. If that is true, this case would indicate that the same user has been registered
                // several times, with different UIDs. In that case we should create removals for all UIDs that were
                // found... Need to verify this.
                //
                // TODO: Could this situation be mitigated in the registration logic that would check if
                //  an authenticated user is not registered with some of his prior UIDs? Currently we only look for
                //  registered user by his current UID - which might not be the one he was previously registered with.
                //  Also, the current registration logic would not allow user to register the same GH username twice.

                String registeredUids = registeredUsers.stream().map(RegisteredUser::getKerberosName)
                        .collect(Collectors.joining(", "));
                logger.warnf("Found several registered users for UID %s: %s", currentUid, registeredUids);
                logRepositoryBean.logMessage(String.format("Found several registered users for UID %s: %s", currentUid,
                        registeredUids));
            }
        }
    }
}
