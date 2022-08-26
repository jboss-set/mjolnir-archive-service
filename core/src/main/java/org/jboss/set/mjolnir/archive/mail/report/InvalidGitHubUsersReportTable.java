package org.jboss.set.mjolnir.archive.mail.report;

import j2html.tags.DomContent;
import org.jboss.set.mjolnir.archive.UserDiscoveryBean;
import org.jboss.set.mjolnir.archive.domain.RegisteredUser;

import javax.inject.Inject;
import javax.naming.NamingException;
import java.io.IOException;
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

/**
 * Prints a list of registered users who no longer have any valid (existing) GitHub accounts
 * (i.e. probably changed their GitHub username).
 */
@SuppressWarnings("UnnecessaryLocalVariable")
public class InvalidGitHubUsersReportTable implements ReportTable {

    private static final String REPORT_TABLE_TITLE = "Users without a valid GitHub account";

    @Inject
    private UserDiscoveryBean userDiscoveryBean;

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
                                th(Constants.LDAP_NAME).withStyle(Styles.TH_STYLE)
                        ),
                        addInvalidUsersRows()
                ))
                .render();
        return html;
    }

    private DomContent addInvalidUsersRows() throws IOException, NamingException {
        // produce sorted list
        List<RegisteredUser> invalidUsers =
                userDiscoveryBean.findInvalidGithubUsers().stream()
                        .sorted()
                        .collect(Collectors.toList());

        return each(invalidUsers, invalidUser -> tr(
                td(invalidUser.getGithubName()).withStyle(Styles.TD_STYLE),
                td(invalidUser.getKerberosName()).withStyle(Styles.TD_STYLE)
        ));
    }

}
