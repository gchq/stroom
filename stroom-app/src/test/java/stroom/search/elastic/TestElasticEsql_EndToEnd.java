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

import stroom.ai.api.ElasticEsql;
import stroom.ai.api.ElasticEsql.Column;
import stroom.ai.api.ElasticEsql.Reason;
import stroom.ai.api.ElasticEsql.Refusal;
import stroom.ai.api.ElasticEsql.Request;
import stroom.ai.api.ElasticEsql.Result;
import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Status;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.TimeRange;
import stroom.test.AbstractCoreIntegrationTest;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Runs ES|QL queries through [ElasticEsql] on a real Elasticsearch node, on an index of synthetic proxy upload
/// events ([ProxyUploadEvents]), checking each answer against the totals worked out from the same events. The events
/// are in three streams in turn, two in Stroom and one not: as the documents' streams aren't looked up (for speed),
/// the events of the stream not in Stroom are in every answer, though a Stroom search would leave them out. See
/// [ElasticTestNode] for how the node is found.
class TestElasticEsql_EndToEnd extends AbstractCoreIntegrationTest {

    private static final int EVENTS = 2_000;
    private static final String TOP_UPLOADERS = """
            | STATS total = SUM(bytes) BY user.name, url.domain
            | SORT total DESC, user.name, url.domain
            | LIMIT 10""";

    @Inject
    private ElasticEndToEndTestHelper helper;
    @Inject
    private ElasticEsql esql;
    @Inject
    private MetaService metaService;

    private ElasticTestNode node;
    private String indexName;
    private DocRef indexRef;

    @BeforeEach
    void setUp() {
        node = ElasticTestNode.getOrSkip();
        indexName = ElasticTestNode.createIndexName("esql");
        final long first = stream();
        final long second = stream();
        // A stream that isn't in Stroom (deleted, say), whose events are still counted: every third event.
        ProxyUploadEvents.seed(node, indexName, EVENTS, List.of(first, second, second + 1_000));
        final DocRef clusterRef = helper.createCluster(node);
        indexRef = helper.createIndexDoc(clusterRef, indexName, "@timestamp", 100);
    }

    @AfterEach
    void tearDown() {
        if (node != null && indexName != null) {
            node.deleteIndices(indexName);
        }
    }

    @Test
    void testTopUploadersByDomain() {
        final Result result = esql.query(new Request(indexRef, TOP_UPLOADERS, null, null, null, null, true));

        assertThat(result.columns()).containsExactly(new Column("total", "long"),
                new Column("user.name", "keyword"), new Column("url.domain", "keyword"));
        assertThat(asTotals(result)).containsExactlyEntriesOf(top(
                ProxyUploadEvents.totalsByUserAndDomain(EVENTS, i -> true), 10));
        assertThat(result.limit()).isEqualTo(10);
        assertThat(result.truncated()).isTrue();
        // Every user and domain pair: the number of groups the top 10 are taken from.
        assertThat(result.totalRows()).isEqualTo(
                (long) ProxyUploadEvents.totalsByUserAndDomain(EVENTS, i -> true).size());
        assertThat(result.query()).startsWith("FROM " + indexName + "\n");
        assertThat(result.version()).startsWith("9.");
    }

