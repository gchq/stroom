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

import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.mcp.impl.McpTestClient;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Status;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.ElasticTestNode;
import stroom.search.elastic.ProxyUploadEvents;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.test.common.PerformanceTest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// `esql_query` over `/mcp` on a real Stroom and Elasticsearch, driving the REAL tools: an index of synthetic proxy
/// upload events ([ProxyUploadEvents]), the grouped question "which users uploaded the most bytes, by site domain,
/// top 10" answered by Elasticsearch, and the same answer from `run_stroomql` over the same index (parity, every
/// stream in Stroom); the documents of a stream not in Stroom, counted by `esql_query` (which doesn't look streams
/// up, for speed) but not by `run_stroomql`, as every `esql_query` result says; a `FROM` that would reach another
/// index, refused; and rows cut short, with how many there are in all.
///
/// The node is [ElasticTestNode]'s (`STROOM_TEST_ELASTIC_URL`): skipped without one unless
/// `STROOM_TEST_ELASTIC_REQUIRED=true`. The index made there is removed after each test.
@SuppressWarnings("unchecked")
class TestE2eEsqlQuery extends AbstractE2eTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestE2eEsqlQuery.class);
    private static final int EVENTS = 2_000;
    private static final int TIMED_EVENTS = 200_000;
    private static final int STREAMS = 20;
    private static final String TOP_UPLOADERS = """
            | STATS total = SUM(bytes) BY user.name, url.domain
            | SORT total DESC, user.name, url.domain
            | LIMIT 10""";
    private static final Pattern RESULTS_PATH = Pattern.compile("The results are in (\\S+\\.md)\\.");

    @Inject
    private ElasticIndexStore elasticIndexStore;
    @Inject
    private MetaService metaService;

    private ElasticTestNode node;
    private String indexName;
    // What run_stroomql last said.
    private String lastStroomQl;

    @BeforeEach
    void setUpNode() {
        node = ElasticTestNode.getOrSkip();
        indexName = ElasticTestNode.createIndexName("esql-tool");
    }

    @AfterEach
    void removeTheIndex() {
        if (node != null && indexName != null) {
            node.deleteIndices(indexName);
        }
    }

    @Test
    void theTopUploadersByDomainMatchRunStroomQl() {
        final DocRef index = seededIndex(EVENTS, false);

        final Map<String, Object> esql = json("esql_query", args("index", index.getUuid(), "pipeline",
                TOP_UPLOADERS));
        dump("esql_query", esql);

        final Map<String, Long> all = ProxyUploadEvents.totalsByUserAndDomain(EVENTS, i -> true);
        final Map<String, Long> expected = top(all, 10);
        assertThat(totals((List<List<Object>>) esql.get("rows"))).containsExactlyEntriesOf(expected);
        assertThat(esql).containsEntry("truncated", true).containsEntry("grouped", true);
        assertThat(number(esql, "limit")).isEqualTo(10);
        assertThat(number(esql, "total_groups")).isEqualTo(all.size());
        assertThat((String) esql.get("note")).contains("so these are the first 10 of " + all.size() + " groups")
                .doesNotContain("streams the user can't see");
        assertThat(esql).doesNotContainKey("excluded_streams");
        assertThat((String) esql.get("query")).startsWith("FROM " + indexName + "\n");

        // The same question through Stroom: every document read back and grouped by Stroom's coprocessors.
        final Map<String, Long> viaStroom = stroomQlTotals(index);
        assertThat(top(viaStroom, 10)).containsExactlyEntriesOf(expected);
        // run_stroomql says esql_query would have grouped them in Elasticsearch, and how many documents it read.
        assertThat(lastStroomQl).contains("This is an aggregation on an Elasticsearch index. Stroom read "
                                          + EVENTS + " documents from Elasticsearch to group them")
                .contains("pipeline '| STATS total = SUM(bytes) BY user.name, url.domain "
                          + "| KEEP user.name, url.domain, total'");
        // The translated pipeline is valid ES|QL, and gives the same totals.
        final Map<String, Object> translated = json("esql_query", args("index", index.getUuid(), "pipeline",
                "| STATS total = SUM(bytes) BY user.name, url.domain | KEEP user.name, url.domain, total"));
        assertThat(number(translated, "row_count")).isEqualTo(viaStroom.size());
        // describe_data_source steers aggregations to esql_query, with an example from the index's fields.
        assertThat(McpTestClient.text(client.call("describe_data_source", args("data_source", index.getUuid()))))
                .contains("Aggregations on this index (Elasticsearch 9.")
                .contains("use esql_query");
    }

    @Test
    void theTimeRangeAndTheRowsTimesAreInTheZoneGiven() {
        // One event a minute from 2026-01-01T00:00Z: 12:00 to 13:00 in Canberra (+11:00 in January) is 01:00Z to
        // 02:00Z, 60 events.
        final DocRef index = seededIndex(EVENTS, false);
        final Map<String, Object> arguments = args("index", index.getUuid(), "pipeline",
                "| STATS n = COUNT(*), earliest = MIN(@timestamp)", "timeFrom", "2026-01-01T12:00:00", "timeTo",
                "2026-01-01T13:00:00", "time_zone", "Australia/Canberra");

        final Map<String, Object> inCanberra = json("esql_query", arguments);
        dump("esql_query in Canberra", inCanberra);

        assertThat((List<List<Object>>) inCanberra.get("rows")).singleElement()
                .satisfies(row -> {
                    assertThat(((Number) row.getFirst()).longValue()).isEqualTo(60L);
                    assertThat(row.getLast()).isEqualTo("2026-01-01T12:00:00.000+11:00");
                });
        assertThat(inCanberra).containsEntry("time_zone", "Australia/Canberra")
                .containsEntry("time_zone_source", "given");
        assertThat((String) inCanberra.get("time_zone_esql_note")).contains("work in UTC");
        assertThat((Map<String, Object>) inCanberra.get("time_range")).containsEntry("from",
                "2026-01-01T12:00:00+11:00");

        arguments.put("time_zone", "UTC");
        final Map<String, Object> inUtc = json("esql_query", arguments);
        assertThat((List<List<Object>>) inUtc.get("rows")).singleElement()
                .satisfies(row -> {
                    assertThat(((Number) row.getFirst()).longValue()).isEqualTo(60L);
                    assertThat(row.getLast()).isEqualTo("2026-01-01T12:00:00.000Z");
                });
        assertThat(inUtc).containsEntry("time_zone", "UTC").doesNotContainKey("time_zone_esql_note");
    }

    @Test
    void theDocumentsOfAStreamNotInStroomAreCountedByEsqlQueryButNotRunStroomQl() {
        final DocRef index = seededIndex(EVENTS, true);

        final Map<String, Object> esql = json("esql_query", args("index", index.getUuid(), "pipeline",
                "| STATS n = COUNT(*), total = SUM(bytes)"));

        // esql_query doesn't look the documents' streams up (for speed): every event is counted, and it says so.
        long total = 0;
        long visibleTotal = 0;
        for (int i = 0; i < EVENTS; i++) {
            total += ProxyUploadEvents.bytes(i);
            if (inStroom(i)) {
                visibleTotal += ProxyUploadEvents.bytes(i);
            }
        }
        assertThat(longs((List<List<Object>>) esql.get("rows"))).containsExactly(List.of((long) EVENTS, total));
        // A Stroom search leaves out the hits of the stream not in Stroom.
        final Map<String, Long> viaStroom = stroomQlTotals(index);
        assertThat(viaStroom.values().stream().mapToLong(Long::longValue).sum())
                .isEqualTo(visibleTotal)
                .isLessThan(total);
    }

    @Test
    void aFromThatWouldReachAnotherIndexIsRefused() {
        final DocRef index = seededIndex(100, false);

        final CallToolResult refused = client.call("esql_query", args("index", index.getUuid(), "pipeline",
                "| FROM .security* | STATS c = COUNT(*)"));
        final String text = McpTestClient.text(refused);

        assertThat(text).contains("The ES|QL command FROM isn't allowed here")
                .contains("give only the commands after it");
        // Hidden in a comment the command is still found; in a string it is only text.
        assertThat(McpTestClient.text(client.call("esql_query", args("index", index.getUuid(), "pipeline",
                "| WHERE bytes > 0 /* hidden */ | /* hidden */ FROM other"))))
                .contains("FROM isn't allowed here");
        final Map<String, Object> inString = json("esql_query", args("index", index.getUuid(), "pipeline",
                "| WHERE message == \"x | FROM other\" | STATS c = COUNT(*)"));
        assertThat(longs((List<List<Object>>) inString.get("rows"))).containsExactly(List.of(0L));
    }

    @Test
    void rowsCutShortSayHowManyThereAre() {
        final DocRef index = seededIndex(EVENTS, false);

        final Map<String, Object> result = json("esql_query", args("index", index.getUuid(), "pipeline",
                "| KEEP user.name, bytes | SORT bytes DESC", "limit", 5));

        assertThat(result).containsEntry("truncated", true).containsEntry("grouped", false);
        assertThat(number(result, "row_count")).isEqualTo(5);
        assertThat(number(result, "total_rows")).isEqualTo(EVENTS);
        assertThat((String) result.get("note")).contains("The query had no LIMIT, so LIMIT 5 was added")
                .contains("there are " + EVENTS + " rows in all");
    }

    @Test
    void aWhereAndTimeRangeNarrowTheDocuments() {
        final DocRef index = seededIndex(EVENTS, false);

        final Map<String, Object> result = json("esql_query", args("index", index.getUuid(), "pipeline",
                "| STATS total = SUM(bytes)", "where", List.of(Map.of("field", "user.name", "value", "alice")),
                "timeFrom", "2026-01-01T00:00:00.000Z", "timeTo", "2026-01-01T10:00:00.000Z"));

        long total = 0;
        for (int i = 0; i < 600; i++) {
            if ("alice".equals(ProxyUploadEvents.user(i))) {
                total += ProxyUploadEvents.bytes(i);
            }
        }
        assertThat(longs((List<List<Object>>) result.get("rows"))).containsExactly(List.of(total));
    }

    /// Times the grouped question through esql_query and through run_stroomql on an index of 200,000 documents.
    /// Nothing is asserted of the timings, which are logged.
    @Test
    @PerformanceTest
    void timeTheGroupedQuestion() {
        final DocRef index = seededIndex(TIMED_EVENTS, false);
        // Once each first, so neither pays for a cold start.
        json("esql_query", args("index", index.getUuid(), "pipeline", TOP_UPLOADERS));
        stroomQlTotals(index);

        final long esqlStart = System.nanoTime();
        final Map<String, Object> esql = json("esql_query", args("index", index.getUuid(), "pipeline",
                TOP_UPLOADERS));
        final long esqlMs = (System.nanoTime() - esqlStart) / 1_000_000;
        final long stroomQlStart = System.nanoTime();
        final Map<String, Long> viaStroom = stroomQlTotals(index);
        final long stroomQlMs = (System.nanoTime() - stroomQlStart) / 1_000_000;

        assertThat(totals((List<List<Object>>) esql.get("rows"))).containsExactlyEntriesOf(top(viaStroom, 10));
        LOGGER.info(() -> LogUtil.message("Grouped question on {} documents: esql_query {} ms (Elasticsearch took "
                                          + "{} ms), run_stroomql {} ms", TIMED_EVENTS, esqlMs,
                esql.get("took_ms"), stroomQlMs));
    }

    // An Elastic Index doc beside the standard templates, on an index of that many events, in STREAMS streams in
    // Stroom and, if notInStroom, every STREAMS + 1th event in one not in Stroom (deleted, say), whose events no
    // Stroom search finds.
    private DocRef seededIndex(final int events, final boolean notInStroom) {
        final List<Long> streams = new ArrayList<>();
        asUser(admin, () -> {
            for (int i = 0; i < STREAMS; i++) {
                streams.add(metaService.create(MetaProperties.builder()
                        .feedName("ESQL-E2E-" + indexName.toUpperCase(Locale.ROOT))
                        .typeName(StreamTypeNames.EVENTS)
                        .createMs(Instant.now().toEpochMilli())
                        .build(), Status.UNLOCKED).getId());
            }
        });
        if (notInStroom) {
            streams.add(streams.getLast() + 1_000);
        }
        ProxyUploadEvents.seed(node, indexName, events, streams);
        final DocRef cluster = liveElasticCluster(node, "E2E ES|QL cluster " + indexName);
        final ExplorerNode created = create(ElasticIndexDoc.TYPE, "E2E proxy uploads " + indexName,
                templateFolder());
        asUser(admin, () -> elasticIndexStore.writeDocument(elasticIndexStore.readDocument(created.getDocRef())
                .copy()
                .clusterRef(cluster)
                .indexName(indexName)
                .timeField("@timestamp")
                .build()));
        return created.getDocRef();
    }

    // Each user and domain's total bytes, through run_stroomql, the most first.
    private Map<String, Long> stroomQlTotals(final DocRef index) {
        lastStroomQl = McpTestClient.text(client.call("run_stroomql", args("query",
                "from \"" + index.getName() + "\"\n"
                + "group by user.name, url.domain\n"
                + "select user.name, url.domain, sum(bytes) as total")));
        final Matcher matcher = RESULTS_PATH.matcher(lastStroomQl);
        assertThat(matcher.find()).as("run_stroomql wrote its results: %s", lastStroomQl).isTrue();
        final String file = McpTestClient.text(client.call("read_file", args("path", matcher.group(1),
                "count", 1_000)));
        final List<List<String>> rows = new ArrayList<>();
        List<String> header = null;
        for (final String line : file.split("\n")) {
            final int tab = line.indexOf('\t');
            final String row = tab < 0
                    ? line
                    : line.substring(tab + 1);
            if (!row.startsWith("|") || row.startsWith("| ---") || row.startsWith("|---")) {
                continue;
            }
            final List<String> cells = new ArrayList<>();
            for (final String cell : row.substring(1, row.lastIndexOf('|')).split("\\|")) {
                cells.add(cell.strip());
            }
            if (header == null) {
                header = cells;
            } else {
                rows.add(cells);
            }
        }
        assertThat(header).as("run_stroomql's table: %s", file).isNotNull();
        final int user = header.indexOf("user.name");
        final int domain = header.indexOf("url.domain");
        final int total = header.indexOf("total");
        final Map<String, Long> totals = new LinkedHashMap<>();
        rows.stream()
                .sorted((a, b) -> {
                    final int byTotal = Long.compare(number(b.get(total)), number(a.get(total)));
                    return byTotal != 0
                            ? byTotal
                            : (a.get(user) + "|" + a.get(domain)).compareTo(b.get(user) + "|" + b.get(domain));
                })
                .forEach(row -> totals.put(row.get(user) + "|" + row.get(domain), number(row.get(total))));
        return totals;
    }

    // Whether an event is in one of the streams in Stroom, when there is one not in Stroom.
    private boolean inStroom(final int i) {
        return i % (STREAMS + 1) != STREAMS;
    }

    private static long number(final Map<String, Object> result, final String key) {
        assertThat(result.get(key)).as(key).isInstanceOf(Number.class);
        return ((Number) result.get(key)).longValue();
    }

    private static List<List<Long>> longs(final List<List<Object>> rows) {
        return rows.stream()
                .map(row -> row.stream().map(value -> ((Number) value).longValue()).toList())
                .toList();
    }

    private static long number(final String text) {
        return (long) Double.parseDouble(text.replace(",", ""));
    }

    private static Map<String, Long> totals(final List<List<Object>> rows) {
        final Map<String, Long> totals = new LinkedHashMap<>();
        for (final List<Object> row : rows) {
            totals.put(row.get(1) + "|" + row.get(2), ((Number) row.get(0)).longValue());
        }
        return totals;
    }

    private static Map<String, Long> top(final Map<String, Long> totals, final int n) {
        final Map<String, Long> top = new LinkedHashMap<>();
        totals.entrySet().stream().limit(n).forEach(entry -> top.put(entry.getKey(), entry.getValue()));
        return top;
    }
}
