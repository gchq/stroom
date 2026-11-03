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
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.QueryField;
import stroom.search.elastic.ElasticEndToEndTestHelper.SearchResult;
import stroom.search.elastic.search.ElasticFieldStatsService;
import stroom.search.elastic.search.ElasticSearchProvider;
import stroom.search.elastic.shared.ElasticFieldStats;
import stroom.search.elastic.shared.ElasticFieldStatsRequest;
import stroom.search.elastic.shared.ElasticFieldValueCount;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexFieldSettings;
import stroom.search.elastic.shared.ElasticVirtualFieldGroup;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.io.StreamUtil;
import stroom.util.json.JsonUtil;
import stroom.util.shared.EntityServiceException;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/// Searches and samples a real Elasticsearch index with field settings: a virtual field, whose groups stand for
/// conditions on a keyword field, and a disabled field. Uses the documents of [TestElasticSearchConditions_EndToEnd].
///
/// Documents 1 to 4 have a `Keyword` of `apple`, `banana`, `Cherry` and `apple pie`, a `Long` of -5, 0, 5 and 10
/// and a `Date` on 1 to 4 January 2026. Document 5 has none of these.
class TestElasticFieldSettings_EndToEnd extends AbstractCoreIntegrationTest {

    private static final Path INDEX_BODY = ElasticEndToEndTestHelper.RESOURCES_DIR.resolve("conditions-index.json");
    private static final Path DOCUMENTS = ElasticEndToEndTestHelper.RESOURCES_DIR.resolve("conditions-docs.json");
    private static final String ID = "Id";
    private static final String FEED = "ELASTIC_FIELD_SETTINGS";
    private static final String VIRTUAL_FIELD = "Fruit";

    @Inject
    private ElasticEndToEndTestHelper helper;
    @Inject
    private ElasticIndexStore elasticIndexStore;
    @Inject
    private ElasticSearchProvider elasticSearchProvider;
    @Inject
    private ElasticFieldStatsService elasticFieldStatsService;

    private ElasticTestNode node;
    private String indexName;
    private DocRef indexRef;

    @BeforeEach
    void setUp() {
        node = ElasticTestNode.getOrSkip();
        indexName = ElasticTestNode.createIndexName("field-settings");
        node.createIndex(indexName, StreamUtil.fileToString(INDEX_BODY));
        // Stroom only returns the hits of streams that exist, so the documents must belong to a real stream
        final long streamId = helper.addEventsStream(FEED, ElasticEndToEndTestHelper.CONDITIONS_EVENTS);
        node.bulkIndex(indexName, JsonUtil.getMapper().readTree(StreamUtil.fileToString(DOCUMENTS)
                .replace("\"StreamId\": 1,", "\"StreamId\": " + streamId + ",")));

        final DocRef clusterRef = helper.createCluster(node);
        indexRef = helper.createIndexDoc(clusterRef, indexName, "Date", 100);
        final ElasticIndexDoc doc = elasticIndexStore.readDocument(indexRef);
        elasticIndexStore.writeDocument(doc.copy()
                .fieldSettings(List.of(
                        ElasticIndexFieldSettings.builder(VIRTUAL_FIELD)
                                .virtualGroups(List.of(
                                        new ElasticVirtualFieldGroup("red", ExpressionOperator.builder()
                                                .addTerm("Keyword", Condition.IN, "apple,Cherry")
                                                .build()),
                                        new ElasticVirtualFieldGroup("yellow", ExpressionOperator.builder()
                                                .addTerm("Keyword", Condition.EQUALS, "banana")
                                                .build())))
                                .build(),
                        ElasticIndexFieldSettings.builder("Ip").enabled(false).build()))
                .build());
    }

    @AfterEach
    void tearDown() {
        if (node != null && indexName != null) {
            node.deleteIndices(indexName);
        }
    }

