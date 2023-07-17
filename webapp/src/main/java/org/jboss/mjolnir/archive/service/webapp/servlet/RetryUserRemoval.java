package org.jboss.mjolnir.archive.service.webapp.servlet;

import org.jboss.set.mjolnir.archive.UserDiscoveryBean;
import org.jboss.set.mjolnir.archive.domain.UserRemoval;

import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Triggers a batch job that archives repositories of offboarder users and removes their GH team memberships.
 */
@WebServlet("/retry-user-removal")
public class RetryUserRemoval extends HttpServlet {

    @Inject
    private UserDiscoveryBean userDiscoveryBean;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String idString = req.getParameter("id");
        if (idString == null || idString.isEmpty()) {
            resp.getWriter().println("Removal ID was not given.");
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        long id;
        try {
            id = Long.parseLong(idString);
        } catch (NumberFormatException e) {
            resp.getWriter().println("Given ID cannot be parsed.");
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        UserRemoval userRemoval = userDiscoveryBean.getUserRemoval(id);
        userDiscoveryBean.createUserRemoval(userRemoval.getLdapUsername());
    }
}
