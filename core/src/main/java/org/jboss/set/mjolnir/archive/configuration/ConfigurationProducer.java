package org.jboss.set.mjolnir.archive.configuration;

import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.client.IGitHubConstants;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.sql.DataSource;
import java.net.HttpURLConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Loads configuration from a database.
 */
public class ConfigurationProducer {

    private final static String GITHUB_TOKEN_KEY = "github.token";
    private final static String REPOSITORY_ARCHIVE_ROOT_KEY = "application.archive_root";
    private final static String UNSUBSCRIBE_USERS = "application.unsubscribe_users";
    private final static String REMOVE_USERS_WITHOUT_LDAP_ACCOUNT = "application.remove_users_without_ldap_account";
    private final static String REPORTING_EMAIL = "application.reporting_email";
    private final static String SENDER_EMAIL = "application.sender_email";
    private final static String LDAP_URL = "ldap.url";
    private final static String LDAP_SEARCH_CONTEXT = "ldap.search_context";
    private final static String REMOVE_ARCHIVES = "application.remove_archives";
    private final static String REMOVE_ARCHIVES_AFTER= "application.remove_archives_after";

    private final Logger logger = Logger.getLogger(getClass());

    @Produces
    @ApplicationScoped
    public Configuration createConfiguration(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement stmt = connection.prepareStatement("select param_name, param_value from application_parameters");
            ResultSet resultSet = stmt.executeQuery();

            Configuration.ConfigurationBuilder configurationBuilder = new Configuration.ConfigurationBuilder();

            while (resultSet.next()) {
                String name = resultSet.getString("param_name");
                String value = resultSet.getString("param_value");
                switch (name) {
                    case GITHUB_TOKEN_KEY:
                        configurationBuilder.setGitHubToken(value);
                        break;
                    case REPOSITORY_ARCHIVE_ROOT_KEY:
                        configurationBuilder.setRepositoryArchiveRoot(value);
                        break;
                    case UNSUBSCRIBE_USERS:
                        boolean boolValue = Boolean.parseBoolean(value);
                        configurationBuilder.setUnsubscribeUsers(boolValue);
                        break;
                    case REMOVE_USERS_WITHOUT_LDAP_ACCOUNT:
                        configurationBuilder.setRemoveUsersWithoutLdapAccount(Boolean.parseBoolean(value));
                        break;
                    case REPORTING_EMAIL:
                        configurationBuilder.setReportingEmail(value);
                        break;
                    case SENDER_EMAIL:
                        configurationBuilder.setSenderEmail(value);
                        break;
                    case LDAP_URL:
                        configurationBuilder.setLdapUrl(value);
                        break;
                    case LDAP_SEARCH_CONTEXT:
                        configurationBuilder.setLdapSearchContext(value);
                        break;
                    case REMOVE_ARCHIVES:
                        configurationBuilder.setRemoveArchives(Boolean.parseBoolean(value));
                        break;
                    case REMOVE_ARCHIVES_AFTER:
                        configurationBuilder.setRemoveArchivesAfter(Integer.parseInt(value));
                        break;
                    default:
                        logger.infof("Skipping configuration parameter %s", name);
                }
            }

            return configurationBuilder.build();
        } catch (SQLException e) {
            throw new RuntimeException("Can't connect to database", e);
        }
    }

    /**
     * Default GitHubClient producer method, used by application.
     */
    @Produces
    public GitHubClient createGitHubClient(Configuration configuration) {
        return createGitHubClient(configuration, IGitHubConstants.HOST_API, -1, IGitHubConstants.PROTOCOL_HTTPS);
    }

    /**
     * More parameter-rich factory method, used in tests.
     */
    public GitHubClient createGitHubClient(Configuration configuration, String hostname, int port, String scheme) {
        GitHubClient gitHubClient = new GitHubClient(hostname, port, scheme) {
            @Override
            protected HttpURLConnection configureRequest(HttpURLConnection request) {
                HttpURLConnection conn = super.configureRequest(request);

                // configure timeouts
                conn.setConnectTimeout(configuration.getConnectTimeout());
                conn.setReadTimeout(configuration.getReadTimeout());

                return conn;
            }
        };
        gitHubClient.setOAuth2Token(configuration.getGitHubToken());
        return gitHubClient;
    }

}
