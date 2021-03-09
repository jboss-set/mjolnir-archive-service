package org.jboss.mjolnir.archive.service.webapp.servlet;

import org.jboss.set.mjolnir.archive.UserDiscoveryBean;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Retrieves all GH team members and creates removal records for members without active LDAP account.
 */
@WebServlet("/ldap-scan")
public class LdapScanningServlet extends HttpServlet {

    @Inject
    private UserDiscoveryBean userDiscoveryBean;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        userDiscoveryBean.createRemovalsForUsersWithoutLdapAccount();

        resp.setContentType("text/plain");
        try (PrintWriter writer = resp.getWriter()) {
            writer.println("OK");
        }
    }
}
