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
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.TimeRange;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.shared.FetchFieldValuesRequest;
import stroom.query.shared.FieldValueCount;
import stroom.query.shared.FieldValues;
import stroom.search.elastic.suggest.ElasticSuggestionsQueryHandler;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.io.StreamUtil;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/// Lists the most common values of fields of an Elasticsearch index for the value picker of an expression term,
/// with a `terms` aggregation run on a real Elasticsearch node. Uses the documents of
/// {@link TestElasticSearchConditions_EndToEnd}: documents 1 to 4 have a value for every field and document 5
/// has none.
class TestElasticFieldValues_EndToEnd extends AbstractCoreIntegrationTest {

    private static final Path INDEX_BODY = ElasticEndToEndTestHelper.RESOURCES_DIR.resolve("conditions-index.json");
    private static final Path DOCUMENTS = ElasticEndToEndTestHelper.RESOURCES_DIR.resolve("conditions-docs.json");
    private static final Path NESTED_INDEX_BODY = ElasticEndToEndTestHelper.RESOURCES_DIR.resolve(
            "field-values-nested-index.json");
    private static final Path NESTED_DOCUMENTS = ElasticEndToEndTestHelper.RESOURCES_DIR.resolve(
            "field-values-nested-docs.json");
    private static final DateTimeSettings DATE_TIME_SETTINGS = DateTimeSettings.builder()
            .referenceTime(System.currentTimeMillis())
            .build();

    @Inject
    private ElasticEndToEndTestHelper helper;
    @Inject
    private ElasticSuggestionsQueryHandler handler;

    private ElasticTestNode node;
    private String indexName;
    private DocRef indexRef;
    private DocRef clusterRef;
    private String nestedIndexName;

    @BeforeEach
    void setUp() {
        node = ElasticTestNode.getOrSkip();
        indexName = ElasticTestNode.createIndexName("field-values");
        node.createIndex(indexName, StreamUtil.fileToString(INDEX_BODY));
        node.bulkIndex(indexName, JsonUtil.getMapper().readTree(StreamUtil.fileToString(DOCUMENTS)));

        clusterRef = helper.createCluster(node);
        indexRef = helper.createIndexDoc(clusterRef, indexName, "Date", 100);
    }

    @AfterEach
    void tearDown() {
        if (node != null && indexName != null) {
            node.deleteIndices(indexName);
        }
        if (node != null && nestedIndexName != null) {
            node.deleteIndices(nestedIndexName);
        }
    }

    @Test
    void testKeyword_allTime() {
        final FieldValues fieldValues = fetch("Keyword", FieldType.KEYWORD, null, null);

        assertThat(fieldValues.getValues())
                .extracting(FieldValueCount::getValue)
                .containsExactlyInAnyOrder("apple", "banana", "Cherry", "apple pie");
        assertThat(fieldValues.getValues())
                .extracting(FieldValueCount::getCount)
                .containsOnly(1L);
        // Counted across all documents, including the one without a value
        assertThat(fieldValues.getTotalCount()).isEqualTo(5);
        assertThat(fieldValues.isAllTime()).isTrue();
    }

    @Test
    void testKeyword_filter() {
        assertThat(fetch("Keyword", FieldType.KEYWORD, "ap", null).getValues())
                .extracting(FieldValueCount::getValue)
                .containsExactlyInAnyOrder("apple", "apple pie");
        // The filter ignores case
        assertThat(fetch("Keyword", FieldType.KEYWORD, "AP", null).getValues())
                .extracting(FieldValueCount::getValue)
                .containsExactlyInAnyOrder("apple", "apple pie");
        assertThat(fetch("Keyword", FieldType.KEYWORD, "CHE", null).getValues())
                .extracting(FieldValueCount::getValue)
                .containsExactly("Cherry");
        // Characters with a special meaning in regular expressions are matched literally
        assertThat(fetch("Keyword", FieldType.KEYWORD, "e p", null).getValues())
                .extracting(FieldValueCount::getValue)
                .containsExactly("apple pie");
        assertThat(fetch("Keyword", FieldType.KEYWORD, "a.*", null).getValues())
                .isEmpty();
    }

