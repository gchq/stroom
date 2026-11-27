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

import stroom.ai.impl.tools.draft.DraftMapping;
import stroom.ai.impl.tools.sample.SplitterDryRun;
import stroom.ai.impl.tools.sample.SplitterSpec;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// A header record's date, written by the splitter on the records after it, joined with each time of day and rolled
/// over midnight (stroom:put and stroom:get, in processing). Seen: logs whose lines hold only a time, their date in a
/// header; Stroom's own completion of a dateless time moves it back a month.
class TestXsltGeneratorStreamValues extends XsltRuns {

    private static final Map<String, Object> HEADER = Map.of("when", List.of(GenerationFixtures.equalsCondition(
            "kind", "header")), "reason", "the file's header: its date is the lines' date");
    private static final Pattern HEADER_DATE = Pattern.compile("name=\"date\" value=\"([^\"]+)\"");
    // The splitter writes the header's date on each line after it (carry).
    private static final String JOINED = "concat(data[@name='header_date']/@value, ' ', data[@name='time']/@value)";
    private static final Map<String, Object> TIME = Map.of("path", "EventTime/TimeCreated", "xpath", JOINED,
            "time_format", "yyyy-MM-dd HH:mm:ss", "rollover", true);

    @Test
    void aHeadersDateIsJoinedWithEachLinesTimeAndRollsOverMidnight() {
        final List<String> times = timesOf(records(
                header("2026-10-11"), line("23:58:00"), line("23:59:30"), line("00:00:10"), line("00:01:00"),
                // A new header: its date, the day count started again.
                header("2026-10-12"), line("00:02:00"), line("23:00:00"), line("00:30:00")));

        assertThat(times).containsExactly("2026-10-11T23:58:00.000Z", "2026-10-11T23:59:30.000Z",
                "2026-10-12T00:00:10.000Z", "2026-10-12T00:01:00.000Z", "2026-10-12T00:02:00.000Z",
                "2026-10-12T23:00:00.000Z", "2026-10-13T00:30:00.000Z");
    }

    @Test
    void withoutRolloverTheHeadersDateIsKept() {
        final List<Object> common = commonWith(Map.of("path", "EventTime/TimeCreated", "xpath", JOINED,
                "time_format", "yyyy-MM-dd HH:mm:ss"));

        assertThat(timesOf(common, records(header("2026-10-11"), line("23:59:30"), line("00:00:10"))))
                .containsExactly("2026-10-11T23:59:30.000Z", "2026-10-11T00:00:10.000Z");
    }

    @Test
    void theHeaderWritesNoEvent() {
        final XsltGenerator.Generated generated = generate(commonWith(TIME));

        assertThat(generated.xslt()).contains("left untranslated on purpose");
        assertThat(children(transform(generated.xslt(), records(header("2026-10-11"))), "Event")).isEmpty();
    }

    @Test
    void aTimeThatWontParseTakesTheFallbackTheUserAgreedWithAWarning() {
        // Seen: an empty TimeCreated, which the schema refuses, for each record of a second time layout.
        final List<Object> common = commonWith(Map.of("path", "EventTime/TimeCreated", "field", "time",
                "time_format", "yyyy-MM-dd HH:mm:ss", "time_fallback", "ReceivedTime"));
        final XsltGenerator.Generated generated = generate(common);

        assertThat(generated.xslt()).contains("stroom:meta('ReceivedTime')", "the time didn");
        assertThat(timesOf(common, records(dated("2026-10-11 10:00:00"), dated("11/Oct/2026:10:00:00"))))
                .containsExactly("2026-10-11T10:00:00.000Z", RECEIVED);
        assertThatThrownBy(() -> GenerationFixtures.mapping(GenerationFixtures.with("common", commonWith(Map.of(
                "path", "EventTime/TimeCreated", "field", "time", "time_fallback", "ReceivedTime")))))
                .hasMessageContaining("time_fallback names a stream meta time");
    }

