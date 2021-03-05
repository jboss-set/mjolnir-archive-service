package org.jboss.set.mjolnir.archive.util;

import org.eclipse.egit.github.core.client.GitHubClient;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.jboss.set.mjolnir.archive.ArchivingBean;
import org.jboss.set.mjolnir.archive.configuration.Configuration;
import org.jboss.set.mjolnir.archive.ldap.LdapClientBean;
import org.mockito.Mockito;

import javax.enterprise.inject.Alternative;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * Produces CDI beans for unit testing purposes.
 *
 * Normally, some of these beans are provided by an application container, while for other we want to provide mocks
 * or stubs for integration testing.
 */
@Alternative
public class IntegrationTestProducer {

    @Produces
    @Singleton
    EntityManagerFactory createEntityManagerFactory() {
        Map<String, String> properties = new HashMap<>();
        properties.put("javax.persistence.transactionType", "RESOURCE_LOCAL");
        properties.put("hibernate.show_sql", "true");
        return new HibernatePersistenceProvider().createEntityManagerFactory("mjolnir-archive-service", properties);
    }

    @SuppressWarnings("unused")
    public void closeEntityManagerFactory(@Disposes EntityManagerFactory emf) {
        if (emf.isOpen()) {
            emf.close();
        }
    }

    @Produces
    @Singleton
    public EntityManager createEntityManager(EntityManagerFactory emf) {
        return emf.createEntityManager();
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
     * TODO: Make ConfigurationProducer use EntityManager instead of DataSource and then we can remove this.
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
                .setRemoveArchives(true)
                .setUnsubscribeUsers(true)
                .setRepositoryArchiveRoot("/tmp/mjolnir-repository-archive-integration-tests")
                .build();
    }

    @Produces
    public GitHubClient createGitHubClient(Configuration configuration) {
        return new GitHubClient(configuration.getGitHubApiHost(),
                configuration.getGitHubApiPort(),
                configuration.getGitHubApiScheme());
    }

    // mock following beans for integration tests

    @Produces
    @Singleton
    public ArchivingBean createArchivingBeanMock() {
        return Mockito.mock(ArchivingBean.class);
    }

    @Produces
    @Singleton
    public LdapClientBean createLdapDiscoveryBeanMock() {
        return Mockito.mock(LdapClientBean.class);
    }

}
