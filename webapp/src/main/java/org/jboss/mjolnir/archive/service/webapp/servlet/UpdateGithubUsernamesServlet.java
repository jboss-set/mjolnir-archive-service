package org.jboss.mjolnir.archive.service.webapp.servlet;

import org.jboss.mjolnir.archive.service.webapp.BatchUtils;
import org.jboss.mjolnir.archive.service.webapp.Constants;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Triggers a batch job that updates GH usernames of registered users.
 */
@WebServlet("/update-github-usernames")
public class UpdateGithubUsernamesServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Long executionId = BatchUtils.startBatchJob(Constants.UPDATE_GITHUB_USERNAMES_JOB_NAME);
        resp.setContentType("text/plain");
        resp.getOutputStream().println("Started job execution ID: " + executionId);
    }
}
