package org.jboss.set.mjolnir.archive.mail.report;

import j2html.tags.DomContent;
import org.jboss.set.mjolnir.archive.domain.RegisteredUser;
import org.jboss.set.mjolnir.archive.domain.repositories.RegisteredUserRepositoryBean;
import org.jboss.set.mjolnir.archive.github.GitHubTeamServiceBean;
import org.jboss.set.mjolnir.archive.ldap.LdapDiscoveryBean;
import org.jboss.set.mjolnir.archive.ldap.LdapScanningBean;

import javax.inject.Inject;
import javax.naming.NamingException;
import javax.persistence.EntityManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static j2html.TagCreator.*;
import static j2html.TagCreator.th;

public class InvalidResponsiblePersonTable implements ReportTable {

    private static final String NAME_LABEL = "Name";
    private static final String RESPONSIBLE_PERSON_LABEL = "Responsible person";
    private static final String REPORT_TABLE_TITLE = "Responsible Person contains Inactive LDAP Account";

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
                table().withStyle(Styles.TABLE_STYLE + Styles.TD_STYLE).with(
                        tr().with(
                                th(NAME_LABEL).withStyle(Styles.TH_STYLE),
                                th(RESPONSIBLE_PERSON_LABEL).withStyle(Styles.TH_STYLE)
                        ),
                        addInvalidUserIds(getInvalidLdapUser())
                ))
                .render();
        return html;
    }

    private static DomContent addInvalidUserIds(List<RegisteredUser> invalidIds) {
        return each(invalidIds, user -> tr(
                td(ReportUtils.stringOrEmpty(user.getKerberosName())).withStyle(Styles.TD_STYLE),
                td(ReportUtils.stringOrEmpty(user.getResponsiblePerson())).withStyle(Styles.TD_STYLE)
        ));
    }

    public List<RegisteredUser> getInvalidLdapUser() throws NamingException {
        List<RegisteredUser> listOfUsersTable = userRepositoryBean.getAllUsers();
        Set<String> setOfResponsiblePerson = new HashSet<>();
        listOfUsersTable.forEach((e) -> {
            setOfResponsiblePerson.add(e.getResponsiblePerson());
        });

        Map<String, Boolean> userLdapMap = ldapDiscoveryBean.checkUsersExists(setOfResponsiblePerson);

        List<String> getInvalidUser = userLdapMap.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        List<RegisteredUser> listOfInvalidUser = new ArrayList<>();

        for (String str : getInvalidUser) {
            listOfUsersTable.stream().filter(x -> x.getResponsiblePerson().equals(str))
                    .forEach((e) -> {
                        RegisteredUser user = new RegisteredUser();
                        user.setKerberosName(e.getKerberosName());
                        user.setResponsiblePerson(e.getResponsiblePerson());
                        listOfInvalidUser.add(user);
                    });
        }
        return listOfInvalidUser;
    }

}
