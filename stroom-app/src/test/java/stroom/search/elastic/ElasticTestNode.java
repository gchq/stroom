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

import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.assertj.core.api.Assumptions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import static org.assertj.core.api.Assertions.assertThat;

/// A real Elasticsearch node that the Elasticsearch end-to-end tests index to and search. The tests use it
/// directly, outside Stroom, to create indices with explicit mappings and to check what Stroom indexed.
///
/// The node is normally the `elasticsearch` service of stroom-resources. Its URL is taken from the
/// [#URL_ENV_VAR] environment variable, defaulting to [#DEFAULT_URL]. If the node can't be reached the tests
/// are skipped, unless [#REQUIRED_ENV_VAR] is `true` (as it should be in CI), in which case they fail.
///
/// The TLS tests use a second node, with Elasticsearch's security enabled, so that it serves HTTPS with a
/// certificate signed by its own CA and needs authentication. See [#getTlsOrSkip()].
public final class ElasticTestNode {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticTestNode.class);

    /// The environment variable holding the base URL of the node.
    public static final String URL_ENV_VAR = "STROOM_TEST_ELASTIC_URL";
    /// The environment variable that, when `true`, makes an unreachable node fail the tests rather than
    /// skip them.
    public static final String REQUIRED_ENV_VAR = "STROOM_TEST_ELASTIC_REQUIRED";
    /// The URL of the stroom-resources `elasticsearch` service with its default port.
    public static final String DEFAULT_URL = "http://localhost:9200";
    /// The environment variable holding the base URL of the node with security enabled.
    public static final String TLS_URL_ENV_VAR = "STROOM_TEST_ELASTIC_TLS_URL";
    /// The environment variable holding the password of the `elastic` user of the node with security enabled.
    public static final String TLS_PASSWORD_ENV_VAR = "STROOM_TEST_ELASTIC_TLS_PASSWORD";
    /// The environment variable that, when `true`, makes an unavailable node with security enabled fail the
    /// tests rather than skip them.
    public static final String TLS_REQUIRED_ENV_VAR = "STROOM_TEST_ELASTIC_TLS_REQUIRED";
    /// The default URL of the node with security enabled.
    public static final String DEFAULT_TLS_URL = "https://localhost:9202";

    private static final String INDEX_NAME_PREFIX = "stroom-e2e-";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String JSON_CONTENT_TYPE = "application/json";

    private final String baseUrl;
    private final HttpClient httpClient;
    private final String authorization;
    private final X509Certificate caCertificate;

    private ElasticTestNode(final String baseUrl,
                            final SSLContext sslContext,
                            final String authorization,
                            final X509Certificate caCertificate) {
        this.baseUrl = baseUrl;
        final HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5));
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        this.httpClient = builder.build();
        this.authorization = authorization;
        this.caCertificate = caCertificate;
    }

    /// Get the node the tests should use, skipping the calling test if it can't be reached (or failing it,
    /// if [#REQUIRED_ENV_VAR] is `true`).
    ///
    /// @return The reachable node.
    public static ElasticTestNode getOrSkip() {
        final String url = stripTrailingSlash(getEnv(URL_ENV_VAR, DEFAULT_URL));
        final ElasticTestNode node = new ElasticTestNode(url, null, null, null);
        skipIfUnavailable(
                node.getUnavailableReason(),
                REQUIRED_ENV_VAR,
                LogUtil.message("Elasticsearch node {}", url),
                LogUtil.message("Start it with stroom-resources, e.g. './bounceIt.sh -x -y elasticsearch', or "
                                + "set {} to the URL of another node.", URL_ENV_VAR));
        return node;
    }

    /// Get the node with Elasticsearch's security enabled, skipping the calling test if it can't be reached
    /// (or failing it, if [#TLS_REQUIRED_ENV_VAR] is `true`).
    ///
    /// The node serves HTTPS with a certificate signed by its own CA, as Elasticsearch does when it
    /// configures its security automatically on first start. This reads the CA's certificate from the chain
    /// the node presents, then trusts only that CA, authenticating as the `elastic` user. The URL is taken
    /// from [#TLS_URL_ENV_VAR], defaulting to [#DEFAULT_TLS_URL], and the password from
    /// [#TLS_PASSWORD_ENV_VAR].
    ///
    /// @return The reachable node.
    public static ElasticTestNode getTlsOrSkip() {
        final String url = stripTrailingSlash(getEnv(TLS_URL_ENV_VAR, DEFAULT_TLS_URL));
        final String password = getEnv(TLS_PASSWORD_ENV_VAR, null);
        ElasticTestNode node = null;
        String unavailableReason;
        if (password == null) {
            unavailableReason = TLS_PASSWORD_ENV_VAR + " is not set";
        } else {
            try {
                final X509Certificate caCertificate = readCaCertificate(URI.create(url));
                final String authorization = "Basic " + Base64.getEncoder().encodeToString(
                        ("elastic:" + password).getBytes(StandardCharsets.UTF_8));
                node = new ElasticTestNode(url, createSslContext(caCertificate), authorization, caCertificate);
                unavailableReason = node.getUnavailableReason();
            } catch (final RuntimeException e) {
                unavailableReason = e.getMessage();
            }
        }
        skipIfUnavailable(
                unavailableReason,
                TLS_REQUIRED_ENV_VAR,
                LogUtil.message("Elasticsearch node {} with security enabled", url),
                LogUtil.message("Start one, e.g. 'docker run -d -p 127.0.0.1:9202:9200 -e discovery.type=single-node "
                                + "-e ELASTIC_PASSWORD=<password> "
                                + "docker.elastic.co/elasticsearch/elasticsearch:<version>', "
                                + "and set {} to its password, or set {} to the URL of another node.",
                        TLS_PASSWORD_ENV_VAR, TLS_URL_ENV_VAR));
        return node;
    }

    private static void skipIfUnavailable(final String unavailableReason,
                                          final String requiredEnvVar,
                                          final String nodeDescription,
                                          final String advice) {
        if (unavailableReason != null) {
            final String message = LogUtil.message(
                    "{} is not available ({}). {}", nodeDescription, unavailableReason, advice);
            if (Boolean.parseBoolean(getEnv(requiredEnvVar, "false"))) {
                throw new IllegalStateException(message);
            }
            LOGGER.warn(message);
            Assumptions.assumeThat(unavailableReason)
                    .as(message)
                    .isNull();
        }
    }

    /// @return The base URL of the node, as used in an Elastic Cluster document's connection URLs.
    public String getUrl() {
        return baseUrl;
    }

    /// @return The certificate of the CA that signed the certificate of a node with security enabled, or
    /// `null` for a node without.
    public X509Certificate getCaCertificate() {
        return caCertificate;
    }

    /// Create an API key for the `elastic` user of a node with security enabled.
    ///
    /// @param name The name of the key.
    /// @return The encoded key, as Stroom's API key credential holds it.
    public String createApiKey(final String name) {
        final HttpResponse<String> response = send(
                "POST",
                "/_security/api_key",
                "{\"name\": \"" + name + "\", \"expiration\": \"1h\"}");
        assertSuccess(response, "create API key " + name);
        return readJson(response.body()).get("encoded").asString();
    }

    /// Create a unique index name for a test, so that tests don't interfere with each other or with
    /// anything else using the node.
    ///
    /// @param purpose A short name for what the index holds, e.g. `network`.
    /// @return A unique, lower case index name beginning with `stroom-e2e-`.
    public static String createIndexName(final String purpose) {
        return INDEX_NAME_PREFIX
               + purpose.toLowerCase(Locale.ROOT)
               + "-"
               + UUID.randomUUID().toString().substring(0, 8);
    }

    /// Create an index with the supplied settings and mappings.
    ///
    /// @param indexName The name of the index to create.
    /// @param body The JSON body of the create index request, i.e. its `settings` and `mappings`.
    public void createIndex(final String indexName, final String body) {
        final HttpResponse<String> response = send("PUT", "/" + indexName, body);
        assertSuccess(response, "create index " + indexName);
    }

    /// Index documents directly, without Stroom, and make them visible to searches.
    ///
    /// @param indexName The index to add the documents to.
    /// @param documents The documents, each a JSON object with an `_id` property giving its id.
    public void bulkIndex(final String indexName, final JsonNode documents) {
        final StringBuilder body = new StringBuilder();
        for (final JsonNode document : documents) {
            final String id = document.get("_id").asString();
            final ObjectNode source = (ObjectNode) document.deepCopy();
            source.remove("_id");
            body.append("{\"index\": {\"_index\": \"")
                    .append(indexName)
                    .append("\", \"_id\": \"")
                    .append(id)
                    .append("\"}}\n")
                    .append(JsonUtil.writeValueAsString(source, false))
                    .append("\n");
        }
        final HttpResponse<String> response = send("POST", "/_bulk?refresh=true", body.toString());
        assertSuccess(response, "bulk index to " + indexName);
        assertThat(readJson(response.body()).get("errors").asBoolean())
                .withFailMessage(() -> "Bulk index errors: " + response.body())
                .isFalse();
    }

    /// Create (or replace) an index template, which gives the settings and mappings of any index created
    /// later whose name matches one of its patterns.
    ///
    /// @param templateName The name of the template.
    /// @param body The JSON body of the template, including its `index_patterns`.
    public void createIndexTemplate(final String templateName, final String body) {
        final HttpResponse<String> response = send("PUT", "/_index_template/" + templateName, body);
        assertSuccess(response, "create index template " + templateName);
    }

    /// Delete an index template, if it exists.
    ///
    /// @param templateName The name of the template.
    public void deleteIndexTemplate(final String templateName) {
        final HttpResponse<String> response = send("DELETE", "/_index_template/" + templateName, null);
        if (!isSuccess(response) && response.statusCode() != 404) {
            LOGGER.warn("Failed to delete index template '{}': {} {}",
                    templateName, response.statusCode(), response.body());
        }
    }

    /// Delete all indices matching the supplied name or wildcard pattern, if there are any.
    ///
    /// @param indexPattern The index name or pattern, e.g. `stroom-e2e-network-1234abcd*`.
    public void deleteIndices(final String indexPattern) {
        // Elasticsearch refuses to delete indices by wildcard (`action.destructive_requires_name`), so find the
        // names of the matching indices and delete them by name
        final HttpResponse<String> catResponse = send(
                "GET",
                "/_cat/indices/" + indexPattern + "?format=json&h=index",
                null);
        if (catResponse.statusCode() == 404) {
            // An index name, rather than a pattern, that doesn't exist, e.g. as the test failed before creating it
            return;
        }
        assertSuccess(catResponse, "list indices " + indexPattern);
        final List<String> indexNames = new ArrayList<>();
        for (final JsonNode index : readJson(catResponse.body())) {
            indexNames.add(index.get("index").asString());
        }
        if (indexNames.isEmpty()) {
            return;
        }
        final HttpResponse<String> response = send(
                "DELETE",
                "/" + String.join(",", indexNames) + "?ignore_unavailable=true",
                null);
        if (!isSuccess(response)) {
            LOGGER.warn("Failed to delete indices {}: {} {}", indexNames, response.statusCode(), response.body());
        }
    }

    /// Refresh the indices matching a name or pattern so that everything indexed is visible to searches.
    ///
    /// @param indexPattern The index name or pattern.
    public void refresh(final String indexPattern) {
        final HttpResponse<String> response = send("POST", "/" + indexPattern + "/_refresh", null);
        assertSuccess(response, "refresh " + indexPattern);
    }

    /// Count the documents matching a query, in the indices matching a name or pattern.
    ///
    /// @param indexPattern The index name or pattern.
    /// @param query The JSON of a query, e.g. `{"term": {"UserId": "user5"}}`, or `null` to match all.
    /// @return The number of matching documents.
    public long count(final String indexPattern, final String query) {
        final String body = query == null
                ? null
                : "{\"query\": " + query + "}";
        final HttpResponse<String> response = send("POST", "/" + indexPattern + "/_count", body);
        assertSuccess(response, "count " + indexPattern);
        return readJson(response.body()).get("count").asLong();
    }

    /// Search the indices matching a name or pattern.
    ///
    /// @param indexPattern The index name or pattern.
    /// @param body The JSON body of the search request.
    /// @return The search response.
    public JsonNode search(final String indexPattern, final String body) {
        final HttpResponse<String> response = send("POST", "/" + indexPattern + "/_search", body);
        assertSuccess(response, "search " + indexPattern);
        return readJson(response.body());
    }

    /// Get the indices matching a name or pattern.
    ///
    /// @param indexPattern The index name or pattern.
    /// @return The JSON response of the `_cat/indices` API, one object per index. Its `docs.count` includes the
    /// hidden documents Elasticsearch stores for nested objects, so use [#count] to count the documents indexed.
    public JsonNode catIndices(final String indexPattern) {
        final HttpResponse<String> response = send(
                "GET",
                "/_cat/indices/" + indexPattern + "?format=json&h=index,docs.count",
                null);
        assertSuccess(response, "list indices " + indexPattern);
        return readJson(response.body());
    }

    /// @return The number of search contexts (e.g. scrolls) currently open across the node.
    public long getOpenSearchContexts() {
        final HttpResponse<String> response = send("GET", "/_nodes/stats/indices/search", null);
        assertSuccess(response, "get node search stats");
        long total = 0;
        for (final JsonNode node : readJson(response.body()).get("nodes")) {
            total += node.at("/indices/search/open_contexts").asLong();
        }
        return total;
    }

    private String getUnavailableReason() {
        try {
            final HttpResponse<String> response = send(
                    "GET",
                    "/_cluster/health?wait_for_status=yellow&timeout=10s",
                    null);
            if (!isSuccess(response)) {
                return "cluster health returned HTTP " + response.statusCode();
            }
            final String status = readJson(response.body()).get("status").asString();
            return "red".equals(status)
                    ? "cluster status is red"
                    : null;
        } catch (final RuntimeException e) {
            return e.getMessage();
        }
    }

    private HttpResponse<String> send(final String method, final String path, final String body) {
        final HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(TIMEOUT)
                .header("Accept", JSON_CONTENT_TYPE);
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        if (body != null) {
            builder.header("Content-Type", JSON_CONTENT_TYPE)
                    .method(method, BodyPublishers.ofString(body));
        } else {
            builder.method(method, BodyPublishers.noBody());
        }
        try {
            return httpClient.send(builder.build(), BodyHandlers.ofString());
        } catch (final IOException e) {
            throw new UncheckedIOException(
                    LogUtil.message("Error sending {} {}{}: {}", method, baseUrl, path, e.getMessage()), e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted sending " + method + " " + baseUrl + path, e);
        }
    }

    /// Read the certificate of the CA at the end of the chain a node presents. The connection trusts any
    /// certificate, so that the chain can be read, but nothing is sent over it.
    private static X509Certificate readCaCertificate(final URI uri) {
        try {
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new TrustAllTrustManager()}, null);
            try (final SSLSocket socket = (SSLSocket) sslContext.getSocketFactory()
                    .createSocket(uri.getHost(), uri.getPort() == -1
                            ? 443
                            : uri.getPort())) {
                socket.setSoTimeout((int) Duration.ofSeconds(5).toMillis());
                socket.startHandshake();
                final Certificate[] chain = socket.getSession().getPeerCertificates();
                final X509Certificate caCertificate = (X509Certificate) chain[chain.length - 1];
                if (chain.length < 2 || caCertificate.getBasicConstraints() < 0) {
                    throw new IllegalStateException("The node's certificate is not signed by a CA");
                }
                return caCertificate;
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("Unable to read the node's certificates: " + e.getMessage(), e);
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException("Unable to read the node's certificates: " + e.getMessage(), e);
        }
    }

    private static SSLContext createSslContext(final X509Certificate caCertificate) {
        try {
            final KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            trustStore.setCertificateEntry("ca", caCertificate);
            final TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            return sslContext;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException("Unable to create SSL context: " + e.getMessage(), e);
        }
    }

    private static boolean isSuccess(final HttpResponse<String> response) {
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    private static void assertSuccess(final HttpResponse<String> response, final String action) {
        assertThat(isSuccess(response))
                .withFailMessage(() -> LogUtil.message(
                        "Failed to {}: HTTP {} {}", action, response.statusCode(), response.body()))
                .isTrue();
    }

    private static JsonNode readJson(final String json) {
        return JsonUtil.getMapper().readTree(json);
    }

    private static String getEnv(final String name, final String defaultValue) {
        final String value = System.getenv(name);
        return value == null || value.isBlank()
                ? defaultValue
                : value.trim();
    }

    private static String stripTrailingSlash(final String url) {
        return url.endsWith("/")
                ? url.substring(0, url.length() - 1)
                : url;
    }

    /// Trusts any certificate. Only used to read the chain a node presents.
    @SuppressWarnings("java:S4830") // Deliberately trusts anything, as nothing is sent over the connection
    private static final class TrustAllTrustManager implements X509TrustManager {

        @Override
        public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
            // Trust anything
        }

        @Override
        public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
            // Trust anything
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
