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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Stroom's own Data Splitter parser on sample text, and the dry run agreeing with it on what the converter
/// build_data_splitter writes. The behaviours tested were each seen in Stroom first.
class TestStroomSplitterRun {

    private static final SplitterSpec CSV = SplitterSpec.builder("delimited").delimiter(",").header(true)
            .quote("\"").build();
    private static final DataSplitterSchema SCHEMA = DataSplitterSchemas.held();
    private static final String SYSLOG_START = "(?:<\\d{1,3}>)?[A-Z][a-z]{2} [ \\d]\\d \\d{2}:\\d{2}:\\d{2} ";
    private static final String MIXED_START = SYSLOG_START + "|\\w[\\w.-]*=|\\d{4}-\\d{2}-\\d{2} ";
    // Syslog with key=value messages, key=value lines and timestamped text lines, in one stream.
    private static final SplitterSpec MIXED = SplitterSpec.builder(SplitterSpec.ALTERNATIVES).parts(List.of(
            SplitterSpec.builder("syslog").variant("syslog")
                    .body(SplitterSpec.builder("key_value").delimiter(" ").build()).build(),
            SplitterSpec.builder("key_value").variant("key_value").delimiter(" ").build(),
            SplitterSpec.builder("regex").variant("text").pattern("^(\\d{4}-\\d{2}-\\d{2} \\S+) (\\S+) (.*)$")
                    .names(List.of("time", "level", "message")).build())).build();
    private static final String MIXED_LINES = "<13>Oct 11 22:14:15 host1 app[42]: user=alice action=login\n"
                                              + "user=bob action=logout src=10.0.0.1\n"
                                              + "2026-10-11 22:14:17 ERROR Something failed\n"
                                              + "<13>Oct 11 22:14:18 host1 app[42]: user=carol action=login\n";
    private static final String TRACE = "java.lang.IllegalStateException: boom\n"
                                        + "\tat com.acme.Foo.bar(Foo.java:12)\n"
                                        + "\tat com.acme.Main.main(Main.java:3)\n";

    private static final SplitterSpec KEYED = SplitterSpec.builder("key_value").delimiter(" ").quote("\"")
            .recordKey("(?<!\\S)msg=(\"[^\"]*\"|[^\\s\"]+)").keyField("msg")
            .lineName("\\s*type=\"?([A-Za-z_][\\w.-]*)\"?\\s*").leadField("type").singleQuotes(true).build();

