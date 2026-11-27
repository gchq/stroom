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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// A condition's tests: starts_with, contains and not beside the others, read from JSON and written back.
class TestCondition {

    @Test
    void startsWithAndContainsAreTestsOfTheirOwn() {
        final Condition starts = Condition.fromJson(Map.of("field", "message", "starts_with", "Request:"));
        final Condition holds = Condition.fromJson(Map.of("field", "message", "contains", "fault"));

        assertThat(starts.startsWith()).isEqualTo("Request:");
        assertThat(starts.not()).isFalse();
        assertThat(holds.contains()).isEqualTo("fault");
        assertThat(starts.toJson(DumpMode.COMPACT)).containsExactlyEntriesOf(json("field", "message",
                "starts_with", "Request:"));
        assertThat(holds.toJson(DumpMode.COMPACT)).containsExactlyEntriesOf(json("field", "message",
                "contains", "fault"));
    }

    // Chat 194: all 11 catalogue proposals had their conditions written as "evt=logon" and were refused with only
    // "Input should be a valid dictionary or instance of Condition"; the agent had to read the mapping guide.
    @Test
    void aConditionWrittenAsTextIsRefusedShowingTheConditionItWouldBe() {
        final ModelReader reader = new ModelReader();

        final Condition read = Condition.read(reader, "evt=logon", List.of("when", 0));

        assertThat(read).isNull();
        assertThat(reader.errors()).singleElement().satisfies(error -> {
            assertThat(error.type()).isEqualTo("model_type");
            assertThat(error.message()).startsWith("Input should be a valid dictionary or instance of Condition: ")
                    .contains("{field (or xpath), <one test>}")
                    .endsWith("\"evt=logon\" is {\"field\": \"evt\", \"equals\": \"logon\"}");
        });
    }

    @Test
    void aConditionWrittenAsTextShowsQuotedNegatedAndEmptyValues() {
        assertThat(Condition.textHint("user = 'alice'")).endsWith("is {\"field\": \"user\", \"equals\": \"alice\"}");
        assertThat(Condition.textHint("evt!=lock")).endsWith(
                "is {\"field\": \"evt\", \"equals\": \"lock\", \"not\": true}");
        assertThat(Condition.textHint("app=")).endsWith("{\"field\": \"app\", \"present\": false} for a field with no "
                                                      + "value");
        // Not one reading: the form alone, with a placeholder example.
        assertThat(Condition.textHint("evt=a=b")).endsWith("e.g. {\"field\": \"<field>\", \"equals\": \"<value>\"}");
        assertThat(Condition.textHint("starts with logon")).endsWith(
                "e.g. {\"field\": \"<field>\", \"equals\": \"<value>\"}");
        // A number is no text: the message stays the model's own.
        final ModelReader reader = new ModelReader();
        Condition.read(reader, 3, List.of());
        assertThat(reader.errors()).singleElement().satisfies(error -> assertThat(error.message())
                .isEqualTo("Input should be a valid dictionary or instance of Condition"));
    }

    @Test
    void notIsWrittenOnlyWhenSet() {
        final Condition not = Condition.fromJson(Map.of("field", "eventType", "equals", "Logon", "not", true));
        final Condition given = Condition.fromJson(Map.of("field", "eventType", "equals", "Logon", "not", false));

        assertThat(not.not()).isTrue();
        assertThat(not.toJson(DumpMode.COMPACT)).containsExactlyEntriesOf(json("field", "eventType", "equals",
                "Logon", "not", true));
        assertThat(given.toJson(DumpMode.COMPACT)).doesNotContainKey("not");
        // Read laxly, as the other booleans are.
        assertThat(Condition.fromJson(Map.of("field", "a", "contains", "x", "not", "yes")).not()).isTrue();
    }

    @Test
    void theNewTestsAreAsExclusiveAsTheOthers() {
        assertThatThrownBy(() -> Condition.fromJson(Map.of("field", "m", "starts_with", "a", "contains", "b")))
                .isInstanceOf(MappingValidationException.class)
                .hasMessageContaining("exactly one of equals, one_of, matches, starts_with, contains, present or "
                                      + "in_dictionary");
        assertThatThrownBy(() -> Condition.fromJson(Map.of("field", "m", "equals", "a", "starts_with", "b")))
                .isInstanceOf(MappingValidationException.class);
        assertThatThrownBy(() -> Condition.fromJson(Map.of("field", "m", "not", true)))
                .isInstanceOf(MappingValidationException.class);
    }

    @Test
    void notWithPresentIsRefusedSayingPresentFalse() {
        assertThatThrownBy(() -> Condition.fromJson(Map.of("field", "m", "present", true, "not", true)))
                .isInstanceOf(MappingValidationException.class)
                .hasMessageContaining("present: false");
        // not: false beside present is no negation, so nothing to refuse.
        assertThat(Condition.fromJson(Map.of("field", "m", "present", true, "not", false)).present()).isTrue();
    }

    @Test
    void aTestsValueGivenWithItsInputIsSpreadOut() {
        final Condition spread = Condition.fromJson(Map.of("starts_with", Map.of("value", "Request:", "field",
                "message")));
        final Condition held = Condition.fromJson(Map.of("contains", Map.of("contains", "x", "xpath", "a/b")));

        assertThat(spread.field()).isEqualTo("message");
        assertThat(spread.startsWith()).isEqualTo("Request:");
        assertThat(held.xpath()).isEqualTo("a/b");
        assertThat(held.contains()).isEqualTo("x");
    }

    @Test
    void aStartsWithOrContainsOfEmptyTextIsKeptAsGiven() {
        // Empty text: every value starts with it and holds it, as XPath's starts-with() and contains() say.
        assertThat(Condition.fromJson(Map.of("field", "m", "starts_with", "")).startsWith()).isEmpty();
        assertThat(Condition.fromJson(Map.of("field", "m", "contains", "")).contains()).isEmpty();
        assertThat(List.of(Condition.fromJson(Map.of("field", "m", "contains", "")).toJson(DumpMode.COMPACT)
                .get("contains"))).containsExactly("");
    }

    private static Map<String, Object> json(final Object... keysAndValues) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            out.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return out;
    }
}
