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

package stroom.mcp.impl.e2e;

import stroom.ai.api.ElasticKeyStreams;
import stroom.ai.impl.AiConfig;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.shared.AiToolAccess;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.mcp.impl.McpTestClient;
import stroom.mcp.impl.McpTestClients;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.ElasticTestNode;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.api.DocumentPermissionService;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.test.ConfigMapperSpy;
import stroom.util.config.AbstractConfigUtil;
import stroom.util.shared.PermissionException;
import stroom.util.shared.PropertyPath;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// `sequence_query`'s streaming join over `/mcp` on a real Stroom and Elasticsearch: a proxy index (downloads), an
/// endpoint index (copies, its own time field), a directory of users with teams (no time field) and an
/// authentication index that writes users with their domain (`CORP` and a backslash before the name). 26,000
/// users download then copy, so the join reads past 52,000 rows and finds past 10,000 sequences, enough for its
/// workers: the same sequences with one worker and with four. A team is pushed down as terms; the estimate and the
/// caps are met; mismatched join values are diagnosed and bridged by a transform; and a doc the user can't use is
/// refused, by the tool and by Stroom's Elasticsearch module itself.
///
/// The node is [ElasticTestNode]'s (`STROOM_TEST_ELASTIC_URL`): skipped without one unless
/// `STROOM_TEST_ELASTIC_REQUIRED=true`. The indexes made there are removed after each test.
@SuppressWarnings("unchecked")
class TestE2eStreamingJoin extends AbstractE2eTest {

    private static final int USERS = 26_000;
    private static final int TEAM = 20;
    private static final long T0 = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
    private static final Pattern TABLE = Pattern.compile("They are in (\\S+\\.md)");
    private static final String PROXY_BODY = """
            {"settings": {"number_of_shards": 2, "number_of_replicas": 0},
             "mappings": {"properties": {
               "@timestamp": {"type": "date"}, "user": {"type": "keyword"}, "action": {"type": "keyword"},
               "url": {"type": "keyword"}, "StreamId": {"type": "long"}, "EventId": {"type": "long"}}}}""";
    private static final String ENDPOINT_BODY = """
            {"settings": {"number_of_shards": 2, "number_of_replicas": 0},
             "mappings": {"properties": {
               "EventTime": {"type": "date"}, "UserName": {"type": "keyword"}, "action": {"type": "keyword"},
               "file": {"type": "keyword"}, "StreamId": {"type": "long"}, "EventId": {"type": "long"}}}}""";
    private static final String USERS_BODY = """
            {"settings": {"number_of_shards": 1, "number_of_replicas": 0},
             "mappings": {"properties": {
               "sAMAccountName": {"type": "keyword"}, "team": {"type": "keyword"}}}}""";
    private static final String AUTH_BODY = """
            {"settings": {"number_of_shards": 1, "number_of_replicas": 0},
             "mappings": {"properties": {
               "@timestamp": {"type": "date"}, "TargetUserName": {"type": "keyword"},
               "event": {"type": "keyword"}, "StreamId": {"type": "long"}, "EventId": {"type": "long"}}}}""";

    @Inject
    private ElasticIndexStore elasticIndexStore;
    @Inject
    private ConfigMapperSpy configMapperSpy;
    @Inject
    private DocumentPermissionService documentPermissionService;
    @Inject
    private ElasticKeyStreams elasticKeyStreams;
    @Inject
    private McpTestClients clients;

    private ElasticTestNode node;
    private final List<String> indices = new ArrayList<>();
    private DocRef proxy;
    private DocRef endpoint;
    private DocRef users;
    private DocRef auth;
    private DocRef cluster;

