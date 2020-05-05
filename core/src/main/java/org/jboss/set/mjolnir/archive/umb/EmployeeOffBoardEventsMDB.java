package org.jboss.set.mjolnir.archive.umb;

import org.jboss.logging.Logger;
import org.jboss.set.mjolnir.archive.domain.RegisteredUser;
import org.jboss.set.mjolnir.archive.domain.repositories.RegisteredUserRepositoryBean;
import org.jboss.set.mjolnir.archive.ldap.LdapScanningBean;
import org.json.JSONObject;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import java.util.Optional;

@MessageDriven(name = "EmployeeOffBoardEventsMDB", activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "queue/EMPLOYEEEVENTSMDBQueue"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
        @ActivationConfigProperty(propertyName = "acknowledgeMode", propertyValue = "Auto-acknowledge")})
public class EmployeeOffBoardEventsMDB implements MessageListener {

    private static final String OFF_BOARD_EVENT = "offboard";

    private Logger logger = Logger.getLogger(getClass());

    @Inject
    private RegisteredUserRepositoryBean userRepositoryBean;

    @Inject
    private LdapScanningBean ldapScanningBean;

    public void onMessage(Message rcvMessage) {
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
    }

    private void processMessage(String message) {
        JSONObject jsonObject = new JSONObject(message);
        String event = jsonObject.getString("event");

        if (OFF_BOARD_EVENT.equals(event)) {
            JSONObject person = jsonObject.getJSONObject("person");
            String kerberosName = person.getString("dn");

            Optional<RegisteredUser> registeredUser = userRepositoryBean.findByKrbUsername(kerberosName);
            registeredUser.ifPresent(user -> {
                if (user.isWhitelisted()) {
                    logger.infof("Skipping whitelisted user %s.", user.getGithubName());
                } else {
                    ldapScanningBean.createUserRemoval(user.getKerberosName());
                }
            });
        }
    }
}
