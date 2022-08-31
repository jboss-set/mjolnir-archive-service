package org.jboss.set.mjolnir.archive.mail.report;

import j2html.tags.DomContent;
import org.jboss.set.mjolnir.archive.UserDiscoveryBean;
import org.jboss.set.mjolnir.archive.domain.RegisteredUser;

import javax.inject.Inject;
import javax.naming.NamingException;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;
import static j2html.TagCreator.each;
import static j2html.TagCreator.h2;
import static j2html.TagCreator.p;
import static j2html.TagCreator.table;
import static j2html.TagCreator.td;
import static j2html.TagCreator.th;
import static j2html.TagCreator.tr;
import static org.jboss.set.mjolnir.archive.mail.report.ReportUtils.optionalToString;
import static org.jboss.set.mjolnir.archive.mail.report.ReportUtils.stringOrEmpty;

/**
 * Prints a list of registered users who no longer have any valid (existing) GitHub accounts
 * (i.e. probably changed their GitHub username).
 */
@SuppressWarnings("UnnecessaryLocalVariable")
public class InvalidGitHubUsersReportTable implements ReportTable {

    private static final String REPORT_TABLE_TITLE = "Users without a valid GitHub account";

    @Inject
    UserDiscoveryBean userDiscoveryBean;

    @Override
    public String composeTable() throws NamingException, IOException {
        String html = div().with(
                h2(REPORT_TABLE_TITLE).withStyle(Styles.H2_STYLE),
                p("These users were registered in Mjolnir, but their GitHub accounts no longer exist. " +
                        "This table should remain empty.")
                        .withStyle(Styles.SUB_HEADING_STYLE),
                table().withStyle(Styles.TABLE_STYLE + Styles.TD_STYLE).with(
                        tr().with(
                                th(Constants.GH_NAME).withStyle(Styles.TH_STYLE),
                                th(Constants.GH_ID).withStyle(Styles.TH_STYLE),
                                th(Constants.GH_NAME_FOR_ID).withStyle(Styles.TH_STYLE),
                                th(Constants.LDAP_NAME).withStyle(Styles.TH_STYLE),
                                th(Constants.ACTIVE_LDAP_ACCOUNT).withStyle(Styles.TH_STYLE),
                                th(Constants.REGISTERED).withStyle(Styles.TH_STYLE)
                        ),
                        addInvalidUsersRows()
                ))
                .render();
        return html;
    }

    private DomContent addInvalidUsersRows() {
        // produce sorted list
        List<RegisteredUser> invalidUsers =
                userDiscoveryBean.findInvalidGithubUsers().stream()
                        .sorted(new GithubNameUserComparator())
                        .collect(Collectors.toList());

        return each(invalidUsers, invalidUser -> tr(
                td(invalidUser.getGithubName()).withStyle(Styles.TD_STYLE),
                td(optionalToString(invalidUser.getGithubId())).withStyle(Styles.TD_STYLE),
                td(stringOrEmpty(userDiscoveryBean.findGithubLoginForID(invalidUser.getGithubId())))
                        .withStyle(Styles.TD_STYLE),
                td(invalidUser.getKerberosName()).withStyle(Styles.TD_STYLE),
                td(hasActiveLdapAccount(invalidUser)).withStyle(Styles.TD_STYLE),
                td(optionalToString(invalidUser.getCreated())).withStyle(Styles.TD_STYLE)
        ));
    }

    private String hasActiveLdapAccount(RegisteredUser registeredUser) {
        if (registeredUser == null) {
            return "";
        }
        Boolean active = userDiscoveryBean.hasActiveLdapAccount(registeredUser.getKerberosName());
        if (active == null) {
            return "Failed to discover";
        }
        return active ? "Yes" : "No";
    }

    private static class GithubNameUserComparator implements Comparator<RegisteredUser> {

        @Override
        public int compare(RegisteredUser o1, RegisteredUser o2) {
            if (o1.getGithubName() == null && o2.getGithubName() == null) {
                return 0;
            }
            if (o1.getGithubName() == null) {
                return 1;
            }
            if (o2.getGithubName() == null) {
                return -1;
            }
            return o1.getGithubName().compareToIgnoreCase(o2.getGithubName());
        }
    }

}
