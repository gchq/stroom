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

package stroom.ai.impl.tools.sample;

import stroom.ai.impl.tools.draft.SplitterXml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/// Records before formats, and streams whose records come in several formats. Seen in testing: a stream of syslog,
/// key=value and plain log lines read as text, its parser and XSLT left to the agent; and stack traces outnumbering
/// the entries they belong to.
class TestLineFormats {

    private static final String MIXED = resource("mixed-lines.log");
    private static final DataSplitterSchema SCHEMA = DataSplitterSchemas.held();

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "<13>Oct 11 22:14:15 host1 app[42]: hello|true",
            "user=bob action=logout|true",
            "2026-10-11 22:14:17 ERROR failed|true",
            "plain text without a time|true",
            "'\tat com.acme.Foo.bar(Foo.java:12)'|false",
            "'    indented, a wrapped message'|false",
            "at com.acme.Foo.bar(Foo.java:12)|false",
            "Caused by: java.sql.SQLException: down|false",
            "Suppressed: java.io.IOException: closed|false",
            "... 12 more|false",
            "java.lang.IllegalStateException: boom|false",
            "com.acme.AcmeError|false",
            "Traceback (most recent call last):|false",
            "ValueError: bad input|false",
            "Error: disk full|true",
            "ERROR something failed|true"})
    void aLineStartsARecordUnlessItContinuesTheOneBefore(final String line, final boolean starts) {
        assertThat(LineFormats.startsRecord(line)).isEqualTo(starts);
    }

    @Test
    void stackTracesAreGroupedWithTheirEntriesAndACutTraceIsARecordOfItsOwn() {
        final LineFormats.Grouped grouped = LineFormats.group(List.of("\tat a.B.c(B.java:1)", "entry one",
                "java.lang.IllegalStateException: boom", "\tat a.B.c(B.java:1)", "entry two"));

        assertThat(grouped).isNotNull();
        assertThat(grouped.records()).containsExactly("\tat a.B.c(B.java:1)",
                "entry one\njava.lang.IllegalStateException: boom\n\tat a.B.c(B.java:1)", "entry two");
        assertThat(grouped.continued()).isEqualTo(1);
        // Nothing continues: nothing to group; an indented layout is not entries and their traces.
        assertThat(LineFormats.group(List.of("a", "b"))).isNull();
        assertThat(LineFormats.group(List.of("heading", "  one", "  two"))).isNull();
    }

    @Test
    void aStreamOfSeveralFormatsIsProfiledAsMixedLines() {
        // The fixture's generator counted these: RFC 3164 syslog (key=value, quoted and plain messages), RFC 5424,
        // CEF with and without a syslog header, key=value lines (quotes escaped, unclosed, single), timestamped text
        // in several layouts (Java and Python traces, wrapped lines), and lines in no format.
        final Map<String, Object> profile = SampleProfiler.profile(MIXED);

        assertThat(profile).containsEntry("format", SampleProfiler.MIXED_LINES)
                .containsEntry("records", 970)
                .containsEntry(SplitterSpec.CONTINUATION, true)
                .containsEntry("record_start", LineFormats.RECORD_LINE_START)
                .containsEntry("continued_records", 81);
        final List<Map<String, Object>> formats = list(profile.get("line_formats"));
        assertThat(formats).extracting(format -> format.get("format")).containsExactly(
                LineFormats.TIMESTAMPED_TEXT, SampleProfiler.SYSLOG_3164_FORMAT, SampleProfiler.KEY_VALUE_FORMAT,
                SampleProfiler.SYSLOG_5424_FORMAT, SampleProfiler.CEF_FORMAT);
        assertThat(formats).extracting(format -> format.get("records")).containsExactly(398, 313, 134, 53, 40);
        assertThat((Map<String, Object>) profile.get("unrecognised")).containsEntry("records", 32);
        // The stream starts in a stack trace: a record no format fits, shown by its head.
        assertThat(String.valueOf(((Map<?, ?>) profile.get("unrecognised")).get("examples")))
                .startsWith("[\tat com.example.app.Worker.run(Worker.java:41)");
        assertThat(String.valueOf(profile.get("suggested_parser"))).contains("alternatives", "catch_all",
                "continuation");
    }

    @Test
    void theInferredSplitterReadsEveryFormatAsStroomDoes() {
        final SplitterSpec spec = SplitterDryRun.inferSpec(MIXED).spec();

        assertThat(spec.kind()).isEqualTo(SplitterSpec.ALTERNATIVES);
        // Every format has its part, those under a tenth of the records too; a part per shape of time.
        assertThat(spec.parts()).extracting(SplitterSpec::variant).startsWith("cef", "syslog_5424", "syslog",
                "key_value", "text").contains("text_2", "text_3", "text_4", "text_5");
        assertThat(spec.catchAll()).isTrue();
        assertThat(spec.continuation()).isTrue();
        // Syslog messages some key=value (quoted) and some text: each read by its own part.
        final SplitterSpec syslog = spec.parts().get(2);
        assertThat(syslog.body().kind()).isEqualTo(SplitterSpec.ALTERNATIVES);
        assertThat(syslog.body().variantField()).isEqualTo("message_format");

        // With the read buffer build_data_splitter sets for the sample's longest record (30,000 characters).
        final String converter = SplitterXml.withBufferSize(SplitterXml.converter(spec),
                SplitterXml.bufferSizeFor(SplitterDryRun.longestRecord(spec, MIXED)).orElseThrow());
        final StroomSplitterRun.Result stroom = StroomSplitterRun.run(SCHEMA, converter, List.of(MIXED)).getFirst();
        final SplitterDryRun.DryRun dryRun = SplitterDryRun.dryRun(spec, MIXED);

        assertThat(stroom.errors()).as("Stroom's errors").isEmpty();
        assertThat(stroom.records()).hasSize(970).isEqualTo(dryRun.records());
        assertThat(dryRun.unmatchedLines()).isEmpty();
        final Map<String, String> failed = stroom.records().stream()
                .filter(record -> record.getOrDefault(SplitterSpec.CONTINUATION, "").startsWith("java.")
                                  && record.getOrDefault(SplitterSpec.CONTINUATION, "").contains("Caused by: "))
                .findFirst()
                .orElseThrow();
        assertThat(failed.get(SplitterSpec.VARIANT)).startsWith("text");
        assertThat(failed.get("message")).contains("ERROR");
        assertThat(stroom.records()).anySatisfy(record -> assertThat(record.getOrDefault(SplitterSpec.CONTINUATION,
                "")).startsWith("Traceback (most recent call last):").endsWith("ValueError: bad input"));
        // Quoting: an escaped quote kept in its value; a value holding = and |; a Windows path.
        assertThat(stroom.records()).anySatisfy(record -> assertThat(record).containsEntry("msg",
                "said \\\"hi\\\" twice"));
        assertThat(stroom.records()).anySatisfy(record -> assertThat(record).containsEntry("query",
                "a=b&c=d|e=f"));
        assertThat(stroom.records()).anySatisfy(record -> assertThat(record).containsEntry("path",
                "C:\\Program Files\\Acme\\app.exe"));
        // An unclosed quote: not a key=value record the part can read, so kept whole, not an error and lost.
        assertThat(stroom.records()).anySatisfy(record -> assertThat(record.get(SplitterSpec.UNPARSED))
                .endsWith("note=\"unterminated value"));
        assertThat(stroom.records()).anySatisfy(record -> assertThat(record)
                .containsEntry(SplitterSpec.VARIANT, "syslog")
                .containsEntry("message_format", "key_value")
                .containsKey("user"));
        assertThat(stroom.records()).anySatisfy(record -> assertThat(record)
                .containsEntry(SplitterSpec.VARIANT, "cef")
                .containsEntry("cef_name", "Blocked request \\| rule 2"));
        // An escaped = inside a CEF value is part of it, not a key.
        assertThat(stroom.records()).anySatisfy(record -> assertThat(record.getOrDefault("msg", ""))
                .endsWith("matched a\\=b"));
        assertThat(stroom.records()).noneSatisfy(record -> assertThat(record).containsKey("a\\"));
        assertThat(stroom.records()).anySatisfy(record -> assertThat(record)
                .containsEntry(SplitterSpec.VARIANT, "key_value")
                .containsEntry("user", "bulk"));
        assertThat(stroom.records().getFirst()).containsOnlyKeys(SplitterSpec.UNPARSED, SplitterSpec.CONTINUATION);
    }

    @Test
    void timesWithNoDateAreReadWithTheHeaderThatGivesIt() {
        // dateless-lines-generator.py: a "Log opened 2026-10-11" header, 120 time-only lines with stack traces, a
        // "Day changed to 2026-10-12" header part-way.
        final String dateless = resource("dateless-lines.log");
        final Map<String, Object> profile = SampleProfiler.profile(dateless);
        assertThat(profile).containsEntry("format", SampleProfiler.MIXED_LINES).containsEntry("records", 122);
        assertThat(String.valueOf(profile.get("dateless_times"))).contains("a header line gives it");
        assertThat(list(profile.get("line_formats"))).extracting(format -> format.get("format"))
                .containsExactly(LineFormats.TIMESTAMPED_TEXT, LineFormats.DATE_HEADER);

        final SplitterSpec spec = SplitterDryRun.inferSpec(dateless).spec();
        assertThat(spec.parts().getLast().variant()).isEqualTo(SplitterDryRun.HEADER_VARIANT);
        final StroomSplitterRun.Result stroom = StroomSplitterRun.run(SCHEMA, SplitterXml.converter(spec),
                List.of(dateless)).getFirst();
        assertThat(stroom.errors()).isEmpty();
        assertThat(stroom.records()).hasSize(122).isEqualTo(SplitterDryRun.dryRun(spec, dateless).records());
        assertThat(stroom.records()).filteredOn(record -> SplitterDryRun.HEADER_VARIANT.equals(record.get(
                SplitterSpec.VARIANT))).extracting(record -> record.get(SplitterDryRun.HEADER_DATE))
                .containsExactly("2026-10-11", "2026-10-12");
        // A time with no date at all, no header: still read apart, for the user to say where the date comes from.
        final Map<String, Object> headless = SampleProfiler.profile("23:59:30 a\n00:00:10 b\n00:01:00 c\n");
        assertThat(String.valueOf(headless.get("dateless_times"))).contains("ask the user where the date");
    }

    @Test
    void syslogWhoseStackTracesOutnumberItsEntriesIsStillSyslog() {
        // Before, nine lines in ten were frames: no format reached its share of the lines, and it was text.
        final StringBuilder text = new StringBuilder();
        for (int n = 0; n < 5; n++) {
            text.append("<13>Oct 11 22:14:1").append(n).append(" host1 app[42]: user=u").append(n)
                    .append(" action=fail\njava.lang.IllegalStateException: boom\n");
            for (int frame = 0; frame < 30; frame++) {
                text.append("\tat com.example.Service.handle(Service.java:").append(frame).append(")\n");
            }
        }

        final Map<String, Object> profile = SampleProfiler.profile(text.toString());

        assertThat(profile).containsEntry("format", SampleProfiler.SYSLOG_3164_FORMAT)
                .containsEntry("records", 5)
                .containsEntry(SplitterSpec.CONTINUATION, true);
        final SplitterSpec spec = SplitterDryRun.inferSpec(text.toString()).spec();
        assertThat(spec.kind()).isEqualTo("syslog");
        assertThat(spec.recordStart()).isEqualTo(LineFormats.RECORD_LINE_START);
        final StroomSplitterRun.Result stroom = StroomSplitterRun.run(SCHEMA, SplitterXml.converter(spec),
                List.of(text.toString())).getFirst();
        assertThat(stroom.errors()).isEmpty();
        assertThat(stroom.records()).hasSize(5).allSatisfy(record -> assertThat(record).containsKeys("user",
                SplitterSpec.CONTINUATION));
    }

    @Test
    void aStreamMostlyOfOneFormatIsThatFormat() {
        // Asked for by the user: a format has its own threshold first, 95% syslog stays syslog.
        final List<String> lines = new ArrayList<>();
        for (int n = 0; n < 19; n++) {
            lines.add("<13>Oct 11 22:14:1" + (n % 10) + " host1 app[42]: hello " + n);
        }
        lines.add("user=bob action=logout src=10.0.0.1");

        assertThat(SampleProfiler.profile(String.join("\n", lines))).containsEntry("format",
                SampleProfiler.SYSLOG_3164_FORMAT);
        // One format, with lines in no other: not mixed.
        assertThat(LineFormats.mixed(List.of("2026-10-11 10:00:00 a", "2026-10-11 10:00:01 b", "odd"), false))
                .isNull();
    }

    @Test
    void aPartReadsOnlyTheRecordsItsMatchFits() {
        final SplitterSpec part = SplitterSpec.builder("key_value").variant("kv").delimiter(" ").build();

        // Text before its pairs is not a key=value part's to read: Stroom reports that text.
        assertThat(java.util.regex.Pattern.compile(part.guard()).matcher("user=bob action=x").lookingAt()).isTrue();
        assertThat(java.util.regex.Pattern.compile(part.guard()).matcher("2026-10-11 user=bob").lookingAt())
                .isFalse();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(final Object value) {
        return (List<Map<String, Object>>) value;
    }

    private static String resource(final String name) {
        try (InputStream in = Objects.requireNonNull(TestLineFormats.class.getResourceAsStream(name), name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
