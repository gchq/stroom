/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.search.elastic;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.search.elastic.ElasticEndToEndTestHelper.SearchResult;
import stroom.search.elastic.shared.ElasticClusterTestResponse;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.shared.http.HttpClientConfig;
import stroom.util.shared.http.HttpTlsConfig;

import jakarta.inject.Inject;
import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/// Connects to an Elasticsearch node with its security enabled, as most production clusters are, through an
/// Elastic Cluster document's API key and HTTP client settings. The node serves HTTPS with a certificate
/// signed by its own CA, so it is only trusted when that CA is given as the trust store. See
/// [ElasticTestNode#getTlsOrSkip()] for how the node is found.
class TestElasticTls_EndToEnd extends AbstractCoreIntegrationTest {

    // Resolves to a loopback address, but isn't a name the node's certificate is for
    private static final String OTHER_HOST_NAME = "localhost4";

    @Inject
    private ElasticEndToEndTestHelper helper;
    @Inject
    private ElasticClusterStore elasticClusterStore;
    @Inject
    private ElasticClusterResourceImpl elasticClusterResource;
    @Inject
    private ElasticConnectionResolver elasticConnectionResolver;

    private ElasticTestNode node;
    private String trustStoreName;
    private String apiKeyName;
    private final List<String> indexPatternsToDelete = new ArrayList<>();
    private final List<String> templatesToDelete = new ArrayList<>();

    @BeforeEach
    void setUp() {
        node = ElasticTestNode.getTlsOrSkip();
        final String suffix = UUID.randomUUID().toString().substring(0, 8);
        trustStoreName = helper.createTrustStoreCredential("Elasticsearch CA " + suffix, node.getCaCertificate());
        apiKeyName = helper.createApiKeyCredential(
                "Elasticsearch API key " + suffix,
                node.createApiKey("stroom-e2e-" + suffix));
    }

    @AfterEach
    void tearDown() {
        if (node != null) {
            indexPatternsToDelete.forEach(node::deleteIndices);
            templatesToDelete.forEach(node::deleteIndexTemplate);
        }
    }

    @Test
    void testCaTrustStoreAndApiKey() {
        final DocRef clusterRef = helper.createCluster(
                List.of(node.getUrl()),
                apiKeyName,
                httpClientConfig(tls().trustStoreName(trustStoreName)));

        final ElasticClusterTestResponse response = testCluster(clusterRef);
        assertThat(response.isOk())
                .as("Test connection: %s", response.getMessage())
                .isTrue();

        // Index with a pipeline, then search, both over the same connection
        final String indexBaseName = ElasticTestNode.createIndexName("tls");
        templatesToDelete.add(indexBaseName);
        indexPatternsToDelete.add(indexBaseName + "-*");
        helper.indexTypedEvents(node, clusterRef, indexBaseName);
        final DocRef indexRef = helper.createIndexDoc(clusterRef, indexBaseName + "-*", "Time", 100);

        final SearchResult result = helper.search(
                indexRef,
                ExpressionOperator.builder()
                        .addTerm("Host", Condition.EQUALS, "gamma")
                        .build(),
                List.of("Host", "Ip"));

        assertThat(result.errors()).isEmpty();
        assertThat(result.rows()).containsExactly(List.of("gamma", "192.168.1.1"));
    }

    @Test
    void testNoTrustStore() {
        // Without a trust store only the JVM's default CAs are trusted, which don't include the node's CA
        final DocRef clusterRef = helper.createCluster(List.of(node.getUrl()), apiKeyName, null);

        final ElasticClusterTestResponse response = testCluster(clusterRef);

        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("unable to find valid certification path");
    }

    @Test
    void testTrustSelfSignedCertificatesDoesNotTrustCaSignedCertificate() {
        // Turning off both checks still doesn't trust a certificate signed by a CA that isn't trusted, as only
        // a certificate presented on its own counts as self-signed. There is no setting that turns off
        // certificate verification, so the node's CA must be given as the trust store.
        final DocRef clusterRef = helper.createCluster(
                List.of(node.getUrl()),
                apiKeyName,
                httpClientConfig(tls()
                        .trustSelfSignedCertificates(true)
                        .verifyHostname(false)));

        final ElasticClusterTestResponse response = testCluster(clusterRef);

        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("unable to find valid certification path");
    }

    @Test
    void testHostnameMismatch() {
        final List<String> urls = List.of(urlWithOtherHostName());
        final DocRef clusterRef = helper.createCluster(
                urls,
                apiKeyName,
                httpClientConfig(tls().trustStoreName(trustStoreName)));

        final ElasticClusterTestResponse response = testCluster(clusterRef);

        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("doesn't match any of the subject alternative names");
    }

    @Test
    void testHostnameVerificationDisabled() {
        final List<String> urls = List.of(urlWithOtherHostName());
        final DocRef clusterRef = helper.createCluster(
                urls,
                apiKeyName,
                httpClientConfig(tls()
                        .trustStoreName(trustStoreName)
                        .verifyHostname(false)));

        final ElasticClusterTestResponse response = testCluster(clusterRef);

        assertThat(response.isOk())
                .as("Test connection: %s", response.getMessage())
                .isTrue();
    }

    @Test
    void testNoApiKey() {
        // The node is trusted, but rejects the request as it isn't authenticated
        final DocRef clusterRef = helper.createCluster(
                List.of(node.getUrl()),
                null,
                httpClientConfig(tls().trustStoreName(trustStoreName)));

        final ElasticClusterTestResponse response = testCluster(clusterRef);

        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("security_exception");
    }

    private ElasticClusterTestResponse testCluster(final DocRef clusterRef) {
        return elasticClusterResource.testCluster(elasticClusterStore.readDocument(clusterRef));
    }

    /// @return The default TLS settings, as the UI starts from.
    private HttpTlsConfig.Builder tls() {
        return elasticConnectionResolver.getDefaultHttpClientConfig()
                .getTls()
                .copy();
    }

    /// @return The default HTTP client settings, as the UI starts from, with the supplied TLS settings.
    private HttpClientConfig httpClientConfig(final HttpTlsConfig.Builder tls) {
        return elasticConnectionResolver.getDefaultHttpClientConfig()
                .copy()
                .tlsConfiguration(tls.build())
                .build();
    }

    /// @return The node's URL with a host name that resolves to the same (loopback) address, but which the
    /// node's certificate isn't for. Skips the test if there is no such host name.
    private String urlWithOtherHostName() {
        final URI uri = URI.create(node.getUrl());
        boolean isLoopback;
        try {
            isLoopback = InetAddress.getByName(OTHER_HOST_NAME).isLoopbackAddress()
                         && InetAddress.getByName(uri.getHost()).isLoopbackAddress();
        } catch (final UnknownHostException e) {
            isLoopback = false;
        }
        Assumptions.assumeThat(isLoopback)
                .as("%s and the node's host must both be loopback addresses", OTHER_HOST_NAME)
                .isTrue();
        return uri.getScheme() + "://" + OTHER_HOST_NAME + ":" + uri.getPort();
    }
}
