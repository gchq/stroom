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
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.search.elastic.ElasticEndToEndTestHelper.SearchResult;
import stroom.search.elastic.search.ElasticSearchProvider;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.io.StreamUtil;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Searches a field of every Elasticsearch field type that Stroom supports (other than `dense_vector`, which
/// needs an embedding model), with every condition Stroom offers for that type, plus the `= *` and `!= *`
/// existence conditions. Each case states the documents Elasticsearch should match, or that Stroom should
/// report an error.
///
/// The documents are indexed directly rather than with a Stroom pipeline, as indexing is tested elsewhere.
/// Documents 1 to 4 have a value for every field and document 5 has none (other than the ids every document
/// Stroom searches needs), see `conditions-docs.json`. They all belong to one stream, which must exist.
class TestElasticSearchConditions_EndToEnd extends AbstractCoreIntegrationTest {

    private static final Path INDEX_BODY = ElasticEndToEndTestHelper.RESOURCES_DIR.resolve("conditions-index.json");
    private static final Path DOCUMENTS = ElasticEndToEndTestHelper.RESOURCES_DIR.resolve("conditions-docs.json");
    private static final String ID = "Id";
    private static final String FEED = "ELASTIC_CONDITIONS";
    private static final List<String> ALL = List.of("1", "2", "3", "4", "5");
    private static final List<String> HAVE_VALUES = List.of("1", "2", "3", "4");
    private static final List<String> NONE = List.of();

    @Inject
    private ElasticEndToEndTestHelper helper;
    @Inject
    private ElasticIndexStore elasticIndexStore;
    @Inject
    private ElasticSearchProvider elasticSearchProvider;

    private ElasticTestNode node;
    private String indexName;

    @AfterEach
    void tearDown() {
        if (node != null && indexName != null) {
            node.deleteIndices(indexName);
        }
    }

    @TestFactory
    Stream<DynamicTest> testConditions() {
        node = ElasticTestNode.getOrSkip();
        indexName = ElasticTestNode.createIndexName("conditions");
        node.createIndex(indexName, StreamUtil.fileToString(INDEX_BODY));
        // Stroom only returns the hits of streams that exist, so the documents must belong to a real stream
        final long streamId = helper.addEventsStream(FEED, ElasticEndToEndTestHelper.CONDITIONS_EVENTS);
        node.bulkIndex(indexName, JsonUtil.getMapper().readTree(StreamUtil.fileToString(DOCUMENTS)
                .replace("\"StreamId\": 1,", "\"StreamId\": " + streamId + ",")));

        final DocRef clusterRef = helper.createCluster(node);
        final DocRef indexRef = helper.createIndexDoc(clusterRef, indexName, "Date", 100);
        final Map<String, QueryField> fields = elasticSearchProvider
                .getDataSourceFields(elasticIndexStore.readDocument(indexRef))
                .stream()
                .collect(Collectors.toMap(QueryField::getFldName, Function.identity()));

        final List<ConditionCase> cases = createCases();
        assertConditionsCovered(fields, cases);

        return cases.stream()
                .map(conditionCase -> DynamicTest.dynamicTest(
                        conditionCase.toString(),
                        () -> conditionCase.assertSearch(helper, indexRef)));
    }

    /// Check that the cases cover every condition the field picker offers for each field, so that a condition
    /// added to a field type's condition set can't go untested.
    private static void assertConditionsCovered(final Map<String, QueryField> fields,
                                                final List<ConditionCase> cases) {
        final Map<String, Set<Condition>> testedConditions = cases.stream()
                .collect(Collectors.groupingBy(
                        ConditionCase::field,
                        Collectors.mapping(ConditionCase::condition, Collectors.toSet())));
        for (final Map.Entry<String, Set<Condition>> entry : testedConditions.entrySet()) {
            final QueryField field = fields.get(entry.getKey());
            assertThat(field)
                    .as("Field %s", entry.getKey())
                    .isNotNull();
            final Set<Condition> offered = EnumSet.copyOf(field.getConditionSet().getConditionList());
            assertThat(entry.getValue())
                    .as("Conditions tested for field %s (%s)", entry.getKey(), field.getFldType())
                    .containsAll(offered);
        }
        // Every field in the index is tested, other than the ids, which identify the results, and the nested
        // object itself, which can't be queried
        final Set<String> untested = fields.keySet().stream()
                .filter(name -> !testedConditions.containsKey(name))
                .collect(Collectors.toSet());
        assertThat(untested).containsExactlyInAnyOrder("StreamId", "EventId", ID, "Nested");
        assertThat(fields.get("Nested").getFldType()).isEqualTo(FieldType.NESTED);
    }

    private static List<ConditionCase> createCases() {
        final List<ConditionCase> cases = new ArrayList<>();

        // keyword: exact, case-sensitive terms, or case-insensitive wildcard patterns
        cases.add(match("Keyword", Condition.EQUALS, "apple", "1"));
        cases.add(match("Keyword", Condition.EQUALS, "Apple"));
        cases.add(match("Keyword", Condition.EQUALS, "apple pie", "4"));
        cases.add(match("Keyword", Condition.EQUALS, "\"apple pie\"", "4"));
        cases.add(match("Keyword", Condition.EQUALS, "APP*", "1", "4"));
        cases.add(match("Keyword", Condition.EQUALS, "?herry", "3"));
        cases.add(match("Keyword", Condition.NOT_EQUALS, "apple", "2", "3", "4", "5"));
        cases.add(match("Keyword", Condition.IN, "apple,banana", "1", "2"));
        cases.add(dictionary("Keyword", List.of("banana", "cher*"), "2", "3"));
        cases.add(match("Keyword", Condition.MATCHES_REGEX, "ban.*", "2"));
        cases.add(match("Keyword", Condition.MATCHES_REGEX, "cherry"));
        cases.addAll(existence("Keyword"));

        // constant_keyword: every document has the value given in the mapping, even those that don't specify it
        cases.add(match("ConstantKeyword", Condition.EQUALS, "fixed", ALL));
        cases.add(match("ConstantKeyword", Condition.EQUALS, "other", NONE));
        cases.add(match("ConstantKeyword", Condition.NOT_EQUALS, "fixed", NONE));
        cases.add(match("ConstantKeyword", Condition.IN, "other,fixed", ALL));
        cases.add(dictionary("ConstantKeyword", List.of("fixed"), ALL));
        cases.add(match("ConstantKeyword", Condition.MATCHES_REGEX, "fix.*", ALL));
        cases.add(match("ConstantKeyword", Condition.EQUALS, "*", ALL));
        cases.add(match("ConstantKeyword", Condition.NOT_EQUALS, "*", NONE));

        // wildcard: like keyword, matching whole values
        cases.add(match("Wildcard", Condition.EQUALS, "apple", "1"));
        cases.add(match("Wildcard", Condition.EQUALS, "*pie", "4"));
        cases.add(match("Wildcard", Condition.NOT_EQUALS, "apple", "2", "3", "4", "5"));
        cases.add(match("Wildcard", Condition.IN, "apple,banana", "1", "2"));
        cases.add(dictionary("Wildcard", List.of("banana", "*pie"), "2", "4"));
        cases.add(match("Wildcard", Condition.MATCHES_REGEX, "ban.*", "2"));
        cases.addAll(existence("Wildcard"));

        // text: analysed, so matching is by case-insensitive word
        cases.add(match("Text", Condition.EQUALS, "apple", "1", "4"));
        cases.add(match("Text", Condition.EQUALS, "APPLE", "1", "4"));
        cases.add(match("Text", Condition.EQUALS, "\"red apple\"", "1"));
        cases.add(match("Text", Condition.EQUALS, "cher*", "3"));
        cases.add(match("Text", Condition.NOT_EQUALS, "apple", "2", "3", "5"));
        cases.add(match("Text", Condition.IN, "banana,cherry", "2", "3"));
        cases.add(dictionary("Text", List.of("banana", "\"dark cherry\""), "2", "3"));
        cases.add(match("Text", Condition.MATCHES_REGEX, "ban.*", "2"));
        cases.addAll(existence("Text"));

        // boolean
        cases.add(match("Boolean", Condition.EQUALS, "true", "1", "3"));
        cases.add(match("Boolean", Condition.EQUALS, "false", "2", "4"));
        cases.add(match("Boolean", Condition.NOT_EQUALS, "true", "2", "4", "5"));
        // Only equals and not equals are offered for a boolean field, so the others are rejected if used anyway
        cases.add(error("Boolean", Condition.IN, "true,false"));
        cases.add(dictionaryError("Boolean", List.of("true")));
        cases.add(error("Boolean", Condition.MATCHES_REGEX, "tr.*"));
        cases.addAll(existence("Boolean"));

        // date
        cases.addAll(range("Date",
                "2026-01-01T00:00:00.000Z",
                "2026-01-02T00:00:00.000Z",
                "2026-01-03T00:00:00.000Z",
                "2026-01-04T00:00:00.000Z"));
        cases.add(error("Date", Condition.EQUALS, "not a date"));

        // date_nanos: document 2 is a nanosecond after midnight. Stroom's dates have millisecond precision, and
        // Elasticsearch takes a millisecond value to cover the whole millisecond, so document 2 equals midnight.
        cases.add(match("DateNanos", Condition.EQUALS, "2026-01-01T00:00:00.000Z", "1"));
        cases.add(match("DateNanos", Condition.EQUALS, "2026-01-02T00:00:00.000Z", "2"));
        cases.add(match("DateNanos", Condition.EQUALS, "2026-01-02T00:00:00.001Z"));
        cases.add(match("DateNanos", Condition.NOT_EQUALS, "2026-01-01T00:00:00.000Z", "2", "3", "4", "5"));
        cases.add(match("DateNanos", Condition.GREATER_THAN, "2026-01-02T00:00:00.000Z", "3", "4"));
        cases.add(match("DateNanos", Condition.GREATER_THAN, "2026-01-01T23:59:59.999Z", "2", "3", "4"));
        cases.add(match("DateNanos", Condition.GREATER_THAN_OR_EQUAL_TO, "2026-01-02T00:00:00.000Z",
                "2", "3", "4"));
        cases.add(match("DateNanos", Condition.LESS_THAN, "2026-01-02T00:00:00.000Z", "1"));
        cases.add(match("DateNanos", Condition.LESS_THAN_OR_EQUAL_TO, "2026-01-02T00:00:00.000Z", "1", "2"));
        cases.add(match("DateNanos", Condition.BETWEEN, "2026-01-02T00:00:00.000Z,2026-01-03T00:00:00.000Z",
                "2", "3"));
        cases.add(match("DateNanos", Condition.IN, "2026-01-01T00:00:00.000Z,2026-01-04T00:00:00.000Z",
                "1", "4"));
        cases.add(dictionary("DateNanos", List.of("2026-01-03T00:00:00.000Z"), "3"));
        cases.addAll(existence("DateNanos"));

        // ip, which also accepts CIDR blocks for equality conditions
        cases.addAll(range("Ip", "10.0.0.1", "10.0.0.2", "192.168.0.1", "192.168.1.1"));
        cases.add(match("Ip", Condition.EQUALS, "10.0.0.0/8", "1", "2"));
        cases.add(match("Ip", Condition.IN, "10.0.0.1,192.168.0.0/16", "1", "3", "4"));
        cases.add(dictionary("Ip", List.of("192.168.0.0/24"), "3"));
        cases.add(error("Ip", Condition.GREATER_THAN, "10.0.0.0/8"));
        cases.add(error("Ip", Condition.EQUALS, "10.0.0"));

        // whole numbers
        cases.addAll(range("Long", "-5", "0", "5", "10"));
        cases.add(error("Long", Condition.EQUALS, "five"));
        cases.addAll(range("UnsignedLong", "1", "42", "9223372036854775807", "18446744073709551615"));
        cases.addAll(range("Integer", "10", "20", "30", "40"));
        cases.addAll(range("Short", "10", "20", "30", "40"));
        cases.addAll(range("Byte", "1", "2", "3", "4"));
        // version: ordered by semantic version, so 10.0.0 is after 2.0.0
        cases.addAll(range("Version", "1.0.0", "1.2.0", "2.0.0", "10.0.0"));

        // floating point numbers
        cases.addAll(range("Float", "1.5", "2.5", "3.5", "4.5"));
        cases.addAll(range("HalfFloat", "1.5", "2.5", "3.5", "4.5"));
        cases.addAll(range("ScaledFloat", "1.25", "2.5", "3.75", "4.5"));
        cases.addAll(range("Double", "1.5", "2.5", "3.5", "4.5"));
        cases.add(error("Double", Condition.EQUALS, "one"));

        // A number within nested objects: document 1 has 1 and 2, document 2 has 3, the others have none
        cases.add(match("Nested.Number", Condition.EQUALS, "2", "1"));
        cases.add(match("Nested.Number", Condition.NOT_EQUALS, "3", "1", "3", "4", "5"));
        cases.add(match("Nested.Number", Condition.GREATER_THAN, "2", "2"));
        cases.add(match("Nested.Number", Condition.GREATER_THAN_OR_EQUAL_TO, "2", "1", "2"));
        cases.add(match("Nested.Number", Condition.LESS_THAN, "2", "1"));
        cases.add(match("Nested.Number", Condition.LESS_THAN_OR_EQUAL_TO, "2", "1"));
        cases.add(match("Nested.Number", Condition.BETWEEN, "2,3", "1", "2"));
        cases.add(match("Nested.Number", Condition.IN, "1,3", "1", "2"));
        cases.add(dictionary("Nested.Number", List.of("3"), "2"));
        cases.add(match("Nested.Number", Condition.EQUALS, "*", "1", "2"));
        cases.add(match("Nested.Number", Condition.NOT_EQUALS, "*", "3", "4", "5"));

        return cases;
    }

    /// Cases for every condition of an ordered field type, whose documents 1 to 4 have the supplied values in
    /// ascending order.
    private static List<ConditionCase> range(final String field,
                                             final String value1,
                                             final String value2,
                                             final String value3,
                                             final String value4) {
        final List<ConditionCase> cases = new ArrayList<>();
        cases.add(match(field, Condition.EQUALS, value2, "2"));
        cases.add(match(field, Condition.EQUALS, value4, "4"));
        cases.add(match(field, Condition.NOT_EQUALS, value2, "1", "3", "4", "5"));
        cases.add(match(field, Condition.GREATER_THAN, value2, "3", "4"));
        cases.add(match(field, Condition.GREATER_THAN, value3, "4"));
        cases.add(match(field, Condition.GREATER_THAN_OR_EQUAL_TO, value2, "2", "3", "4"));
        cases.add(match(field, Condition.LESS_THAN, value2, "1"));
        cases.add(match(field, Condition.LESS_THAN_OR_EQUAL_TO, value2, "1", "2"));
        cases.add(match(field, Condition.BETWEEN, value2 + "," + value3, "2", "3"));
        cases.add(match(field, Condition.IN, value1 + "," + value4, "1", "4"));
        cases.add(dictionary(field, List.of(value3, value4), "3", "4"));
        cases.addAll(existence(field));
        return cases;
    }

    /// The `= *` and `!= *` cases, for a field that documents 1 to 4 have a value for.
    private static List<ConditionCase> existence(final String field) {
        return List.of(
                match(field, Condition.EQUALS, "*", HAVE_VALUES),
                match(field, Condition.NOT_EQUALS, "*", "5"));
    }

    private static ConditionCase match(final String field,
                                       final Condition condition,
                                       final String value,
                                       final String... expectedIds) {
        return match(field, condition, value, List.of(expectedIds));
    }

    private static ConditionCase match(final String field,
                                       final Condition condition,
                                       final String value,
                                       final List<String> expectedIds) {
        return new ConditionCase(field, condition, value, null, expectedIds);
    }

    private static ConditionCase dictionary(final String field,
                                            final List<String> lines,
                                            final String... expectedIds) {
        return dictionary(field, lines, List.of(expectedIds));
    }

    private static ConditionCase dictionary(final String field,
                                            final List<String> lines,
                                            final List<String> expectedIds) {
        return new ConditionCase(field, Condition.IN_DICTIONARY, null, lines, expectedIds);
    }

    private static ConditionCase dictionaryError(final String field, final List<String> lines) {
        return new ConditionCase(field, Condition.IN_DICTIONARY, null, lines, null);
    }

    private static ConditionCase error(final String field, final Condition condition, final String value) {
        return new ConditionCase(field, condition, value, null, null);
    }

    /// A search of one field with one condition.
    ///
    /// @param field The field to search.
    /// @param condition The condition.
    /// @param value The value of the term, or `null` for a dictionary term.
    /// @param dictionaryLines The lines of the dictionary for an `IN_DICTIONARY` term, otherwise `null`.
    /// @param expectedIds The ids of the documents that should match, or `null` if the search should fail.
    private record ConditionCase(String field,
                                 Condition condition,
                                 String value,
                                 List<String> dictionaryLines,
                                 List<String> expectedIds) {

        void assertSearch(final ElasticEndToEndTestHelper helper, final DocRef indexRef) {
            final ExpressionTerm.Builder term = ExpressionTerm.builder()
                    .field(field)
                    .condition(condition);
            if (dictionaryLines != null) {
                term.docRef(helper.createDictionary(field + " dictionary", String.join("\n", dictionaryLines)));
            } else {
                term.value(value);
            }
            final SearchResult result = helper.search(
                    indexRef,
                    ExpressionOperator.builder().addTerm(term.build()).build(),
                    List.of(ID));

            if (expectedIds == null) {
                assertThat(result.errors())
                        .as("Errors")
                        .isNotEmpty();
                assertThat(result.rows())
                        .as("Rows")
                        .isEmpty();
            } else {
                assertThat(result.errors())
                        .as("Errors")
                        .isEmpty();
                assertThat(result.column(0))
                        .as("Ids of matching documents")
                        .containsExactlyInAnyOrderElementsOf(expectedIds);
            }
        }

        @Override
        public String toString() {
            final String operand = dictionaryLines != null
                    ? dictionaryLines.toString()
                    : value;
            final String outcome = expectedIds != null
                    ? expectedIds.toString()
                    : "error";
            return field + " " + condition.getDisplayValue() + " " + operand + " → " + outcome;
        }
    }
}