    @Test
    void testKeyword_timeRange() {
        // Documents 2 and 3 are dated 2 and 3 January
        final FieldValues fieldValues = fetch("Keyword", FieldType.KEYWORD, null, new TimeRange(
                "Two days",
                null,
                "2026-01-02T00:00:00.000Z",
                "2026-01-04T00:00:00.000Z"));

        assertThat(fieldValues.getValues())
                .extracting(FieldValueCount::getValue)
                .containsExactlyInAnyOrder("banana", "Cherry");
        assertThat(fieldValues.getTotalCount()).isEqualTo(2);
        assertThat(fieldValues.isAllTime()).isFalse();
    }

    @Test
    void testKeyword_queryTimeConditions() {
        // The query's own conditions on the time field narrow the count when the dashboard has no time range
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm("Date", Condition.GREATER_THAN_OR_EQUAL_TO, "2026-01-02T00:00:00.000Z")
                .addTerm("Date", Condition.LESS_THAN, "2026-01-04T00:00:00.000Z")
                .addTerm("Keyword", Condition.EQUALS, "apple")
                .build();

        final FieldValues fieldValues = fetch("Keyword", FieldType.KEYWORD, null, null, expression);

        // Other terms, such as Keyword = apple, don't narrow the count
        assertThat(fieldValues.getValues())
                .extracting(FieldValueCount::getValue)
                .containsExactlyInAnyOrder("banana", "Cherry");
        assertThat(fieldValues.isAllTime()).isFalse();
        assertThat(fieldValues.getScope())
                .isEqualTo("Date >= 2026-01-02T00:00:00.000Z and Date < 2026-01-04T00:00:00.000Z");
    }

    @Test
    void testWildcard() {
        assertThat(fetch("Wildcard", FieldType.KEYWORD, "pie", null).getValues())
                .extracting(FieldValueCount::getValue)
                .containsExactly("apple pie");
    }

    @Test
    void testTextUsesKeywordMultiField() {
        assertThat(fetch("MatchOnlyText", FieldType.TEXT, null, null).getValues())
                .extracting(FieldValueCount::getValue)
                .containsExactlyInAnyOrder("The red apple", "A yellow banana", "The dark cherry", "Apple pie recipe");
        // A text field without a keyword multi-field can't list its values
        assertThat(fetch("Text", FieldType.TEXT, null, null))
                .isEqualTo(FieldValues.EMPTY);
    }

    @Test
    void testBoolean() {
        assertThat(fetch("Boolean", FieldType.BOOLEAN, null, null).getValues())
                .containsExactlyInAnyOrder(new FieldValueCount("true", 2L), new FieldValueCount("false", 2L));
    }

    @Test
    void testIp() {
        assertThat(fetch("Ip", FieldType.IPV4_ADDRESS, null, null).getValues())
                .extracting(FieldValueCount::getValue)
                .containsExactlyInAnyOrder("10.0.0.1", "10.0.0.2", "192.168.0.1", "192.168.1.1");
    }

    @Test
    void testUnsupportedFields() {
        assertThat(fetch("Date", FieldType.DATE, null, null)).isEqualTo(FieldValues.EMPTY);
        // The nested object itself has no values to list. Its mapping type decides that, not the requested type.
        assertThat(fetch("Nested", FieldType.KEYWORD, null, null)).isEqualTo(FieldValues.EMPTY);
    }

    @Test
    void testNested() {
        // Document 1 holds 1 and 2, document 2 holds 3
        assertThat(fetch("Nested.Number", FieldType.LONG, null, null).getValues())
                .containsExactlyInAnyOrder(
                        new FieldValueCount("1", 1L),
                        new FieldValueCount("2", 1L),
                        new FieldValueCount("3", 1L));
    }

