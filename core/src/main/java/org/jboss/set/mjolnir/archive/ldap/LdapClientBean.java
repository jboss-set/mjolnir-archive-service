package org.jboss.set.mjolnir.archive.ldap;

import org.jboss.set.mjolnir.archive.configuration.Configuration;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.SearchResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Provides querying of LDAP directory server.
 */
public class LdapClientBean {

    private static final int GROUPING_FACTOR = 50; // query for that many users at a time

    @Inject
    private Configuration configuration;

    private LdapClient ldapClient;

    @SuppressWarnings("unused")
    public LdapClientBean() {
    }

    LdapClientBean(Configuration configuration, LdapClient ldapClient) {
        this.configuration = configuration;
        this.ldapClient = ldapClient;
    }

    @PostConstruct
    public void init() {
        // fetch ldap url
        final String ldapUrl = configuration.getLdapUrl();
        ldapClient = new LdapClient(ldapUrl);
    }

    /**
     * Verifies that given UID exists in LDAP database.
     */
    public boolean checkUserExists(String uid) throws NamingException {
        final NamingEnumeration<SearchResult> results =
                ldapClient.search(configuration.getLdapSearchContext(),
                        "(|(uid=" + uid + ")(rhatPriorUid=" + uid + "))");
        try {
            return results.hasMore();
        } finally {
            results.close();
        }
    }

    /**
     * Returns list of user UIDs - i.e. his current UID and all his prior UIDs.
     * @param uid prior or current UID
     * @return list of current and prior UIDs
     */
    public List<String> findAllUserUids(String uid) throws NamingException {
        final NamingEnumeration<SearchResult> results =
                ldapClient.search(configuration.getLdapSearchContext(),
                        "(|(uid=" + uid + ")(rhatPriorUid=" + uid + "))");
        try {
            if (results.hasMore()) {
                ArrayList<String> uids = new ArrayList<>();

                SearchResult searchResult = results.next();
                String currentUid = (String) searchResult.getAttributes().get("uid").get();
                uids.add(currentUid);

                // add user's prior UIDs to the map of existing users
                Attribute priorUidAttr = searchResult.getAttributes().get("rhatPriorUid");
                if (priorUidAttr != null) {
                    NamingEnumeration<?> priorUids = priorUidAttr.getAll();
                    while (priorUids.hasMore()) {
                        String priorUid = (String) priorUids.next();
                        uids.add(priorUid);
                    }
                }

                return uids;
            } else {
                return Collections.emptyList();
            }
        } finally {
            results.close();
        }
    }

    /**
     * Verifies which of given UIDs exists in LDAP database.
     *
     * Given names are looked for either in the uid or rhatPriorUid attributes. If an existing user has some rhatPriorUid
     * attributes, the resulting map will contain his uid and all his rhatPriorUid values.
     *
     * @param users list of users to check
     * @return map where keys are UIDs and values are booleans indicating if given UID exists or not
     */
    public Map<String, Boolean> checkUsersExists(Collection<String> users) throws NamingException {
        final Map<String, Boolean> result = new HashMap<>();
        final Iterator<String> iterator = users.iterator();
        final List<String> tempUserList = new ArrayList<>(GROUPING_FACTOR);
        while (iterator.hasNext()) {
            tempUserList.add(iterator.next());
            if (tempUserList.size() >= GROUPING_FACTOR || !iterator.hasNext()) {
                final Map<String, Boolean> tempResultMap = checkUsersSubsetExists(tempUserList);
                result.putAll(tempResultMap);
                tempUserList.clear();
            }
        }
        return result;
    }

    private Map<String, Boolean> checkUsersSubsetExists(List<String> users) throws NamingException {
        // build a query
        final StringBuilder query = new StringBuilder("(|");
        for (String uid : users) {
            query.append("(|(uid=")
                    .append(uid)
                    .append(")(rhatPriorUid=")
                    .append(uid)
                    .append("))");
        }
        query.append(")");

        final NamingEnumeration<SearchResult> searchResults =
                ldapClient.search(configuration.getLdapSearchContext(), query.toString());

        final Map<String, Boolean> result = new HashMap<>();
        try {
            // fill the result map with found users
            while (searchResults.hasMore()) {
                final SearchResult record = searchResults.next();

                // add user's UID to the map of existing users
                String uid = (String) record.getAttributes().get("uid").get();
                result.put(uid, true);

                // add user's prior UIDs to the map of existing users
                Attribute priorUidAttr = record.getAttributes().get("rhatPriorUid");
                if (priorUidAttr != null) {
                    NamingEnumeration<?> priorUids = priorUidAttr.getAll();
                    while (priorUids.hasMore()) {
                        String priorUid = (String) priorUids.next();
                        result.put(priorUid, true);
                    }
                }
            }
        } finally {
            searchResults.close();
        }

        // fill the result map with users that weren't found
        for (String uid : users) {
            if (!result.containsKey(uid)) {
                result.put(uid, false);
            }
        }

        return result;
    }

}
