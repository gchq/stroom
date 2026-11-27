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

import stroom.ai.impl.AiConfig;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.ElasticTestNode;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.test.ConfigMapperSpy;
import stroom.util.config.AbstractConfigUtil;
import stroom.util.shared.PropertyPath;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/// The streaming join's benchmark, one worker against four on the same joins (asked for by the product owner,
/// 2026-11-23), before the default of 4 is kept: a proxy index and an endpoint index of [#EVENTS] events each
/// ([#USERS] users), joined as "download then copy within 2 minutes" (every event read), the same with a
/// `lowercase` transform on one side (a Painless runtime field), and as "users who did both" (keys only), each with
/// 1 and then 4 workers. For each it prints the elapsed time, the rows read, the keys merged,
/// the sequences found and the JVM's peak heap during the join (the whole test JVM's, so an upper bound).
///
/// Opt-in: `-DbenchStreamingJoin=true` (forwarded to the test JVM by the root build's `bench*` properties); the
/// sizes by `-DbenchStreamingJoinEvents=` and `-DbenchStreamingJoinUsers=`. Needs [ElasticTestNode]'s node
/// (`STROOM_TEST_ELASTIC_URL`). Its indices are removed afterwards.
@EnabledIfSystemProperty(named = "benchStreamingJoin", matches = "true")
@SuppressWarnings("unchecked")
class TestBenchStreamingJoin extends AbstractE2eTest {

    private static final int EVENTS = Integer.getInteger("benchStreamingJoinEvents", 1_500_000);
    private static final int USERS = Integer.getInteger("benchStreamingJoinUsers", 200_000);
    private static final int BATCH = 10_000;
    private static final long T0 = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
    private static final String BODY = """
            {"settings": {"number_of_shards": 2, "number_of_replicas": 0, "refresh_interval": "-1"},
             "mappings": {"properties": {
               "%s": {"type": "date"}, "%s": {"type": "keyword"}, "action": {"type": "keyword"},
               "StreamId": {"type": "long"}, "EventId": {"type": "long"}}}}""";

    @Inject
    private ElasticIndexStore elasticIndexStore;
    @Inject
    private ConfigMapperSpy configMapperSpy;

    private ElasticTestNode node;
    private final List<String> indices = new ArrayList<>();

    @AfterEach
    void removeTheIndexes() {
        configMapperSpy.setConfigValueMappers(Map.of());
        if (node != null) {
            indices.forEach(node::deleteIndices);
        }
    }

    @Test
    void oneWorkerAgainstFour() {
        node = ElasticTestNode.getOrSkip();
        final String proxyIndex = index("bench-proxy", BODY.formatted("@timestamp", "user"));
        final String endpointIndex = index("bench-endpoint", BODY.formatted("EventTime", "UserName"));
        final Instant loading = Instant.now();
        load(proxyIndex, "@timestamp", "user", "download", 0);
        load(endpointIndex, "EventTime", "UserName", "copy", 30_000);
        node.request("PUT", "/" + proxyIndex + "," + endpointIndex + "/_settings",
                "{\"index\": {\"refresh_interval\": \"1s\"}}");
        node.refresh("stroom-e2e-bench-*");
        System.out.printf(Locale.ROOT, "BENCH loaded %,d events into each of two indices in %s%n", EVENTS,
                Duration.between(loading, Instant.now()));

        final DocRef cluster = liveElasticCluster(node, "Bench join cluster " + proxyIndex);
        final DocRef proxy = elasticIndex("Bench proxy " + proxyIndex, proxyIndex, cluster, "@timestamp");
        final DocRef endpoint = elasticIndex("Bench endpoint " + endpointIndex, endpointIndex, cluster,
                "EventTime");
        final Map<String, Object> sequence = new LinkedHashMap<>();
        sequence.put("engine", "stream");
        sequence.put("maxspan", "2m");
        sequence.put("limit", 1_000);
        sequence.put("runAnyway", true);
        sequence.put("steps", List.of(
                Map.of("index", proxy.getUuid(), "by", List.of("user"), "where", List.of(Map.of("field", "action",
                        "value", "download"))),
                Map.of("index", endpoint.getUuid(), "by", List.of("UserName"), "where", List.of(Map.of("field",
                        "action", "value", "copy")))));
        // The same join with a transform on one side: its key a Painless runtime field, computed per event.
        final Map<String, Object> transformed = new LinkedHashMap<>(sequence);
        transformed.put("steps", List.of(
                Map.of("index", proxy.getUuid(), "by", List.of("user"), "where", List.of(Map.of("field", "action",
                        "value", "download"))),
                Map.of("index", endpoint.getUuid(), "by", List.of("UserName"), "transform", List.of("lowercase"),
                        "where", List.of(Map.of("field", "action", "value", "copy")))));
        final Map<String, Object> entities = new LinkedHashMap<>();
        entities.put("engine", "stream");
        entities.put("limit", 1_000);
        entities.put("runAnyway", true);
        entities.put("steps", List.of(
                Map.of("index", proxy.getUuid(), "by", List.of("user"), "keysOnly", true),
                Map.of("index", endpoint.getUuid(), "by", List.of("UserName"), "keysOnly", true)));

        final List<String> lines = new ArrayList<>();
        final Map<String, Map<String, Object>> joins = new LinkedHashMap<>();
        joins.put("events", sequence);
        joins.put("events with a transform", transformed);
        joins.put("keys", entities);
        for (final Map.Entry<String, Map<String, Object>> join : joins.entrySet()) {
            for (final int workers : List.of(1, 4)) {
                lines.add(run(join.getKey(), join.getValue(), workers));
            }
        }
        lines.forEach(line -> System.out.println("BENCH " + line));
        assertThat(lines).hasSize(6);
    }

