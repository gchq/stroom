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

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Stroom's own Data Splitter parser on sample text, and the dry run agreeing with it on what the converter
/// build_data_splitter writes. The behaviours tested were each seen in Stroom first.
class TestStroomSplitterRun {

    private static final SplitterSpec CSV = SplitterSpec.builder("delimited").delimiter(",").header(true)
            .quote("\"").build();

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
                Arguments.of("W3C directives",
                        SplitterSpec.builder("delimited").delimiter(" ").header(true).commentPrefix("#")
                                .headerDirective("#Fields:").build(),
                        "#Version: 1.0\n#Fields: date time user\n2026-10-11 08:00:01 jsmith\n\n"
                        + "2026-10-11 08:00:03 guest\n"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void dryRunAndStroomAgree(final String name, final SplitterSpec spec, final String sample) {
        final StroomSplitterRun.Result stroom = StroomSplitterRun.run(SplitterXml.converter(spec), sample);

        assertThat(stroom.errors()).as("Stroom's errors").isEmpty();
        assertThat(stroom.records()).isNotEmpty();
        assertThat(stroom.records()).isEqualTo(SplitterDryRun.dryRun(spec, sample).records());
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
}
