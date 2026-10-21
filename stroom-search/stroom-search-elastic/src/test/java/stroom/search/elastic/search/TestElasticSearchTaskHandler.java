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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
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
