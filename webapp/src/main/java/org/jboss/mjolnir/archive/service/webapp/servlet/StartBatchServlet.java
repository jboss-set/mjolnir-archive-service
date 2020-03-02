package org.jboss.mjolnir.archive.service.webapp.servlet;

import org.jboss.logging.Logger;

import javax.batch.operations.JobOperator;
import javax.batch.operations.NoSuchJobException;
import javax.batch.runtime.BatchRuntime;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

@WebServlet("/start-batch")
public class StartBatchServlet extends HttpServlet {

    public static final String JOB_NAME = "membershipRemovalJob";

    private Logger logger = Logger.getLogger(getClass());

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/plain");

        JobOperator jobOperator = BatchRuntime.getJobOperator();
        try {
            List<Long> runningExecutions = jobOperator.getRunningExecutions(JOB_NAME);

            if (runningExecutions.size() > 0) {
                resp.getOutputStream().println("Job already running.");
                logger.infof("Jobs already running: ", runningExecutions.size());
                return;
            }
        } catch (NoSuchJobException e) {
            // pass
        }

        long executionId = jobOperator.start(JOB_NAME, new Properties());
        logger.infof("Started job ID %d", executionId);
        resp.getOutputStream().println("Started execution ID: " + executionId);
    }
}
