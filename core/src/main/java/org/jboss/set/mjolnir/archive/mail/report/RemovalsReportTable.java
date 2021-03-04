package org.jboss.set.mjolnir.archive.mail.report;

import j2html.tags.DomContent;
import org.jboss.set.mjolnir.archive.domain.RemovalStatus;
import org.jboss.set.mjolnir.archive.domain.UserRemoval;
import org.jboss.set.mjolnir.archive.mail.RemovalsReportBean;

import javax.inject.Inject;
import java.text.SimpleDateFormat;
import java.util.List;

import static j2html.TagCreator.*;
import static j2html.TagCreator.th;

/**
 * Prints a list of removals performed durting the last week.
 */
public class RemovalsReportTable implements ReportTable {

    @Inject
    private RemovalsReportBean removalsReportBean;

    @Override
    public String composeTable() {
        String html = div().with(
                h2("User Removals").withStyle(Styles.H2_STYLE),
                p("...performed during the last week").withStyle(Styles.SUB_HEADING_STYLE),
                table().withStyle(Styles.TABLE_STYLE + Styles.TD_STYLE).with(
                        tr().with(
                                th(Constants.LDAP_NAME).withStyle(Styles.TH_STYLE),
                                th(Constants.GH_NAME).withStyle(Styles.TH_STYLE),
                                th(Constants.CREATED).withStyle(Styles.TH_STYLE),
                                th(Constants.STARTED).withStyle(Styles.TH_STYLE),
                                th(Constants.STATUS).withStyle(Styles.TH_STYLE)
                        ),
                        addUserRemovalRows(getRemovals())
                )).render();
        return html;
    }

    private <T> DomContent addUserRemovalRows(List<UserRemoval> removals) {
        SimpleDateFormat noMillisFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

        return each(removals, removal -> tr(
                td(ReportUtils.stringOrEmpty(removal.getLdapUsername())).withStyle(Styles.TD_STYLE),
                td(ReportUtils.stringOrEmpty(removal.getGithubUsername())).withStyle(Styles.TD_STYLE),
                td(noMillisFormat.format(removal.getCreated())).withStyle(Styles.TD_STYLE),
                td(noMillisFormat.format(removal.getStarted())).withStyle(Styles.TD_STYLE),
                RemovalStatus.COMPLETED.equals(removal.getStatus()) ?
                        td(removal.getStatus().toString()).withStyle(Styles.TD_STYLE + Styles.FONT_SUCCESS_STYLE) :
                        td(removal.getStatus().toString()).withStyle(Styles.TD_STYLE + Styles.FONT_ERROR_STYLE)
        ));
    }

    private List<UserRemoval> getRemovals() {
        return removalsReportBean.getLastFinishedRemovals();
    }
}
