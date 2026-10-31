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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.ErrorTriage;
import stroom.ai.impl.tools.draft.SplitterXml;
import stroom.ai.impl.tools.sample.SampleProfiler;
import stroom.ai.impl.tools.sample.SplitterSpec;
import stroom.ai.impl.tools.sample.StroomSplitterRun;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/// build_data_splitter, the tool (the spec, its inference and dry run are
/// [stroom.ai.impl.tools.sample.SplitterDryRun]'s, tested with it).
@SuppressWarnings("unchecked")
class TestBuildDataSplitterTool {

    private static final String CSV = "time,user,action\n2026-10-01T10:00:00Z,alice,login\n"
                                       + "2026-10-01T10:05:00Z,bob,logout\n";

    private final TranslationWorld world = new TranslationWorld();
    private final BuildDataSplitterTool tool = new BuildDataSplitterTool(world.sampleStreamTexts,
            world.sampleProfiling, world.docs, world.eventVersions, world.plan);

    @Test
    void toolsReadTheSampleFromItsStreamsInsteadOfItsText() {
        // The text goes through the model once, to upload_sample; the tools after it take stream_ids.
        Mockito.when(world.sampleStreamTexts.read(List.of(7L))).thenReturn(new SampleStreamTexts.Texts(
                Map.of("stream 7", CSV), List.of()));

        final Map<String, Object> splitter = world.call(tool, Map.of("stream_ids", List.of(7)));

        assertThat(splitter).containsEntry("records", 2).containsEntry("inferred", true)
                .containsEntry("converter_type", "DATA_SPLITTER");
        assertThat(((Map<?, ?>) splitter.get("spec")).get("kind")).isEqualTo("delimited");
        assertThat((String) splitter.get("hint")).startsWith("Every line parsed.");
    }

    @Test
    void everySampleStreamMustParseNotJustTheFirst() {
        Mockito.when(world.sampleStreamTexts.read(List.of(7L, 8L))).thenReturn(new SampleStreamTexts.Texts(
                orderedOf("stream 7", CSV, "stream 8", "time,user,action\n2026-10-01T11:00:00Z,carol,login\n"
                                                       + "garbled line with no commas\n"), List.of()));

        final Map<String, Object> splitter = world.call(tool, Map.of("stream_ids", List.of(7, 8), "spec",
                Map.of("kind", "regex", "pattern", "^([^ ,]+),([^ ,]+),([^ ,]+)$", "names",
                        List.of("time", "user", "action")), "save_as", "ACME-SPLITTER", "build", "acme-v1"));

        // The header lines are records to a regex: 3 of each, and the garbled line in the second.
        assertThat(splitter.get("records")).isEqualTo(5);
        assertThat(splitter.get("unmatched_count")).isEqualTo(1);
        assertThat((String) splitter.get("not_saved")).contains("1 line(s) match nothing");
        assertThat(splitter).doesNotContainKey("saved");
    }

    @Test
    void longLinesGetAReadBufferBigEnoughForThem() {
        // Seen in testing: CSV lines of about 20,000 characters, longer than the Data Splitter's default buffer,
        // were lost in processing.
        final String csv = "time,user,action\n2026-10-01T10:00:00Z,alice," + "x".repeat(20_000) + "\n";

        final Map<String, Object> splitter = world.call(tool, Map.of("sample", csv));

        assertThat(splitter).containsEntry("buffer_size", 90_000);
        assertThat((String) splitter.get("converter")).contains("version=\"3.0\" bufferSize=\"90000\">");
        // And Stroom's own parser keeps the line whole with it.
        assertThat(stroomCheck(splitter)).containsEntry("matches", true).containsEntry("records", 1);
        assertThat(firstRecord(splitter)).containsEntry("action", "x".repeat(20_000));
        // Agreeing on the sample says nothing of records further in (seen in testing: taken for the whole feed).
        assertThat((String) stroomCheck(splitter).get("summary"))
                .startsWith("writes the same 1 record(s), of the 20,045 characters of sample read.")
                .contains("Records further in, longer or laid out differently, are not checked");
    }

