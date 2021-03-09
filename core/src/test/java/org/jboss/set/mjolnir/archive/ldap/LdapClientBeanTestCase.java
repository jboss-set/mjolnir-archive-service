package org.jboss.set.mjolnir.archive.ldap;

import org.jboss.set.mjolnir.archive.configuration.Configuration;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.SearchResult;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class LdapClientBeanTestCase {

    private final Configuration configuration = new Configuration.ConfigurationBuilder()
            .setLdapSearchContext("context")
            .build();
    private final LdapClient ldapClientMock = Mockito.mock(LdapClient.class);
    private final LdapClientBean ldapClientBean = new LdapClientBean(configuration, ldapClientMock);

    private final ArgumentCaptor<String> baseCaptor = ArgumentCaptor.forClass(String.class);
    private final ArgumentCaptor<String> filterCaptor = ArgumentCaptor.forClass(String.class);

    @Test
    public void testCheckUserExistsPriorUids() throws Exception {
        Mockito.when(ldapClientMock.search(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(new ResultEnumeration(Collections.emptyIterator()));

        assertThat(ldapClientBean.checkUserExists("tom")).isFalse();

        Mockito.verify(ldapClientMock).search(baseCaptor.capture(), filterCaptor.capture());
        assertThat(baseCaptor.getValue()).isEqualTo("context");
        assertThat(filterCaptor.getValue()).isEqualTo("(|(uid=tom)(rhatPriorUid=tom))");
    }

    @Test
    public void testCheckUsersExistsPriorUids() throws Exception {
        // mock ldap client
        List<SearchResult> results = Arrays.asList(
                createSearchResult("alice"),
                createSearchResult("bob", "robert"),
                createSearchResult("jim", "james", "jimmy")
        );
        ResultEnumeration resultEnumeration = new ResultEnumeration(results.iterator());
        Mockito.when(ldapClientMock.search(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(resultEnumeration);

        // method call
        LinkedHashSet<String> ldapNames = new LinkedHashSet<>();
        ldapNames.add("alice");
        ldapNames.add("bob");
        ldapNames.add("james"); // using prior uid instead of current uid
        ldapNames.add("tom");

        Map<String, Boolean> existingUsersMap = ldapClientBean.checkUsersExists(ldapNames);

        // verify resulting map
        assertThat(existingUsersMap.get("alice")).isTrue();
        assertThat(existingUsersMap.get("bob")).isTrue();
        assertThat(existingUsersMap.get("robert")).isTrue();
        assertThat(existingUsersMap.get("jim")).isTrue();
        assertThat(existingUsersMap.get("james")).isTrue();
        assertThat(existingUsersMap.get("jimmy")).isTrue();
        assertThat(existingUsersMap.get("tom")).isFalse();

        // verify call to ldapClient
        Mockito.verify(ldapClientMock).search(baseCaptor.capture(), filterCaptor.capture());
        assertThat(baseCaptor.getValue()).isEqualTo("context");
        assertThat(filterCaptor.getValue())
                .contains("(|(uid=alice)(rhatPriorUid=alice))")
                .contains("(|(uid=bob)(rhatPriorUid=bob))")
                .contains("(|(uid=james)(rhatPriorUid=james))")
                .contains("(|(uid=tom)(rhatPriorUid=tom))");
    }

    private SearchResult createSearchResult(String uid, String... priorUids) {
        Attributes attrs = new BasicAttributes();
        attrs.put("uid", uid);

        BasicAttribute priorUidAttr = new BasicAttribute("rhatPriorUid");
        for (String priorUid : priorUids) {
            priorUidAttr.add(priorUid);
            attrs.put(priorUidAttr);
        }

        return new SearchResult(uid, null, attrs);
    }

    private static class ResultEnumeration implements NamingEnumeration<SearchResult> {

        private final Iterator<SearchResult> iterator;

        public ResultEnumeration(Iterator<SearchResult> iterator) {
            this.iterator = iterator;
        }

        @Override
        public SearchResult next() {
            return iterator.next();
        }

        @Override
        public boolean hasMore() {
            return iterator.hasNext();
        }

        @Override
        public void close() {
            // pass
        }

        @Override
        public boolean hasMoreElements() {
            return iterator.hasNext();
        }

        @Override
        public SearchResult nextElement() {
            return iterator.next();
        }
    }
}
