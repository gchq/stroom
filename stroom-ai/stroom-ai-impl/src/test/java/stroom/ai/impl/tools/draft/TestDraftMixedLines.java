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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.sample.SplitterSpec;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/// The draft of a stream whose records come in several formats, some with stack traces. Seen in testing: such a
/// stream's splitter split it on lines, and the agent hand-wrote the XSLT over many turns.
class TestDraftMixedLines {

    private static final String MIXED = resource("/stroom/ai/impl/tools/sample/mixed-lines.log");

    @Test
    void aRulePerFormatWhereNoFieldNamesTheKind() {
        final Map<String, Object> draft = DraftMapping.draft(List.of(MIXED), "Mixed", null, null, null, null, null);

        assertThat(draft).containsEntry("format", "mixed lines");
        assertThat(((Map<?, ?>) draft.get("splitter")).get("kind")).isEqualTo(SplitterSpec.ALTERNATIVES);
        final Map<String, Object> mapping = mapping(draft);
        // The records no naming field covers are told apart by the format they were read in.
        final List<Map<String, Object>> rules = JsonValues.maps(mapping.get("events"));
        assertThat(rules).anySatisfy(rule -> assertThat(String.valueOf(rule.get("when")))
                .contains("variant").contains("text"));
        assertThat(rules).anySatisfy(rule -> assertThat(String.valueOf(rule.get("when")))
                .contains("variant").contains("syslog"));
        // The exception a trace starts with, and its message, are fields of their own; the exception is not a kind.
        assertThat(JsonValues.maps(mapping.get("extract"))).anySatisfy(extract -> assertThat(extract)
                .containsEntry("field", SplitterSpec.CONTINUATION)
                .containsEntry("names", List.of(DraftMapping.EXCEPTION_FIELD, DraftMapping.EXCEPTION_MESSAGE_FIELD)));
        assertThat(rules).noneSatisfy(rule -> assertThat(String.valueOf(rule.get("when")))
                .contains(DraftMapping.EXCEPTION_FIELD));
        assertThat(String.valueOf(draft.get("notes"))).contains("stack traces");

        // The draft generates, once its placeholder kinds are decided.
        final XsltGenerator.Generated generated = XsltGenerator.generate(TranslationMapping.fromJson(
                TestDraftMapping.decided(agentsDecisions(mapping))), DraftFixtures.SCHEMA_352, "3.5.2");
        assertThat(generated.problems()).isEmpty();
        assertThat(generated.xslt()).contains("data[@name='variant']", "data[@name='continuation']");
    }

    @Test
    void theTraceIsKeptWholeUnlessTheUserChoosesLess() {
        final Map<String, Object> mapping = mapping(DraftMapping.draft(List.of(MIXED), "Mixed", null, null, null,
                null, null));
        final String whole = String.valueOf(mapping);

        assertThat(DraftMapping.withContinuationLines(mapping, 0)).isZero();
        assertThat(String.valueOf(mapping)).isEqualTo(whole);

        assertThat(DraftMapping.withContinuationLines(mapping, 20)).isPositive();
        assertThat(String.valueOf(mapping)).contains("string-join(tokenize(data[@name='continuation']/@value, "
                                                     + "'\\n')[position() le 20], codepoints-to-string(10))");
        final XsltGenerator.Generated generated = XsltGenerator.generate(TranslationMapping.fromJson(
                TestDraftMapping.decided(agentsDecisions(mapping))), DraftFixtures.SCHEMA_352, "3.5.2");
        assertThat(generated.problems()).isEmpty();
    }

    @Test
    void eachFormatHasItsOwnTimeAndWhatEveryFormatWritesIsCommon() {
        // Seen: one draft over every format's records took one time field and format, so the others' times were
        // written empty.
        final Map<String, Object> draft = DraftMapping.draft(List.of(MIXED), "Mixed", "Mixed", "Test", null, null,
                null);
        final Map<String, Object> mapping = mapping(draft);
        final List<Map<String, Object>> rules = JsonValues.maps(mapping.get("events"));

        // The system's name and environment are every format's: common.
        assertThat(JsonValues.maps(mapping.get("common"))).extracting(entry -> entry.get("path"))
                .contains("EventSource/System/Name", "EventSource/System/Environment")
                .doesNotContain("EventTime/TimeCreated");
        // Each format's time, in its rules, with its own format.
        final List<Object> formats = rules.stream()
                .flatMap(rule -> JsonValues.maps(rule.get("fields")).stream())
                .filter(entry -> "EventTime/TimeCreated".equals(entry.get("path")))
                .map(entry -> entry.get("time_format"))
                .distinct()
                .toList();
        assertThat(formats).hasSizeGreaterThanOrEqualTo(4);
        // Every rule is for one format's records.
        assertThat(rules).allSatisfy(rule -> assertThat(String.valueOf(rule.get("when"))).contains("variant"));
        // A format with no time of its own is said, for the agent to map.
        assertThat(String.valueOf(draft.get("notes"))).contains("[key_value] No timestamp field was recognised");
    }

