package org.jboss.set.mjolnir.archive.configuration;

import org.eclipse.egit.github.core.client.GitHubClient;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import javax.sql.DataSource;
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
    private final static String LDAP_URL = "ldap.url";
    private final static String LDAP_SEARCH_CONTEXT = "ldap.search_context";

    private Logger logger = Logger.getLogger(getClass());

    @Inject
    private DataSource dataSource;

    @Produces
    @ApplicationScoped
    public Configuration createConfiguration() {
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
                    case LDAP_URL:
                        configurationBuilder.setLdapUrl(value);
                        break;
                    case LDAP_SEARCH_CONTEXT:
                        configurationBuilder.setLdapSearchContext(value);
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

    @Produces
    public GitHubClient createGitHubClient(Configuration configuration) {
        GitHubClient gitHubClient = new GitHubClient();
        gitHubClient.setOAuth2Token(configuration.getGitHubToken());
        return gitHubClient;
    }

}
