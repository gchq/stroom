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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestFieldValuesFixture {

    private static final FieldValuesFixture FIXTURE = new FieldValuesFixture(counts(), 1000);

    @Test
    void testNoFilterFindsEveryValueInOrder() {
        assertThat(values(FIXTURE.find("{\"field\":{\"fldName\":\"Status\"}}")))
                .containsExactly("error", "warn", "info", "a,b");
        assertThat(values(FIXTURE.find("{\"filter\":\"  \"}"))).hasSize(4);
        assertThat(FieldValuesFixture.filterOf("{\"filter\":null}")).isNull();
    }

    @Test
    void testFilterContainsIgnoringCase() {
        assertThat(values(FIXTURE.find("{\"filter\":\"R\"}"))).containsExactly("error", "warn");
        assertThat(values(FIXTURE.find("{\"filter\":\" Wa \"}"))).containsExactly("warn");
        assertThat(values(FIXTURE.find("{\"filter\":\"none\"}"))).isEmpty();
    }

    @Test
    void testReplyHasCountsAndTotal() {
        final Map<?, ?> reply = (Map<?, ?>) JsonValues.parse(FIXTURE.find("{\"filter\":\"warn\"}"));
        assertThat(((Number) reply.get("totalCount")).longValue()).isEqualTo(1000);
        assertThat(reply.get("allTime")).isEqualTo(true);
        final Map<?, ?> warn = (Map<?, ?>) ((List<?>) reply.get("values")).get(0);
        assertThat(((Number) warn.get("count")).longValue()).isEqualTo(300);
    }

    private static Map<String, Long> counts() {
        final Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("error", 500L);
        counts.put("warn", 300L);
        counts.put("info", 150L);
        counts.put("a,b", 50L);
        return counts;
    }

    private static List<String> values(final String replyJson) {
        final Map<?, ?> reply = (Map<?, ?>) JsonValues.parse(replyJson);
        final List<String> values = new ArrayList<>();
        for (final Object value : (List<?>) reply.get("values")) {
            values.add((String) ((Map<?, ?>) value).get("value"));
        }
        return values;
    }
}
