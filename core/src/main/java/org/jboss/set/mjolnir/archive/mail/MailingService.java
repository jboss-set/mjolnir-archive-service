package org.jboss.set.mjolnir.archive.mail;

import javax.annotation.Resource;
import javax.enterprise.context.ApplicationScoped;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.*;

@ApplicationScoped
public class MailingService {

    @Resource (mappedName="java:jboss/mail/Default")
    private Session mailSession;

    public void send(String fromAddress, String toAddress, String subject, String body) throws MessagingException {
        MimeBodyPart mailMessage = new MimeBodyPart();
        mailMessage.setContent(body, "text/html; charset=utf-8");

        MimeMultipart mailContent = new MimeMultipart();
        mailContent.addBodyPart(mailMessage);

        Message message = new MimeMessage(mailSession);
        message.setFrom(new InternetAddress(fromAddress));
        message.addRecipient(Message.RecipientType.TO, new InternetAddress(toAddress));
        message.setSubject(subject);
        message.setContent(mailContent);

        Transport.send(message);
    }
}
