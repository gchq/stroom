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
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.io.StreamUtil;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// Returns the value of a field of every Elasticsearch field type that Stroom supports (other than
/// `dense_vector`, which isn't a value to show in a table) in a table, both as Elasticsearch stored it and as an
/// extraction pipeline extracts it from the source events. Extracted values are converted to the type of the
/// index field, so each type needs converting correctly.
///
/// Uses the documents of [TestElasticSearchConditions_EndToEnd], see `conditions-docs.json`, and the events
/// they came from, see `conditions-events.xml`. Documents 1 to 4 have a value for every field and document 5
/// has none. The `unsigned_long` field is tested on its own, as document 4's value of it is beyond the range of
/// a long, which is an error.
class TestElasticValueExtraction_EndToEnd extends AbstractCoreIntegrationTest {

    private static final Path INDEX_BODY = ElasticEndToEndTestHelper.RESOURCES_DIR.resolve("conditions-index.json");
    private static final Path DOCUMENTS = ElasticEndToEndTestHelper.RESOURCES_DIR.resolve("conditions-docs.json");
    private static final Path EXTRACTION_XSLT = ElasticEndToEndTestHelper.RESOURCES_DIR
            .resolve("conditions-events-to-records.xsl");
    private static final String FEED = "ELASTIC_VALUE_EXTRACTION";
    private static final String UNSIGNED_LONG = "UnsignedLong";
    // The id first, to sort the rows by
    private static final List<String> UNSIGNED_LONG_FIELDS = List.of("Id", UNSIGNED_LONG);
    // The id first, to sort the rows by
    private static final List<String> FIELDS = List.of(
            "Id",
            "Keyword",
            "ConstantKeyword",
            "Wildcard",
            "Text",
            "Boolean",
            "Date",
            "DateNanos",
            "Ip",
            "Long",
            "Integer",
            "Short",
            "Byte",
            "Version",
            "Float",
            "HalfFloat",
            "ScaledFloat",
            "Double",
            "Nested.Number",
            "MatchOnlyText",
            "MatchOnlyText.keyword",
            "Attributes.Host");

    @Inject
    private ElasticEndToEndTestHelper helper;

    private ElasticTestNode node;
    private String indexName;
    private DocRef indexRef;

    @BeforeEach
    void setUp() {
        node = ElasticTestNode.getOrSkip();
        indexName = ElasticTestNode.createIndexName("extraction");
        node.createIndex(indexName, StreamUtil.fileToString(INDEX_BODY));
        // Stroom only returns the hits of streams that exist, and extracts values from the stream's events
        final long streamId = helper.addEventsStream(FEED, ElasticEndToEndTestHelper.CONDITIONS_EVENTS);
        node.bulkIndex(indexName, JsonUtil.getMapper().readTree(StreamUtil.fileToString(DOCUMENTS)
                .replace("\"StreamId\": 1,", "\"StreamId\": " + streamId + ",")));

        final DocRef clusterRef = helper.createCluster(node);
        indexRef = helper.createIndexDoc(clusterRef, indexName, "Date", 100);
    }

    @AfterEach
    void tearDown() {
        if (node != null && indexName != null) {
            node.deleteIndices(indexName);
        }
    }

    @Test
    void testStoredValues() {
        final SearchResult result = helper.search(indexRef, ExpressionOperator.builder().build(), FIELDS);

        assertThat(result.errors()).isEmpty();
        assertThat(sortById(result.rows())).containsExactly(
                row("1", "apple", "fixed", "apple", "The red apple", "true",
                        "2026-01-01T00:00:00.000Z", "2026-01-01T00:00:00.000Z", "10.0.0.1",
                        "-5", "10", "10", "1", "1.0.0", "1.5", "1.5", "1.25", "1.5", "1, 2",
                        "The red apple", "The red apple", "apple"),
                row("2", "banana", "fixed", "banana", "A yellow banana", "false",
                        "2026-01-02T00:00:00.000Z", "2026-01-02T00:00:00.000000001Z", "10.0.0.2",
                        "0", "20", "20", "2", "1.2.0", "2.5", "2.5", "2.5", "2.5", "3",
                        "A yellow banana", "A yellow banana", "banana"),
                row("3", "Cherry", "fixed", "Cherry", "The dark cherry", "true",
                        "2026-01-03T00:00:00.000Z", "2026-01-03T00:00:00.000Z", "192.168.0.1",
                        "5", "30", "30", "3", "2.0.0", "3.5", "3.5", "3.75", "3.5", null,
                        "The dark cherry", "The dark cherry", "Cherry"),
                row("4", "apple pie", "fixed", "apple pie", "Apple pie recipe", "false",
                        "2026-01-04T00:00:00.000Z", "2026-01-04T00:00:00.000Z", "192.168.1.1",
                        "10", "40", "40", "4", "10.0.0", "4.5", "4.5", "4.5", "4.5", null,
                        "Apple pie recipe", "Apple pie recipe", "apple pie"),
                row("5", null, "fixed", null, null, null,
                        null, null, null,
                        null, null, null, null, null, null, null, null, null, null,
                        null, null, null));
    }

