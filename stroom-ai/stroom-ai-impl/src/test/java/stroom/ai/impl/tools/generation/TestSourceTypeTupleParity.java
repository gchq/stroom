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

import stroom.ai.impl.tools.sample.SourceTypeReading;
import stroom.ai.impl.tools.sample.SourceTypeRows;
import stroom.ai.impl.tools.sample.ToolJson;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Parity of a combination of readings between the tools and a source type group's generated XSLT: the same records,
/// classified by the tools ([SourceTypeReading]'s key, the registry's rows tried in order, each matched part by part)
/// and by the group's XSLT run through Saxon, land in the same source type each with the same key: an outer field
/// with an inner held name in JSON, key=value and XML (one part read in mixed formats), a part with no value (plain
/// text, missing), padded and nil values, and every operator: `=`, `~`, `∈` and `*`.
class TestSourceTypeTupleParity extends XsltRuns {

    private static final List<String> LINES = List.of(
            line("Teams", "{\"logger_name\": \"System\", \"cpu\": 1}"),
            line("Teams", "logger_name=Application user=u1 call=c1"),
            line("Teams", "<Log logger=\"Audit\"><logger_name>Audit</logger_name></Log>"),
            line("Skype for Business", "{\"logger_name\": \"System\", \"disk\": 2}"),
            line("Skype for Business", "{\"logger_name\": \" Application \", \"sip\": \"x\"}"),
            line("Skype for Business", "Just some words of plain text"),
            line("Lync", "{\"logger_name\": \"System\"}"),
            line("Lync", "{\"logger_name\": \"n/a\"}"),
            line("  Teams ", "{\"logger_name\": \"Audit\"}"),
            line("Web, Mail", "{\"logger_name\": \"Relay\"}"),
            line("-", "{\"logger_name\": \"System\"}"),
            "{\"id\": \"no-event\", \"system\": \"Teams\"}",
            "{\"id\": \"nothing\", \"other\": \"x\"}",
            line("Teams", "\"{\\\"logger_name\\\": \\\"Twice\\\"}\""));
    private static final String PAIR = "system + event>logger_name | event>xml:@logger";

    @ParameterizedTest
    @ValueSource(strings = {PAIR, "system + event>logger_name", "event>logger_name + system",
            "system + event>#format", "id + system + event>logger_name + event>cpu"})
    void aGroupReadsEachRecordsKeyAsTheToolsDoAndAnExactRowAKeyTakesIt(final String spec) {
        final SourceTypeReading reading = SourceTypeReading.of(spec);
        final List<String> tools = parsed().stream().map(reading::valueOf).toList();
        // A row a key whose parts all have a value, each part exactly, then (no value), as record_source_types
        // proposes them where nothing better is known.
        final List<SourceTypeModule.Row> rows = new ArrayList<>();
        new LinkedHashSet<>(tools).stream()
                .filter(key -> key != null && !SourceTypeReading.keyParts(key).contains(""))
                .forEach(key -> rows.add(new SourceTypeModule.Row("type " + key, SourceTypeRows.TUPLE,
                        String.join(" + ", SourceTypeReading.keyParts(key).stream().map(part -> "= " + part)
                                .toList()))));
        rows.add(SourceTypeModule.Row.of(SourceTypeRows.noValue()));

        assertSameAsTheTools(spec, rows, tools);
    }

    @Test
    void everyOperatorMatchesAsTheToolsMatchIt() {
        final List<SourceTypeModule.Row> rows = List.of(
                tuple("Teams System", "= Teams + = System"),
                tuple("Teams audit", "= Teams + ~ ^Aud"),
                tuple("Teams other", "= Teams + *"),
                tuple("Skype", "~ ^Skype + ~ ^(System|Application)$"),
                tuple("Mail relay", "∈ Mail + = Relay"),
                tuple("Any System", "* + = System"),
                SourceTypeModule.Row.of(SourceTypeRows.noValue()));
        final List<String> tools = parsed().stream().map(SourceTypeReading.of(PAIR)::valueOf).toList();

        final List<String[]> xslt = assertSameAsTheTools(PAIR, rows, tools);

        // The keys and source types decided: a part of no value falls to (no value) unless a row with * takes it.
        assertThat(xslt.stream().map(each -> each[0] + " -> " + each[1]).toList()).containsExactly(
                "Teams ‖ System -> Teams System",
                "Teams ‖ Application -> Teams other",
                "Teams ‖ Audit -> Teams audit",
                "Skype for Business ‖ System -> Skype",
                "Skype for Business ‖ Application -> Skype",
                "Skype for Business ‖  -> (no value)",
                "Lync ‖ System -> Any System",
                "Lync ‖  -> (no value)",
                "Teams ‖ Audit -> Teams audit",
                "Web, Mail ‖ Relay -> Mail relay",
                " ‖ System -> Any System",
                "Teams ‖  -> Teams other",
                " -> (no value)",
                "Teams ‖ Twice -> Teams other");
    }

