package org.jboss.set.mjolnir.archive.util;

import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

public final class TestUtils {

    private TestUtils() {
    }

    /**
     * Prepares GitHub API stubs.
     *
     * The test case need to contain @Rule annotated WireMockRule field.
     */
    public static void setupGitHubApiStubs() throws IOException, URISyntaxException {
        stubFor(get(urlPathEqualTo("/api/v3/orgs/testorg/repos"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(readSampleResponse("responses/gh-orgs-repos-response.json"))));

        stubFor(get(urlPathMatching("/api/v3/repos/testorg/aphrodite/forks"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(readSampleResponse("responses/gh-repos-forks-aphrodite-response.json"))));

        stubFor(get(urlPathMatching("/api/v3/repos/testorg/activemq-artemis/forks"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(readSampleResponse("responses/gh-repos-forks-artemis-response.json"))));
    }

    public static String readSampleResponse(String responseFileName) throws URISyntaxException, IOException {
        URL resource = Thread.currentThread().getContextClassLoader().getResource(responseFileName);

        if (resource == null) {
            throw new RuntimeException();
        }
        return FileUtils.readFileToString(
                Paths.get(resource.toURI()).toFile(),
                StandardCharsets.UTF_8.name());
    }
}
