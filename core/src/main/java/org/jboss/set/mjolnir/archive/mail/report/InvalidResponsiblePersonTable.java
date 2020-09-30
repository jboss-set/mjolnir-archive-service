package org.jboss.set.mjolnir.archive.mail.report;

import j2html.tags.DomContent;
import org.apache.commons.lang3.StringUtils;
import org.jboss.set.mjolnir.archive.domain.RegisteredUser;
import org.jboss.set.mjolnir.archive.domain.repositories.RegisteredUserRepositoryBean;
import org.jboss.set.mjolnir.archive.ldap.LdapDiscoveryBean;
import org.jboss.set.mjolnir.archive.ldap.LdapScanningBean;

import javax.inject.Inject;
import javax.naming.NamingException;
import javax.persistence.EntityManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;
import static j2html.TagCreator.th;

public class InvalidResponsiblePersonTable implements ReportTable {

    private static final String REPORT_TABLE_TITLE = "Invalid Responsible Persons";

    @Inject
    LdapScanningBean ldapScanningBean;

    @Inject
    private EntityManager em;

    @Inject
    private LdapDiscoveryBean ldapDiscoveryBean;

    @Inject
    private RegisteredUserRepositoryBean userRepositoryBean;

    @Override
    public String composeTable() throws IOException, NamingException {
        String html = div().with(
                h2(REPORT_TABLE_TITLE).withStyle(Styles.H2_STYLE),
                p("Responsible person is not an LDAP name of an existing user.").withStyle(Styles.SUB_HEADING_STYLE),
                table().withStyle(Styles.TABLE_STYLE + Styles.TD_STYLE).with(
                        tr().with(
                                th(Constants.LDAP_NAME).withStyle(Styles.TH_STYLE),
                                th(Constants.GH_NAME).withStyle(Styles.TH_STYLE),
                                th(Constants.RESPONSIBLE_PERSON).withStyle(Styles.TH_STYLE)
                        ),
                        addInvalidUserIds(getInvalidLdapUser())
                ))
                .render();
        return html;
    }

    private static DomContent addInvalidUserIds(List<RegisteredUser> invalidIds) {
        return each(invalidIds, user -> tr(
                td(ReportUtils.stringOrEmpty(user.getKerberosName())).withStyle(Styles.TD_STYLE),
                td(ReportUtils.stringOrEmpty(user.getGithubName())).withStyle(Styles.TD_STYLE),
                td(ReportUtils.stringOrEmpty(user.getResponsiblePerson())).withStyle(Styles.TD_STYLE)
        ));
    }

    public List<RegisteredUser> getInvalidLdapUser() throws NamingException {
        List<RegisteredUser> listOfUsersTable = userRepositoryBean.getAllUsers();
        Set<String> setOfResponsiblePersons = listOfUsersTable.stream()
                .map(RegisteredUser::getResponsiblePerson)
                .filter(StringUtils::isNoneBlank)
                .collect(Collectors.toSet());

        Map<String, Boolean> userLdapMap = ldapDiscoveryBean.checkUsersExists(setOfResponsiblePersons);

        List<String> invalidResponsiblePersons = userLdapMap.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<RegisteredUser> listOfInvalidUsers = new ArrayList<>();

        for (String str : invalidResponsiblePersons) {
            listOfUsersTable.stream()
                    .filter(x -> StringUtils.equals(x.getResponsiblePerson(), str))
                    .forEach(listOfInvalidUsers::add);
        }
        return listOfInvalidUsers;
    }

}