    @Test
    void anExhaustedBufferIsExplainedAsOverHalfOfIt() {
        // Seen in testing: told a record was "longer than the read buffer", the agent took a lost 751,000-character
        // record under a 1,000,000 buffer for a quoting fault.
        assertThat(ErrorTriage.bundled().classify("ERROR", "dsParser", true, "Top level expression 'split[1]' "
                                                  + "matched too much content and exhausted the buffer.").reason())
                .contains("longer than half the Data Splitter's read buffer")
                .contains("at least double the buffer");
    }

    @ParameterizedTest(name = "a line of {0} characters")
    @ValueSource(ints = {9_990, 10_010, 15_000, 19_990, 20_010, 39_990, 100_000})
    void stroomKeepsALongLineWithTheBufferItIsGiven(final int length) {
        // Seen in Stroom: the default buffer keeps a line of 19,990 characters and loses one of 20,010.
        final String csv = "time,user,action\n2026-10-01T10:00:00Z,alice," + "x".repeat(length) + "\n"
                           + "2026-10-01T10:05:00Z,bob,logout\n";

        final Map<String, Object> splitter = world.call(tool, Map.of("sample", csv));

        assertThat(stroomCheck(splitter)).containsEntry("matches", true).containsEntry("records", 2);
    }

    @Test
    void onlyARecordUpToHalfTheBufferIsSureToFit() {
        // Seen in testing: a 751,000-character record lost with bufferSize 1000000, after many shorter ones. Stroom
        // refills the buffer only once less than half of it is unread, so a record over half fits or not by where
        // it starts (a long line near the start of a sample fits, which the tests above rely on).
        final int buffer = 40_000;
        final String converter = SplitterXml.withBufferSize(lines(), buffer);
        assertThat(StroomSplitterRun.run(converter, after(15_000, 19_000)).errors()).as("under half").isEmpty();
        assertThat(StroomSplitterRun.run(converter, after(15_000, 25_000)).errors()).as("over half, started late")
                .anyMatch(error -> error.contains("exhausted the buffer"));
        assertThat(StroomSplitterRun.run(converter, after(0, 25_000)).errors()).as("over half, started early")
                .isEmpty();

        // The size the tool gives (four times the longest) keeps it wherever it starts.
        final String sized = SplitterXml.withBufferSize(lines(), SplitterXml.bufferSizeFor(25_000).orElseThrow());
        for (int before = 0; before <= 60_000; before += 7_500) {
            assertThat(StroomSplitterRun.run(sized, after(before, 25_000)).errors()).as("after %s", before)
                    .isEmpty();
        }
    }

    @Test
    void aLongLineInALaterSampleStreamSizesTheBufferToo() {
        final String longCsv = "time,user,action\n2026-10-01T11:00:00Z,carol," + "y".repeat(30_000) + "\n";
        Mockito.when(world.sampleStreamTexts.read(List.of(7L, 8L))).thenReturn(new SampleStreamTexts.Texts(
                orderedOf("stream 7", CSV, "stream 8", longCsv), List.of()));

        final Map<String, Object> splitter = world.call(tool, Map.of("stream_ids", List.of(7, 8)));

        assertThat(splitter).containsEntry("buffer_size", 130_000);
        assertThat(stroomCheck(splitter)).containsEntry("matches", true).containsEntry("records", 3);
    }

    @Test
    void aRecordOverSeveralLinesIsSizedWhole() {
        // A stack trace of short lines: no line is long, the record is. Sized by its longest line, it was lost.
        final StringBuilder trace = new StringBuilder("2026-10-01 ERROR boom\n");
        for (int i = 0; i < 1_000; i++) {
            trace.append("    at com.example.Service.method").append(i).append("(Service.java:").append(i)
                    .append(")\n");
        }
        final String sample = trace + "2026-10-02 INFO next\n";

        final Map<String, Object> splitter = world.call(tool, Map.of("sample", sample, "spec", Map.of("kind",
                "regex", "record_start", "\\d{4}-\\d{2}-\\d{2}", "pattern", "(?s)^(\\S+) (\\S+) (.*)$", "names",
                List.of("time", "level", "message"))));

        assertThat((Integer) splitter.get("buffer_size")).isGreaterThan(trace.length());
        assertThat(stroomCheck(splitter)).containsEntry("matches", true).containsEntry("records", 2);
    }

