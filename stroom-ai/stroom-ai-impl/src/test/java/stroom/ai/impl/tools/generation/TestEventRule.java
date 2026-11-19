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

package stroom.ai.impl.tools.generation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// A rule's catch_all flag: the rule for the records no other rule takes, last and without conditions.
class TestEventRule extends GenerationFixtures {

    @Test
    void catchAllIsReadAndWrittenOnlyWhenSet() {
        final EventRule rest = EventRule.fromJson(Map.of("name", "rest", "catch_all", true));
        final EventRule plain = EventRule.fromJson(Map.of("name", "rest"));

        assertThat(rest.catchAll()).isTrue();
        assertThat(rest.toJson(DumpMode.COMPACT)).containsEntry("catch_all", true);
        assertThat(plain.catchAll()).isFalse();
        assertThat(plain.toJson(DumpMode.COMPACT)).doesNotContainKey("catch_all");
        assertThat(EventRule.of("x", List.of(), false, List.of()).catchAll()).isFalse();
    }

    @Test
    void aCatchAllWithConditionsOrDroppingIsRefused() {
        assertThatThrownBy(() -> EventRule.fromJson(Map.of("name", "rest", "catch_all", true,
                "when", List.of(equalsCondition("action", "login")))))
                .isInstanceOf(MappingValidationException.class)
                .hasMessageContaining("[rest] catch_all is the rule for the records no other rule takes");
        assertThatThrownBy(() -> EventRule.fromJson(Map.of("name", "rest", "catch_all", true, "drop", true)))
                .isInstanceOf(MappingValidationException.class)
                .hasMessageContaining("no drop");
    }

    @Test
    void aMappingHasOneCatchAllAtMostAndLast() {
        final Map<String, Object> first = rule("first", List.of(), LOGON);
        first.put("catch_all", true);
        final Map<String, Object> last = otherRule();
        last.put("catch_all", true);

        assertThatThrownBy(() -> mapping(with("events", List.of(first, last))))
                .isInstanceOf(MappingValidationException.class)
                .hasMessageContaining("are each marked catch_all");
        assertThatThrownBy(() -> mapping(with("events", List.of(first, rule("logon",
                List.of(equalsCondition("action", "login")), LOGON)))))
                .isInstanceOf(MappingValidationException.class)
                .hasMessageContaining("is marked catch_all but isn't last");
        assertThat(mapping(with("events", List.of(rule("logon", List.of(equalsCondition("action", "login")), LOGON),
                last))).events().getLast().catchAll()).isTrue();
    }
}
