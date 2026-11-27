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

package stroom.query.common.v2;

import stroom.query.api.Column;
import stroom.query.api.FlatResult;
import stroom.query.api.Format;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestVisJson {

    @Test
    void testTextHasNoMinMaxOrSum() {
        // Rows of a text field whose values look like dates and numbers, and a number field
        final List<Column> columns = List.of(
                Column.builder().name(":ParentKey").build(),
                Column.builder().name(":Key").build(),
                Column.builder().name(":Depth").build(),
                Column.builder().name("Code").format(Format.TEXT).build(),
                Column.builder().name("Count").format(Format.NUMBER).build());
        final List<List<Object>> rows = List.of(
                Arrays.asList(null, null, 0, "2024-01-01T00:00:00.000Z", 5.0),
                Arrays.asList(null, null, 0, "404", 7.0));
        final String json = new VisJson().createJson(new FlatResult("vis", columns, rows, 2L, null, null));

        final JsonNode store = JsonUtil.getNoIndentMapper().readTree(json);
        assertThat(store.get("types").toString()).isEqualTo("[\"TEXT\",\"NUMBER\"]");
        assertThat(store.get("min").get(0).isNull()).isTrue();
        assertThat(store.get("max").get(0).isNull()).isTrue();
        assertThat(store.get("min").get(1).asDouble()).isEqualTo(5.0);
        assertThat(store.get("max").get(1).asDouble()).isEqualTo(7.0);
        assertThat(store.get("sum").get(1).asDouble()).isEqualTo(12.0);
    }

    @Test
    void testKeyTypeOfDurationGroups() {
        assertThat(getKeyTypes(Format.DURATION, 5000.0, 7000.0, 1.0)).isEqualTo("NUMBER NUMBER");
        assertThat(getKeyTypes(Format.DURATION, 5000.0, null, 1.0)).isEqualTo("NUMBER NUMBER");
        // They must be what they are labelled as.
        assertThat(getKeyTypes(Format.DURATION, 5000.0, "60m", 1.0)).isEqualTo("TEXT NUMBER");
        assertThat(getKeyTypes(Format.DURATION, "1h", "60m", 1.0)).isEqualTo("TEXT NUMBER");
        assertThat(getKeyTypes(Format.DURATION, 5000.0, true, 1.0)).isEqualTo("TEXT NUMBER");
        // Each group has its own.
        assertThat(getKeyTypes(Format.DURATION, 5000.0, 7000.0, "1h")).isEqualTo("NUMBER TEXT");
        assertThat(getKeyTypes(Format.NUMBER, 5000.0, "a", "1h")).isEqualTo("NUMBER TEXT");
    }

    // Gets the types of the keys of two nested groups.
    private static String getKeyTypes(final Format format,
                                      final Object key1,
                                      final Object key2,
                                      final Object nestedKey) {
        final List<Column> columns = List.of(
                Column.builder().name(":ParentKey").build(),
                Column.builder().name(":Key").build(),
                Column.builder().name(":Depth").build(),
                Column.builder().name("Elapsed").group(0).format(format).build(),
                Column.builder().name("Size").group(1).format(Format.DURATION).build(),
                Column.builder().name("Count").format(Format.NUMBER).build());
        final List<List<Object>> rows = List.of(
                Arrays.asList(null, Arrays.asList(key1), 0, null, null, 1.0),
                Arrays.asList(null, Arrays.asList(key2), 0, null, null, 1.0),
                Arrays.asList(Arrays.asList(key1), Arrays.asList(key1, nestedKey), 1, null, null, 1.0),
                Arrays.asList(Arrays.asList(key1, nestedKey), null, 2, null, null, 1.0));
        final String json = new VisJson().createJson(new FlatResult("vis", columns, rows, 4L, null, null));

        final JsonNode store = JsonUtil.getNoIndentMapper().readTree(json);
        return store.get("keyType").asString() + " " + store.get("values").get(0).get("keyType").asString();
    }

    @Test
    void testKeyTypeOfOtherGroups() {
        final List<Column> columns = List.of(
                Column.builder().name(":ParentKey").build(),
                Column.builder().name(":Key").build(),
                Column.builder().name(":Depth").build(),
                Column.builder().name("Size").group(0).format(Format.NUMBER).build(),
                Column.builder().name("Count").format(Format.NUMBER).build());
        final List<List<Object>> rows = List.of(
                Arrays.asList(null, List.of("a"), 0, null, 1.0),
                Arrays.asList(List.of("a"), null, 1, null, 1.0));
        final String json = new VisJson().createJson(new FlatResult("vis", columns, rows, 2L, null, null));

        assertThat(JsonUtil.getNoIndentMapper().readTree(json).get("keyType").asString()).isEqualTo("NUMBER");
    }

    @Test
    void testDurationsAreNumbers() {
        final List<Column> columns = List.of(
                Column.builder().name(":ParentKey").build(),
                Column.builder().name(":Key").build(),
                Column.builder().name(":Depth").build(),
                Column.builder().name("Elapsed").format(Format.DURATION).build());
        final List<List<Object>> rows = List.of(
                Arrays.asList(null, null, 0, 5000.0),
                Arrays.asList(null, null, 0, 7000.0));
        final String json = new VisJson().createJson(new FlatResult("vis", columns, rows, 2L, null, null));

        final JsonNode store = JsonUtil.getNoIndentMapper().readTree(json);
        assertThat(store.get("types").toString()).isEqualTo("[\"NUMBER\"]");
        assertThat(store.get("sum").get(0).asDouble()).isEqualTo(12000.0);
    }
}
