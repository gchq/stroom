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

import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.sample.ToolJson;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A mapping's drop rules on a key of a held JSON field that processing's string cut leaves unreadable (seen in
/// testing: a drop rule on `event.appender_ref` of an aggregated feed, whose `event` was longer than the JSON
/// parser's `stringTruncateLength` on some records).
class TestCutHeldDrops {

    private static final int CUT = LocalCheck.JSON_PARSER_CUT;

    @Test
    void aPositiveDropConditionInsideACutHeldFieldDropsNothingThereInProcessing() {
        final CheckedRecords checked = checked(List.of(Map.of("reason", "noise", "when", List.of(Map.of("field",
                "event.appender_ref", "equals", "NOISE")))), List.of());

        assertThat(CutHeldDrops.warnings(checked, CUT)).containsExactly("drop rule drop_when 'noise': 'event' is "
                + "longer than stringTruncateLength (10,000 characters) on 1 sample record(s), so in processing none "
                + "of its keys can be read there (the cut text isn't JSON): the rule drops them read whole, but in "
                + "processing doesn't. Raise the pipeline's stringTruncateLength (long_values=whole), or test a "
                + "field outside 'event'");
    }

    @Test
    void aNegatedOneDropsThemInProcessingWhateverTheyHold() {
        final Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("name", "not_security");
        rule.put("drop", true);
        rule.put("when", List.of(Map.of("field", "event.appender_ref", "equals", "SECURITY", "not", true)));
        final CheckedRecords checked = checked(List.of(), List.of(rule), "SECURITY");

        assertThat(CutHeldDrops.warnings(checked, CUT)).singleElement().asString()
                .startsWith("drop rule rule 'not_security': 'event' is longer than stringTruncateLength (10,000 "
                            + "characters) on 1 sample record(s)")
                .contains("the rule keeps them read whole, but in processing drops them");
    }

    @Test
    void nothingIsSaidWhereTheCutChangesNoVerdictOrTheValuesAreKeptWhole() {
        // The long record's appender isn't NOISE: dropped neither way.
        final CheckedRecords other = checked(List.of(Map.of("reason", "noise", "when", List.of(Map.of("field",
                "event.appender_ref", "equals", "OTHER")))), List.of());
        final CheckedRecords noise = checked(List.of(Map.of("reason", "noise", "when", List.of(Map.of("field",
                "event.appender_ref", "equals", "NOISE")))), List.of());
        // A condition outside the held field.
        final CheckedRecords outside = checked(List.of(Map.of("reason", "host", "when", List.of(Map.of("field",
                "host", "equals", "web-1")))), List.of());

        assertThat(CutHeldDrops.warnings(other, CUT)).isEmpty();
        assertThat(CutHeldDrops.warnings(outside, CUT)).isEmpty();
        // Kept whole (the user's long_values=whole raises the parser's limit past the longest): nothing cut.
        assertThat(CutHeldDrops.warnings(noise, 20_000)).isEmpty();
    }

    // Three records, the last one's event longer than the cut, its appender NOISE.
    private static CheckedRecords checked(final List<Map<String, Object>> dropWhen,
                                          final List<Map<String, Object>> dropRules) {
        return checked(dropWhen, dropRules, "NOISE");
    }

    // Three records, the last one's event longer than the cut, its appender the one given.
    private static CheckedRecords checked(final List<Map<String, Object>> dropWhen,
                                          final List<Map<String, Object>> dropRules,
                                          final String longAppender) {
        final Map<String, Object> json = new LinkedHashMap<>();
        json.put("input", "json");
        json.put("json_fields", List.of("event"));
        if (!dropWhen.isEmpty()) {
            json.put("drop_when", dropWhen);
        }
        final List<Map<String, Object>> events = new ArrayList<>(dropRules);
        events.add(Map.of("name", "rest", "catch_all", true, "allow_unknown", "the rest",
                "fields", List.of(Map.of("path", "EventDetail/TypeId", "value", "Unknown"))));
        json.put("events", events);
        final TranslationMapping mapping = TranslationMapping.parse(json);
        final List<String> lines = List.of(line("SECURITY", ""), line("OTHER", ""),
                line(longAppender, "p".repeat(CUT + 50)));
        return CheckedRecords.of(mapping, LocalCheck.sampleRecords(mapping, String.join("\n", lines), null)
                .records());
    }

    private static String line(final String appender, final String padding) {
        final Map<String, Object> event = new LinkedHashMap<>();
        event.put("padding", padding);
        event.put("appender_ref", appender);
        final Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("host", "web-1");
        outer.put("event", ToolJson.dumps(event));
        return ToolJson.dumps(outer);
    }
}