    @Test
    void testNested_countsDocumentsNotNestedObjects() {
        final DocRef nestedIndexRef = createNestedIndex();

        // Document 1 has two users named alice but counts once. Ranked by count, then by value.
        final FieldValues fieldValues = fetch(nestedIndexRef, "Users.Name", FieldType.KEYWORD, null, null, null);
        assertThat(fieldValues.getValues())
                .containsExactly(
                        new FieldValueCount("bob", 2L),
                        new FieldValueCount("alice", 1L),
                        new FieldValueCount("carol", 1L));
        assertThat(fieldValues.getTotalCount()).isEqualTo(4);
    }

    @Test
    void testNested_withinNestedObject() {
        final DocRef nestedIndexRef = createNestedIndex();

        // admin is held by three roles in documents 1 and 3; user by three roles in documents 1 and 2
        assertThat(fetch(nestedIndexRef, "Users.Roles.Role", FieldType.KEYWORD, null, null, null).getValues())
                .containsExactly(new FieldValueCount("admin", 2L), new FieldValueCount("user", 2L));
    }

    @Test
    void testNested_objectWithinNestedObject() {
        final DocRef nestedIndexRef = createNestedIndex();

        assertThat(fetch(nestedIndexRef, "Users.Address.City", FieldType.KEYWORD, null, null, null).getValues())
                .containsExactly(new FieldValueCount("Paris", 2L), new FieldValueCount("Rome", 1L));
    }

    @Test
    void testNested_textUsesKeywordMultiField() {
        final DocRef nestedIndexRef = createNestedIndex();

        assertThat(fetch(nestedIndexRef, "Users.Message", FieldType.TEXT, null, null, null).getValues())
                .extracting(FieldValueCount::getValue)
                .containsExactly("Logged in", "Logged out");
    }

    @Test
    void testNested_filterAndTimeRange() {
        final DocRef nestedIndexRef = createNestedIndex();

        assertThat(fetch(nestedIndexRef, "Users.Name", FieldType.KEYWORD, "AL", null, null).getValues())
                .containsExactly(new FieldValueCount("alice", 1L));

        // Documents 2 and 3 are dated 2 and 3 January
        final FieldValues fieldValues = fetch(nestedIndexRef, "Users.Name", FieldType.KEYWORD, null, new TimeRange(
                "Two days",
                null,
                "2026-01-02T00:00:00.000Z",
                "2026-01-04T00:00:00.000Z"), null);
        assertThat(fieldValues.getValues())
                .containsExactly(new FieldValueCount("bob", 1L), new FieldValueCount("carol", 1L));
        assertThat(fieldValues.getTotalCount()).isEqualTo(2);
    }

    private DocRef createNestedIndex() {
        nestedIndexName = ElasticTestNode.createIndexName("field-values-nested");
        node.createIndex(nestedIndexName, StreamUtil.fileToString(NESTED_INDEX_BODY));
        node.bulkIndex(nestedIndexName, JsonUtil.getMapper().readTree(StreamUtil.fileToString(NESTED_DOCUMENTS)));
        return helper.createIndexDoc(clusterRef, nestedIndexName, "Date", 100);
    }

    private FieldValues fetch(final String fieldName,
                              final FieldType fieldType,
                              final String filter,
                              final TimeRange timeRange) {
        return fetch(fieldName, fieldType, filter, timeRange, null);
    }

    private FieldValues fetch(final String fieldName,
                              final FieldType fieldType,
                              final String filter,
                              final TimeRange timeRange,
                              final ExpressionOperator expression) {
        return fetch(indexRef, fieldName, fieldType, filter, timeRange, expression);
    }

    private FieldValues fetch(final DocRef dataSource,
                              final String fieldName,
                              final FieldType fieldType,
                              final String filter,
                              final TimeRange timeRange,
                              final ExpressionOperator expression) {
        return handler.getFieldValues(new FetchFieldValuesRequest(
                dataSource,
                QueryField.builder().fldName(fieldName).fldType(fieldType).build(),
                filter,
                timeRange,
                expression,
                DATE_TIME_SETTINGS));
    }
}
