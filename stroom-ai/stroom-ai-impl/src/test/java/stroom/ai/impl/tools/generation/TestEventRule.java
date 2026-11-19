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

/// A rule's catch_all flag: the rule for the records no other rule takes, last and without conditions; or a line
/// format's, its only conditions on the variant field, last of its format's rules.
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
    void aCatchAllWithConditionsOnMoreThanOneFieldOrDroppingIsRefused() {
        assertThatThrownBy(() -> EventRule.fromJson(Map.of("name", "rest", "catch_all", true,
                "when", List.of(equalsCondition("variant", "gw"), equalsCondition("action", "login")))))
                .isInstanceOf(MappingValidationException.class)
                .hasMessageContaining("[rest] catch_all is the rule for the records no other rule takes");
        assertThatThrownBy(() -> EventRule.fromJson(Map.of("name", "rest", "catch_all", true,
                "when", List.of(Map.of("xpath", "data[@name='variant']/@value", "equals", "gw")))))
                .isInstanceOf(MappingValidationException.class)
                .hasMessageContaining("only conditions on the variant field");
        assertThatThrownBy(() -> EventRule.fromJson(Map.of("name", "rest", "catch_all", true, "drop", true)))
                .isInstanceOf(MappingValidationException.class)
                .hasMessageContaining("no drop");
    }

    @Test
    void aLineFormatsCatchAllHasItsVariantConditionAsItsOnlyCondition() {
        final EventRule gw = EventRule.fromJson(Map.of("name", "gw_uncatalogued", "catch_all", true,
                "when", List.of(equalsCondition("variant", "gw"))));
        final EventRule unread = EventRule.fromJson(Map.of("name", "unread_uncatalogued", "catch_all", true,
                "when", List.of(Map.of("field", "variant", "present", false))));

        assertThat(gw.catchAll()).isTrue();
        assertThat(gw.variantField()).isEqualTo("variant");
        assertThat(unread.variantField()).isEqualTo("variant");
        assertThat(EventRule.fromJson(Map.of("name", "rest", "catch_all", true)).variantField()).isNull();
    }

    @Test
    void aMappingMayHaveALineFormatsCatchAllForEachFormatLastOfItsRules() {
        final List<Map<String, Object>> events = List.of(
                rule("gw_logon", List.of(equalsCondition("variant", "gw"), equalsCondition("action", "login")),
                        LOGON),
                catchAll("gw_uncatalogued", equalsCondition("variant", "gw")),
                rule("linux_logon", List.of(equalsCondition("variant", "linux"), equalsCondition("action",
                        "login")), LOGON),
                catchAll("linux_uncatalogued", equalsCondition("variant", "linux")));

        assertThat(mapping(with("events", events)).events()).extracting(EventRule::catchAll)
                .containsExactly(false, true, false, true);
    }

    @Test
    void aLineFormatsCatchAllBeforeARuleOfItsFormatOrOnAnotherFieldIsRefused() {
        assertThatThrownBy(() -> mapping(with("events", List.of(
                catchAll("gw_uncatalogued", equalsCondition("variant", "gw")),
                rule("gw_logon", List.of(equalsCondition("variant", "gw"), equalsCondition("action", "login")),
                        LOGON)))))
                .isInstanceOf(MappingValidationException.class)
                .hasMessageContaining("'gw_uncatalogued' is marked catch_all but isn't the last of its line "
                                      + "format's rules ('gw_logon' comes after it)");
        assertThatThrownBy(() -> mapping(with("events", List.of(
                catchAll("gw_uncatalogued", equalsCondition("variant", "gw")),
                catchAll("other_uncatalogued", equalsCondition("format", "linux"))))))
                .isInstanceOf(MappingValidationException.class)
                .hasMessageContaining("test different fields ('variant', 'format')");
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

    private static Map<String, Object> catchAll(final String name, final Map<String, Object> condition) {
        final Map<String, Object> rule = rule(name, List.of(condition), LOGON);
        rule.put("catch_all", true);
        return rule;
    }
}
