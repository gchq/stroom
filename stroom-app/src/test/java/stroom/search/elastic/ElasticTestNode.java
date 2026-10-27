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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/// A real Elasticsearch node that the Elasticsearch end-to-end tests index to and search. The tests use it
/// directly, outside Stroom, to create indices with explicit mappings and to check what Stroom indexed.
///
/// The node is normally the `elasticsearch` service of stroom-resources. Its URL is taken from the
/// [#URL_ENV_VAR] environment variable, defaulting to [#DEFAULT_URL]. If the node can't be reached the tests
/// are skipped, unless [#REQUIRED_ENV_VAR] is `true` (as it should be in CI), in which case they fail.
public final class ElasticTestNode {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ElasticTestNode.class);

    /// The environment variable holding the base URL of the node.
    public static final String URL_ENV_VAR = "STROOM_TEST_ELASTIC_URL";
    /// The environment variable that, when `true`, makes an unreachable node fail the tests rather than
    /// skip them.
    public static final String REQUIRED_ENV_VAR = "STROOM_TEST_ELASTIC_REQUIRED";
    /// The URL of the stroom-resources `elasticsearch` service with its default port.
    public static final String DEFAULT_URL = "http://localhost:9200";

    private static final String INDEX_NAME_PREFIX = "stroom-e2e-";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String JSON_CONTENT_TYPE = "application/json";

    private final String baseUrl;
    private final HttpClient httpClient;

    private ElasticTestNode(final String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /// Get the node the tests should use, skipping the calling test if it can't be reached (or failing it,
    /// if [#REQUIRED_ENV_VAR] is `true`).
    ///
    /// @return The reachable node.
    public static ElasticTestNode getOrSkip() {
        final String url = stripTrailingSlash(getEnv(URL_ENV_VAR, DEFAULT_URL));
        final boolean required = Boolean.parseBoolean(getEnv(REQUIRED_ENV_VAR, "false"));
        final ElasticTestNode node = new ElasticTestNode(url);
        final String unavailableReason = node.getUnavailableReason();
        if (unavailableReason != null) {
            final String message = LogUtil.message(
                    "Elasticsearch node {} is not available ({}). Start it with stroom-resources, e.g. "
                    + "'./bounceIt.sh -x -y elasticsearch', or set {} to the URL of another node.",
                    url, unavailableReason, URL_ENV_VAR);
            if (required) {
                throw new IllegalStateException(message);
            }
            LOGGER.warn(message);
            Assumptions.assumeThat(unavailableReason)
                    .as(message)
                    .isNull();
        }
        return node;
    }

    /// @return The base URL of the node, as used in an Elastic Cluster document's connection URLs.
    public String getUrl() {
        return baseUrl;
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
}
