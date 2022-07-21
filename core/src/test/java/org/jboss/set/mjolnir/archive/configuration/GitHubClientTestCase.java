package org.jboss.set.mjolnir.archive.configuration;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.client.GitHubRequest;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

public class GitHubClientTestCase {

    private static final int PORT = 8089;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(PORT);

    /**
     * Verifies that the GitHubClient times out after specified period.
     */
    @Test(expected = SocketTimeoutException.class)
    public void testTimeout() throws IOException {
        // wiremock config
        stubFor(get(urlEqualTo("/api/v3/delay")).willReturn(
                aResponse()
                        .withBody("{a:'b'}")
                        .withStatus(200)
                        .withFixedDelay(500))); // wiremock endpoint responds after 500 ms delay

        // assemble the client
        ConfigurationProducer configurationProducer = new ConfigurationProducer();
        Configuration configuration = new Configuration.ConfigurationBuilder()
                .setConnectTimeout(200)
                .setReadTimeout(200) // 200 ms timeout is set to the client
                .build();
        GitHubClient gitHubClient = configurationProducer.createGitHubClient(configuration, "localhost", PORT, "http");

        // request invocation
        GitHubRequest request = new GitHubRequest();
        request.setUri("/delay");
        InputStream stream = gitHubClient.getStream(request);
        stream.read(); // should throw SocketTimeoutException
        Assert.fail("Request expected to timeout.");
    }
}
