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

package stroom.query.api.datasource;

import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestQueryField {

    @Test
    void testSerialisation_queryOptionSet() {
        final QueryField field = QueryField.builder()
                .fldName("user.name")
                .fldType(FieldType.KEYWORD)
                .queryOptionSet(QueryOptionSetFixtures.SET_A)
                .build();

        final String json = JsonUtil.writeValueAsString(field, false);
        // The definitions travel with the field, so the UI needs no knowledge of the data source
        assertThat(json)
                .contains("\"queryOptionSet\":{\"definitions\":[{\"id\":\"toggle\"");

        final QueryField field2 = JsonUtil.readValue(json, QueryField.class);
        assertThat(field2.getQueryOptionSet())
                .isEqualTo(QueryOptionSetFixtures.SET_A);
        assertThat(field2.copy().build().getQueryOptionSet())
                .isEqualTo(QueryOptionSetFixtures.SET_A);
    }

    @Test
    void testSerialisation_noQueryOptionSet() {
        final QueryField field = QueryField.createKeyword("user.name");

        final String json = JsonUtil.writeValueAsString(field, false);
        assertThat(json)
                .doesNotContain("queryOptionSet");
        assertThat(JsonUtil.readValue(json, QueryField.class).getQueryOptionSet())
                .isNull();
    }
}