    @Test
    void aDeviceLeftOutAsAStandInTakesTheSendersAddress() {
        // Seen in testing: '-' for the host, the only source of EventSource/Device, was to be left out, and those
        // events would have had no Device, which the schema requires.
        final List<Object> common = commonWith(TIME);
        common.set(4, Map.of("path", "EventSource/Device/IPAddress", "field", "host", "meta_fallback",
                List.of("RemoteAddress")));
        final XsltGenerator.Generated generated = XsltGenerator.generate(GenerationFixtures.mapping(
                GenerationFixtures.with("drop_when", List.of(HEADER), "common", common, "nil_values",
                        List.of("-"))), GenerationFixtures.SCHEMA, "4.1.0");
        assertThat(generated.problems()).isEmpty();
        final XmlElement events = transform(generated.xslt(), records(header("2026-10-11"),
                line("10:00:00").replace("<record>", "<record><data name=\"host\" value=\"10.0.0.1\"/>"),
                line("10:00:01").replace("<record>", "<record><data name=\"host\" value=\"-\"/>"),
                line("10:00:02")));

        assertThat(invalid(events, GenerationFixtures.XSD)).as(generated.xslt()).isEmpty();
        assertThat(children(events, "Event").stream()
                .map(event -> text(event, "EventSource/Device/IPAddress"))
                .toList()).containsExactly("10.0.0.1", REMOTE_ADDRESS, REMOTE_ADDRESS);
        // Kept in the mapping's JSON, as saved, and checked.
        assertThat(GenerationFixtures.mapping(GenerationFixtures.with("drop_when", List.of(HEADER), "common", common))
                .toJson(DumpMode.COMPACT).toString()).contains("meta_fallback=[RemoteAddress]");
        common.set(4, Map.of("path", "EventSource/Device/IPAddress", "value", "x", "meta_fallback",
                List.of("RemoteAddress")));
        assertThatThrownBy(() -> GenerationFixtures.mapping(GenerationFixtures.with("common", common)))
                .hasMessageContaining("meta_fallback");
    }

    @Test
    void aRolloverIsChecked() {
        assertThatThrownBy(() -> GenerationFixtures.mapping(GenerationFixtures.with("common", commonWith(Map.of(
                "path", "EventTime/TimeCreated", "field", "time", "time_format", "epoch_ms", "rollover", true)))))
                .hasMessageContaining("rollover goes with a time_format pattern");
        // Kept in the mapping's JSON, as saved.
        assertThat(GenerationFixtures.mapping(GenerationFixtures.with("drop_when", List.of(HEADER), "common",
                commonWith(TIME))).toJson(DumpMode.COMPACT).toString()).contains("rollover=true");
    }

    @Test
    void aDatelessLogIsDraftedWithItsHeadersDateAndRolledOverMidnight() {
        // The fixture: a "Log opened 2026-10-11" header, 120 lines of time only crossing midnight, stack traces on
        // errors, a "Day changed to 2026-10-12" header part-way (dateless-lines-generator.py).
        final String sample = sampleResource("/stroom/ai/impl/tools/sample/dateless-lines.log");
        final SplitterSpec spec = SplitterDryRun.inferSpec(sample).spec();
        assertThat(spec.parts()).extracting(SplitterSpec::variant).contains("text", SplitterDryRun.HEADER_VARIANT);
        final Map<String, Object> draft = DraftMapping.draft(List.of(sample), "App", "App", "Test", null, null, spec);
        @SuppressWarnings("unchecked") final Map<String, Object> mapping = (Map<String, Object>) draft.get("mapping");
        assertThat(String.valueOf(mapping.get("drop_when"))).contains("variant", "header");
        assertThat(String.valueOf(mapping)).contains("rollover=true", "data[@name='header_date']/@value");

        final XsltGenerator.Generated generated = XsltGenerator.generate(TranslationMapping.fromJson(
                decided(mapping)), GenerationFixtures.SCHEMA, "4.1.0");
        assertThat(generated.problems()).isEmpty();
        final XmlElement events = transform(generated.xslt(), recordsOf(SplitterDryRun.dryRun(spec, sample)));
        assertThat(invalid(events, GenerationFixtures.XSD)).isEmpty();
        final List<String> times = children(events, "Event").stream()
                .map(event -> text(event, "EventTime/TimeCreated"))
                .toList();
        // Every line an Event, the headers none; each time after the one before, past midnight onto the next day.
        assertThat(times).hasSize(120).doesNotContainNull();
        assertThat(times.getFirst()).startsWith("2026-10-11T23:50");
        assertThat(times.getLast()).startsWith("2026-10-12T00:");
        assertThat(times).isSorted();
    }

