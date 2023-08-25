package org.jboss.set.mjolnir.archive.configuration;

import javax.enterprise.inject.Vetoed;
import java.net.URI;

@Vetoed
public class Configuration {

    private String gitHubToken;
    private String gitHubApiHost;
    private Integer gitHubApiPort;
    private String gitHubApiScheme;
    private String repositoryArchiveRoot;
    private String reportingEmail;
    private String senderEmail;
    private String ldapUrl;
    private String ldapSearchContext;
    private boolean unsubscribeUsers = false;
    private boolean removeUsersWithoutLdapAccount = false;
    private boolean removeArchives = false;
    private int removeArchivesAfter = 90;
    private int connectTimeout = 5 * 60 * 1000;
    private int readTimeout = 5 * 60 * 1000;

    public Configuration() {
    }

    public String getGitHubToken() {
        return gitHubToken;
    }

    public String getGitHubApiHost() {
        return gitHubApiHost;
    }

    public Integer getGitHubApiPort() {
        return gitHubApiPort;
    }

    public String getGitHubApiScheme() {
        return gitHubApiScheme;
    }

    /**
     * Path to the archive directory where user repositories are to be archived.
     */
    public String getRepositoryArchiveRoot() {
        return repositoryArchiveRoot;
    }

    /**
     * Email address where reporting emails will be sent to.
     */
    public String getReportingEmail() {
        return reportingEmail;
    }

    /**
     * Email address to use as a sender address.
     */
    public String getSenderEmail() {
        return senderEmail;
    }

    public String getLdapUrl() {
        return ldapUrl;
    }

    public String getLdapSearchContext() {
        return ldapSearchContext;
    }

    public boolean getRemoveArchives() {
        return removeArchives;
    }

    public int getRemoveArchivesAfter() {
        return removeArchivesAfter;
    }

    /**
     * If true, processed users will be really unsubscribed from the GitHub teams. If false, users will be just
     * reported (user repositories will still be archived).
     */
    public boolean isUnsubscribeUsers() {
        return unsubscribeUsers;
    }

    public boolean isRemoveUsersWithoutLdapAccount() {
        return removeUsersWithoutLdapAccount;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public static class ConfigurationBuilder {

        private final Configuration configuration = new Configuration();

        public ConfigurationBuilder setGitHubToken(String gitHubToken) {
            this.configuration.gitHubToken = gitHubToken;
            return this;
        }

        public ConfigurationBuilder setGitHubApiUri(URI uri) {
            this.configuration.gitHubApiHost = uri.getHost();
            this.configuration.gitHubApiPort = uri.getPort();
            this.configuration.gitHubApiScheme = uri.getScheme();
            return this;
        }

        public ConfigurationBuilder setRepositoryArchiveRoot(String repositoryArchiveRoot) {
            this.configuration.repositoryArchiveRoot = repositoryArchiveRoot;
            return this;
        }

        public ConfigurationBuilder setReportingEmail(String reportingEmail) {
            this.configuration.reportingEmail = reportingEmail;
            return this;
        }

        public ConfigurationBuilder setSenderEmail(String senderEmail) {
            this.configuration.senderEmail = senderEmail;
            return this;
        }

        public ConfigurationBuilder setUnsubscribeUsers(boolean unsubscribeUsers) {
            this.configuration.unsubscribeUsers = unsubscribeUsers;
            return this;
        }

        public ConfigurationBuilder setRemoveUsersWithoutLdapAccount(boolean removeUsersWithoutLdapAccount) {
            this.configuration.removeUsersWithoutLdapAccount = removeUsersWithoutLdapAccount;
            return this;
        }

        public ConfigurationBuilder setLdapUrl(String url) {
            this.configuration.ldapUrl = url;
            return this;
        }

        public ConfigurationBuilder setLdapSearchContext(String context) {
            this.configuration.ldapSearchContext = context;
            return this;
        }

        public ConfigurationBuilder setRemoveArchives(boolean removeArchives) {
            this.configuration.removeArchives = removeArchives;
            return this;
        }

        public ConfigurationBuilder setRemoveArchivesAfter(int days) {
            this.configuration.removeArchivesAfter = days;
            return this;
        }

        public ConfigurationBuilder setConnectTimeout(int connectTimeout) {
            this.configuration.connectTimeout = connectTimeout;
            return this;
        }

        public ConfigurationBuilder setReadTimeout(int readTimeout) {
            this.configuration.readTimeout = readTimeout;
            return this;
        }

        public Configuration build() {
            return configuration;
        }
    }
}