    @Test
    void testExtractedValues() {
        final DocRef extractionPipelineRef = helper.createExtractionPipeline(
                "Value extraction", EXTRACTION_XSLT);

        final SearchResult result = helper.search(
                indexRef, ExpressionOperator.builder().build(), FIELDS, extractionPipelineRef);

        // Every value is converted to the type of its field. Dates are converted to milliseconds, whatever form
        // they are extracted in, and a nested field can only hold one extracted number.
        assertThat(result.errors()).isEmpty();
        assertThat(sortById(result.rows())).containsExactly(
                row("1", "apple", "fixed", "apple", "The red apple", "true",
                        "2026-01-01T00:00:00.000Z", "2026-01-01T00:00:00.000Z", "10.0.0.1",
                        "-5", "10", "10", "1", "1.0.0", "1.5", "1.5", "1.25", "1.5", "2",
                        "The red apple", "The red apple", "apple"),
                row("2", "banana", "fixed", "banana", "A yellow banana", "false",
                        "2026-01-02T00:00:00.000Z", "2026-01-02T00:00:00.000Z", "10.0.0.2",
                        "0", "20", "20", "2", "1.2.0", "2.5", "2.5", "2.5", "2.5", "3",
                        "A yellow banana", "A yellow banana", "banana"),
                row("3", "Cherry", "fixed", "Cherry", "The dark cherry", "true",
                        "2026-01-03T00:00:00.000Z", "2026-01-03T00:00:00.000Z", "192.168.0.1",
                        "5", "30", "30", "3", "2.0.0", "3.5", "3.5", "3.75", "3.5", null,
                        "The dark cherry", "The dark cherry", "Cherry"),
                row("4", "apple pie", "fixed", "apple pie", "Apple pie recipe", "false",
                        "2026-01-04T00:00:00.000Z", "2026-01-04T00:00:00.000Z", "192.168.1.1",
                        "10", "40", "40", "4", "10.0.0", "4.5", "4.5", "4.5", "4.5", null,
                        "Apple pie recipe", "Apple pie recipe", "apple pie"),
                row("5", null, null, null, null, null,
                        null, null, null,
                        null, null, null, null, null, null, null, null, null, null,
                        null, null, null));
    }

    @Test
    void testStoredUnsignedLongValues() {
        final SearchResult result = helper.search(indexRef, unsignedLongWithinTheRangeOfALong(), UNSIGNED_LONG_FIELDS);

        assertThat(result.errors()).isEmpty();
        assertThat(sortById(result.rows())).containsExactly(
                row("1", "1"),
                row("2", "42"),
                row("3", "9223372036854775807"));
    }

    @Test
    void testStoredUnsignedLongValueBeyondTheRangeOfALong() {
        final SearchResult result = helper.search(indexRef, idEquals("4"), UNSIGNED_LONG_FIELDS);

        // No Val type can hold the value, so it is an error rather than a value
        assertThat(result.rows()).isEmpty();
        assertThat(result.errors())
                .anySatisfy(error -> assertThat(error)
                        .contains("field 'UnsignedLong'")
                        .contains("Value 18446744073709551615 is outside the range of a long"));
    }

    @Test
    void testExtractedUnsignedLongValues() {
        final DocRef extractionPipelineRef = helper.createExtractionPipeline(
                "Unsigned long extraction", EXTRACTION_XSLT);

        final SearchResult result = helper.search(
                indexRef, unsignedLongWithinTheRangeOfALong(), UNSIGNED_LONG_FIELDS, extractionPipelineRef);

        assertThat(result.errors()).isEmpty();
        assertThat(sortById(result.rows())).containsExactly(
                row("1", "1"),
                row("2", "42"),
                row("3", "9223372036854775807"));
    }

    @Test
    void testExtractedUnsignedLongValueBeyondTheRangeOfALong() {
        final DocRef extractionPipelineRef = helper.createExtractionPipeline(
                "Unsigned long extraction", EXTRACTION_XSLT);

        final SearchResult result = helper.search(
                indexRef, idEquals("4"), UNSIGNED_LONG_FIELDS, extractionPipelineRef);

        // Elasticsearch still returns the stored value, which no Val type can hold, so it is an error even when
        // values are extracted
        assertThat(result.rows()).isEmpty();
        assertThat(result.errors())
                .anySatisfy(error -> assertThat(error)
                        .contains("field 'UnsignedLong'")
                        .contains("Value 18446744073709551615 is outside the range of a long"));
    }

    private static ExpressionOperator unsignedLongWithinTheRangeOfALong() {
        return ExpressionOperator.builder()
                .addTerm(UNSIGNED_LONG, Condition.LESS_THAN_OR_EQUAL_TO, String.valueOf(Long.MAX_VALUE))
                .build();
    }

    private static ExpressionOperator idEquals(final String id) {
        return ExpressionOperator.builder()
                .addTerm("Id", Condition.EQUALS, id)
                .build();
    }

    private static List<String> row(final String... values) {
        // Allows nulls, for blank cells
        return Arrays.asList(values);
    }

    private static List<List<String>> sortById(final List<List<String>> rows) {
        final List<List<String>> sorted = new ArrayList<>(rows);
        // A blank id sorts first, so that a failure shows the rest of the values
        sorted.sort(Comparator.comparing(List::getFirst, Comparator.nullsFirst(Comparator.<String>naturalOrder())));
        return sorted;
    }
}
