package org.jboss.set.mjolnir.archive.util;

import org.eclipse.egit.github.core.client.GitHubClient;
import org.jboss.set.mjolnir.archive.ArchivingBean;
import org.jboss.set.mjolnir.archive.configuration.Configuration;
import org.jboss.set.mjolnir.archive.ldap.LdapDiscoveryBean;
import org.mockito.Mockito;

import javax.enterprise.inject.Alternative;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.Persistence;
import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * Produces beans for unit testing purposes.
 */
@Alternative
public class UnitTestProducer {

    @Produces
    @Singleton
    public EntityManager createEntityManager() {
        Map<String, String> properties = new HashMap<>();
        properties.put("javax.persistence.transactionType", "RESOURCE_LOCAL");
        properties.put("hibernate.show_sql", "true");
        return Persistence.createEntityManagerFactory("mjolnir-archive-service", properties).createEntityManager();
    }

    @SuppressWarnings("unused")
    public void closeEntityManager(@Disposes EntityManager em) {
        if (em.isOpen()) {
            em.close();
        }
    }

    /**
     * This is only needed because the DataSource injection is required by ConfigurationProducer.
     *
     * TODO: make ConfigurationProducer use EntityManager instead and remove this?
     */
    @Produces
    public DataSource createDatasource() {
        return Mockito.mock(DataSource.class);
    }

    @Produces
    public Configuration createConfiguration() throws URISyntaxException {
        return new Configuration.ConfigurationBuilder()
                .setGitHubToken("")
                .setGitHubApiUri(new URI("http://localhost:8089"))
                .setRemoveUsersWithoutLdapAccount(true)
                .build();
    }

    @Produces
    public GitHubClient createGitHubClient(Configuration configuration) {
        return new GitHubClient(configuration.getGitHubApiHost(),
                configuration.getGitHubApiPort(),
                configuration.getGitHubApiScheme());
    }

    // mock following beans for CDI tests

    @Produces
    @Singleton
    public ArchivingBean createArchivingBeanMock() {
        return Mockito.mock(ArchivingBean.class);
    }

    @Produces
    @Singleton
    public LdapDiscoveryBean createLdapDiscoveryBeanMock() {
        return Mockito.mock(LdapDiscoveryBean.class);
    }

}
