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

import stroom.query.common.v2.ResultStore;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.time.StroomDuration;

import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import jakarta.json.Json;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.node.ObjectNode;

import java.io.StringReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/// Unit tests for the pure field resolution and value conversion helpers in [ElasticSearchTaskHandler].
/// No Elasticsearch cluster is involved; hit `fields` maps are built by hand in the shapes that
/// Elasticsearch returns them.
@ExtendWith(MockitoExtension.class)
class TestElasticSearchTaskHandler {

    @Mock
    private ValuesConsumer valuesConsumer;
    @Mock
    private ErrorConsumer errorConsumer;
    @Mock
    private ResultStore resultStore;

    // --------------------------------------------------------------------------------
    // getFieldValue
    // --------------------------------------------------------------------------------

    @Test
    void testGetFieldValue_plainSingleValue() {
        final Map<String, JsonData> fields = fields("""
                {"user.name": ["Alice"]}""");

        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "user.name"))
                .isEqualTo("Alice");
    }

    @Test
    void testGetFieldValue_plainMultiValued() {
        final Map<String, JsonData> fields = fields("""
                {"tags": ["a", "b", "c"]}""");

        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "tags"))
                .isEqualTo(List.of("a", "b", "c"));
    }

    @Test
    void testGetFieldValue_plainBareScalar() {
        // Elasticsearch always returns arrays, but a bare scalar is tolerated
        final Map<String, JsonData> fields = fields("""
                {"count": 5}""");

        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "count"))
                .isEqualTo(5L);
    }

    @Test
    void testGetFieldValue_plainFieldAlongsideOthers() {
        final Map<String, JsonData> fields = fields("""
                {"user.name": ["Alice"], "user.age": [42], "host": ["web01"]}""");

        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "user.age"))
                .isEqualTo(42L);
        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "host"))
                .isEqualTo("web01");
    }

    @Test
    void testGetFieldValue_nestedSingleValue() {
        final Map<String, JsonData> fields = fields("""
                {"user": [{"name": ["Alice"]}]}""");

        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "user.name"))
                .isEqualTo("Alice");
    }

    @Test
    void testGetFieldValue_nestedAcrossSiblings() {
        // Values for the same field gathered across sibling nested objects
        final Map<String, JsonData> fields = fields("""
                {"user": [
                  {"name": ["Alice"], "age": [30]},
                  {"name": ["Bob"]},
                  {"name": ["Carol", "Caz"], "age": [25]}
                ]}""");

        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "user.name"))
                .isEqualTo(List.of("Alice", "Bob", "Carol", "Caz"));
        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "user.age"))
                .isEqualTo(List.of(30L, 25L));
    }

    @Test
    void testGetFieldValue_nestedOnlyOneSiblingHasField() {
        final Map<String, JsonData> fields = fields("""
                {"user": [{"name": ["Alice"]}, {"age": [3]}]}""");

        // Only one value collected, so a single value rather than a list is returned
        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "user.age"))
                .isEqualTo(3L);
    }

    @Test
    void testGetFieldValue_doublyNested() {
        final Map<String, JsonData> fields = fields("""
                {"a": [{"b": [{"c": [1]}, {"c": [2]}]}, {"b": [{"c": [3]}]}]}""");

        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "a.b.c"))
                .isEqualTo(List.of(1L, 2L, 3L));
    }

    @Test
    void testGetFieldValue_doublyNestedSingleValue() {
        final Map<String, JsonData> fields = fields("""
                {"a": [{"b": [{"c": [1]}]}]}""");

        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "a.b.c"))
                .isEqualTo(1L);
    }

    @Test
    void testGetFieldValue_nestedContainerWithObjectPathSegment() {
        // `a` is nested and `a.b.c` is nested, with `b` a plain object, so the inner container is keyed
        // `b.c` relative to `a`
        final Map<String, JsonData> fields = fields("""
                {"a": [{"b.c": [{"d": ["x"]}, {"d": ["y"]}]}]}""");

        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "a.b.c.d"))
                .isEqualTo(List.of("x", "y"));
    }

    @Test
    void testGetFieldValue_multiFieldInsideNested() {
        final Map<String, JsonData> fields = fields("""
                {"user": [{"name.keyword": ["x"]}, {"name.keyword": ["y"]}]}""");

        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "user.name.keyword"))
                .isEqualTo(List.of("x", "y"));
        // The text field itself was not requested, so is not present
        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "user.name"))
                .isNull();
    }

    @Test
    void testGetFieldValue_nestedAndPlainFieldsTogether() {
        final Map<String, JsonData> fields = fields("""
                {"host": ["web01"], "user": [{"name": ["Alice"]}]}""");

        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "host"))
                .isEqualTo("web01");
        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "user.name"))
                .isEqualTo("Alice");
    }

    @Test
    void testGetFieldValue_missingField() {
        final Map<String, JsonData> fields = fields("""
                {"host": ["web01"], "user": [{"name": ["Alice"]}]}""");

        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "other"))
                .isNull();
        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "user.age"))
                .isNull();
        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "host.name"))
                .isNull();
        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "a.b.c"))
                .isNull();
    }

    @Test
    void testGetFieldValue_emptyArrays() {
        final Map<String, JsonData> fields = fields("""
                {"tags": [], "user": [], "group": [{"name": []}]}""");

        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "tags"))
                .isNull();
        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "user.name"))
                .isNull();
        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "group.name"))
                .isNull();
    }

    @Test
    void testGetFieldValue_nullsSkipped() {
        final Map<String, JsonData> fields = fields("""
                {"tags": [null, "a", null], "empty": [null]}""");

        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "tags"))
                .isEqualTo("a");
        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "empty"))
                .isNull();
    }

    @Test
    void testGetFieldValue_nullOrEmptyInput() {
        assertThat(ElasticSearchTaskHandler.getFieldValue(null, "a"))
                .isNull();
        assertThat(ElasticSearchTaskHandler.getFieldValue(Map.of(), "a"))
                .isNull();
        assertThat(ElasticSearchTaskHandler.getFieldValue(fields("""
                {"a": ["x"]}"""), null))
                .isNull();
    }

    @Test
    void testGetFieldValue_jsonDataFromJson() {
        // JsonData parsed by the Elasticsearch client itself, rather than wrapping a jakarta JsonValue
        final Map<String, JsonData> fields = new HashMap<>();
        fields.put("name", JsonData.fromJson("[\"Alice\", \"Bob\"]"));
        fields.put("user", JsonData.fromJson("[{\"age\": [7]}]"));

        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "name"))
                .isEqualTo(List.of("Alice", "Bob"));
        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "user.age"))
                .isEqualTo(7L);
    }

    @Test
    void testGetFieldValue_valueTypes() {
        final Map<String, JsonData> fields = fields("""
                {
                  "long": [9007199254740993],
                  "double": [1.5],
                  "doubleWithZeroFraction": [2.0],
                  "true": [true],
                  "false": [false],
                  "string": ["text"],
                  "object": [{"type": "Point", "coordinates": [1, 2]}]
                }""");

        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "long"))
                .isEqualTo(9007199254740993L);
        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "double"))
                .isEqualTo(1.5D);
        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "doubleWithZeroFraction"))
                .isEqualTo(2.0D);
        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "true"))
                .isEqualTo(true);
        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "false"))
                .isEqualTo(false);
        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "string"))
                .isEqualTo("text");
        // Objects returned directly for a field, e.g. a geo_point, are rendered as JSON text
        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "object"))
                .isEqualTo("{\"type\":\"Point\",\"coordinates\":[1,2]}");
    }

    @Test
    void testGetFieldValue_unsignedLongAboveLongMax() {
        final Map<String, JsonData> fields = fields("""
                {"big": [18446744073709551615]}""");

        final Object value = ElasticSearchTaskHandler.getFieldValue(fields, "big");

        assertThat(String.valueOf(value))
                .isEqualTo("18446744073709551615");
        assertThat(value)
                .isEqualTo(new BigInteger("18446744073709551615"));
    }

    @Test
    void testGetFieldValue_unsignedLongJsonDataFromJson() {
        // JsonData parsed by the Elasticsearch client itself, as for a real search response
        final Map<String, JsonData> fields = new HashMap<>();
        fields.put("big", JsonData.fromJson("[18446744073709551615]"));

        assertThat(ElasticSearchTaskHandler.getFieldValue(fields, "big"))
                .isEqualTo(new BigInteger("18446744073709551615"));
    }

    @Test
    void testJsonValueToNative_longBoundaries() {
        // Values that fit in a long remain a Long
        assertThat(ElasticSearchTaskHandler.jsonValueToNative(Json.createValue(Long.MAX_VALUE)))
                .isEqualTo(Long.MAX_VALUE);
        assertThat(ElasticSearchTaskHandler.jsonValueToNative(Json.createValue(Long.MIN_VALUE)))
                .isEqualTo(Long.MIN_VALUE);

        // Values just outside the range of a long are preserved exactly
        final BigInteger aboveMax = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
        final BigInteger belowMin = BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE);
        assertThat(ElasticSearchTaskHandler.jsonValueToNative(Json.createValue(aboveMax)))
                .isEqualTo(aboveMax);
        assertThat(ElasticSearchTaskHandler.jsonValueToNative(Json.createValue(belowMin)))
                .isEqualTo(belowMin);
    }

    // --------------------------------------------------------------------------------
    // collectFieldValues / findNestedSplit
    // --------------------------------------------------------------------------------

    @Test
    void testCollectFieldValues_appendsToExistingList() {
        final Map<String, JsonValue> fields = jsonFields("""
                {"user": [{"name": ["Alice"]}, {"name": ["Bob"]}]}""");
        final List<Object> out = new ArrayList<>(List.of("existing"));

        ElasticSearchTaskHandler.collectFieldValues(fields, "user.name", out);

        assertThat(out)
                .containsExactly("existing", "Alice", "Bob");
    }

    @Test
    void testCollectFieldValues_ignoresNonObjectElements() {
        final Map<String, JsonValue> fields = jsonFields("""
                {"user": ["oops", {"name": ["Alice"]}, 3]}""");
        final List<Object> out = new ArrayList<>();

        ElasticSearchTaskHandler.collectFieldValues(fields, "user.name", out);

        assertThat(out)
                .containsExactly("Alice");
    }

    @Test
    void testCollectFieldValues_nullInput() {
        final List<Object> out = new ArrayList<>();

        ElasticSearchTaskHandler.collectFieldValues(null, "a", out);
        ElasticSearchTaskHandler.collectFieldValues(jsonFields("""
                {"a": ["x"]}"""), null, out);

        assertThat(out)
                .isEmpty();
    }

    @Test
    void testCollectFieldValues_missing() {
        final List<Object> out = new ArrayList<>();

        ElasticSearchTaskHandler.collectFieldValues(jsonFields("""
                {"a": ["x"]}"""), "b.c", out);

        assertThat(out)
                .isEmpty();
    }

    @Test
    void testFindNestedSplit_singleLevel() {
        final Map<String, JsonValue> fields = jsonFields("""
                {"user": [{"name": ["Alice"]}]}""");

        assertThat(ElasticSearchTaskHandler.findNestedSplit(fields, "user.name"))
                .isEqualTo("user".length());
    }

    @Test
    void testFindNestedSplit_prefersLongestPrefix() {
        // Both `a` and `a.b` look like nested containers, so the longer one should win
        final Map<String, JsonValue> fields = jsonFields("""
                {"a": [{"x": [1]}], "a.b": [{"c": [2]}]}""");

        assertThat(ElasticSearchTaskHandler.findNestedSplit(fields, "a.b.c"))
                .isEqualTo("a.b".length());
    }

    @Test
    void testFindNestedSplit_skipsShorterWhenLongerAbsent() {
        final Map<String, JsonValue> fields = jsonFields("""
                {"a": [{"b.c": [{"d": [1]}]}]}""");

        assertThat(ElasticSearchTaskHandler.findNestedSplit(fields, "a.b.c.d"))
                .isEqualTo("a".length());
    }

    @Test
    void testFindNestedSplit_notFound() {
        final Map<String, JsonValue> fields = jsonFields("""
                {"a": ["x"], "b": [], "c": [{"d": [1]}]}""");

        // `a` is an array of scalars and `b` is empty, so neither is a nested container
        assertThat(ElasticSearchTaskHandler.findNestedSplit(fields, "a.x"))
                .isEqualTo(-1);
        assertThat(ElasticSearchTaskHandler.findNestedSplit(fields, "b.x"))
                .isEqualTo(-1);
        // No dot in the path
        assertThat(ElasticSearchTaskHandler.findNestedSplit(fields, "c"))
                .isEqualTo(-1);
        // A leading dot is not treated as a split point
        assertThat(ElasticSearchTaskHandler.findNestedSplit(fields, ".c"))
                .isEqualTo(-1);
        assertThat(ElasticSearchTaskHandler.findNestedSplit(fields, "z.y"))
                .isEqualTo(-1);
    }

    @Test
    void testFindNestedSplit_mixedArrayCountsAsContainer() {
        final Map<String, JsonValue> fields = jsonFields("""
                {"a": ["x", {"b": [1]}]}""");

        assertThat(ElasticSearchTaskHandler.findNestedSplit(fields, "a.b"))
                .isEqualTo(1);
    }

    // --------------------------------------------------------------------------------
    // Field name helpers
    // --------------------------------------------------------------------------------

    @Test
    void testGetFieldBaseName() {
        assertThat(ElasticSearchTaskHandler.getFieldBaseName("content.vector"))
                .isEqualTo("content");
        assertThat(ElasticSearchTaskHandler.getFieldBaseName("a.b.vector"))
                .isEqualTo("a.b");
        assertThat(ElasticSearchTaskHandler.getFieldBaseName("vector"))
                .isNull();
        assertThat(ElasticSearchTaskHandler.getFieldBaseName("trailing."))
                .isNull();
        assertThat(ElasticSearchTaskHandler.getFieldBaseName(".leading"))
                .isNull();
    }

    @Test
    void testGetSuffixedFieldName() {
        assertThat(ElasticSearchTaskHandler.getSuffixedFieldName("content.vector", ".text"))
                .isEqualTo("content.text");
        assertThat(ElasticSearchTaskHandler.getSuffixedFieldName("a.b.vector", ".score"))
                .isEqualTo("a.b.score");
        assertThat(ElasticSearchTaskHandler.getSuffixedFieldName("vector", ".text"))
                .isNull();
    }

    @Test
    void testFirstStringValue() {
        assertThat(ElasticSearchTaskHandler.firstStringValue(null))
                .isNull();
        assertThat(ElasticSearchTaskHandler.firstStringValue("a"))
                .isEqualTo("a");
        assertThat(ElasticSearchTaskHandler.firstStringValue(12L))
                .isEqualTo("12");
        assertThat(ElasticSearchTaskHandler.firstStringValue(List.of("a", "b")))
                .isEqualTo("a");
        assertThat(ElasticSearchTaskHandler.firstStringValue(List.of()))
                .isNull();
    }

    @Test
    void testJsonValueToNative() {
        assertThat(ElasticSearchTaskHandler.jsonValueToNative(null))
                .isNull();
        assertThat(ElasticSearchTaskHandler.jsonValueToNative(JsonValue.NULL))
                .isNull();
        assertThat(ElasticSearchTaskHandler.jsonValueToNative(JsonValue.TRUE))
                .isEqualTo(true);
        assertThat(ElasticSearchTaskHandler.jsonValueToNative(Json.createValue(3)))
                .isEqualTo(3L);
        assertThat(ElasticSearchTaskHandler.jsonValueToNative(Json.createValue(3.25D)))
                .isEqualTo(3.25D);
        assertThat(ElasticSearchTaskHandler.jsonValueToNative(Json.createValue("s")))
                .isEqualTo("s");
        assertThat(ElasticSearchTaskHandler.jsonValueToNative(JsonValue.EMPTY_JSON_ARRAY))
                .isEqualTo("[]");
    }

    // --------------------------------------------------------------------------------
    // processResultBatch (conversion to Val)
    // --------------------------------------------------------------------------------

    @Test
    void testProcessResultBatch_valuesInFieldIndexPositions() {
        final FieldIndex fieldIndex = fieldIndex("host", "user.name", "count", "ratio", "flag");
        final List<Hit<ObjectNode>> hits = List.of(
                hit("1", """
                        {
                          "host": ["web01"],
                          "user": [{"name": ["Alice"]}],
                          "count": [7],
                          "ratio": [0.5],
                          "flag": [true]
                        }"""));
        final AtomicLong hitCount = new AtomicLong();

        processResultBatch(fieldIndex, hits, hitCount);

        final List<Val[]> rows = captureRows(1);
        assertThat(rows.getFirst())
                .containsExactly(
                        ValString.create("web01"),
                        ValString.create("Alice"),
                        ValLong.create(7),
                        ValDouble.create(0.5),
                        ValBoolean.create(true));
        assertThat(hitCount)
                .hasValue(1);
        verifyNoInteractions(errorConsumer);
        verifyNoInteractions(resultStore);
    }

    @Test
    void testProcessResultBatch_collectionsFlattenedToCommaJoinedString() {
        final FieldIndex fieldIndex = fieldIndex("tags", "user.name", "ids");
        final List<Hit<ObjectNode>> hits = List.of(
                hit("1", """
                        {
                          "tags": ["a", "b"],
                          "user": [{"name": ["Alice"]}, {"name": ["Bob"]}],
                          "ids": [1, 2, 3]
                        }"""));

        processResultBatch(fieldIndex, hits, new AtomicLong());

        final List<Val[]> rows = captureRows(1);
        assertThat(rows.getFirst())
                .containsExactly(
                        ValString.create("a, b"),
                        ValString.create("Alice, Bob"),
                        ValString.create("1, 2, 3"));
    }

    @Test
    void testProcessResultBatch_missingFieldsLeftNull() {
        final FieldIndex fieldIndex = fieldIndex("a", "b", "c");
        final List<Hit<ObjectNode>> hits = List.of(
                hit("1", """
                        {"b": ["x"]}"""));

        processResultBatch(fieldIndex, hits, new AtomicLong());

        final List<Val[]> rows = captureRows(1);
        assertThat(rows.getFirst())
                .containsExactly(null, ValString.create("x"), null);
    }

    @Test
    void testProcessResultBatch_hitWithNoValuesNotConsumed() {
        final FieldIndex fieldIndex = fieldIndex("a");
        final List<Hit<ObjectNode>> hits = List.of(
                hit("1", """
                        {"other": ["x"]}"""),
                hit("2", """
                        {"a": []}"""),
                hit("3", """
                        {"a": ["y"]}"""));
        final AtomicLong hitCount = new AtomicLong();

        processResultBatch(fieldIndex, hits, hitCount);

        final List<Val[]> rows = captureRows(1);
        assertThat(rows.getFirst())
                .containsExactly(ValString.create("y"));
        // Every hit is counted, even if it contributes no values
        assertThat(hitCount)
                .hasValue(3);
    }

    @Test
    void testProcessResultBatch_emptyBatch() {
        final AtomicLong hitCount = new AtomicLong();

        processResultBatch(fieldIndex("a"), List.of(), hitCount);

        verify(valuesConsumer, never()).accept(any());
        assertThat(hitCount)
                .hasValue(0);
    }

    @Test
    void testProcessResultBatch_highlights() {
        final ElasticSearchTaskHandler handler = handler(true);
        final Hit<ObjectNode> hit = Hit.of(h -> h
                .index("test-index")
                .id("1")
                .fields(fields("""
                        {"a": ["x"]}"""))
                .highlight("a", List.of("<em>x</em>", "<em>x</em>")));

        handler.processResultBatch(fieldIndex("a"), Set.of(), elasticIndexDoc(), new ElasticQueryParams(),
                null, resultStore, valuesConsumer, errorConsumer, new AtomicLong(), List.of(hit));

        verify(resultStore).addHighlights(Set.of("<em>x</em>"));
        captureRows(1);
    }

    @Test
    void testProcessResultBatch_consumerErrorRoutedToErrorConsumer() {
        final RuntimeException exception = new RuntimeException("consumer failed");
        final ValuesConsumer failingConsumer = values -> {
            throw exception;
        };

        handler(false).processResultBatch(fieldIndex("a"), Set.of(), elasticIndexDoc(), new ElasticQueryParams(),
                null, resultStore, failingConsumer, errorConsumer, new AtomicLong(),
                List.of(hit("1", """
                        {"a": ["x"]}""")));

        verify(errorConsumer).add(exception);
    }

    @Test
    void testProcessResultBatch_unsignedLongAboveLongMax() {
        final FieldIndex fieldIndex = fieldIndex("big", "small", "bigs");
        final List<Hit<ObjectNode>> hits = List.of(
                hit("1", """
                        {
                          "big": [18446744073709551615],
                          "small": [42],
                          "bigs": [1, 18446744073709551615]
                        }"""));

        processResultBatch(fieldIndex, hits, new AtomicLong());

        // No Val type can hold the value exactly as a number, so its exact decimal string is used
        final List<Val[]> rows = captureRows(1);
        assertThat(rows.getFirst())
                .containsExactly(
                        ValString.create("18446744073709551615"),
                        ValLong.create(42),
                        ValString.create("1, 18446744073709551615"));
        verifyNoInteractions(errorConsumer);
    }

    private void processResultBatch(final FieldIndex fieldIndex,
                                    final List<Hit<ObjectNode>> hits,
                                    final AtomicLong hitCount) {
        handler(false).processResultBatch(fieldIndex, Set.of(), elasticIndexDoc(), new ElasticQueryParams(),
                null, resultStore, valuesConsumer, errorConsumer, hitCount, hits);
    }

    private List<Val[]> captureRows(final int expectedCount) {
        final ArgumentCaptor<Val[]> captor = ArgumentCaptor.forClass(Val[].class);
        verify(valuesConsumer, times(expectedCount)).accept(captor.capture());
        return captor.getAllValues();
    }

    private static ElasticSearchTaskHandler handler(final boolean highlight) {
        final ElasticSearchConfig config = new ElasticSearchConfig(
                highlight, StroomDuration.ofMinutes(1), null, null);
        return new ElasticSearchTaskHandler(
                () -> config, null, null, null, null, null, null);
    }

    private static ElasticIndexDoc elasticIndexDoc() {
        return ElasticIndexDoc.builder()
                .uuid("test-uuid")
                .name("test")
                .indexName("test-index")
                .build();
    }

    private static FieldIndex fieldIndex(final String... fieldNames) {
        final FieldIndex fieldIndex = new FieldIndex();
        for (final String fieldName : fieldNames) {
            fieldIndex.create(fieldName);
        }
        return fieldIndex;
    }

    private static Hit<ObjectNode> hit(final String id, final String fieldsJson) {
        return Hit.of(h -> h
                .index("test-index")
                .id(id)
                .fields(fields(fieldsJson)));
    }

    /// Parses a JSON object into a hit `fields` map, as returned by Elasticsearch.
    private static Map<String, JsonData> fields(final String json) {
        final Map<String, JsonData> fields = new HashMap<>();
        jsonFields(json).forEach((key, value) -> fields.put(key, JsonData.of(value)));
        return fields;
    }

    private static Map<String, JsonValue> jsonFields(final String json) {
        try (final JsonReader reader = Json.createReader(new StringReader(json))) {
            return new HashMap<>(reader.readObject());
        }
    }
}