    @Test
    void aQuotedValueOverSeveralLinesIsSizedWhole() {
        final StringBuilder value = new StringBuilder();
        for (int i = 0; i < 1_000; i++) {
            value.append("line ").append(i).append(" of a long ticket description\n");
        }
        final String sample = "id,description\n1,\"" + value + "\"\n2,short\n";

        final Map<String, Object> splitter = world.call(tool, Map.of("sample", sample, "spec", Map.of("kind",
                "delimited", "delimiter", ",", "header", true, "quote", "\"", "quoted_newlines", true)));

        assertThat((Integer) splitter.get("buffer_size")).isGreaterThan(value.length());
        assertThat(stroomCheck(splitter)).containsEntry("matches", true).containsEntry("records", 2);
    }

    @Test
    void aBufferGivenForTheFeedsLongerRecordsKeepsThemInStroom() {
        // The sample's lines are short; the feed's are not (seen in testing: found only in processing).
        final Map<String, Object> splitter = world.call(tool, Map.of("sample", CSV, "buffer_size", 100_000));
        final String feed = "time,user,action\n2026-10-01T10:00:00Z,alice," + "z".repeat(60_000) + "\n";

        assertThat(StroomSplitterRun.run((String) splitter.get("converter"), feed).clean()).isTrue();
        assertThat(StroomSplitterRun.run(SplitterXml.converter(SplitterSpec.fromMap(
                (Map<String, Object>) splitter.get("spec"))), feed).errors())
                .first().asString().contains("exhausted the buffer");
    }

    @Test
    void recordsStroomWritesWithOtherValuesAreReportedButSaved() {
        // Stroom takes control characters out of the text; the dry run keeps them.
        final Map<String, Object> splitter = world.call(tool, Map.of("sample",
                "time,user,action\n2026-10-01T10:00:00Z,al\u0007ice,login\n", "save_as", "ACME-SPLITTER", "build",
                "acme-v1"));

        assertThat(stroomCheck(splitter)).containsEntry("matches", false).containsEntry("loses_records", false);
        assertThat((List<Map<String, Object>>) stroomCheck(splitter).get("differences")).singleElement()
                .satisfies(difference -> {
                    assertThat(difference.get("dry_run")).isEqualTo(Map.of("user", "al\u0007ice"));
                    assertThat(difference.get("stroom")).isEqualTo(Map.of("user", "alice"));
                });
        assertThat(splitter).containsKey("saved");
    }

    @Test
    void shortLinesKeepStroomsDefaultBuffer() {
        final Map<String, Object> splitter = world.call(tool, Map.of("sample", CSV));

        assertThat(splitter).doesNotContainKey("buffer_size");
        assertThat((String) splitter.get("converter")).doesNotContain("bufferSize");
    }

    @Test
    void theBufferMayBeGivenForRecordsTheSampleDoesNotHold() {
        final Map<String, Object> splitter = world.call(tool, Map.of("sample", CSV, "buffer_size", 200_000));

        assertThat(splitter).containsEntry("buffer_size", 200_000);
        assertThat((String) splitter.get("converter")).contains("bufferSize=\"200000\"");
        // Less than Stroom's default, or more than its most, is no buffer it takes.
        assertThat((String) world.call(tool, Map.of("sample", CSV, "buffer_size", 1_000)).get("error"))
                .startsWith("buffer_size is from 20000 (Stroom's default) to 1000000000 characters, not 1000");
    }

