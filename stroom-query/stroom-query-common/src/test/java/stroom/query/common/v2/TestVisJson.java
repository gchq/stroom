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
}