    // What an agent decides beyond the draft: a host for the Events (the log names none), and each rule kept as
    // Unknown on purpose.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> decided(final Map<String, Object> mapping) {
        final List<Object> common = new ArrayList<>((List<Object>) mapping.get("common"));
        common.add(Map.of("path", "EventSource/Device/HostName", "value", "app-host"));
        mapping.put("common", common);
        for (final Object rule : (List<Object>) mapping.get("events")) {
            final Map<String, Object> map = (Map<String, Object>) rule;
            if (String.valueOf(map.get("fields")).contains("/Unknown/")) {
                map.put("allow_unknown", "kept Unknown for the test");
            }
        }
        return mapping;
    }

    // The records a Data Splitter writes, as records:2.
    private static String recordsOf(final SplitterDryRun.DryRun dryRun) {
        final StringBuilder out = new StringBuilder("<records xmlns=\"records:2\">");
        for (final Map<String, String> record : dryRun.records()) {
            out.append("<record>");
            record.forEach((name, value) -> out.append("<data name=\"").append(escaped(name)).append("\" value=\"")
                    .append(escaped(value)).append("\"/>"));
            out.append("</record>");
        }
        return out.append("</records>").toString();
    }

    private static String escaped(final String text) {
        return text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\n", "&#10;").replace("\t", "&#9;");
    }

    private static String sampleResource(final String name) {
        try (InputStream in = Objects.requireNonNull(TestXsltGeneratorStreamValues.class.getResourceAsStream(name),
                name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> timesOf(final String records) {
        return timesOf(commonWith(TIME), records);
    }

    private static List<String> timesOf(final List<Object> common, final String records) {
        final XsltGenerator.Generated generated = generate(common);
        final XmlElement events = transform(generated.xslt(), records);
        assertThat(invalid(events, GenerationFixtures.XSD)).as(generated.xslt()).isEmpty();
        return children(events, "Event").stream().map(event -> text(event, "EventTime/TimeCreated")).toList();
    }

    private static XsltGenerator.Generated generate(final List<Object> common) {
        final XsltGenerator.Generated generated = XsltGenerator.generate(GenerationFixtures.mapping(
                GenerationFixtures.with("drop_when", List.of(HEADER), "common", common)), GenerationFixtures.SCHEMA,
                "4.1.0");
        assertThat(generated.problems()).isEmpty();
        return generated;
    }

    // The base mapping's common fields, TimeCreated's entry replaced.
    private static List<Object> commonWith(final Map<String, Object> time) {
        final List<Object> common = new ArrayList<>(GenerationFixtures.BASE);
        common.set(0, time);
        return common;
    }

    private static String header(final String date) {
        return "<record><data name=\"kind\" value=\"header\"/><data name=\"date\" value=\"" + date + "\"/></record>";
    }

    private static String line(final String time) {
        return "<record><data name=\"time\" value=\"" + time + "\"/><data name=\"action\" value=\"login\"/>"
               + "<data name=\"user\" value=\"dave\"/><data name=\"result\" value=\"ok\"/></record>";
    }

    private static String dated(final String time) {
        return line(time);
    }

    // The records as the splitter writes them: each line after a header carries its date (header_date).
    private static String records(final String... records) {
        final StringBuilder out = new StringBuilder("<records xmlns=\"records:2\">");
        String date = null;
        for (final String record : records) {
            final Matcher header = HEADER_DATE.matcher(record);
            if (header.find()) {
                date = header.group(1);
                out.append(record);
            } else if (date != null) {
                out.append(record.replace("<record>", "<record><data name=\"header_date\" value=\"" + date
                                                      + "\"/>"));
            } else {
                out.append(record);
            }
        }
        return out.append("</records>").toString();
    }
}