    // What an agent decides beyond the draft for the formats that have none of their own: a time and a host.
    @SuppressWarnings("unchecked")
    @Test
    void aConfirmedKindIsTheRuleOfTheFormatWhoseRecordsHoldItNotOfEveryFormat() {
        // Seen in chat 163: confirmed kinds applied to every format with an 'event' field gave a rule for each kind
        // in each format (linux_icastart, netscaler_history), failing the schema checks.
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            text.append("2026-11-06T09:00:0%d gw01 GW %s alice%d\n".formatted(i, i % 2 == 0
                    ? "LOGIN"
                    : "LOGOUT", i));
            text.append("2026-11-06T09:01:0%d web01 LINUX %s bob%d\n".formatted(i, i % 2 == 0
                    ? "SUDO"
                    : "CRON", i));
        }
        final SplitterSpec splitter = SplitterSpec.fromMap(Map.of("kind", "alternatives", "parts", List.of(
                Map.of("kind", "regex", "variant", "gw", "pattern", "^(\\S+) (\\S+) GW (\\S+) (\\S+)$",
                        "names", List.of("time", "host", "event", "user")),
                Map.of("kind", "regex", "variant", "linux", "pattern", "^(\\S+) (\\S+) LINUX (\\S+) (\\S+)$",
                        "names", List.of("time", "host", "event", "user")))));
        final Map<String, Object> notes = Map.of("source", "Mixed", "fields", List.of(), "events", List.of(
                Map.of("event", "LOGIN", "field", "event", "value", "LOGIN", "description", "A gateway logon",
                        "event_detail", "Authenticate", "action", "Logon"),
                Map.of("event", "SUDO", "field", "event", "value", "SUDO", "description", "A sudo command",
                        "event_detail", "Process", "action", "Execute")));

        final Map<String, Object> draft = DraftMapping.draft(List.of(text.toString()), "Mixed", "Mixed", "Test",
                notes, null, splitter);

        final List<String> rules = JsonValues.maps(mapping(draft).get("events")).stream()
                .map(rule -> JsonValues.maps(rule.get("when")).stream()
                        .map(condition -> condition.get("field") + "=" + condition.get("equals"))
                        .toList()
                        .toString())
                .toList();
        assertThat(rules).as("%s", rules).contains("[variant=gw, event=LOGIN]", "[variant=linux, event=SUDO]")
                .doesNotContain("[variant=linux, event=LOGIN]", "[variant=gw, event=SUDO]");
    }

    private static Map<String, Object> agentsDecisions(final Map<String, Object> mapping) {
        final List<Map<String, Object>> common = JsonValues.maps(mapping.get("common"));
        for (final Map<String, Object> rule : JsonValues.maps(mapping.get("events"))) {
            final List<Map<String, Object>> fields = (List<Map<String, Object>>) rule.get("fields");
            final String written = String.valueOf(common) + fields;
            if (!written.contains("EventTime/TimeCreated")) {
                fields.addFirst(new java.util.LinkedHashMap<>(Map.of("path", "EventTime/TimeCreated", "value",
                        "2026-10-11T00:00:00.000Z")));
            }
            if (!written.contains("EventSource/Device/")) {
                fields.add(new java.util.LinkedHashMap<>(Map.of("path", "EventSource/Device/HostName", "value",
                        "mixed-host")));
            }
        }
        return mapping;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapping(final Map<String, Object> draft) {
        return (Map<String, Object>) draft.get("mapping");
    }

    private static String resource(final String name) {
        try (InputStream in = Objects.requireNonNull(TestDraftMixedLines.class.getResourceAsStream(name), name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