    @Test
    void aWholeTranslationLogsAKeyTheSurveyDidNotSeeAndNeverAKeyWithAPartOfNoValue() {
        final TranslationMapping mapping = TranslationMapping.fromJson(json("""
                {"input": "json", "json_layout": "lines",
                 "common": [{"path": "EventTime/TimeCreated", "field": "timestamp"},
                            {"path": "EventSource/System/Name", "value": "UC"},
                            {"path": "EventSource/System/Environment", "value": "Prod"},
                            {"path": "EventSource/Generator", "value": "UC"},
                            {"path": "EventSource/Device/HostName", "value": "uc01"}],
                 "events": [{"name": "line", "allow_unknown": "a test",
                             "fields": [{"path": "EventDetail/TypeId", "field": "id"},
                                        {"path": "EventDetail/Unknown/Data", "data_name": "system",
                                         "field": "system"}]}]}"""))
                .withSeenSourceTypes(SeenSourceTypes.ofRows(PAIR, List.of(
                        new SourceTypeRows.Row("Teams", SourceTypeRows.TUPLE, "= Teams + *"),
                        new SourceTypeRows.Row("Skype", SourceTypeRows.TUPLE, "~ ^Skype + = System"))));
        final XsltGenerator.Generated generated = XsltGenerator.generate(mapping, SCHEMA, "4.1.0");
        assertThat(generated.problems()).isEmpty();

        final List<String> logged = new ArrayList<>();
        transform(generated.xslt(), TestSourceTypeReadingParity.records(LINES), Map.of(), logged);

        final List<String> unseen = logged.stream().filter(line -> line.contains(XsltGenerator.UNSEEN_SOURCE_TYPE))
                .map(line -> line.substring(line.indexOf(XsltGenerator.UNSEEN_SOURCE_TYPE)
                                            + XsltGenerator.UNSEEN_SOURCE_TYPE.length(),
                        line.indexOf(XsltGenerator.UNSEEN_SOURCE_TYPE_IN)))
                .toList();
        assertThat(unseen).containsExactlyInAnyOrder("Skype for Business ‖ Application", "Lync ‖ System",
                "Web, Mail ‖ Relay");
        // The tools judge the same keys new.
        final List<String> tools = parsed().stream().map(SourceTypeReading.of(PAIR)::valueOf)
                .filter(key -> key != null && !mapping.seenSourceTypes().seen(key))
                .distinct()
                .toList();
        assertThat(tools).containsExactlyInAnyOrderElementsOf(unseen);
        assertThat(TranslationMapping.fromJson(mapping.toJson(DumpMode.COMPACT)).seenSourceTypes())
                .isEqualTo(mapping.seenSourceTypes());
    }

    @Test
    void aGroupWhoseCombinationIsNoneOrWhoseRowsArentItsIsRefusedSayingWhy() {
        final List<SourceTypeModule.Row> rows = List.of(tuple("a", "= Teams + *"));
        assertThat(problems("system + meta:Feed", rows)).anySatisfy(problem -> assertThat(problem)
                .contains("isn't one", "never a part"));
        assertThat(problems(PAIR, List.of(new SourceTypeModule.Row("a", SourceTypeRows.VALUE, "Teams"))))
                .anySatisfy(problem -> assertThat(problem).contains("matches one value"));
        assertThat(problems(PAIR, List.of(tuple("a", "* + *")))).anySatisfy(problem -> assertThat(problem)
                .contains("every part is '*'"));
        // The schema's problems of this bare mapping aside, nothing of its source types.
        assertThat(problems(PAIR, rows)).noneMatch(problem -> problem.contains("Registry row")
                                                              || problem.contains("source type"));
    }

    // The group's XSLT run over the records: each lands in the source type the tools' rows give it, its key the
    // tools' key.
    private static List<String[]> assertSameAsTheTools(final String spec,
                                                       final List<SourceTypeModule.Row> rows,
                                                       final List<String> tools) {
        final List<SourceTypeRows.Row> toolRows = rows.stream().map(SourceTypeModule.Row::toolRow).toList();
        final List<String[]> xslt = TestSourceTypeReadingParity.classify(spec, rows, LINES);

        assertThat(xslt).hasSize(LINES.size());
        for (int i = 0; i < LINES.size(); i++) {
            final String expectedKey = tools.get(i) == null
                    ? ""
                    : tools.get(i);
            final String expectedType = SourceTypeRows.first(toolRows, tools.get(i)).map(SourceTypeRows.Row::name)
                    .orElse("");
            assertThat(xslt.get(i)).as("record %s by %s", LINES.get(i), spec)
                    .containsExactly(expectedKey, expectedType);
        }
        return xslt;
    }

    private static List<String> problems(final String spec, final List<SourceTypeModule.Row> rows) {
        final TranslationMapping mapping = TranslationMapping.fromJson(json("""
                {"input": "json", "json_layout": "lines",
                 "events": [{"name": "line", "allow_unknown": "a test",
                             "fields": [{"path": "EventDetail/TypeId", "field": "id"},
                                        {"path": "EventDetail/Unknown/Data", "data_name": "event",
                                         "field": "event"}]}]}"""));
        return XsltGenerator.generate(mapping.withModule(new SourceTypeModule(SourceTypeModule.GROUP, "UC", "uc",
                rows.stream().map(SourceTypeModule.Row::name).toList(), null, null, List.of(), null, spec, rows)),
                SCHEMA, "4.1.0").problems();
    }

    private static SourceTypeModule.Row tuple(final String name, final String parts) {
        return new SourceTypeModule.Row(name, SourceTypeRows.TUPLE, parts);
    }

    private static List<Map<String, Object>> parsed() {
        return LINES.stream().map(TestSourceTypeReadingParity::parsed).toList();
    }

    // A wrapper record of a system whose `event` holds the text given.
    private static String line(final String system, final String event) {
        return "{\"id\": \"" + system.strip() + "\", \"timestamp\": \"2026-11-18T10:00:00.000Z\", \"hostname\": "
               + "\"uc01\", \"system\": " + ToolJson.dumps(system) + ", \"event\": " + ToolJson.dumps(event) + "}";
    }
}