    @BeforeEach
    void seed() {
        node = ElasticTestNode.getOrSkip();
        final String proxyIndex = index("join-proxy", PROXY_BODY);
        final String endpointIndex = index("join-endpoint", ENDPOINT_BODY);
        final String usersIndex = index("join-users", USERS_BODY);
        final String authIndex = index("join-auth", AUTH_BODY);
        final StringBuilder proxyDocs = new StringBuilder();
        final StringBuilder endpointDocs = new StringBuilder();
        for (int i = 0; i < USERS; i++) {
            proxyDocs.append("{\"@timestamp\": ").append(T0 + i * 1_000L).append(", \"user\": \"").append(user(i))
                    .append("\", \"action\": \"download\", \"url\": \"abc.com/f").append(i)
                    .append("\", \"StreamId\": 1, \"EventId\": ").append(i + 1).append("}\n");
            endpointDocs.append("{\"EventTime\": ").append(T0 + i * 1_000L + 30_000L).append(", \"UserName\": \"")
                    .append(user(i)).append("\", \"action\": \"copy\", \"file\": \"f").append(i)
                    .append("\", \"StreamId\": 2, \"EventId\": ").append(i + 1).append("}\n");
        }
        bulk(proxyIndex, proxyDocs);
        bulk(endpointIndex, endpointDocs);
        final StringBuilder userDocs = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            userDocs.append("{\"sAMAccountName\": \"").append(user(i * 13)).append("\", \"team\": \"")
                    .append(i < TEAM
                            ? "A"
                            : "B").append("\"}\n");
        }
        bulk(usersIndex, userDocs);
        final StringBuilder authDocs = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            // Ten seconds before each user's download.
            authDocs.append("{\"@timestamp\": ").append(T0 + i * 1_000L - 10_000L)
                    .append(", \"TargetUserName\": \"CORP\\\\").append(user(i))
                    .append("\", \"event\": \"logon\", \"StreamId\": 3, \"EventId\": ").append(i + 1).append("}\n");
        }
        bulk(authIndex, authDocs);
        node.refresh("stroom-e2e-join-*");
        cluster = liveElasticCluster(node, "E2E join cluster " + proxyIndex);
        proxy = elasticIndex("E2E join proxy " + proxyIndex, proxyIndex, cluster, "@timestamp");
        endpoint = elasticIndex("E2E join endpoint " + endpointIndex, endpointIndex, cluster, "EventTime");
        users = elasticIndex("E2E join users " + usersIndex, usersIndex, cluster, "@timestamp");
        auth = elasticIndex("E2E join auth " + authIndex, authIndex, cluster, "@timestamp");
    }

    @AfterEach
    void removeTheIndexes() {
        configMapperSpy.setConfigValueMappers(Map.of());
        if (node != null) {
            indices.forEach(node::deleteIndices);
        }
    }

    @Test
    void aJoinPastTenThousandSequencesAcrossTwoIndexesFindsEveryOneAndClosesItsScans() {
        final Map<String, Object> result = json("sequence_query", downloadThenCopy(Map.of("limit", 30_000)));
        dump("sequence_query, streaming", result);

        // Different time fields, which one EQL sequence can't order by: auto chose the stream itself.
        assertThat(result).containsEntry("engine", "stream").containsEntry("sequence_count", (long) USERS)
                .containsEntry("entity_count", (long) USERS).containsEntry("truncated", false);
        assertThat((String) result.get("engine_reason")).contains("different time fields");
        assertThat(((Number) result.get("rows_read")).longValue()).isEqualTo(2L * USERS);
        assertThat((String) result.get("caveat")).contains("straight from Elasticsearch");
        assertThat((List<Map<String, Object>>) result.get("review_queries")).hasSize(2);
        assertThat(node.getOpenSearchContexts("stroom-e2e-join-*")).isZero();
    }

    @Test
    void fourWorkersGiveTheSameSequencesAsOne() {
        workers(1, 10_000_000L);
        final Map<String, Object> one = json("sequence_query", downloadThenCopy(Map.of("limit", 30_000)));
        workers(4, 10_000_000L);
        final Map<String, Object> four = json("sequence_query", downloadThenCopy(Map.of("limit", 30_000)));
        dump("sequence_query, 4 workers", four);

        assertThat(((Number) one.get("workers")).intValue()).isEqualTo(1);
        assertThat(((Number) one.get("key_ranges")).intValue()).isEqualTo(1);
        assertThat(((Number) four.get("workers")).intValue()).isEqualTo(4);
        assertThat(((Number) four.get("key_ranges")).intValue()).isEqualTo(4);
        assertThat(four.get("sequence_count")).isEqualTo(one.get("sequence_count")).isEqualTo((long) USERS);
        assertThat(table(four)).isEqualTo(table(one)).hasSize(USERS);
    }

    @Test
    void aTeamIsPushedDownAsTermsSoTheProxyIsCutBeforeItStreams() {
        final Map<String, Object> result = json("sequence_query", Map.of("steps", List.of(
                Map.of("index", users.getUuid(), "by", List.of("sAMAccountName"), "keysOnly", true,
                        "where", List.of(Map.of("field", "team", "value", "A"))),
                Map.of("index", proxy.getUuid(), "by", List.of("user"), "where", List.of(Map.of("field",
                        "action", "value", "download"))))));
        dump("sequence_query, pushed down", result);

        assertThat(result).containsEntry("engine", "stream").containsEntry("sequence_count", (long) TEAM);
        assertThat((String) result.get("pushed_down")).contains("Step 1 has " + TEAM + " keys");
        // The team's keys and their downloads, not the proxy's 26,000.
        assertThat(((Number) result.get("rows_read")).longValue()).isEqualTo(2L * TEAM);
    }

    @Test
    void aJoinNearTheCapsIsRefusedWithItsEstimateAndRunAnywayStopsAtTheCap() {
        workers(1, 40_000L);

        final String refused = McpTestClient.text(plainClient.call("sequence_query",
                downloadThenCopy(Map.of())));
        assertThat(refused).contains("Not run: the join is near its caps: about 52,000 rows to read (the cap is "
                                     + "40,000) and 26,000 keys")
                .contains("runAnyway=true");

        final Map<String, Object> capped = json("sequence_query", downloadThenCopy(Map.of("runAnyway", true)));
        dump("sequence_query, capped", capped);
        assertThat((String) capped.get("ended_early")).contains("cap of 40,000 rows read");
        assertThat(((Number) capped.get("sequence_count")).intValue()).isPositive().isLessThan(USERS);
        assertThat(node.getOpenSearchContexts("stroom-e2e-join-*")).isZero();
    }

    @Test
    void mismatchedJoinValuesAreDiagnosedAndATransformBridgesThem() {
        final Map<String, Object> none = json("sequence_query", logonThenDownload(List.of()));
        dump("sequence_query, mismatched", none);

        assertThat(none).containsEntry("sequence_count", 0L);
        final List<String> diagnosis = (List<String>) none.get("diagnosis");
        assertThat(String.join("\n", diagnosis)).contains("No shared values between step 1")
                .contains("e.g. `CORP\\user000")
                .contains("With transform [strip_domain]");

        final Map<String, Object> found = json("sequence_query", logonThenDownload(List.of("strip_domain")));
        assertThat(found).containsEntry("sequence_count", 5L).doesNotContainKey("diagnosis");
    }

    @Test
    void aDocTheUserCantUseIsRefusedByTheToolAndByStroomsElasticsearchModule() {
        final UserRef limited = user("join-limited-" + Instant.now().toEpochMilli(),
                Set.of(AppPermission.USE_MCP_TOOLS));
        toolPolicy("Join reader " + limited.getSubjectId(), AiToolAccess.READ_ONLY, limited);
        asUser(admin, () -> {
            // The tool finds a data source by reading it, so View (which holds Use); nothing on the endpoint.
            documentPermissionService.setPermission(proxy, limited, DocumentPermission.VIEW);
            // As a folder's permissions would give it: Use on the cluster the index is on.
            documentPermissionService.setPermission(cluster, limited, DocumentPermission.USE);
            return null;
        });

        final String refused = McpTestClient.text(clients.client(limited, false).call("sequence_query",
                downloadThenCopy(Map.of("engine", "stream"))));
        assertThat(refused).contains("No data source with uuid or name '" + endpoint.getUuid()
                                     + "' that you can use");
        assertThatThrownBy(() -> asUser(limited, () -> elasticKeyStreams.target(List.of(proxy, endpoint))))
                .isInstanceOf(PermissionException.class)
                .hasMessageContaining(endpoint.getUuid());
        assertThat(asUser(limited, () -> elasticKeyStreams.target(List.of(proxy))).docs()).hasSize(1);
    }

    // --------------------------------------------------------------------------------

    private Map<String, Object> downloadThenCopy(final Map<String, Object> extra) {
        final Map<String, Object> args = new LinkedHashMap<>();
        args.put("maxspan", "2m");
        args.put("steps", List.of(
                Map.of("index", proxy.getUuid(), "by", List.of("user"), "where", List.of(Map.of("field", "action",
                        "value", "download"))),
                Map.of("index", endpoint.getUuid(), "by", List.of("UserName"), "where", List.of(Map.of("field",
                        "action", "value", "copy")))));
        args.putAll(extra);
        return args;
    }

    private Map<String, Object> logonThenDownload(final List<String> transform) {
        final Map<String, Object> logon = new LinkedHashMap<>();
        logon.put("index", auth.getUuid());
        logon.put("by", List.of("TargetUserName"));
        logon.put("where", List.of(Map.of("field", "event", "value", "logon")));
        if (!transform.isEmpty()) {
            logon.put("transform", transform);
        }
        return Map.of("engine", "stream", "maxspan", "1m", "steps", List.of(logon,
                Map.of("index", proxy.getUuid(), "by", List.of("user"), "where", List.of(Map.of("field",
                        "action", "value", "download")))));
    }

    // The join's settings, as an administrator would set them.
    private void workers(final int workers, final long maxRows) {
        configMapperSpy.setConfigValueMapper(AiConfig.class, config -> {
            final PropertyPath path = Objects.requireNonNullElse(config.getBasePath(),
                    PropertyPath.fromParts("stroom", "ai"));
            return AbstractConfigUtil.mutateTree(config, path, Map.of(path.merge("workspace"),
                    new AiWorkspaceConfig(null, null, null, null, null, null, null, null, null, null, null, null,
                            null, null, workers, 0, null, maxRows)));
        });
    }

    // Every row of a result's table, read a page at a time.
    private List<String> table(final Map<String, Object> result) {
        final String path = (String) result.get("table");
        assertThat(path).isNotNull();
        final List<String> rows = new ArrayList<>();
        int start = 1;
        while (true) {
            final String page = McpTestClient.text(client.call("read_file", args("path", path, "startLine", start,
                    "lineCount", 500)));
            int read = 0;
            int last = start - 1;
            for (final String line : page.split("\n")) {
                final Matcher numbered = Pattern.compile("^\\s*(\\d+)\\t(.*)$").matcher(line);
                if (numbered.matches()) {
                    last = Integer.parseInt(numbered.group(1));
                    final String row = numbered.group(2);
                    if (row.startsWith("|") && !row.startsWith("| ---") && !row.startsWith("|---") && last > 1) {
                        rows.add(row);
                    }
                    read++;
                }
            }
            if (read == 0) {
                return rows;
            }
            start = last + 1;
        }
    }

    private String index(final String purpose, final String body) {
        final String name = ElasticTestNode.createIndexName(purpose);
        node.createIndex(name, body);
        indices.add(name);
        return name;
    }

    // Indexes NDJSON sources, 5,000 a request.
    private void bulk(final String index, final StringBuilder sources) {
        final String[] lines = sources.toString().split("\n");
        for (int from = 0; from < lines.length; from += 5_000) {
            final StringBuilder body = new StringBuilder();
            for (int i = from; i < Math.min(lines.length, from + 5_000); i++) {
                body.append("{\"index\": {\"_index\": \"").append(index).append("\"}}\n").append(lines[i])
                        .append('\n');
            }
            final ElasticTestNode.Response response = node.request("POST", "/_bulk", body.toString());
            assertThat(response.isSuccess()).as(response.body()).isTrue();
            assertThat(response.body()).as("bulk errors").contains("\"errors\":false");
        }
    }

    private static String user(final int i) {
        return String.format("user%06d", i);
    }

    private DocRef elasticIndex(final String name,
                                final String indexName,
                                final DocRef cluster,
                                final String timeField) {
        final ExplorerNode created = create(ElasticIndexDoc.TYPE, name, templateFolder());
        asUser(admin, () -> elasticIndexStore.writeDocument(elasticIndexStore.readDocument(created.getDocRef())
                .copy()
                .clusterRef(cluster)
                .indexName(indexName)
                .timeField(timeField)
                .build()));
        return created.getDocRef();
    }
}
