package org.jboss.set.mjolnir.archive.mail.report;

import org.assertj.core.api.Assertions;
import org.jboss.set.mjolnir.archive.UserDiscoveryBean;
import org.jboss.set.mjolnir.archive.domain.RegisteredUser;
import org.junit.Test;
import org.mockito.Mockito;

import javax.naming.NamingException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class InvalidGitHubUsersReportTableTestCase {

    @Test
    public void test() throws IOException, NamingException {
        RegisteredUser user1 = new RegisteredUser();
        user1.setKerberosName("user-1");
        user1.setGithubName("gh-name-1");

        RegisteredUser user2 = new RegisteredUser();
        user2.setKerberosName("user-2");
        user2.setGithubName("gh-name-2");

        RegisteredUser user3 = new RegisteredUser();
        user3.setKerberosName("user-3");
        user3.setGithubName(null);

        RegisteredUser user4 = new RegisteredUser();
        user4.setKerberosName("user-4");
        user4.setGithubName(null);

        List<RegisteredUser> sampleUsers = Arrays.asList(user1, user2, user3, user4);

        UserDiscoveryBean discoveryBeanMock = Mockito.mock(UserDiscoveryBean.class);
        Mockito.when(discoveryBeanMock.findInvalidGithubUsers()).thenReturn(sampleUsers);

        InvalidGitHubUsersReportTable table = new InvalidGitHubUsersReportTable();
        table.userDiscoveryBean = discoveryBeanMock;

        String tableString = table.composeTable();
        Assertions.assertThat(tableString).contains(Arrays.asList("gh-name-1", "gh-name-2"));
        Assertions.assertThat(tableString).contains(Arrays.asList("user-1", "user-2", "user-3"));
    }
}