    static Stream<Arguments> dryRunAndStroomAgree() {
        return Stream.of(
                Arguments.of("CSV, cells trimmed as Stroom trims them", CSV,
                        "a, b ,c\n1,  2  , \" 3 \"\n"),
                Arguments.of("CSV with blank lines between records", CSV, "a,b\n1,2\n\n   \n3,4\n\n"),
                Arguments.of("CSV, an empty last cell", CSV, "a,b,c\n1,2,\n1,,3\n"),
                Arguments.of("CSV, a cell past the heading", CSV, "a,b\n1,2,3\n"),
                Arguments.of("CSV, no new line at the end", CSV, "a,b\n1,2\n3,4"),
                Arguments.of("CSV with escapes",
                        CSV.toBuilder().escape("\\").build(), "a,b\nx\\,y,\"say \\\"hi\\\"\"\n"),
                Arguments.of("Named columns with escapes",
                        SplitterSpec.builder("delimited").delimiter(",").header(List.of("a", "b"))
                                .escape("\\").build(), "x\\,y,2\n3,4\n"),
                Arguments.of("Named columns", SplitterSpec.builder("delimited").delimiter("|")
                        .header(List.of("time", "user")).build(), "10:00|alice\n10:01|bob\n"),
                Arguments.of("key=value", SplitterSpec.builder("key_value").delimiter(" ").quote("\"").build(),
                        "a=1 b=\"two words\" c=3\n"),
                Arguments.of("key=value, values holding spaces (the Stroom docs' reverse example)",
                        SplitterSpec.builder("key_value").delimiter(" ").spacedValues(true).build(),
                        "ipAddress=123.123.123.123 zones=Zone 1, Zone 2, Zone 3 location=loc1 A user=An end user "
                        + "serverName=bigserver\na=1 b=two  words\n"),
                Arguments.of("Fields in any order",
                        SplitterSpec.builder("fields").fields(fields("user", "user=(\\S+)", "src", "src=(\\S+)"))
                                .build(), "Login failed user=bob from src=1.2.3.4\nsrc=5.6.7.8 then user=al\n"),
                Arguments.of("Fields in any order, the rest kept",
                        SplitterSpec.builder("fields").fields(fields("user", "user=(\\S+)")).catchAll(true)
                                .build(), "Login failed user=bob from here\nnothing to find\nuser=al\n"),
                Arguments.of("A regex, a record it doesn't match kept",
                        SplitterSpec.builder("regex").pattern("^(\\d+) (\\w+)$").names(List.of("n", "word"))
                                .catchAll(true).build(), "1 one\nsomething else\n2 two\n"),
                Arguments.of("Records separated by blank lines",
                        SplitterSpec.builder("key_value").delimiter("\n").pairSeparator("=").blankLineRecords(true)
                                .build(), "a=1\nb=2\n\nc=3\nd=4\n\n\ne=5"),
                Arguments.of("Syslog with a key=value body",
                        SplitterSpec.builder("syslog").rfc("rfc3164")
                                .body(SplitterSpec.builder("key_value").delimiter(" ").build()).build(),
                        "<13>Oct 11 22:14:15 host1 app[42]: user=alice action=login\n"),
                Arguments.of("Records over several lines",
                        SplitterSpec.builder("regex").recordStart("\\d{4}-\\d{2}-\\d{2}")
                                .pattern("(?s)^(\\S+) (.*)$").names(List.of("time", "message")).build(),
                        "2026-10-01 first\n  at line two\n2026-10-02 second\n"),
                Arguments.of("Lines in several formats, each read by its own part",
                        MIXED, MIXED_LINES),
                Arguments.of("Lines in several formats, one no part reads kept",
                        MIXED.toBuilder().catchAll(true).build(), MIXED_LINES + "nothing any part reads\n"),
                Arguments.of("Syslog whose entries carry stack traces",
                        SplitterSpec.builder("syslog").rfc("rfc3164").recordStart(SYSLOG_START).continuation(true)
                                .body(SplitterSpec.builder("key_value").delimiter(" ").build()).build(),
                        "<13>Oct 11 22:14:15 host1 app[42]: user=alice action=login\n"
                        + TRACE + "<13>Oct 11 22:14:16 host1 app[42]: user=bob action=logout\n"),
                Arguments.of("Lines in several formats, with stack traces",
                        MIXED.toBuilder().recordStart(MIXED_START).continuation(true).catchAll(true).build(),
                        MIXED_LINES.replace("Something failed\n", "Something failed\n" + TRACE)
                        + "2026-10-11 odd\n" + TRACE),
                Arguments.of("key=value, values in single quotes, one of them pairs",
                        SplitterSpec.builder("key_value").delimiter(" ").quote("\"").singleQuotes(true).build(),
                        "a='two words' msg='op=login acct=\"bob\" res=success' b=1\nc=it's d='' e=3\n"),
                Arguments.of("Lines tied by a key, each under its name (auditd)",
                        KEYED, "type=SYSCALL msg=audit(1.5:7): exe=\"/bin/ls\" a=1\n"
                               + "type=PATH msg=audit(1.5:7): item=0 name=\"/bin/ls\"\n"
                               + "type=PATH msg=audit(1.5:7): item=1 name=\"/lib/ld\"\n"
                               + "type=USER_LOGIN msg=audit(2.5:8): pid=1 msg='op=login res=success'\n"
                               + "note=restarted\n"
                               + "type=EOE msg=audit(2.5:8):\n"),
                Arguments.of("Lines tied by a key, the generated auditd log",
                        SplitterDryRun.inferSpec(resource("auditd-lines.log")).spec(), resource("auditd-lines.log")),
                keyed("auditd-node-lines.log", "auditd forwarded, node= before each line's type"),
                Arguments.of("Lines tied by a key, auditd ENRICHED with its group separators made spaces",
                        SplitterDryRun.inferSpec(TestKeyedRecords.enriched()).spec(), TestKeyedRecords.enriched()),
                keyed("auditd-ausearch-lines.log", "ausearch's output, ---- and time-> lines passed over"),
                keyed("txn-lines.log", "a transaction log, tied by txn= and named by step="),
                Arguments.of("Lines tied by a key, sections between [[job-ID:STEP]] markers",
                        SplitterDryRun.inferSpec(TestKeyedRecords.sectioned()).spec(), TestKeyedRecords.sectioned()),
                Arguments.of("key=value after the same few words, the words fields of their own",
                        SplitterDryRun.inferSpec(TestFormatsShortlist.PREFIXED).spec(), TestFormatsShortlist.PREFIXED),
                Arguments.of("Quoted key:\"value\" pairs separated by \"; \"",
                        SplitterSpec.builder("key_value").pairSeparator(":").quote("\"").delimiter("; ").build(),
                        "action:\"Accept\"; src:\"10.1.2.3\"; rule_name:\"Allow web; and more\"\n"),
                Arguments.of("Text lines tied by a key given by hand (the guide's PostgreSQL spec)",
                        TestFormatsShortlist.POSTGRES, TestFormatsShortlist.resource("postgres-lines.log")),
                Arguments.of("key=value, text around the pairs kept as words",
                        SplitterSpec.builder("key_value").delimiter(" ").quote("\"").words(true).build(),
                        "[12.3] DROP: IN=eth0 OUT= DF PROTO=TCP SYN URGP=0\nconn=1 op=0 BIND dn=\"uid=a\" x=1\n"),
                // Not key=value: a regex reads each line, a bracketed request id ties them, the step names them.
                Arguments.of("Lines tied by a key, text lines read by a regex",
                        SplitterSpec.builder("regex").pattern("^(\\S+) (\\w+) \\[(req-\\d+)\\] (\\w+) ?(.*)$")
                                .names(List.of("time", "level", "request", "step", "message"))
                                .recordKey("\\[(req-\\d+)\\]").keyField("request")
                                .lineName("\\S+ \\w+ \\[req-\\d+\\] (\\w+)").leadField("step").build(),
                        "2026-10-11T10:00:01Z INFO [req-1] receive GET /orders\n"
                        + "2026-10-11T10:00:01Z INFO [req-1] query 12 rows\n"
                        + "2026-10-11T10:00:02Z WARN [req-1] respond 200 slow\n"
                        + "2026-10-11T10:00:03Z INFO [req-2] receive GET /stock\n"
                        + "2026-10-11T10:00:03Z INFO [req-2] respond 404\n"),
                Arguments.of("W3C directives",
                        SplitterSpec.builder("delimited").delimiter(" ").header(true).commentPrefix("#")
                                .headerDirective("#Fields:").build(),
                        "#Version: 1.0\n#Fields: date time user\n2026-10-11 08:00:01 jsmith\n\n"
                        + "2026-10-11 08:00:03 guest\n"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void dryRunAndStroomAgree(final String name, final SplitterSpec spec, final String sample) {
        // Checked against the schema first, as the DSParser element checks it: every converter written is valid.
        final StroomSplitterRun.Result stroom = StroomSplitterRun.run(SCHEMA, SplitterXml.converter(spec),
                List.of(sample)).getFirst();

        assertThat(stroom.errors()).as("Stroom's errors").isEmpty();
        assertThat(stroom.records()).isNotEmpty();
        assertThat(stroom.records()).isEqualTo(SplitterDryRun.dryRun(spec, sample).records());
    }

    @Test
    void schemaErrorsComeBeforeTheRecordsOfTheFirstText() {
        // As seen in testing: the fields named with <entry>, which Stroom's parser ignores, and group on the regex.
        final String converter = DataSplitterSchema.ROOT + "<regex pattern=\"^(\\S+) (.*)$\" group=\"0\">"
                                 + "<entry>ReceiveTime</entry><entry>Body</entry></regex></dataSplitter>";

        final List<StroomSplitterRun.Result> runs = StroomSplitterRun.run(SCHEMA, converter,
                List.of("a b\n", "c d\n"));

        assertThat(runs.getFirst().errors()).anySatisfy(error -> assertThat(error).contains("group"))
                .anySatisfy(error -> assertThat(error).contains("entry"));
        assertThat(runs.getFirst().clean()).isFalse();
        // Said once, with the first text.
        assertThat(runs.get(1).errors()).noneSatisfy(error -> assertThat(error).contains("entry"));
    }

    @Test
    void withNoSchemaHeldNothingRuns() {
        final List<StroomSplitterRun.Result> runs = StroomSplitterRun.run(DataSplitterSchemas.none(),
                SplitterXml.converter(CSV), List.of("a,b\n1,2\n"));

        assertThat(runs).singleElement().satisfies(run -> {
            assertThat(run.records()).isEmpty();
            assertThat(run.errors()).containsExactly(DataSplitterSchema.NOT_HELD);
        });
    }

    @Test
    void aPatternThatDoesntCompileIsUnreadable() {
        assertThat(StroomSplitterRun.unreadable(DataSplitterSchema.ROOT
                                                + "<regex pattern=\"(unclosed\"><data value=\"$1\"/></regex>"
                                                + "</dataSplitter>"))
                .hasValueSatisfying(why -> assertThat(why).startsWith("Stroom can't read the converter: ")
                        .contains("(unclosed"));
        assertThat(StroomSplitterRun.unreadable(SplitterXml.converter(CSV))).isEmpty();
    }

    @Test
    void stroomTrimsCellsAndNamesNoneBeyondTheHeading() {
        final StroomSplitterRun.Result run = StroomSplitterRun.run(SplitterXml.converter(CSV),
                "a, b\n 1 ,\"  2  \",3\n");

        assertThat(run.records()).containsExactly(Map.of("a", "1", "b", "2", StroomSplitterRun.UNNAMED, "3"));
    }

    @Test
    void blankLinesAreRecordsOfEmptyFieldsUnlessPassedOver() {
        // generateSplitter's plain converter writes them, with no error.
        final String sample = "a,b\n1,2\n\n3,4\n";
        assertThat(StroomSplitterRun.run(SplitterXml.generateSplitter(CSV), sample).records())
                .contains(Map.of("a", ""));
        assertThat(StroomSplitterRun.run(SplitterXml.converter(CSV), sample).records())
                .containsExactly(Map.of("a", "1", "b", "2"), Map.of("a", "3", "b", "4"));
    }

    @Test
    void linesEndedByACarriageReturnAloneGiveNoRecordsAndNoError() {
        final StroomSplitterRun.Result run = StroomSplitterRun.run(SplitterXml.converter(CSV), "a,b\r1,2\r3,4\r");

        assertThat(run.records()).isEmpty();
        assertThat(run.clean()).isTrue();
    }

    @Test
    void aRecordOverTheReadBufferIsLostWithAnErrorSayingSo() {
        final String sample = "a,b\n1," + "x".repeat(25_000) + "\n2,3\n";

        final StroomSplitterRun.Result lost = StroomSplitterRun.run(SplitterXml.converter(CSV), sample);
        assertThat(lost.records()).containsExactly(Map.of("a", "2", "b", "3"));
        assertThat(lost.errors()).singleElement().asString().contains("exhausted the buffer");

        final StroomSplitterRun.Result kept = StroomSplitterRun.run(
                SplitterXml.withBufferSize(SplitterXml.converter(CSV), 100_000), sample);
        assertThat(kept.clean()).isTrue();
        assertThat(kept.records()).hasSize(2);
    }

    @Test
    void anErrorNamesThePartOfTheSpecByItsId() {
        final SplitterSpec spec = SplitterSpec.builder("regex").pattern("^(\\d+)$").names(List.of("n")).build();

        final StroomSplitterRun.Result run = StroomSplitterRun.run(SplitterXml.converter(spec), "1\nx\n2\n");

        assertThat(run.records()).containsExactly(Map.of("n", "1"), Map.of("n", "2"));
        assertThat(run.errors()).singleElement().asString()
                .startsWith("line 2, column 1: ERROR: ")
                .contains("<group id=\"fields\">")
                .contains("unmatched content: [x");
    }

    @Test
    void valuesHoldingSpacesAreLostReadAsPairsEndingAtTheDelimiter() {
        final SplitterSpec plain = SplitterSpec.builder("key_value").delimiter(" ").build();
        final String sample = "zones=Zone 1, Zone 2 user=An end user\n";

        assertThat(StroomSplitterRun.run(SplitterXml.converter(plain), sample).clean()).isFalse();
        // The dry run says so too, rather than keeping zones=Zone.
        assertThat(SplitterDryRun.dryRun(plain, sample).unmatchedLines()).hasSize(1);
        // Inferred from the sample: read from the end.
        assertThat(SplitterDryRun.spacedValues(List.of(sample.strip()), " ", "=")).isTrue();
        // sudo's body reads neither way: left as it was.
        assertThat(SplitterDryRun.spacedValues(List.of("alice : TTY=pts/0 ; PWD=/home ; COMMAND=/bin/ls -la"), " ",
                "=")).isFalse();
    }

    @Test
    void aConverterStroomCannotReadIsAnErrorWithNoRecords() {
        final StroomSplitterRun.Result run = StroomSplitterRun.run("<dataSplitter", "a,b\n");

        assertThat(run.records()).isEmpty();
        assertThat(run.errors()).singleElement().asString().startsWith("Stroom couldn't run the converter: ");
    }

    @Test
    void aRunOverItsTimeIsReportedAsTooSlow() {
        final StroomSplitterRun.Result run = StroomSplitterRun.run(SplitterXml.converter(CSV), "a,b\n1,2\n",
                Duration.ZERO);

        assertThat(run.errors()).singleElement().asString().contains("backtracks too much");
    }

    private static Map<String, String> fields(final String... namesAndRegexes) {
        final Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < namesAndRegexes.length; i += 2) {
            fields.put(namesAndRegexes[i], namesAndRegexes[i + 1]);
        }
        return fields;
    }

    // Each of the formats shortlist (formats-shortlist-generator.py) as inferred.
    static Stream<Arguments> theFormatsShortlistAgrees() {
        return TestFormatsShortlist.fixtures().stream().map(name -> {
            final String sample = TestFormatsShortlist.resource(name + "-lines.log");
            return Arguments.of(name, SplitterDryRun.inferSpec(sample).spec(), sample);
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void theFormatsShortlistAgrees(final String name, final SplitterSpec spec, final String sample) {
        dryRunAndStroomAgree(name, spec, sample);
    }

    private static Arguments keyed(final String fixture, final String name) {
        final String sample = resource(fixture);
        return Arguments.of("Lines tied by a key, " + name, SplitterDryRun.inferSpec(sample).spec(), sample);
    }

    private static String resource(final String name) {
        try (InputStream in = Objects.requireNonNull(TestStroomSplitterRun.class.getResourceAsStream(name), name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