    @Test
    void testVirtualFieldSearches() {
        assertIds(term(VIRTUAL_FIELD, Condition.EQUALS, "red"), "1", "3");
        assertIds(term(VIRTUAL_FIELD, Condition.NOT_EQUALS, "red"), "2", "4", "5");
        assertIds(term(VIRTUAL_FIELD, Condition.IN, "red, yellow"), "1", "2", "3");
        // The same as the groups' own conditions
        assertIds(ExpressionOperator.builder()
                .op(Op.OR)
                .addTerm("Keyword", Condition.IN, "apple,Cherry")
                .addTerm("Keyword", Condition.EQUALS, "banana")
                .build(), "1", "2", "3");
    }

    @Test
    void testVirtualFieldUnknownGroup() {
        final SearchResult result = search(term(VIRTUAL_FIELD, Condition.EQUALS, "green"));

        assertThat(result.errors()).anyMatch(error -> error.contains("Unknown group 'green'"));
    }

    @Test
    void testDataSourceFields() {
        final List<String> fieldNames = elasticSearchProvider
                .getDataSourceFields(elasticIndexStore.readDocument(indexRef))
                .stream()
                .map(QueryField::getFldName)
                .toList();

        assertThat(fieldNames).contains(VIRTUAL_FIELD, "Keyword").doesNotContain("Ip");
    }

    @Test
    void testDisabledField() {
        final SearchResult queried = search(term("Ip", Condition.EQUALS, "10.0.0.1"));
        assertThat(queried.errors()).anyMatch(error -> error.contains("Field 'Ip' is disabled"));

        // A column of a disabled field has no values, with a warning
        final SearchResult column = helper.search(indexRef, term(ID, Condition.EQUALS, "1"), List.of(ID, "Ip"));
        assertThat(column.column(0)).containsExactly("1");
        assertThat(column.column(1)).containsOnlyNulls();
        assertThat(column.errors()).anyMatch(error -> error.contains("disabled or virtual: Ip"));
    }

    @Test
    void testInvalidVirtualFieldRejectedOnSave() {
        final ElasticIndexDoc doc = elasticIndexStore.readDocument(indexRef);
        final ElasticIndexDoc invalid = doc.copy()
                .fieldSettings(List.of(ElasticIndexFieldSettings.builder(VIRTUAL_FIELD)
                        .virtualGroups(List.of(new ElasticVirtualFieldGroup("text", ExpressionOperator.builder()
                                .addTerm("Text", Condition.EQUALS, "apple")
                                .build())))
                        .build()))
                .build();

        assertThatThrownBy(() -> elasticIndexStore.writeDocument(invalid))
                .isInstanceOf(EntityServiceException.class)
                .hasMessageContaining("uses field 'Text', which is not a keyword field");
    }

    @Test
    void testKeywordFieldStats() {
        final ElasticFieldStats stats = stats("Keyword", null, null);

        assertThat(stats.getMessage()).isNull();
        assertThat(stats.getSampledDocs()).isEqualTo(5);
        assertThat(stats.getDocsWithValue()).isEqualTo(4);
        assertThat(stats.getDistinctCount()).isEqualTo(4);
        assertThat(stats.getTopValues())
                .extracting(ElasticFieldValueCount::getValue)
                .containsExactlyInAnyOrder("apple", "banana", "Cherry", "apple pie");
    }

    @Test
    void testNumericFieldStats() {
        final ElasticFieldStats stats = stats("Long", null, null);

        assertThat(stats.getMin()).isEqualTo("-5");
        assertThat(stats.getMax()).isEqualTo("10");
        assertThat(stats.getMean()).isEqualTo(2.5);
        assertThat(stats.getMedian()).isNotNull();
        assertThat(stats.getHistogram())
                .extracting(ElasticFieldValueCount::getCount)
                .containsOnly(0L, 1L)
                .contains(1L);
        assertThat(stats.getHistogram().stream().mapToLong(ElasticFieldValueCount::getCount).sum())
                .isEqualTo(4);
    }

    @Test
    void testDateFieldStatsWithinTimeRange() {
        // Only documents 2 and 3 fall within the range on the doc's time field
        final ElasticFieldStats stats = stats(
                "Date",
                Instant.parse("2026-01-02T00:00:00Z").toEpochMilli(),
                Instant.parse("2026-01-04T00:00:00Z").toEpochMilli());

        assertThat(stats.getSampledDocs()).isEqualTo(2);
        assertThat(stats.getMin()).startsWith("2026-01-02");
        assertThat(stats.getMax()).startsWith("2026-01-03");
        assertThat(stats.getHistogram()).isNotEmpty();
    }

