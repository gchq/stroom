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

import stroom.ai.impl.tools.sample.PyJson;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// A fix to a translation mapping sends only what changes (the Stroom MCP server's `tests/test_mapping_changes.py`,
/// its merging; the tool's own cases are the build tool's), merged into the mapping sent before. And the port
/// against the server's own: `changes_cases.json` holds every merge the server's tests made, and more, with what
/// `apply_changes` (or `apply_plan_changes`) gave. Made by running the server's code (`make_changes.py`).
class TestMappingChanges {

    private static final String BASE_JSON = """
            {"input": "json", "common": [{"path": "EventTime/TimeCreated", "field": "ts"},
                                         {"path": "EventDetail/Unknown/Data", "data_name": "a", "field": "a"}],
             "events": [{"name": "logon", "fields": []}, {"name": "other", "fields": []}]}""";

    @TestFactory
    Stream<DynamicTest> theChangesMergeAsThePythonOnesDo() {
        final List<Map<String, Object>> cases = ReadingFixtures.json("changes_cases.json");
        assertThat(cases).hasSizeGreaterThan(30);
        return IntStream.range(0, cases.size()).mapToObj(n -> {
            final Map<String, Object> c = cases.get(n);
            return DynamicTest.dynamicTest(n + " " + PyJson.dumps(c.get("changes")), () -> check(c));
        });
    }

    @SuppressWarnings("unchecked")
    private static void check(final Map<String, Object> c) {
        final Map<String, Object> base = (Map<String, Object>) c.get("base");
        final String before = PyJson.dumps(base);
        final boolean plan = "plan".equals(c.get("kind"));
        if (c.containsKey("error")) {
            assertThatThrownBy(() -> apply(plan, base, c.get("changes")))
                    .isInstanceOf(MappingChangeException.class)
                    .hasMessage((String) c.get("error"));
        } else {
            final MappingChanges.Applied applied = apply(plan, base, c.get("changes"));
            assertThat(PyJson.dumps(applied.merged())).isEqualTo(PyJson.dumps(c.get("merged")));
            assertThat(applied.done()).isEqualTo(c.get("done"));
        }
        assertThat(PyJson.dumps(base)).isEqualTo(before);
    }

    private static MappingChanges.Applied apply(final boolean plan,
                                                final Map<String, Object> base,
                                                final Object changes) {
        return plan
                ? MappingChanges.applyPlanChanges(base, changes)
                : MappingChanges.applyChanges(base, changes);
    }

    @Test
    void rulesByNameCommonEntriesByPathAndOtherKeysWhole() {
        final Map<String, Object> base = GenerationFixtures.json(BASE_JSON);
        final MappingChanges.Applied applied = MappingChanges.applyChanges(base, GenerationFixtures.json("""
                {"events": [{"name": "other", "fields": [{"path": "EventDetail/TypeId", "value": "x"}]},
                            {"name": "logoff", "fields": []}, {"name": "logon", "remove": true}],
                 "common": [{"path": "EventDetail/Unknown/Data", "data_name": "a", "field": "b"},
                            {"path": "EventTime/TimeCreated", "remove": true}],
                 "unmatched": "skip"}"""));
        final List<Map<String, Object>> rules = list(applied.merged().get("events"));
        assertThat(rules.stream().map(rule -> rule.get("name"))).containsExactly("other", "logoff");
        assertThat(PyJson.dumps(rules.getFirst().get("fields")))
                .isEqualTo("[{\"path\": \"EventDetail/TypeId\", \"value\": \"x\"}]");
        assertThat(PyJson.dumps(applied.merged().get("common")))
                .isEqualTo("[{\"path\": \"EventDetail/Unknown/Data\", \"data_name\": \"a\", \"field\": \"b\"}]");
        assertThat(applied.merged().get("unmatched")).isEqualTo("skip");
        // The base is left alone.
        final List<Map<String, Object>> baseRules = list(base.get("events"));
        assertThat(baseRules.getFirst()).containsEntry("name", "logon");
        assertThat(applied.done()).containsExactly("rule 'other' replaced", "rule 'logoff' added after the others",
                "rule 'logon' removed", "common 'EventDetail/Unknown/Data' (Data 'a') replaced",
                "common 'EventTime/TimeCreated' removed", "'unmatched' replaced");
    }

    @ParameterizedTest
    @MethodSource("refused")
    void changesThatCannotApplyAreRefused(final String changes, final String message) {
        final Map<String, Object> base = GenerationFixtures.json(BASE_JSON);
        assertThatThrownBy(() -> MappingChanges.applyChanges(base, GenerationFixtures.json(changes)))
                .isInstanceOf(MappingChangeException.class)
                .hasMessageContaining(message);
    }

    private static Stream<Arguments> refused() {
        return Stream.of(
                Arguments.of("{}", "only what changes"),
                Arguments.of("{\"events\": [{\"fields\": []}]}", "needs its name"),
                Arguments.of("{\"events\": [{\"name\": \"nope\", \"remove\": true}]}",
                        "removes rule 'nope', which the mapping does not have"),
                Arguments.of("{\"common\": [{\"value\": \"x\"}]}", "needs its path"));
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> list(final Object value) {
        return (List<T>) value;
    }
}
