package org.jboss.set.mjolnir.archive.mail.report;

import j2html.tags.DomContent;
import org.jboss.set.mjolnir.archive.domain.GitHubTeam;
import org.jboss.set.mjolnir.archive.ldap.LdapScanningBean;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

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

public class UnregisteredMembersReportTable implements ReportTable {

    @Inject
    private LdapScanningBean ldapScanningBean;

    @Override
    public String composeTable() throws IOException {
        String html = div().with(
                h2("Unknown GH Teams Members").withStyle(Styles.H2_STYLE),
                p("These users are members of GitHub teams, but are not registered in our database.")
                        .withStyle(Styles.SUB_HEADING_STYLE),
                table().withStyle(Styles.TABLE_STYLE + Styles.TD_STYLE).with(
                        tr().with(
                                th(Constants.LDAP_NAME).withStyle(Styles.TH_STYLE),
                                th(Constants.TEAMS).withStyle(Styles.TH_STYLE)
                        ),
                        addUnregisteredOrganizationMembersRows(getUnregisteredMembersWithTeams())
                ))
                .render();
        return html;
    }

    private <T> DomContent addUnregisteredOrganizationMembersRows(Map<String, List<GitHubTeam>> userTeams) {
        return each(userTeams, userTeam -> tr(
                td(userTeam.getKey()).withStyle(Styles.TD_STYLE),
                td(
                        ul().withStyle(Styles.UL_STYLE)
                                .with(each(userTeam.getValue(), team -> li(team.getName())))
                ).withStyle(Styles.BORDER_STYLE)
        ));
    }

    private Map<String, List<GitHubTeam>> getUnregisteredMembersWithTeams() throws IOException {
        Set<String> unregisteredOrganizationMembers = ldapScanningBean.getUnregisteredOrganizationMembers();
        SortedMap<String, List<GitHubTeam>> userTeams = new TreeMap<>(String::compareToIgnoreCase);
        for (String member : unregisteredOrganizationMembers) {
            userTeams.put(member, ldapScanningBean.getAllUsersTeams(member));
        }

        return userTeams;
    }
}
