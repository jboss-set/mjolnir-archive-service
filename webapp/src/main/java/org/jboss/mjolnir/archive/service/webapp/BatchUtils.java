package org.jboss.mjolnir.archive.service.webapp;

import org.jboss.logging.Logger;

import javax.batch.operations.JobOperator;
import javax.batch.operations.NoSuchJobException;
import javax.batch.runtime.BatchRuntime;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.Properties;

public final class BatchUtils {

    private static final Logger logger = Logger.getLogger(BatchUtils.class);

    private BatchUtils() {
    }

    public static Long startBatchJob(String jobName) {
        logger.infof("Starting task %s", jobName);

        // check if batch jobs are already running
        JobOperator jobOperator = BatchRuntime.getJobOperator();
        try {
            List<Long> runningExecutions = jobOperator.getRunningExecutions(jobName);

            if (runningExecutions.size() > 0) {
                logger.warnf("%d jobs with name %s are already running. Batch job is not started now.",
                        runningExecutions.size(), jobName);
                logThreadDump();
                return null;
            }
        } catch (NoSuchJobException e) {
            logger.infof("No jobs with name %s found.", jobName);
        }

        // if no job is currently running, start new one
        long executionId = jobOperator.start(jobName, new Properties());
        logger.infof("Started batch job # %d", executionId);
        return executionId;
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
