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

import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.generation.XmlParser;
import stroom.ai.impl.tools.sample.SplitterDryRun;
import stroom.ai.impl.tools.sample.SplitterSpec;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The Data Splitter XML a spec is written as (the Stroom MCP server's `generate_splitter`, and its tests in
/// `tests/test_directive_logs.py`, `test_formats.py`, `test_sources.py` and `test_wild_formats.py`).
class TestSplitterXml extends DraftFixtures {

    private static final String DS = "data-splitter:3";

    private static XmlElement only(final XmlElement parent, final String name) {
        return parent.childElements(DS, name).getFirst();
    }

    private static Map<String, String> attributes(final XmlElement element) {
        final Map<String, String> out = new LinkedHashMap<>();
        element.attributes().forEach(attribute -> out.put(attribute.name(), attribute.value()));
        return out;
    }

    @Test
    void theConverterNamesItsPartsAndPassesOverBlankLines() {
        final SplitterSpec spec = SplitterSpec.builder("delimited").delimiter(",").header(true).build();

        final XmlElement root = XmlParser.parse(SplitterXml.converter(spec));

        assertThat(root.childElements(DS, "split")).extracting(split -> attributes(split).get("id"))
                .containsExactly("header", "record");
        // After the head's byte order mark regex.
        assertThat(attributes(root.childElements(DS, "regex").getLast())).containsEntry("id", "blank_line")
                .containsEntry("pattern", SplitterXml.BLANK_LINE);
        assertThat(attributes(only(root.childElements(DS, "split").getLast(), "group"))).containsEntry("id",
                "fields");
        // As the Stroom MCP server wrote it: no ids, no blank line regex.
        assertThat(SplitterXml.generateSplitter(spec)).doesNotContain(" id=\"header\"").doesNotContain("blank_line");
    }

    @Test
    void escapesAreTakenOutOfEachCell() {
        final String headed = SplitterXml.converter(SplitterSpec.builder("delimited").delimiter(",").header(true)
                .escape("\\").build());
        assertThat(headed).contains("escape=\"\\\"").contains("<data name=\"$heading$1\" value=\"$2\" />");

        // Named columns: a split per column, in turn.
        final String named = SplitterXml.converter(SplitterSpec.builder("delimited").delimiter(",")
                .header(List.of("a", "b")).escape("\\").build());
        assertThat(named).contains("escape=\"\\\" maxMatch=\"1\">\n        <data name=\"a\" value=\"$2\" />")
                .contains("<data name=\"b\" value=\"$2\" />");
    }

    @Test
    void spacedValuesFieldsCatchAllAndBlockRecordsAreWrittenAsStroomReadsThem() {
        assertThat(SplitterXml.converter(SplitterSpec.builder("key_value").delimiter(" ").spacedValues(true)
                .build())).contains("<all>\n        <group reverse=\"true\">")
                .contains("<data name=\"$2\" value=\"$1\" />");
        assertThat(SplitterXml.converter(SplitterSpec.builder("fields").fields(Map.of("user", "user=(\\S+)",
                "all", "x+")).build())).contains("<group matchOrder=\"any\">")
                .contains("<data name=\"user\" value=\"$1\" />")
                .contains("<data name=\"all\" value=\"$\" />")
                .contains("<all />");
        assertThat(SplitterXml.converter(SplitterSpec.builder("regex").pattern("^(\\d+)$").names(List.of("n"))
                .catchAll(true).build())).contains("<regex id=\"unparsed\"")
                .contains("<data name=\"unparsed\" value=\"$1\" />");
        assertThat(SplitterXml.converter(SplitterSpec.builder("regex").pattern("(?s)(.*)").names(List.of("m"))
                .blankLineRecords(true).build())).startsWith(SplitterXml.DS_HEAD + "  <regex id=\"record\"");
    }

    @Test
    void theBufferAllowsFourTimesTheLongestRecordWhenTheDefaultIsTooSmall() {
        // Twice the longest fits in Stroom's default of 20,000.
        assertThat(SplitterXml.bufferSizeFor(0)).isEmpty();
        assertThat(SplitterXml.bufferSizeFor(10_000)).isEmpty();
        // Rounded up to the next 10,000.
        assertThat(SplitterXml.bufferSizeFor(10_001)).hasValue(50_000);
        assertThat(SplitterXml.bufferSizeFor(20_000)).hasValue(80_000);
        // Never more than Stroom's most.
        assertThat(SplitterXml.bufferSizeFor(Integer.MAX_VALUE)).hasValue(1_000_000_000);
    }

    @Test
    void theLongestLineIsMeasuredWithoutItsNewLine() {
        assertThat(SplitterXml.longestLine("")).isZero();
        assertThat(SplitterXml.longestLine("ab\nabcd\nabc")).isEqualTo(4);
        assertThat(SplitterXml.longestLine("abcde\n")).isEqualTo(5);
    }

    @Test
    void theBufferIsSetOnTheDataSplitterElement() {
        final String xml = SplitterXml.generateSplitter(SplitterSpec.builder("delimited").delimiter(",")
                .header(true).build());

        final String buffered = SplitterXml.withBufferSize(xml, 50_000);

        assertThat(attributes(XmlParser.parse(buffered))).containsEntry("bufferSize", "50000");
        // Set again, the new size replaces the old.
        assertThat(SplitterXml.withBufferSize(buffered, 60_000))
                .contains("bufferSize=\"60000\"")
                .doesNotContain("50000");
        assertThat(SplitterXml.withBufferSize("<dataSplitter bufferSize='30000'/>", 40_000))
                .isEqualTo("<dataSplitter bufferSize=\"40000\"/>");
        // Not a Data Splitter: left as it is.
        assertThat(SplitterXml.withBufferSize("<records/>", 40_000)).isEqualTo("<records/>");
        assertThatThrownBy(() -> SplitterXml.withBufferSize(xml, 19_999))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theSplitterSetsTheHeadingAndDirectivesFromTheirLines() {
        final SplitterSpec spec = SplitterSpec.builder("delimited").delimiter(" ").header(true).quote("\"")
                .commentPrefix("#").headerDirective("#Fields:").directives(List.of("#Software:")).build();
        final XmlElement root = XmlParser.parse(SplitterXml.generateSplitter(spec));
        final XmlElement group = only(only(root, "split"), "group");
        final List<XmlElement> regexes = group.childElements(DS, "regex");
        assertThat(regexes.stream().map(r -> r.get("pattern")).toList())
                .containsExactly("^\\#Fields:\\s*(.*)$", "^\\#Software:\\s*(.*)$", "^\\#.*$", "^(.+)$");
        assertThat(only(only(only(regexes.getFirst(), "group"), "split"), "var").get("id")).isEqualTo("heading");
        final XmlElement data = regexes.getLast();
        assertThat(attributes(only(data, "data"))).isEqualTo(Map.of("name", "#Software", "value", "$directive0$1"));
        assertThat(attributes(only(only(only(data, "group"), "split"), "data")))
                .isEqualTo(Map.of("name", "$heading$1", "value", "$1"));
    }

    @Test
    void cefAloneAndAfterSyslogIsItsHeaderThenPairs() {
        final String xml = SplitterXml.generateSplitter(SplitterDryRun.inferSpec(format("syslog_cef"))
                .spec());
        assertThat(xml).contains("<data name=\"cef_name\" value=\"$7\" />", "<group value=\"$9\">",
                "<group value=\"$1\">");
    }

    @Test
    void keyValuePairsAreOneRegexWithOneValueGroupQuotedOrNot() {
        // Seen in Stroom: a split on '=' with $2 (a split has none), then $2$3 (a group that took no part can't be
        // named).
        final String xml = SplitterXml.generateSplitter(SplitterDryRun.inferSpec(format("kv_quoted"))
                .spec());
        assertThat(xml.replace(SplitterXml.DS_HEAD, "").split("<regex ", -1)).hasSize(2);
        assertThat(xml).contains("<data name=\"$1\" value=\"$2\" />").doesNotContain("split delimiter=\"=\"");
    }

    @Test
    void headerlessQuotedCsvIsOneGroupPerColumn() {
        // $1$2 per column (a quoted group or a bare one) is refused by Stroom when one took no part.
        final SplitterSpec spec = SplitterSpec.builder("delimited").header(List.of("time", "user", "message"))
                .quote("\"").build();
        final String xml = SplitterXml.generateSplitter(spec);
        assertThat(xml).contains("<data name=\"message\" value=\"$3\" />").doesNotContain("$1$2");
    }

    @Test
    void splitterSpecsGenerateConverters() {
        final SplitterSpec syslog = SplitterSpec.builder("syslog").rfc("rfc3164")
                .body(SplitterSpec.builder("key_value").quote("\"").build()).build();
        final String xml = SplitterXml.generateSplitter(syslog);
        final XmlElement root = XmlParser.parse(xml);
        assertThat(root.namespace()).isEqualTo(DS);
        assertThat(root.localName()).isEqualTo("dataSplitter");
        // Pairs by a regex matched along the body, a quoted value without its quotes (a split on '=' has no $2).
        assertThat(xml).contains("<group value=\"$6\">", "<data name=\"$1\" value=\"$2\" />")
                .doesNotContain("split delimiter=\"=\"");
        final SplitterSpec named = SplitterSpec.builder("delimited").header(List.of("time", "user")).delimiter("|")
                .build();
        assertThat(SplitterXml.generateSplitter(named)).contains("<regex pattern=\"^([^\\|]*)\\|([^\\|]*)$\">");
    }

    @Test
    void terminalOutputAndAByteOrderMark() {
        // Seen in eval case 51: Stroom keeps a byte order mark, which became part of the first key.
        final XmlElement root = XmlParser.parse(SplitterXml.generateSplitter(SplitterSpec.builder("key_value")
                .delimiter(" ").build()));
        final XmlElement first = root.childElements().getFirst();
        assertThat(first.localName()).isEqualTo("regex");
        assertThat(first.get("pattern")).isEqualTo("^\\uFEFF");
        assertThat(first.get("maxMatch")).isEqualTo("1");
        assertThat(SplitterXml.DS_HEAD.split("uFEFF", -1)).hasSize(2);
    }

    @Test
    void aRecordThatEndsAtAMarkedLineKubernetesCri() {
        final String text = """
                2026-10-11T08:00:01.1Z stdout F user=alice action=login
                2026-10-11T08:00:02.1Z stderr P user=bo
                2026-10-11T08:00:02.2Z stderr F b action=export
                2026-10-11T08:00:03.1Z stdout F user=carol action=logout
                """;
        final SplitterSpec spec = SplitterDryRun.inferSpec(text).spec();
        assertThat(spec.recordEnd()).isEqualTo(SplitterDryRun.CRI_END);
        final String xml = SplitterXml.generateSplitter(spec);
        assertThat(xml).doesNotContain("record_end").contains("(?!(?:");
        assertThat(xml).contains(SplitterXml.recordEndRegex(SplitterDryRun.CRI_END).replace("<", "&lt;")
                .replace(">", "&gt;").replace("\n", "\\n"));
    }

    @Test
    void recordsOfSeveralLinesAreTakenFromTheirStart() {
        final SplitterSpec spec = SplitterSpec.builder("regex").recordStart("\\d{4}-\\d{2}-\\d{2}")
                .pattern("(?s)^(\\S+) (.*)$").names(List.of("time", "message")).build();
        final String xml = SplitterXml.generateSplitter(spec);
        assertThat(xml).contains("<regex pattern=\"(?sm)^((?:\\d{4}-\\d{2}-\\d{2}).*?)\\n?(?=^(?:\\d{4}-\\d{2}-\\d{2})"
                                 + "|\\z)\">", "<group value=\"$1\">");
        assertThat(SplitterXml.recordRegex("x")).isEqualTo("(?sm)^((?:x).*?)\\n?(?=^(?:x)|\\z)");
    }

    @Test
    void theExamplesAreSpecs() {
        SplitterXml.EXAMPLES.values().forEach(example ->
                assertThat(SplitterXml.generateSplitter(SplitterSpec.fromMap(example))).startsWith(
                        SplitterXml.DS_HEAD).endsWith(SplitterXml.DS_TAIL));
        assertThat(SplitterXml.EXAMPLES.keySet()).containsExactly("delimited", "key=value", "syslog rfc3164",
                "syslog rfc5424", "unknown text");
    }
}
