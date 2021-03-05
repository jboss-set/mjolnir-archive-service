package org.jboss.set.mjolnir.archive.util;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Collection;
import java.util.HashMap;

public class MockitoAnswers {

    /**
     * Mock answer to `LdapDiscoveryBean#checkUsersExists()` calls.
     */
    public static class UsersNotInLdapAnswer implements Answer<Object> {
        @Override
        public Object answer(InvocationOnMock invocationOnMock) {
            Collection<String> ldapUsernames = invocationOnMock.getArgument(0);
            HashMap<String, Boolean> result = new HashMap<>();
            ldapUsernames.forEach(username -> result.put(username, false));
            return result;
        }
    }

}
