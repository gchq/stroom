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

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestQueryOptionSet {

    private static final QueryOptionDefinition FLAG = QueryOptionDefinition.builder()
            .id("flag")
            .displayName("Flag")
            .booleanType(false)
            .build();

    private static final QueryOptionDefinition LEVEL = QueryOptionDefinition.builder()
            .id("level")
            .displayName("Level")
            .integerType(0, 0, 10)
            .addRule(QueryOptionRule.disabledWhen("flag", "true"))
            .build();

    @Test
    void testGetDefinition() {
        final QueryOptionSet set = QueryOptionSet.of(FLAG, LEVEL);

        assertThat(set.getDefinitions())
                .containsExactly(FLAG, LEVEL);
        assertThat(set.getDefinition("level"))
                .isEqualTo(LEVEL);
        assertThat(set.getDefinition("unknown"))
                .isNull();
    }

    @Test
    void testNullDefinitions() {
        assertThat(new QueryOptionSet(null).getDefinitions())
                .isEmpty();
    }

    @Test
    void testDefinitionsCannotBeChanged() {
        final List<QueryOptionDefinition> definitions = new ArrayList<>(List.of(FLAG));
        final QueryOptionSet set = new QueryOptionSet(definitions);
        definitions.add(LEVEL);

        assertThat(set.getDefinitions())
                .containsExactly(FLAG);
        assertThatThrownBy(() -> set.getDefinitions().add(LEVEL))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testDuplicateId() {
        assertThatThrownBy(() -> QueryOptionSet.of(FLAG, FLAG))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("flag");
    }

    @Test
    void testRuleForOptionNotInSet() {
        assertThatThrownBy(() -> QueryOptionSet.of(LEVEL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("level")
                .hasMessageContaining("flag");
    }

    @Test
    void testSerialisation() {
        final QueryOptionSet set = QueryOptionSet.of(FLAG, LEVEL);

        final String json = JsonUtil.writeValueAsString(set, false);

        assertThat(JsonUtil.readValue(json, QueryOptionSet.class))
                .isEqualTo(set);
    }

    @Test
    void testFixturesAreValid() {
        // Building the fixtures validates them, so this fails if they refer to options not in the set
        assertThat(QueryOptionSetFixtures.SET_A.getDefinitions())
                .hasSize(4);
        assertThat(QueryOptionSetFixtures.SET_B.getDefinitions())
                .hasSize(6);
    }
}