    @Test
    void testBooleanFieldStats() {
        assertThat(stats("Boolean", null, null).getTopValues())
                .extracting(ElasticFieldValueCount::getValue, ElasticFieldValueCount::getCount)
                .containsExactlyInAnyOrder(tuple("true", 2L), tuple("false", 2L));
    }

    @Test
    void testVirtualFieldStats() {
        assertThat(stats(VIRTUAL_FIELD, null, null).getTopValues())
                .extracting(ElasticFieldValueCount::getValue, ElasticFieldValueCount::getCount)
                .containsExactly(tuple("red", 2L), tuple("yellow", 1L));
    }

    @Test
    void testRandomSampleAndExactStats() {
        // A random sample needs many more documents than the sample size
        final String largeIndexName = ElasticTestNode.createIndexName("field-stats-sample");
        node.createIndex(largeIndexName, """
                {"mappings": {"properties": {"Kind": {"type": "keyword"}, "Date": {"type": "date"}}}}""");
        try {
            final ArrayNode documents = JsonUtil.getMapper().createArrayNode();
            for (int i = 0; i < 20_000; i++) {
                documents.addObject()
                        .put("_id", String.valueOf(i))
                        .put("Kind", i % 4 == 0 ? "a" : "b")
                        .put("Date", "2026-01-01T00:00:00.000Z");
            }
            node.bulkIndex(largeIndexName, documents);
            final DocRef largeIndexRef = helper.createIndexDoc(
                    helper.createCluster(node), largeIndexName, "Date", 100);

            final ElasticFieldStats sampled = elasticFieldStatsService.getFieldStats(new ElasticFieldStatsRequest(
                    largeIndexRef, "Kind", 2_000, null, null, null, null));
            assertThat(sampled.getExact()).isFalse();
            assertThat(sampled.getTotalDocs()).isEqualTo(20_000);
            // About 2,000 documents are sampled, and their counts are scaled up to estimate all 20,000
            assertThat(sampled.getSampledDocs()).isBetween(1_000L, 3_000L);
            assertThat(sampled.getDocsWithValue()).isBetween(15_000L, 25_000L);
            assertThat(sampled.getTopValues())
                    .extracting(ElasticFieldValueCount::getValue)
                    .containsExactly("b", "a");

            final ElasticFieldStats exact = elasticFieldStatsService.getFieldStats(new ElasticFieldStatsRequest(
                    largeIndexRef, "Kind", 2_000, null, null, null, true));
            assertThat(exact.getExact()).isTrue();
            assertThat(exact.getSampledDocs()).isEqualTo(20_000);
            assertThat(exact.getDocsWithValue()).isEqualTo(20_000);
            assertThat(exact.getTopValues())
                    .extracting(ElasticFieldValueCount::getValue, ElasticFieldValueCount::getCount)
                    .containsExactly(tuple("b", 15_000L), tuple("a", 5_000L));
        } finally {
            node.deleteIndices(largeIndexName);
        }
    }

    private ElasticFieldStats stats(final String fieldName, final Long fromMs, final Long toMs) {
        return elasticFieldStatsService.getFieldStats(new ElasticFieldStatsRequest(
                indexRef, fieldName, null, fromMs, toMs, null, null));
    }

    private void assertIds(final ExpressionOperator expression, final String... expectedIds) {
        final SearchResult result = search(expression);
        assertThat(result.errors()).isEmpty();
        assertThat(result.column(0)).containsExactlyInAnyOrder(expectedIds);
    }

    private SearchResult search(final ExpressionOperator expression) {
        return helper.search(indexRef, expression, List.of(ID));
    }

    private static ExpressionOperator term(final String field, final Condition condition, final String value) {
        return ExpressionOperator.builder().addTerm(field, condition, value).build();
    }
}