    @Test
    void testFilterAndTimeRange() {
        // The first 600 events are in the first ten hours; of those, alice's allowed ones.
        final ExpressionOperator filter = ExpressionOperator.builder()
                .addTerm("user.name", Condition.EQUALS, "alice")
                .addTerm("event.action", Condition.EQUALS, "allowed")
                .build();
        final TimeRange tenHours = new TimeRange("Ten hours", "2026-01-01T00:00:00.000Z",
                "2026-01-01T10:00:00.000Z");

        final Result result = esql.query(new Request(indexRef, "| STATS n = COUNT(*), total = SUM(bytes)", filter,
                tenHours, null, null, false));

        long count = 0;
        long total = 0;
        for (int i = 0; i < 600; i++) {
            if ("alice".equals(ProxyUploadEvents.user(i)) && !ProxyUploadEvents.blocked(i)) {
                count++;
                total += ProxyUploadEvents.bytes(i);
            }
        }
        assertThat(result.rows()).containsExactly(List.of(count, total));
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void testRowsCutShortAreSaidToBe() {
        final Result result = esql.query(new Request(indexRef, "| KEEP user.name, bytes | SORT bytes DESC", null,
                null, 5, null, true));

        assertThat(result.rows()).hasSize(5);
        assertThat(result.limit()).isEqualTo(5);
        assertThat(result.limitNote()).contains("LIMIT 5 was added");
        assertThat(result.truncated()).isTrue();
        // Every event, those of the stream not in Stroom included: a Stroom search would find two thirds of them.
        assertThat(result.totalRows()).isEqualTo(EVENTS);
    }

    @Test
    void testFromInjectionIsRefused() {
        assertThatThrownBy(() -> esql.query(new Request(indexRef, "| FROM .security* | STATS c = COUNT(*)", null,
                null, null, null, false)))
                .isInstanceOf(Refusal.class)
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.COMMAND_NOT_ALLOWED));
        // A pipe inside a string is no way round it: the whole string is a value.
        final Result result = esql.query(new Request(indexRef,
                "| WHERE message == \"x | FROM other\" | STATS c = COUNT(*)", null, null, null, null, false));
        assertThat(result.rows()).containsExactly(List.of(0L));
    }

    @Test
    void testUnknownField() {
        assertThatThrownBy(() -> esql.query(new Request(indexRef, "| STATS total = SUM(bytes) BY usr.name", null,
                null, null, null, false)))
                .isInstanceOf(Refusal.class)
                .satisfies(e -> {
                    final Refusal refusal = (Refusal) e;
                    assertThat(refusal.reason()).isEqualTo(Reason.UNKNOWN_FIELD);
                    assertThat(refusal.names()).containsExactly("usr.name");
                });
    }

    @Test
    void testTextFieldGroupedByItsKeywordSubField() {
        assertThatThrownBy(() -> esql.query(new Request(indexRef, "| STATS c = COUNT(*) BY message", null, null,
                null, null, false)))
                .isInstanceOf(Refusal.class)
                .satisfies(e -> {
                    final Refusal refusal = (Refusal) e;
                    assertThat(refusal.reason()).isEqualTo(Reason.TEXT_GROUPING);
                    assertThat(refusal.names()).containsExactly("message", "message.keyword");
                });
        final Result result = esql.query(new Request(indexRef, "| STATS c = COUNT(*) BY message.keyword", null,
                null, 3, null, true));
        assertThat(result.totalRows()).isEqualTo(EVENTS);
    }

    @Test
    void testAComparisonOnAMultiValuedFieldIsWarnedOf() {
        // One more event whose user.name has two values: Elasticsearch gives null for a comparison on it, so
        // WHERE drops it, and says so only in a warning header, which must come through.
        final ElasticTestNode.Response indexed = node.request("POST", "/" + indexName + "/_doc?refresh=true", """
                {"@timestamp": "2026-01-01T00:00:00.000Z", "user": {"name": ["alice", "zed"]}}""");
        assertThat(indexed.isSuccess()).as(indexed.body()).isTrue();

        final Result compared = esql.query(new Request(indexRef, "| WHERE user.name == \"zed\" | STATS n = COUNT(*)",
                null, null, null, null, false));

        assertThat(compared.rows()).containsExactly(List.of(0L));
        assertThat(compared.warnings())
                .anySatisfy(warning -> assertThat(warning).contains("single-value function encountered multi-value"))
                .noneSatisfy(warning -> assertThat(warning).contains("No limit defined"));

        final Result expanded = esql.query(new Request(indexRef, "| MV_EXPAND user.name | WHERE user.name == \"zed\" "
                                                                 + "| STATS n = COUNT(*)", null, null, null, null,
                false));

        assertThat(expanded.rows()).containsExactly(List.of(1L));
        assertThat(expanded.warnings()).isEmpty();
    }

    private long stream() {
        return metaService.create(MetaProperties.builder()
                .feedName("ESQL-E2E")
                .typeName(StreamTypeNames.EVENTS)
                .createMs(Instant.now().toEpochMilli())
                .build(), Status.UNLOCKED).getId();
    }

    private static Map<String, Long> asTotals(final Result result) {
        final Map<String, Long> totals = new LinkedHashMap<>();
        for (final List<Object> row : result.rows()) {
            totals.put(row.get(1) + "|" + row.get(2), ((Number) row.get(0)).longValue());
        }
        return totals;
    }

    private static Map<String, Long> top(final Map<String, Long> totals, final int n) {
        final Map<String, Long> top = new LinkedHashMap<>();
        new ArrayList<>(totals.entrySet()).stream()
                .limit(n)
                .forEach(entry -> top.put(entry.getKey(), entry.getValue()));
        return top;
    }
}
