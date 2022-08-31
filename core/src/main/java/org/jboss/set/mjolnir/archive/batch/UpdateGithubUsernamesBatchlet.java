package org.jboss.set.mjolnir.archive.batch;

import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;
import org.jboss.set.mjolnir.archive.UserDiscoveryBean;
import org.jboss.set.mjolnir.archive.domain.RegisteredUser;
import org.jboss.set.mjolnir.archive.domain.repositories.RegisteredUserRepositoryBean;

import javax.batch.api.AbstractBatchlet;
import javax.inject.Inject;
import javax.inject.Named;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.util.List;

/**
 * Goes through all registered users and updates their GH username to match their GH ID. This should keep GH usernames
 * in the database consistent in case users rename their GH accounts.
 */
@Named
public class UpdateGithubUsernamesBatchlet extends AbstractBatchlet {

    private final Logger logger = Logger.getLogger(getClass());

    @Inject
    private UserDiscoveryBean userDiscoveryBean;

    @Inject
    private RegisteredUserRepositoryBean userRepositoryBean;

    @Inject
    private EntityManager em;

    @Override
    public String process() {
        List<RegisteredUser> invalidGithubUsers = userDiscoveryBean.findInvalidGithubUsers();

        for (RegisteredUser registeredUser: invalidGithubUsers) {
            if (registeredUser.getGithubId() != null) {
                String currentLogin = userDiscoveryBean.findGithubLoginForID(registeredUser.getGithubId());
                if (StringUtils.isNotBlank(currentLogin)) {
                    logger.infof("Updating GH name for registered user # %d, LDAP name '%s', from '%s' to '%s'",
                            registeredUser.getId(), registeredUser.getKerberosName(), registeredUser.getGithubName(), currentLogin);
                    registeredUser.setGithubName(currentLogin);

                    EntityTransaction transaction = em.getTransaction();
                    transaction.begin();
                    userRepositoryBean.updateUser(registeredUser);
                    transaction.commit();
                } else {
                    logger.infof("Unable to find current GH name for user # %d, LDAP name '%s', old GH name %s",
                            registeredUser.getId(), registeredUser.getKerberosName(), registeredUser.getGithubName());
                }
            }
        }

        return Constants.DONE;
    }
}