    // Runs one join with that many workers, giving its figures.
    private String run(final String name, final Map<String, Object> args, final int workers) {
        configMapperSpy.setConfigValueMapper(AiConfig.class, config -> {
            final PropertyPath path = Objects.requireNonNullElse(config.getBasePath(),
                    PropertyPath.fromParts("stroom", "ai"));
            return AbstractConfigUtil.mutateTree(config, path, Map.of(path.merge("workspace"),
                    new AiWorkspaceConfig(null, null, null, null, null, StroomDuration.ofMinutes(30),
                            StroomDuration.ofMinutes(30), null, null, null, null, null, null, null, workers, 0,
                            null, null)));
        });
        System.gc();
        final List<MemoryPoolMXBean> heap = ManagementFactory.getMemoryPoolMXBeans().stream()
                .filter(pool -> pool.getType() == MemoryType.HEAP)
                .toList();
        heap.forEach(MemoryPoolMXBean::resetPeakUsage);
        final long before = heap.stream().mapToLong(pool -> pool.getUsage().getUsed()).sum();
        final Instant started = Instant.now();
        final Map<String, Object> result = json("sequence_query", args);
        final Duration took = Duration.between(started, Instant.now());
        final long peak = heap.stream().mapToLong(pool -> pool.getPeakUsage().getUsed()).sum();
        assertThat(result).as(String.valueOf(result)).containsEntry("engine", "stream");
        return String.format(Locale.ROOT, "%s join, %d worker%s (%s key ranges): %,d ms (tool %s ms), %,d rows "
                                          + "read, %,d keys merged, %s matches, heap before %,d MB, peak %,d MB",
                name, workers, workers == 1
                        ? ""
                        : "s", result.get("key_ranges"), took.toMillis(), result.get("took_ms"),
                ((Number) result.get("rows_read")).longValue(), ((Number) result.get("keys_merged")).longValue(),
                result.getOrDefault("sequence_count", result.get("entity_match_count")), before >> 20, peak >> 20);
    }

    // Loads EVENTS events: each of USERS users in turn, a second apart, the time offset by `offsetMs`.
    private void load(final String index,
                      final String timeField,
                      final String userField,
                      final String action,
                      final long offsetMs) {
        for (int from = 0; from < EVENTS; from += BATCH) {
            final StringBuilder body = new StringBuilder();
            for (int i = from; i < Math.min(EVENTS, from + BATCH); i++) {
                body.append("{\"index\": {\"_index\": \"").append(index).append("\"}}\n")
                        .append("{\"").append(timeField).append("\": ").append(T0 + i * 1_000L + offsetMs)
                        .append(", \"").append(userField).append("\": \"")
                        .append(String.format(Locale.ROOT, "user%07d", i % USERS))
                        .append("\", \"action\": \"").append(action).append("\", \"StreamId\": 1, \"EventId\": ")
                        .append(i + 1).append("}\n");
            }
            final ElasticTestNode.Response response = node.request("POST", "/_bulk", body.toString());
            assertThat(response.isSuccess()).as(response.body()).isTrue();
            assertThat(response.body()).as("bulk errors").contains("\"errors\":false");
        }
    }

    private String index(final String purpose, final String body) {
        final String name = ElasticTestNode.createIndexName(purpose);
        node.createIndex(name, body);
        indices.add(name);
        return name;
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
