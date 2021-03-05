package org.jboss.set.mjolnir.archive.mail.report;

import j2html.tags.DomContent;
import org.jboss.set.mjolnir.archive.UserDiscoveryBean;

import javax.inject.Inject;
import java.io.IOException;

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
 * Prints a list of GH users who are members of monitored GH teams, but are not registered in the Mjolnir database.
 */
@SuppressWarnings("UnnecessaryLocalVariable")
public class UnregisteredMembersReportTable implements ReportTable {

    @Inject
    private UserDiscoveryBean userDiscoveryBean;

    @Override
    public String composeTable() throws IOException {
        String html = div().with(
                h2("Unknown GH Teams Members").withStyle(Styles.H2_STYLE),
                p("These users are members of GitHub teams, but are not registered in our database. " +
                        "This table should remain empty.")
                        .withStyle(Styles.SUB_HEADING_STYLE),
                table().withStyle(Styles.TABLE_STYLE + Styles.TD_STYLE).with(
                        tr().with(
                                th(Constants.LDAP_NAME).withStyle(Styles.TH_STYLE),
                                th(Constants.ORGANIZATIONS + " / " + Constants.TEAMS).withStyle(Styles.TH_STYLE)
                        ),
                        addUnregisteredOrganizationMembersRows(),
                        addUnregisteredTeamMembersRows()
                ))
                .render();
        return html;
    }

    private DomContent addUnregisteredOrganizationMembersRows() throws IOException {
        return each(userDiscoveryBean.findUnregisteredOrganizationsMembers(), entry -> tr(
                td(entry.getKey()).withStyle(Styles.TD_STYLE),
                td(ul().withStyle(Styles.UL_STYLE)
                        .with(each(entry.getValue(),
                                team -> li(team.getName())))
                ).withStyle(Styles.BORDER_STYLE)
        ));
    }

    private DomContent addUnregisteredTeamMembersRows() throws IOException {
        return each(userDiscoveryBean.findUnregisteredTeamsMembers(), entry -> tr(
                td(entry.getKey()).withStyle(Styles.TD_STYLE),
                td(ul().withStyle(Styles.UL_STYLE)
                        .with(each(entry.getValue(),
                                team -> li(team.getOrganization().getName() + "/" + team.getName())))
                ).withStyle(Styles.BORDER_STYLE)
        ));
    }

}
