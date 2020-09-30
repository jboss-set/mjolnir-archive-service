package org.jboss.set.mjolnir.archive.mail.report;

import j2html.tags.DomContent;
import org.jboss.set.mjolnir.archive.ldap.LdapScanningBean;

import javax.inject.Inject;
import javax.naming.NamingException;
import java.io.IOException;
import java.util.List;

import static j2html.TagCreator.div;
import static j2html.TagCreator.each;
import static j2html.TagCreator.h2;
import static j2html.TagCreator.p;
import static j2html.TagCreator.table;
import static j2html.TagCreator.td;
import static j2html.TagCreator.th;
import static j2html.TagCreator.tr;

public class UsersWithoutLdapReportTable implements ReportTable {

    private static final String REPORT_TABLE_TITLE = "Users without an LDAP Account";

    @Inject
    private LdapScanningBean ldapScanningBean;

    @Override
    public String composeTable() throws NamingException, IOException {
        String html = div().with(
                h2(REPORT_TABLE_TITLE).withStyle(Styles.H2_STYLE),
                p("These users were registered in Mjolnir, but their LDAP accounts have been removed.")
                        .withStyle(Styles.SUB_HEADING_STYLE),
                table().withStyle(Styles.TABLE_STYLE + Styles.TD_STYLE).with(
                        tr().with(
                                th(Constants.LDAP_NAME).withStyle(Styles.TH_STYLE)
                        ),
                        addUserWithoutLdapRows(getUsersWithoutLdap())
                ))
                .render();
        return html;
    }

    private DomContent addUserWithoutLdapRows(List<String> usersWithoutLdap) {
        usersWithoutLdap.sort(String::compareToIgnoreCase);
        return each(usersWithoutLdap, user -> tr(
                td(user).withStyle(Styles.TD_STYLE)
        ));
    }

    private List<String> getUsersWithoutLdap() throws NamingException, IOException {
        return  ldapScanningBean.getUsersWithoutLdapAccount();
    }
}
