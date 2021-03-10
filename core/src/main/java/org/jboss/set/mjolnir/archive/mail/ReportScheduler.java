package org.jboss.set.mjolnir.archive.mail;

import org.jboss.logging.Logger;
import org.jboss.set.mjolnir.archive.configuration.Configuration;
import org.jboss.set.mjolnir.archive.mail.report.InvalidResponsiblePersonTable;
import org.jboss.set.mjolnir.archive.mail.report.RemovalsReportTable;
import org.jboss.set.mjolnir.archive.mail.report.ReportTable;
import org.jboss.set.mjolnir.archive.mail.report.UnregisteredMembersReportTable;
import org.jboss.set.mjolnir.archive.mail.report.UsersWithoutLdapReportTable;
import org.jboss.set.mjolnir.archive.mail.report.WhitelistedUsersReportTable;

import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.TransactionManagement;
import javax.ejb.TransactionManagementType;
import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.naming.NamingException;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

@Singleton
@TransactionManagement(TransactionManagementType.BEAN) // do not open managed transaction
public class ReportScheduler {

    private Logger logger = Logger.getLogger(getClass());

    private static final String SUBJECT = "User removals report ";

    @Inject
    private Configuration configuration;

    @Inject
    private MailingService mailingService;

    @Inject
    private MailBodyMessageProducer mailBodyMessageProducer;

    @Inject
    private RemovalsReportTable removalsReportTable;

    @Inject
    private UsersWithoutLdapReportTable usersWithoutLdapReportTable;

    @Inject
    private WhitelistedUsersReportTable whitelistedUsersReportTable;

    @Inject
    private UnregisteredMembersReportTable unregisteredMembersReportTable;

    @Inject
    private InvalidResponsiblePersonTable invalidUserId;

    @Schedule(dayOfWeek="Sun", hour="0", persistent = false)
    public void sendMail() throws IOException, NamingException {
        String fromAddress = configuration.getReportingEmail();
        String toAddress = configuration.getReportingEmail();

        SimpleDateFormat noMillisFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        String subject = SUBJECT + noMillisFormat.format(new Timestamp(System.currentTimeMillis()));

        List<ReportTable> reportTables = new ArrayList<>();
        reportTables.add(removalsReportTable);
        reportTables.add(unregisteredMembersReportTable);
        reportTables.add(usersWithoutLdapReportTable);
        reportTables.add(whitelistedUsersReportTable);
        reportTables.add(invalidUserId);

        String body = mailBodyMessageProducer.composeMessageBody(reportTables);

        try {
            mailingService.send(fromAddress, toAddress, subject, body);
            logger.infof("Report email sent successfully");
        } catch (MessagingException e) {
            logger.errorf(e, "Failure of report email sending");
        }
    }
}
