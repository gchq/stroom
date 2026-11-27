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

package stroom.ai.impl.tools.draft;

import stroom.ai.impl.tools.generation.Condition;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestKeptRuleNames {

    @Test
    void aRuleIsMatchedByItsTypeIdAndConditionsInAnyOrder() {
        final KeptRuleNames kept = KeptRuleNames.of(List.of(
                rule("logon_old", "Logon", List.of(eq("evt", "4624"), eq("os", "win")))), List.of(
                wanted("Logon", eq("os", "win"), eq("evt", "4624"))));

        assertThat(kept.nameOf(0)).isEqualTo("logon_old");
        assertThat(kept.catchAll()).isNull();
    }

    @Test
    void theSameConditionsUnderAnotherTypeIdAreNoMatch() {
        final KeptRuleNames kept = KeptRuleNames.of(List.of(
                rule("logon", "Logon", List.of(eq("evt", "4624"))),
                rule("other", "Logoff", List.of(eq("evt", "4634")))), List.of(
                wanted("Authenticate-Logon", eq("evt", "4624")),
                wanted("Logoff", eq("evt", "4634"))));

        assertThat(kept.nameOf(0)).isNull();
        assertThat(kept.nameOf(1)).isEqualTo("other");
    }

    @Test
    void dropRulesAndUnnamedRulesAreNotKept() {
        final Map<String, Object> drop = rule("noise", "Noise", List.of(eq("evt", "1")));
        drop.put("drop", true);
        final KeptRuleNames kept = KeptRuleNames.of(List.of(drop, rule("", "Logon", List.of(eq("evt", "4624")))),
                List.of(wanted("Noise", eq("evt", "1")), wanted("Logon", eq("evt", "4624"))));

        assertThat(kept.nameOf(0)).isNull();
        assertThat(kept.nameOf(1)).isNull();
        assertThat(kept.all()).isEmpty();
    }

    @Test
    void theCatchAllIsTheLastRuleWithNoConditionsThatNoEventTypeTook() {
        final KeptRuleNames withCatchAll = KeptRuleNames.of(List.of(
                rule("logon", "Logon", List.of(eq("evt", "4624"))),
                rule("everything_else", "Unknown", null)), List.of(wanted("Logon", eq("evt", "4624"))));
        assertThat(withCatchAll.catchAll()).isEqualTo("everything_else");
        assertThat(withCatchAll.all()).containsExactlyInAnyOrder("logon", "everything_else");

        // A sole event type's rule (no conditions) taken by that type again: no catch-all.
        final KeptRuleNames sole = KeptRuleNames.of(List.of(rule("all_records", "Process", null)),
                List.of(new KeptRuleNames.Wanted("Process", List.of())));
        assertThat(sole.nameOf(0)).isEqualTo("all_records");
        assertThat(sole.catchAll()).isNull();

        // The last rule has conditions: no catch-all kept.
        assertThat(KeptRuleNames.of(List.of(rule("logon", "Logon", List.of(eq("evt", "4624")))), List.of())
                .catchAll()).isNull();
    }

    @Test
    void conditionsThatDontReadMatchOnlyByPlace() {
        final KeptRuleNames kept = KeptRuleNames.of(List.of(
                rule("logon", "Logon", List.of(Map.of("field", "evt", "no_such_test", "x")))), List.of(
                wanted("Logon", eq("evt", "4624"))));

        assertThat(kept.nameOf(0)).isEqualTo("logon");
    }

    @Test
    void noExistingRulesKeepNothing() {
        final KeptRuleNames kept = KeptRuleNames.of(List.of(), List.of(wanted("Logon", eq("evt", "4624"))));

        assertThat(kept.nameOf(0)).isNull();
        assertThat(kept.nameOf(5)).isNull();
        assertThat(KeptRuleNames.none().all()).isEmpty();
    }

    @SafeVarargs
    private static KeptRuleNames.Wanted wanted(final String typeId, final Map<String, Object>... when) {
        return new KeptRuleNames.Wanted(typeId, Arrays.stream(when).map(Condition::fromJson).toList());
    }

    private static Map<String, Object> rule(final String name,
                                            final String typeId,
                                            final List<Map<String, Object>> when) {
        final Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("name", name);
        if (when != null) {
            rule.put("when", when);
        }
        rule.put("fields", List.of(Map.of("path", "EventDetail/TypeId", "value", typeId)));
        return rule;
    }

    private static Map<String, Object> eq(final String field, final String value) {
        return Map.of("field", field, "equals", value);
    }
}