    @Test
    @SuppressWarnings("unchecked")
    void stroomsOwnParserChecksTheConverter() {
        final Map<String, Object> splitter = world.call(tool, Map.of("sample", CSV));

        final Map<String, Object> check = (Map<String, Object>) splitter.get("stroom_check");
        assertThat(check).containsEntry("matches", true).containsEntry("records", 2)
                .containsEntry("loses_records", false);
        // The converter named in its parts, which Stroom's errors give.
        assertThat((String) splitter.get("converter")).contains("<split id=\"record\"");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aConverterStroomLosesRecordsWithIsNotSaved() {
        // Seen in testing: a value holding a space, read as pairs ending at the space; Stroom kept zones=Zone and
        // reported the rest.
        final Map<String, Object> splitter = world.call(tool, Map.of("sample", "zones=Zone 1, Zone 2 user=al\n",
                "spec", Map.of("kind", "key_value", "delimiter", " "), "save_as", "ACME-SPLITTER", "build",
                "acme-v1"));

        final Map<String, Object> check = (Map<String, Object>) splitter.get("stroom_check");
        assertThat(check).containsEntry("matches", false).containsEntry("loses_records", true);
        assertThat((List<String>) check.get("errors")).first().asString()
                .contains("failed to match from the start of the content");
        assertThat((List<String>) check.get("error_hints")).singleElement().asString().contains("spaced_values");
        assertThat((String) splitter.get("hint")).startsWith("Stroom's own parser doesn't agree with the dry run");
        assertThat(splitter).containsKey("not_saved").doesNotContainKey("saved");
    }

    @Test
    @SuppressWarnings("unchecked")
    void valuesHoldingSpacesAreReadFromTheEnd() {
        final Map<String, Object> splitter = world.call(tool, Map.of("sample",
                "ipAddress=123.123.123.123 zones=Zone 1, Zone 2 location=loc1 A user=An end user\n"
                + "ipAddress=1.2.3.4 zones=Zone 3 location=loc2 user=someone else\n"));

        assertThat((Map<String, Object>) splitter.get("spec")).containsEntry("spaced_values", true);
        assertThat(splitter.get("unmatched_count")).isEqualTo(0);
        assertThat((Map<String, Object>) splitter.get("stroom_check")).containsEntry("matches", true);
        assertThat(((List<Map<String, String>>) splitter.get("first_records")).getFirst())
                .containsEntry("zones", "Zone 1, Zone 2").containsEntry("user", "An end user");
    }

    @Test
    void linesEndedByACarriageReturnAloneAreNotSaved() {
        // Seen in Stroom: such a stream is one line to the Data Splitter, which writes no records and no error.
        final Map<String, Object> splitter = world.call(tool, Map.of("sample", CSV.replace("\n", "\r"), "save_as",
                "ACME-SPLITTER", "build", "acme-v1"));

        assertThat((String) splitter.get("line_endings")).contains("carriage return alone");
        assertThat((String) splitter.get("not_saved")).contains("FindReplaceFilter");
        assertThat(splitter).doesNotContainKey("saved");
    }

    @Test
    void saveAsSavesTheConverterInTheBuildWhenEveryLineParsed() {
        final Map<String, Object> splitter = world.call(tool, Map.of("sample", CSV, "save_as", "ACME-SPLITTER",
                "build", "acme-v1"));

        final Map<?, ?> saved = (Map<?, ?>) splitter.get("saved");
        assertThat(saved.get("type")).isEqualTo(TextConverterDoc.TYPE);
        final TextConverterDoc doc = (TextConverterDoc) world.world.docs.get(String.valueOf(saved.get("uuid")));
        assertThat(doc.getConverterType()).isEqualTo(TextConverterType.DATA_SPLITTER);
        assertThat(doc.getData()).isEqualTo(splitter.get("converter"));
        // Made in the build's Events folder, until a pipeline writing Records runs it.
        assertThat(world.world.guard().folderContents("acme-v1")).anySatisfy(found ->
                assertThat(found.role()).isEqualTo("Events"));
    }

    @Test
    void jsonAndXmlNeedNoDataSplitter() {
        final Map<String, Object> refused = world.call(tool, Map.of("sample",
                "{\"user\": \"alice\"}\n{\"user\": \"bob\"}\n"));

        assertThat((String) refused.get("error")).startsWith("The sample is json lines: it needs no Data Splitter.")
                .endsWith("Go on to build_translation_xslt (input json).");
    }

    @Test
    void aSpecThatIsNotOneSaysWhatOneLooksLike() {
        final Map<String, Object> refused = world.call(tool, Map.of("sample", CSV, "spec", Map.of("kind", "nope")));

        assertThat((String) refused.get("error")).startsWith("spec is not a splitter spec (")
                .contains("For delimited data it looks like {'kind': 'delimited'")
                .endsWith("or leave spec out and it is inferred from the sample.");
    }

    @Test
    void neitherSampleNorSpecIsRefused() {
        assertThat(world.call(tool, Map.of())).containsEntry("error", "Give the sample (the file's text, or "
                                                                      + "stream_ids): the spec is inferred from it");
    }

    @Test
    void theConverterStepGivesFragmentsTheirWrapperRatherThanSkippingIt() {
        // Seen: the plan's converter step called build_data_splitter, which said "no Data Splitter", and none was
        // saved.
        final FragmentWrappers wrappers = Mockito.mock(FragmentWrappers.class);
        final Map<String, Object> live = new LinkedHashMap<>();
        live.put("name", "Event Logging v3.4.2 Fragments");
        live.put("uuid", "w1");
        live.put("path", "/Format Handling");
        live.put("code", TestSampleProfiling.LIVE_WRAPPER);
        live.put("root", "Events");
        live.put("namespace", "event-logging:3");
        Mockito.when(wrappers.find()).thenReturn(List.of(live));
        Mockito.when(wrappers.version(any())).thenReturn(TranslationWorld.VERSION);
        final BuildDataSplitterTool environment = new BuildDataSplitterTool(world.sampleStreamTexts,
                new SampleProfiling.Profiled(wrappers), world.docs, world.eventVersions, world.plan);

        final Map<String, Object> result = world.call(environment, Map.of("sample", TestSampleProfiling.FRAGMENTS,
                "save_as", "ACME-WRAPPER", "build", "acme-v1"));

        assertThat(result).containsEntry("converter_type", "XML_FRAGMENT")
                .containsEntry("converter", SampleProfiler.cleanWrapper(TestSampleProfiling.LIVE_WRAPPER));
        assertThat(((Map<?, ?>) ((Map<?, ?>) result.get("xslt_input")).get("mapping")).get("xml_namespace"))
                .isEqualTo("event-logging:3");
        assertThat((String) result.get("hint")).contains("xml_namespace 'event-logging:3'");
        final TextConverterDoc doc = (TextConverterDoc) world.world.docs.get(String.valueOf(
                ((Map<?, ?>) result.get("saved")).get("uuid")));
        assertThat(doc.getConverterType()).isEqualTo(TextConverterType.XML_FRAGMENT);
    }

    private static Map<String, String> orderedOf(final String k1, final String v1, final String k2, final String v2) {
        final Map<String, String> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    @SuppressWarnings("unchecked")
    // A Data Splitter of one record a line.
    private static String lines() {
        return "<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<dataSplitter xmlns=\"data-splitter:3\" "
               + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"data-splitter:3 "
               + "file://data-splitter-v3.0.xsd\" version=\"3.0\">\n<split id=\"record\" delimiter=\"\\n\">"
               + "<data name=\"line\" value=\"$1\"/></split>\n</dataSplitter>";
    }

    // Short lines to about `before` characters, then a line of `length`, then a short one.
    private static String after(final int before, final int length) {
        final StringBuilder text = new StringBuilder();
        while (text.length() < before) {
            text.append("a short line of text, as most of a feed's records are 0123456789\n");
        }
        return text.append("y".repeat(length)).append("\nafter\n").toString();
    }

    private static Map<String, Object> stroomCheck(final Map<String, Object> splitter) {
        return (Map<String, Object>) splitter.get("stroom_check");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> firstRecord(final Map<String, Object> splitter) {
        return ((List<Map<String, String>>) splitter.get("first_records")).getFirst();
    }
}
