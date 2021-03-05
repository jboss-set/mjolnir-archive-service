package org.jboss.set.mjolnir.archive.mail.report;

import j2html.tags.DomContent;
import org.jboss.set.mjolnir.archive.UserDiscoveryBean;
import org.jboss.set.mjolnir.archive.domain.GitHubOrganization;
import org.jboss.set.mjolnir.archive.domain.GitHubTeam;

import javax.inject.Inject;
import javax.naming.NamingException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static j2html.TagCreator.div;
import static j2html.TagCreator.each;
import static j2html.TagCreator.h2;
import static j2html.TagCreator.li;
import static j2html.TagCreator.p;
import static j2html.TagCreator.table;
import static j2html.TagCreator.td;
import static j2html.TagCreator.th;
import static j2html.TagCreator.tr;
import static j2html.TagCreator.ul;

/**
 * Prints a list GH users who are members of monitored GH teams, are registered in the Mjolnir database, but their
 * LDAP accounts are not active (i.e. probably left the company).
 */
@SuppressWarnings("UnnecessaryLocalVariable")
public class UsersWithoutLdapReportTable implements ReportTable {

    private static final String REPORT_TABLE_TITLE = "Users without an LDAP Account";

    @Inject
    private UserDiscoveryBean userDiscoveryBean;

    @Override
    public String composeTable() throws NamingException, IOException {
        String html = div().with(
                h2(REPORT_TABLE_TITLE).withStyle(Styles.H2_STYLE),
                p("These users were registered in Mjolnir, but their LDAP accounts have been removed. " +
                        "This table should remain empty.")
                        .withStyle(Styles.SUB_HEADING_STYLE),
                table().withStyle(Styles.TABLE_STYLE + Styles.TD_STYLE).with(
                        tr().with(
                                th(Constants.LDAP_NAME).withStyle(Styles.TH_STYLE),
                                th(Constants.ORGANIZATIONS + " / " + Constants.TEAMS).withStyle(Styles.TH_STYLE)
                        ),
                        addOrganizationsMembersRows(),
                        addTeamsMembersRows()
                ))
                .render();
        return html;
    }

    private DomContent addOrganizationsMembersRows() throws IOException, NamingException {
        // produce sorted entry lists
        List<Map.Entry<String, List<GitHubOrganization>>> organizationsMembers =
                userDiscoveryBean.findOrganizationsMembersWithoutLdapAccount().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .collect(Collectors.toList());

        return each(organizationsMembers, entry -> tr(
                td(entry.getKey()).withStyle(Styles.TD_STYLE),
                td(ul().withStyle(Styles.UL_STYLE)
                        .with(each(entry.getValue(), team -> li(team.getName()))))
                        .withStyle(Styles.TD_STYLE)
        ));
    }

    private DomContent addTeamsMembersRows() throws IOException, NamingException {
        // produce sorted entry list
        List<Map.Entry<String, List<GitHubTeam>>> teamsMembers =
                userDiscoveryBean.findTeamsMembersWithoutLdapAccount().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .collect(Collectors.toList());

        return each(teamsMembers, entry -> tr(
                td(entry.getKey()).withStyle(Styles.TD_STYLE),
                td(ul().withStyle(Styles.UL_STYLE)
                        .with(each(entry.getValue(),
                                team -> li(team.getOrganization().getName() + "/" + team.getName()))))
                        .withStyle(Styles.TD_STYLE)
        ));
    }

}
