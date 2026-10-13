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


package stroom.gwt.workbench.client.widgets.query;

import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.query.api.datasource.QueryField;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestFieldFixture {

    private static final FieldFixture FIXTURE = new FieldFixture(List.of(
            QueryField.createText("UserId"),
            QueryField.createDate("EventTime"),
            QueryField.createDocRefByUniqueName("Feed", "Feed"),
            QueryField.createUserRef("RunAsUser", false)));

    @Test
    void testNoFilterFindsEveryField() {
        assertThat(names(FIXTURE.find("{\"pageRequest\":{\"offset\":0,\"length\":100}}")))
                .containsExactly("UserId", "EventTime", "Feed", "RunAsUser");
        assertThat(names(FIXTURE.find("{\"filter\":\"  \"}"))).hasSize(4);
        assertThat(FieldFixture.describe("{\"filter\":null}")).isEqualTo("loadFields");
    }

    @Test
    void testFilterContainsIgnoringCase() {
        assertThat(names(FIXTURE.find("{\"filter\":\"E\"}")))
                .containsExactly("UserId", "EventTime", "Feed", "RunAsUser");
        assertThat(names(FIXTURE.find("{\"filter\":\"time\"}"))).containsExactly("EventTime");
        assertThat(FieldFixture.describe("{\"filter\":\"time\"}")).isEqualTo("loadFields");
    }

    @Test
    void testFindByName() {
        final String request = "{\"filter\":\"==\\\"UserId\\\"\"}";
        assertThat(names(FIXTURE.find(request))).containsExactly("UserId");
        assertThat(FieldFixture.describe(request)).isEqualTo("findFieldByName:UserId");
        // Case sensitive
        assertThat(names(FIXTURE.find(request.replace("UserId", "userid")))).isEmpty();
    }

    @Test
    void testFieldJson() {
        final List<?> values = (List<?>) ((Map<?, ?>) JsonValues.parse(FIXTURE.find("{}"))).get("values");
        assertThat(values.get(0)).isEqualTo(Map.of(
                "fldName", "UserId", "fldType", "TEXT", "conditionSet", "DEFAULT_TEXT", "queryable", true));
        assertThat(values.get(2)).isEqualTo(Map.of(
                "fldName", "Feed", "fldType", "DOC_REF", "conditionSet", "DOC_REF_ALL", "docRefType", "Feed",
                "queryable", true));
        assertThat(((Map<?, ?>) values.get(3)).get("queryable")).isEqualTo(false);
    }

    private static List<String> names(final String json) {
        final List<String> names = new ArrayList<>();
        for (final Object field : (List<?>) ((Map<?, ?>) JsonValues.parse(json)).get("values")) {
            names.add((String) ((Map<?, ?>) field).get("fldName"));
        }
        return names;
    }
}
