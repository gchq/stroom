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

package stroom.search.elastic.search;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.FieldType;
import stroom.query.common.v2.MockIndexFieldCache;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.ElasticWireMock;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.search.elastic.shared.ElasticFieldStats;
import stroom.search.elastic.shared.ElasticFieldStatsRequest;
import stroom.search.elastic.shared.ElasticFieldValueCount;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticVirtualFieldGroup;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.PermissionException;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/// Tests [ElasticFieldStatsService] with the real Elasticsearch Java client against a WireMock server standing in
/// for an Elasticsearch cluster.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestElasticFieldStatsService {

    private static final String INDEX_NAME = "test-index";
    private static final String SEARCH_PATH = "/" + INDEX_NAME + "/_search";
    private static final String COUNT_PATH = "/" + INDEX_NAME + "/_count";
    private static final DocRef INDEX_REF = new DocRef(ElasticIndexDoc.TYPE, "index-uuid", "Test Index");
    private static final DocRef CLUSTER_REF = new DocRef(ElasticClusterDoc.TYPE, "cluster-uuid", "Cluster");
    private static final String SHARDS = """
            "took": 1, "timed_out": false,
            "_shards": {"total": 1, "successful": 1, "skipped": 0, "failed": 0},
            "hits": {"total": {"value": 100, "relation": "eq"}, "max_score": null, "hits": []},""";

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
    private ElasticFieldStatsService service;

    @BeforeEach
    void setUp() {
        client = ElasticWireMock.createClient(ELASTIC);
        indexFieldCache = new MockIndexFieldCache();
        final ElasticClusterDoc cluster = ElasticClusterDoc.builder()
                .uuid(CLUSTER_REF.getUuid())
                .connection(new ElasticConnectionConfig(List.of("http://localhost:9200"), null, null))
                .build();
        when(elasticClusterStore.readDocument(CLUSTER_REF)).thenReturn(cluster);
        when(elasticClientCache.contextResult(eq(cluster.getConnection()), any()))
                .thenAnswer(invocation -> invocation
                        .<Function<ElasticsearchClient, ?>>getArgument(1)
                        .apply(client));
        when(securityContext.hasDocumentPermission(INDEX_REF, DocumentPermission.USE)).thenReturn(true);
        when(securityContext.useAsReadResult(any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
        when(elasticIndexStore.readDocument(INDEX_REF)).thenReturn(createIndex());
        service = new ElasticFieldStatsService(
                securityContext,
                elasticIndexStore,
                elasticClusterStore,
                elasticClientCache,
                ElasticSearchConfig::new,
                null,
                indexFieldCache,
                null);
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    @Test
    void testKeywordFieldStatsFromRandomSample() {
        stubCount(100_000);
        stubSearch(null, randomSample(5_000, """
                "filter#has_value": {"doc_count": 90000},
                "cardinality#distinct": {"value": 3},
                "sterms#top": {
                  "doc_count_error_upper_bound": 0, "sum_other_doc_count": 0,
                  "buckets": [{"key": "web01", "doc_count": 50000}, {"key": "web02", "doc_count": 40000}]
                }"""));

        final ElasticFieldStats stats = service.getFieldStats(request("host", null, 1000L, 2000L, null));

        assertThat(stats.getMessage()).isNull();
        assertThat(stats.getExact()).isFalse();
        assertThat(stats.getSampleSize()).isEqualTo(5_000);
        assertThat(stats.getMaxSampleSize()).isEqualTo(50_000);
        assertThat(stats.getTotalDocs()).isEqualTo(100_000);
        assertThat(stats.getSampledDocs()).isEqualTo(5_000);
        // Counts within the random sampler are already scaled by Elasticsearch to estimate every document
        assertThat(stats.getDocsWithValue()).isEqualTo(90_000);
        assertThat(stats.getDistinctCount()).isEqualTo(3);
        assertThat(stats.getTopValues())
                .extracting(ElasticFieldValueCount::getValue, ElasticFieldValueCount::getCount)
                .containsExactly(tuple("web01", 50_000L), tuple("web02", 40_000L));
        assertThat(stats.getMin()).isNull();

        // Documents are sampled at random, within the time range on the doc's time field
        final String body = onlySearchRequest().getBodyAsString();
        assertThat(body)
                .contains("\"random_sampler\":{\"probability\":0.05}")
                .contains("\"@timestamp\"")
                .contains("\"gte\":1000")
                .contains("\"lt\":2000")
                .contains("\"terms\":{\"field\":\"host\",\"size\":10}");
        assertThat(onlyCountRequest().getBodyAsString()).contains("\"gte\":1000").contains("\"lt\":2000");
    }

    @Test
    void testNumericFieldStatsExact() {
        stubCount(1_000_000);
        stubSearch(null, """
                "filter#has_value": {"doc_count": 10},
                "cardinality#distinct": {"value": 5},
                "stats#stats": {"count": 10, "min": 0.0, "max": 100.0, "avg": 42.5, "sum": 425.0},
                "tdigest_percentiles#median": {"values": [{"key": 50.0, "value": 40.0}]}""");
        stubSearch("\"histogram\"", """
                "histogram#histogram": {"buckets": [{"key": 0.0, "doc_count": 4}, {"key": 5.0, "doc_count": 6}]}""");

        final ElasticFieldStats stats = service.getFieldStats(request("bytes", null, null, null, true));

        assertThat(stats.getExact()).isTrue();
        assertThat(stats.getTotalDocs()).isEqualTo(1_000_000);
        assertThat(stats.getSampledDocs()).isEqualTo(1_000_000);
        assertThat(stats.getMin()).isEqualTo("0");
        assertThat(stats.getMax()).isEqualTo("100");
        assertThat(stats.getMean()).isEqualTo(42.5);
        assertThat(stats.getMedian()).isEqualTo(40.0);
        assertThat(stats.getTopValues()).isNull();
        assertThat(stats.getHistogram())
                .extracting(ElasticFieldValueCount::getValue, ElasticFieldValueCount::getCount)
                .containsExactly(tuple("0", 4L), tuple("5", 6L));
        // Every document is included, with no time range
        assertThat(ELASTIC.findAll(WireMock.postRequestedFor(WireMock.urlPathEqualTo(SEARCH_PATH))))
                .hasSize(2)
                .allSatisfy(request -> assertThat(request.getBodyAsString())
                        .contains("\"match_all\"")
                        .doesNotContain("random_sampler"));
    }

    @Test
    void testSmallIndexIsExact() {
        // A sample of more than half the documents can't be random, so all of them are used
        stubCount(8_000);
        stubSearch(null, """
                "filter#has_value": {"doc_count": 7000}""");

        final ElasticFieldStats stats = service.getFieldStats(request("message", null, null, null, null));

        assertThat(stats.getExact()).isTrue();
        assertThat(stats.getSampledDocs()).isEqualTo(8_000);
        assertThat(stats.getDocsWithValue()).isEqualTo(7_000);
        assertThat(onlySearchRequest().getBodyAsString())
                .doesNotContain("random_sampler")
                .doesNotContain("cardinality");
    }

    @Test
    void testVirtualFieldStatsUseEditedGroups() {
        indexFieldCache.put("host", keyword("host"));
        stubCount(1_000_000);
        stubSearch(null, randomSample(5_000, """
                "filter#has_value": {"doc_count": 12000},
                "filters#groups": {"buckets": {"web": {"doc_count": 5000}, "db": {"doc_count": 7000}}}"""));
        final List<ElasticVirtualFieldGroup> groups = List.of(
                new ElasticVirtualFieldGroup("web", group("web01")),
                new ElasticVirtualFieldGroup("db", group("db01")));

        final ElasticFieldStats stats = service.getFieldStats(new ElasticFieldStatsRequest(
                INDEX_REF, "Category", null, null, null, groups, null));

        assertThat(stats.getDocsWithValue()).isEqualTo(12_000);
        assertThat(stats.getTopValues())
                .extracting(ElasticFieldValueCount::getValue, ElasticFieldValueCount::getCount)
                .containsExactly(tuple("db", 7_000L), tuple("web", 5_000L));
        assertThat(onlySearchRequest().getBodyAsString()).contains("web01").contains("db01");
    }

    @Test
    void testVirtualFieldGroupsWithDuplicateNames() {
        indexFieldCache.put("host", keyword("host"));
        final List<ElasticVirtualFieldGroup> groups = List.of(
                new ElasticVirtualFieldGroup("web", group("web01")),
                new ElasticVirtualFieldGroup("web", group("web02")));

        assertThatThrownBy(() -> service.getFieldStats(new ElasticFieldStatsRequest(
                INDEX_REF, "Category", null, null, null, groups, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than one group named 'web'");
        assertThat(ELASTIC.getAllServeEvents()).isEmpty();
    }

    @Test
    void testSampleSizeLimitedToMaximum() {
        stubCount(10_000_000);
        stubSearch(null, randomSample(50_000, """
                "filter#has_value": {"doc_count": 0}"""));

        final ElasticFieldStats stats = service.getFieldStats(request("message", 1_000_000, null, null, null));

        assertThat(stats.getSampleSize()).isEqualTo(50_000);
        assertThat(onlySearchRequest().getBodyAsString()).contains("\"probability\":0.005");
    }

    @Test
    void testGetSampleProbability() {
        assertThat(ElasticFieldStatsService.getSampleProbability(false, 5_000, 100_000)).isEqualTo(0.05);
        assertThat(ElasticFieldStatsService.getSampleProbability(false, 5_000, 10_000)).isEqualTo(0.5);
        assertThat(ElasticFieldStatsService.getSampleProbability(false, 5_000, 9_999)).isNull();
        assertThat(ElasticFieldStatsService.getSampleProbability(false, 5_000, 0)).isNull();
        assertThat(ElasticFieldStatsService.getSampleProbability(true, 5_000, 100_000)).isNull();
    }

    @Test
    void testNoPermission() {
        when(securityContext.hasDocumentPermission(INDEX_REF, DocumentPermission.USE)).thenReturn(false);

        assertThatThrownBy(() -> service.getFieldStats(request("host", null, null, null, null)))
                .isInstanceOf(PermissionException.class);
        assertThat(ELASTIC.getAllServeEvents()).isEmpty();
    }

    @Test
    void testConflictingField() {
        final ElasticFieldStats stats = service.getFieldStats(request("port", null, null, null, null));

        assertThat(stats.getMessage()).contains("conflicting types");
        assertThat(ELASTIC.getAllServeEvents()).isEmpty();
    }

    @Test
    void testUnknownField() {
        final ElasticFieldStats stats = service.getFieldStats(request("unknown", null, null, null, null));

        assertThat(stats.getMessage()).isEqualTo("Field 'unknown' was not found in any source index");
        assertThat(ELASTIC.getAllServeEvents()).isEmpty();
    }

    @Test
    void testFormatNumber() {
        assertThat(ElasticFieldStatsService.formatNumber(5.0)).isEqualTo("5");
        assertThat(ElasticFieldStatsService.formatNumber(-2.0)).isEqualTo("-2");
        assertThat(ElasticFieldStatsService.formatNumber(2.5)).isEqualTo("2.5");
    }

    private void stubCount(final long count) {
        ELASTIC.stubFor(WireMock.post(WireMock.urlPathEqualTo(COUNT_PATH))
                .willReturn(ElasticWireMock.elasticResponse(200, """
                        {"count": %d, "_shards": {"total": 1, "successful": 1, "skipped": 0, "failed": 0}}"""
                        .formatted(count))));
    }

    private void stubSearch(final String bodyContaining, final String aggregations) {
        final MappingBuilder mapping = WireMock.post(WireMock.urlPathEqualTo(SEARCH_PATH));
        if (bodyContaining != null) {
            mapping.withRequestBody(WireMock.containing(bodyContaining)).atPriority(1);
        } else {
            mapping.atPriority(5);
        }
        ELASTIC.stubFor(mapping.willReturn(ElasticWireMock.elasticResponse(200,
                "{" + SHARDS + "\"aggregations\": {" + aggregations + "}}")));
    }

    /// @return the aggregations of a search response, wrapped in a random sampler aggregate as Elasticsearch
    /// returns it, with the client's typed keys
    private static String randomSample(final long docCount, final String aggregations) {
        return """
                "random_sampler#sample": {
                  "seed": 123, "probability": 0.05, "doc_count": %d,
                  %s
                }""".formatted(docCount, aggregations);
    }

    private LoggedRequest onlySearchRequest() {
        return onlyRequest(SEARCH_PATH);
    }

    private LoggedRequest onlyCountRequest() {
        return onlyRequest(COUNT_PATH);
    }

    private LoggedRequest onlyRequest(final String path) {
        final List<LoggedRequest> requests = ELASTIC.findAll(WireMock.postRequestedFor(WireMock.urlPathEqualTo(path)));
        assertThat(requests).hasSize(1);
        return requests.getFirst();
    }

    private static ElasticFieldStatsRequest request(final String fieldName,
                                                    final Integer sampleSize,
                                                    final Long fromMs,
                                                    final Long toMs,
                                                    final Boolean exact) {
        return new ElasticFieldStatsRequest(INDEX_REF, fieldName, sampleSize, fromMs, toMs, null, exact);
    }

    private static ExpressionOperator group(final String host) {
        return ExpressionOperator.builder().addTerm("host", Condition.EQUALS, host).build();
    }

    private static ElasticIndexField keyword(final String name) {
        return ElasticIndexField.builder().fldName(name).fldType(FieldType.KEYWORD).nativeType("keyword").build();
    }

    private static ElasticIndexDoc createIndex() {
        return ElasticIndexDoc.builder()
                .uuid(INDEX_REF.getUuid())
                .name(INDEX_REF.getName())
                .clusterRef(CLUSTER_REF)
                .indexName(INDEX_NAME)
                .fields(List.of(
                        keyword("host"),
                        ElasticIndexField.builder().fldName("bytes").fldType(FieldType.LONG).nativeType("long")
                                .build(),
                        ElasticIndexField.builder().fldName("message").fldType(FieldType.TEXT).nativeType("text")
                                .build(),
                        ElasticIndexField.builder().fldName("port").fldType(FieldType.LONG).nativeType("long")
                                .conflictingTypes(Map.of("a", "long", "b", "keyword"))
                                .build()))
                .build();
    }
}
