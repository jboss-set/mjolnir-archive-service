package org.jboss.mjolnir.archive.service.webapp;


import org.jboss.logging.Logger;
import org.jboss.set.mjolnir.archive.UserDiscoveryBean;
import org.jboss.set.mjolnir.archive.configuration.Configuration;

import javax.batch.operations.JobOperator;
import javax.batch.operations.NoSuchJobException;
import javax.batch.runtime.BatchRuntime;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.inject.Inject;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.Properties;

@Singleton
@Startup
@TransactionManagement(TransactionManagementType.BEAN) // do not open managed transaction
public class JobScheduler {

    private final static Logger logger = Logger.getLogger(JobScheduler.class);

    @Inject
    private Configuration configuration;

    @Inject
    private UserDiscoveryBean userDiscoveryBean;


    @Schedule(hour = "3", persistent = false)
    public void ldapScan() {
        if (configuration.isRemoveUsersWithoutLdapAccount()) {
            logger.infof("Starting task ldapScan");
            userDiscoveryBean.createRemovalsForUsersWithoutLdapAccount();
        }
    }

    @Schedule(hour = "4", persistent = false)
    public void archiveUsers() {
        logger.infof("Starting task %s", Constants.REMOVE_MEMBERSHIP_JOB_NAME);

        // check if batch jobs are already running
        JobOperator jobOperator = BatchRuntime.getJobOperator();
        try {
            List<Long> runningExecutions = jobOperator.getRunningExecutions(Constants.REMOVE_MEMBERSHIP_JOB_NAME);

            if (runningExecutions.size() > 0) {
                logger.warnf("%d jobs with name %s are already running. Batch job is not started now.",
                        runningExecutions.size(), Constants.REMOVE_MEMBERSHIP_JOB_NAME);
                logThreadDump();
                return;
            }
        } catch (NoSuchJobException e) {
            logger.infof("No jobs with name %s found.", Constants.REMOVE_MEMBERSHIP_JOB_NAME);
        }

        // if no job is currently running, start new one
        long executionId = jobOperator.start(Constants.REMOVE_MEMBERSHIP_JOB_NAME, new Properties());
        logger.infof("Started batch job # %d", executionId);
    }

    @Schedule(dayOfWeek = "6", hour = "6", persistent = false)
    public void remove_archived_users() {
        logger.infof("Starting task %s", Constants.DELETE_ARCHIVES_JOB_NAME);

        // check if batch jobs are already running
        JobOperator jobOperator = BatchRuntime.getJobOperator();
        try {
            List<Long> runningExecutions = jobOperator.getRunningExecutions(Constants.DELETE_ARCHIVES_JOB_NAME);

            if (runningExecutions.size() > 0) {
                logger.infof("%d jobs with name %s are already running. Batch job is not started now.",
                        runningExecutions.size(), Constants.DELETE_ARCHIVES_JOB_NAME);
                return;
            }
        } catch (NoSuchJobException e) {
            logger.infof("No jobs with name %s found.", Constants.DELETE_ARCHIVES_JOB_NAME);
        }

        // if no job is currently running, start new one
        long executionId = jobOperator.start(Constants.DELETE_ARCHIVES_JOB_NAME, new Properties());
        logger.infof("Started batch job # %d", executionId);
    }

    private static void logThreadDump() {
        StringBuilder threadDump = new StringBuilder("Thread dump: ");
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        for(ThreadInfo threadInfo: threadMXBean.dumpAllThreads(true, true)) {
            threadDump.append(threadInfo.toString());
        }
        logger.warn(threadDump.toString());
    }
}
