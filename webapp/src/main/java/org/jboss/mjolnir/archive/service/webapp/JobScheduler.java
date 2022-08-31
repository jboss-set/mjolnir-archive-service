package org.jboss.mjolnir.archive.service.webapp;


import org.jboss.logging.Logger;
import org.jboss.set.mjolnir.archive.UserDiscoveryBean;
import org.jboss.set.mjolnir.archive.configuration.Configuration;

import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.inject.Inject;

@Singleton
@Startup
@TransactionManagement(TransactionManagementType.BEAN) // do not open managed transaction
public class JobScheduler {

    private final static Logger logger = Logger.getLogger(JobScheduler.class);

    @Inject
    private Configuration configuration;

    @Inject
    private UserDiscoveryBean userDiscoveryBean;

    @Schedule(dayOfWeek = "6", hour = "2", persistent = false)
    public void updateGithubUsernames() {
        BatchUtils.startBatchJob(Constants.UPDATE_GITHUB_USERNAMES_JOB_NAME);
    }

    @Schedule(hour = "3", persistent = false)
    public void ldapScan() {
        if (configuration.isRemoveUsersWithoutLdapAccount()) {
            logger.infof("Starting task ldapScan");
            userDiscoveryBean.createRemovalsForUsersWithoutLdapAccount();
        }
    }

    @Schedule(hour = "4", persistent = false)
    public void archiveUsers() {
        BatchUtils.startBatchJob(Constants.REMOVE_MEMBERSHIP_JOB_NAME);
    }

    @Schedule(dayOfWeek = "6", hour = "6", persistent = false)
    public void remove_archived_users() {
        BatchUtils.startBatchJob(Constants.DELETE_ARCHIVES_JOB_NAME);
    }

}
