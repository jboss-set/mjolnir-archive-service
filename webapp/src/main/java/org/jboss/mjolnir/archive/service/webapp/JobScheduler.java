package org.jboss.mjolnir.archive.service.webapp;


import org.jboss.logging.Logger;
import org.jboss.set.mjolnir.archive.configuration.Configuration;
import org.jboss.set.mjolnir.archive.ldap.LdapScanningBean;

import javax.batch.operations.JobOperator;
import javax.batch.operations.NoSuchJobException;
import javax.batch.runtime.BatchRuntime;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.inject.Inject;
import java.util.List;
import java.util.Properties;

@Singleton
@Startup
@TransactionManagement(TransactionManagementType.BEAN) // do not open managed transaction
public class JobScheduler {

    private final Logger logger = Logger.getLogger(getClass());

    @Inject
    private Configuration configuration;

    @Inject
    private LdapScanningBean ldapScanningBean;


    @Schedule(hour = "3", persistent = false)
    public void ldapScan() {
        if (configuration.isRemoveUsersWithoutLdapAccount()) {
            logger.infof("Starting task ldapScan");
            ldapScanningBean.createRemovalsForUsersWithoutLdapAccount();
        }
    }

    @Schedule(hour = "4", persistent = false)
    public void archiveUsers() {
        logger.infof("Starting task archiveUsers");

        // check if batch jobs are already running
        JobOperator jobOperator = BatchRuntime.getJobOperator();
        try {
            List<Long> runningExecutions = jobOperator.getRunningExecutions(Constants.BATCH_JOB_NAME);

            if (runningExecutions.size() > 0) {
                logger.infof("%d jobs with name %s are already running. Batch job is not started now.",
                        runningExecutions.size(), Constants.BATCH_JOB_NAME);
                return;
            }
        } catch (NoSuchJobException e) {
            logger.infof("No jobs with name %s found.", Constants.BATCH_JOB_NAME);
        }

        // if no job is currently running, start new one
        long executionId = jobOperator.start(Constants.BATCH_JOB_NAME, new Properties());
        logger.infof("Started batch job # %d", executionId);
    }

}
