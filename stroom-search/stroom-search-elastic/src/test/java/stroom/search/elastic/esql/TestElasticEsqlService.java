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

package stroom.search.elastic.esql;

import stroom.ai.api.ElasticEsql.Column;
import stroom.ai.api.ElasticEsql.Reason;
import stroom.ai.api.ElasticEsql.Refusal;
import stroom.ai.api.ElasticEsql.Request;
import stroom.ai.api.ElasticEsql.Result;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.TimeRange;
import stroom.query.api.datasource.FieldType;
import stroom.query.common.v2.MockIndexFieldCache;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.ElasticWireMock;
import stroom.search.elastic.search.ElasticSearchConfig;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.PermissionException;
import stroom.util.time.StroomDuration;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/// Tests [ElasticEsqlService] with the real Elasticsearch Java client against a WireMock server standing in for an
/// Elasticsearch cluster.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestElasticEsqlService {

    private static final String INDEX_NAME = "proxy-logs";
    private static final String QUERY_PATH = "/_query";
    private static final DocRef INDEX_REF = new DocRef(ElasticIndexDoc.TYPE, "index-uuid", "Proxy Logs");
    private static final DocRef CLUSTER_REF = new DocRef(ElasticClusterDoc.TYPE, "cluster-uuid", "Cluster");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GROUPED = """
            {"took": 12, "is_partial": false,
             "columns": [{"name": "total", "type": "long"}, {"name": "user", "type": "keyword"}],
             "values": [[300, "alice"], [200, "bob"]]}""";

    @RegisterExtension
    static final WireMockExtension ELASTIC = ElasticWireMock.createExtension();

    @Mock
    private SecurityContext securityContext;
    @Mock
    private ElasticIndexStore elasticIndexStore;
    @Mock
    private ElasticClusterStore elasticClusterStore;
    @Mock
    private ElasticClientCache elasticClientCache;

    private ElasticsearchClient client;
    private MockIndexFieldCache indexFieldCache;
    private ElasticSearchConfig config;
    private ElasticEsqlService service;

    @BeforeEach
    void setUp() {
        client = ElasticWireMock.createClient(ELASTIC);
        indexFieldCache = new MockIndexFieldCache();
        config = new ElasticSearchConfig();
        final ElasticClusterDoc cluster = ElasticClusterDoc.builder()
                .uuid(CLUSTER_REF.getUuid())
                .connection(new ElasticConnectionConfig(List.of("http://localhost:9200"), null, null, null))
                .build();
        when(elasticClusterStore.readDocument(CLUSTER_REF)).thenReturn(cluster);
        when(elasticClientCache.contextResult(eq(cluster.getConnection()), any()))
                .thenAnswer(invocation -> invocation
                        .<Function<ElasticsearchClient, ?>>getArgument(1)
                        .apply(client));
        when(securityContext.hasDocumentPermission(INDEX_REF, DocumentPermission.USE)).thenReturn(true);
        when(securityContext.useAsReadResult(any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
        when(elasticIndexStore.readDocument(INDEX_REF)).thenReturn(createIndex(INDEX_NAME));
        service = new ElasticEsqlService(
                securityContext,
                elasticIndexStore,
                elasticClusterStore,
                elasticClientCache,
                () -> config,
                null,
                indexFieldCache,
                null);
        stubVersion("9.5.3");
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    @Test
    void testGroupedQuery() {
        stubQuery(GROUPED);

        final Result result = service.query(request("| STATS total = SUM(bytes) BY user | SORT total DESC"));

        assertThat(result.columns()).containsExactly(new Column("total", "long"), new Column("user", "keyword"));
        assertThat(result.rows()).containsExactly(List.of(300L, "alice"), List.of(200L, "bob"));
        assertThat(result.tookMs()).isEqualTo(12);
        assertThat(result.limit()).isEqualTo(1000);
        assertThat(result.truncated()).isFalse();
        assertThat(result.totalRows()).isNull();
        assertThat(result.version()).isEqualTo("9.5.3");
        assertThat(result.limitNote()).contains("had no LIMIT");
        assertThat(result.query()).isEqualTo("""
                FROM proxy-logs
                | STATS total = SUM(bytes) BY user
                | SORT total DESC
                | LIMIT 1000""");

        final JsonNode body = onlyQueryBody();
        assertThat(body.path("query").asString()).isEqualTo(result.query());
        assertThat(body.has("filter")).isFalse();
        // Partial results are refused, so a failed shard fails the query.
        assertThat(onlyQueryRequest().queryParameter("allow_partial_results").firstValue()).isEqualTo("false");
    }

    @Test
    void testTruncatedGroupsAreCounted() {
        stubQuery(GROUPED);
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(QUERY_PATH))
                .withRequestBody(WireMock.containing("stroom_total_rows"))
                .atPriority(1)
                .willReturn(ElasticWireMock.elasticResponse(200, """
                        {"took": 3, "columns": [{"name": "stroom_total_rows", "type": "long"}], "values": [[42]]}""")));

        final Result result = service.query(new Request(INDEX_REF, "| STATS total = SUM(bytes) BY user "
                                                                   + "| SORT total DESC | LIMIT 2", null, null, null,
                null, true));

        assertThat(result.limit()).isEqualTo(2);
        assertThat(result.limitNote()).isNull();
        assertThat(result.truncated()).isTrue();
        assertThat(result.totalRows()).isEqualTo(42);
        final List<LoggedRequest> requests = ELASTIC.findAll(WireMock.postRequestedFor(
                WireMock.urlPathEqualTo(QUERY_PATH)));
        assertThat(requests).hasSize(2);
        assertThat(json(requests.get(1).getBodyAsString()).path("query").asString()).isEqualTo("""
                FROM proxy-logs
                | STATS total = SUM(bytes) BY user
                | STATS stroom_total_rows = COUNT(*)""");
    }

    @Test
    void testTotalNotCountedUnlessTruncated() {
        stubQuery(GROUPED);

        final Result result = service.query(new Request(INDEX_REF, "| STATS total = SUM(bytes) BY user", null, null,
                null, null, true));

        assertThat(result.truncated()).isFalse();
        assertThat(result.totalRows()).isNull();
        assertThat(ELASTIC.findAll(WireMock.postRequestedFor(WireMock.urlPathEqualTo(QUERY_PATH)))).hasSize(1);
    }

    @Test
    void testLimitCapped() {
        stubQuery(GROUPED);

        final Result result = service.query(new Request(INDEX_REF, "| SORT bytes DESC", null, null, 50_000, null,
                false));

        assertThat(result.limit()).isEqualTo(10_000);
        assertThat(result.limitNote()).contains("over 10000").contains("had no LIMIT");
        assertThat(result.query()).endsWith("| LIMIT 10000");
    }

    @Test
    void testConfiguredDefaultLimit() {
        config = new ElasticSearchConfig(null, null, null, null, null, null, 25, null);
        stubQuery(GROUPED);

        assertThat(service.query(request("")).query()).isEqualTo("FROM proxy-logs\n| LIMIT 25");
    }

    @Test
    void testFilterAndTimeRangeAreTheRequestFilter() {
        indexFieldCache.put("user", ElasticIndexField.builder().fldName("user").fldType(FieldType.KEYWORD)
                .nativeType("keyword").build());
        stubQuery(GROUPED);
        final ExpressionOperator filter = ExpressionOperator.builder()
                .addTerm("user", Condition.EQUALS, "alice")
                .build();

        service.query(new Request(INDEX_REF, "| STATS c = COUNT(*)", filter, new TimeRange("Two days", null,
                "2026-01-02T00:00:00.000Z", "2026-01-04T00:00:00.000Z"), null, null, false));

        final JsonNode body = onlyQueryBody();
        assertThat(body.path("query").asString()).doesNotContain("alice").doesNotContain("@timestamp");
        final String filterJson = body.path("filter").toString();
        assertThat(filterJson)
                .contains("\"user\"")
                .contains("alice")
                .contains("\"@timestamp\"")
                .contains("\"gte\":1767312000000")
                .contains("\"lt\":1767484800000");
    }

    @Test
    void testBadTimeIsRefused() {
        assertThatThrownBy(() -> service.query(new Request(INDEX_REF, "", null, new TimeRange(null, null,
                "last tuesday", null), null, null, false)))
                .isInstanceOf(Refusal.class)
                .hasMessageContaining("can't be read");
    }

    @Test
    void testFromInjectionIsRefusedBeforeAnythingIsSent() {
        assertThatThrownBy(() -> service.query(request("| FROM secret-index | STATS c = COUNT(*)")))
                .isInstanceOf(Refusal.class)
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.COMMAND_NOT_ALLOWED));
        assertThatThrownBy(() -> service.query(request("| WHERE a == \"x\" /* | FROM secret-index")))
                .isInstanceOf(Refusal.class)
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.INVALID));

        assertThat(ELASTIC.findAll(WireMock.postRequestedFor(WireMock.urlPathEqualTo(QUERY_PATH)))).isEmpty();
    }

    @Test
    void testPermissionChecked() {
        when(securityContext.hasDocumentPermission(INDEX_REF, DocumentPermission.USE)).thenReturn(false);

        assertThatThrownBy(() -> service.query(request("")))
                .isInstanceOf(PermissionException.class);
        assertThat(ELASTIC.findAll(WireMock.anyRequestedFor(WireMock.anyUrl()))).isEmpty();
    }

    @Test
    void testOldClusterRefused() {
        stubVersion("8.13.4");

        assertThatThrownBy(() -> service.query(request("| STATS c = COUNT(*)")))
                .isInstanceOf(Refusal.class)
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.CLUSTER_TOO_OLD))
                .hasMessageContaining("8.13.4")
                .hasMessageContaining("8.14 or later");
        assertThat(ELASTIC.findAll(WireMock.postRequestedFor(WireMock.urlPathEqualTo(QUERY_PATH)))).isEmpty();
    }

    @Test
    void testPartialResultsNotAskedOfOlderClusters() {
        stubVersion("8.14.0");
        stubQuery(GROUPED);

        service.query(request(""));

        assertThat(onlyQueryRequest().queryParameter("allow_partial_results").isPresent()).isFalse();
    }

    @Test
    void testVersions() {
        assertThat(ElasticEsqlService.atLeast("8.14.0", ElasticEsqlService.MIN_VERSION)).isTrue();
        assertThat(ElasticEsqlService.atLeast("8.14.0-SNAPSHOT", ElasticEsqlService.MIN_VERSION)).isTrue();
        assertThat(ElasticEsqlService.atLeast("9.0.0", ElasticEsqlService.MIN_VERSION)).isTrue();
        assertThat(ElasticEsqlService.atLeast("8.13.9", ElasticEsqlService.MIN_VERSION)).isFalse();
        assertThat(ElasticEsqlService.atLeast("7.17.0", ElasticEsqlService.MIN_VERSION)).isFalse();
        assertThat(ElasticEsqlService.atLeast("unknown", ElasticEsqlService.MIN_VERSION)).isFalse();
        assertThat(ElasticEsqlService.atLeast(null, ElasticEsqlService.MIN_VERSION)).isFalse();
    }

    @Test
    void testUnknownColumn() {
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(QUERY_PATH))
                .willReturn(ElasticWireMock.elasticResponse(400, """
                        {"error": {"root_cause": [{"type": "verification_exception",
                         "reason": "Found 1 problem\\nline 2:28: Unknown column [usr]"}],
                         "type": "verification_exception",
                         "reason": "Found 1 problem\\nline 2:28: Unknown column [usr]"}, "status": 400}""")));

        assertThatThrownBy(() -> service.query(request("| STATS c = COUNT(*) BY usr")))
                .isInstanceOf(Refusal.class)
                .satisfies(e -> {
                    final Refusal refusal = (Refusal) e;
                    assertThat(refusal.reason()).isEqualTo(Reason.UNKNOWN_FIELD);
                    assertThat(refusal.names()).containsExactly("usr");
                })
                .hasMessage("Elasticsearch refused the query: line 2:28: Unknown column [usr]");
    }

    @Test
    void testOtherErrorsInElasticsearchsWords() {
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(QUERY_PATH))
                .willReturn(ElasticWireMock.elasticResponse(400, """
                        {"error": {"type": "parsing_exception", "reason": "line 2:3: mismatched input 'STATZ'"},
                         "status": 400}""")));

        assertThatThrownBy(() -> service.query(request("| STATS c = COUNT(*) BY user")))
                .isInstanceOf(Refusal.class)
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.REFUSED))
                .hasMessageContaining("mismatched input 'STATZ'");
    }

    @Test
    void testTimeout() {
        config = new ElasticSearchConfig(null, null, null, null, null, null, null, StroomDuration.ofMillis(300));
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(QUERY_PATH))
                .willReturn(ElasticWireMock.elasticResponse(200, GROUPED).withFixedDelay(3_000)));

        assertThatThrownBy(() -> service.query(request("| STATS c = COUNT(*)")))
                .isInstanceOf(Refusal.class)
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.TIMEOUT))
                .hasMessageContaining("cancelled");
    }

    @Test
    void testRequestedTimeLimitIsNeverLongerThanConfigured() {
        config = new ElasticSearchConfig(null, null, null, null, null, null, null, StroomDuration.ofMillis(300));
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(QUERY_PATH))
                .willReturn(ElasticWireMock.elasticResponse(200, GROUPED).withFixedDelay(3_000)));

        assertThatThrownBy(() -> service.query(new Request(INDEX_REF, "", null, null, null, Duration.ofMinutes(10),
                false)))
                .isInstanceOf(Refusal.class)
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.TIMEOUT));
    }

    @Test
    void testTextGroupingRefusedWithKeywordSubField() {
        assertThatThrownBy(() -> service.query(request("| STATS c = COUNT(*) BY message")))
                .isInstanceOf(Refusal.class)
                .satisfies(e -> {
                    final Refusal refusal = (Refusal) e;
                    assertThat(refusal.reason()).isEqualTo(Reason.TEXT_GROUPING);
                    assertThat(refusal.names()).containsExactly("message", "message.keyword");
                })
                .hasMessageContaining("group by its keyword sub-field 'message.keyword'");
        // Without a keyword sub-field there is nothing better to group by.
        stubQuery(GROUPED);
        assertThat(service.query(request("| STATS c = COUNT(*) BY title")).rows()).hasSize(2);
    }

    @Test
    void testIndexNameThatFromCantRead() {
        when(elasticIndexStore.readDocument(INDEX_REF)).thenReturn(createIndex("logs | FROM secret"));

        assertThatThrownBy(() -> service.query(request("")))
                .isInstanceOf(Refusal.class)
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.NOT_QUERYABLE));
    }

    @Test
    void testIndexPatternsAndListsAreRead() {
        when(elasticIndexStore.readDocument(INDEX_REF)).thenReturn(createIndex("logs-*,archive-2026.*"));
        stubQuery(GROUPED);

        assertThat(service.query(request("")).query()).startsWith("FROM logs-*,archive-2026.*\n");
    }

    @Test
    void testMissingIndexDoc() {
        when(elasticIndexStore.readDocument(INDEX_REF)).thenReturn(null);

        assertThatThrownBy(() -> service.query(request("")))
                .isInstanceOf(Refusal.class)
                .satisfies(e -> assertThat(((Refusal) e).reason()).isEqualTo(Reason.NOT_FOUND));
    }

    @Test
    void testTypedValues() {
        final ElasticEsqlService.Response response = ElasticEsqlService.parse(json("""
                {"took": 1, "columns": [
                  {"name": "l", "type": "long"}, {"name": "d", "type": "double"}, {"name": "b", "type": "boolean"},
                  {"name": "u", "type": "unsigned_long"}, {"name": "m", "type": "keyword"},
                  {"name": "n", "type": "keyword"}, {"name": "t", "type": "date"}],
                 "values": [[1, 2.5, true, 18446744073709551615, ["a", "b"], null, "2026-01-01T00:00:00.000Z"]]}"""));

        assertThat(response.rows().getFirst()).containsExactly(1L, 2.5, true,
                new BigInteger("18446744073709551615"), List.of("a", "b"), null, "2026-01-01T00:00:00.000Z");
    }

    @Test
    void testPartialResultsRefused() {
        assertThatThrownBy(() -> ElasticEsqlService.parse(json("""
                {"took": 1, "is_partial": true, "columns": [], "values": []}""")))
                .isInstanceOf(Refusal.class)
                .hasMessageContaining("only part of the results");
    }

    private static Request request(final String pipeline) {
        return new Request(INDEX_REF, pipeline, null, null, null, null, false);
    }

    private void stubVersion(final String version) {
        ELASTIC.stubFor(WireMock.get(WireMock.urlPathEqualTo("/"))
                .willReturn(ElasticWireMock.elasticResponse(200, """
                        {"name": "node", "cluster_name": "c", "cluster_uuid": "u",
                         "version": {"number": "%s", "build_flavor": "default", "build_type": "docker",
                          "build_hash": "h", "build_date": "2026-01-01T00:00:00Z", "build_snapshot": false,
                          "lucene_version": "10", "minimum_wire_compatibility_version": "8.0.0",
                          "minimum_index_compatibility_version": "8.0.0"},
                         "tagline": "You Know, for Search"}""".formatted(version))));
    }

    private void stubQuery(final String body) {
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(QUERY_PATH))
                .atPriority(5)
                .willReturn(ElasticWireMock.elasticResponse(200, body)));
    }

    private LoggedRequest onlyQueryRequest() {
        final List<LoggedRequest> requests = ELASTIC.findAll(WireMock.postRequestedFor(
                WireMock.urlPathEqualTo(QUERY_PATH)));
        assertThat(requests).hasSize(1);
        return requests.getFirst();
    }

    private JsonNode onlyQueryBody() {
        return json(onlyQueryRequest().getBodyAsString());
    }

    private static JsonNode json(final String text) {
        return MAPPER.readTree(text);
    }

    private static ElasticIndexDoc createIndex(final String indexName) {
        return ElasticIndexDoc.builder()
                .uuid(INDEX_REF.getUuid())
                .name(INDEX_REF.getName())
                .clusterRef(CLUSTER_REF)
                .indexName(indexName)
                .timeField("@timestamp")
                .fields(List.of(
                        ElasticIndexField.builder().fldName("user").fldType(FieldType.KEYWORD).nativeType("keyword")
                                .build(),
                        ElasticIndexField.builder().fldName("bytes").fldType(FieldType.LONG).nativeType("long")
                                .build(),
                        ElasticIndexField.builder().fldName("message").fldType(FieldType.TEXT).nativeType("text")
                                .build(),
                        ElasticIndexField.builder().fldName("message.keyword").fldType(FieldType.KEYWORD)
                                .nativeType("keyword").multiFieldParent("message").build(),
                        ElasticIndexField.builder().fldName("title").fldType(FieldType.TEXT).nativeType("text")
                                .build(),
                        ElasticIndexField.builder().fldName("@timestamp").fldType(FieldType.DATE).nativeType("date")
                                .build()))
                .build();
    }
}
