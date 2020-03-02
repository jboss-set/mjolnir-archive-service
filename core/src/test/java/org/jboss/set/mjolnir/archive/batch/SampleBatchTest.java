package org.jboss.set.mjolnir.archive.batch;

public class SampleBatchTest {

    // TODO: running the whole batch would probably need to be tested by an arquillian test, as we would need
    //  the CDI functionality as well as the batch processing environment.

//    private static final int MAX_TRIES = 40;
//    private static final int THREAD_SLEEP = 1000;

    /*@Test
    public void testBatch() throws Exception {
        JobOperator jobOperator = BatchRuntime.getJobOperator();
        long executionId = jobOperator.start("membershipRemovalJob", new Properties());
        JobExecution jobExecution = jobOperator.getJobExecution(executionId);
        jobExecution = keepTestAlive(jobExecution);
        assertEquals(jobExecution.getBatchStatus(), BatchStatus.COMPLETED);
    }

    public static JobExecution keepTestAlive(JobExecution jobExecution) throws InterruptedException {
        int maxTries = 0;
        while (!jobExecution.getBatchStatus()
                .equals(BatchStatus.COMPLETED)) {
            if (maxTries < MAX_TRIES) {
                maxTries++;
                Thread.sleep(THREAD_SLEEP);
                jobExecution = BatchRuntime.getJobOperator()
                        .getJobExecution(jobExecution.getExecutionId());
            } else {
                break;
            }
        }
        Thread.sleep(THREAD_SLEEP);
        return jobExecution;
    }*/
}
