package org.jboss.mjolnir.archive.service.webapp.servlet;

import org.jboss.mjolnir.archive.service.webapp.BatchUtils;
import org.jboss.mjolnir.archive.service.webapp.Constants;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Triggers a batch job that archives repositories of boarder users and removes their GH team memberships after 90 days.
 */
@WebServlet("/delete-old-archives")
public class DeleteArchivesServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Long executionId = BatchUtils.startBatchJob(Constants.DELETE_ARCHIVES_JOB_NAME);
        resp.setContentType("text/plain");
        resp.getOutputStream().println("Started job execution ID: " + executionId);
    }
}
