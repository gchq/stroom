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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestRecordSplitter {

    private static final String WINDOWS = "http://schemas.microsoft.com/win/2004/08/events/event";

    @Test
    void xmlFragmentsAreRecordsWithTheirOwnNamespace() {
        final String text = IntStream.range(0, 5)
                .mapToObj(n -> "<Event xmlns=\"urn:win\"><Id>" + n + "</Id></Event>")
                .reduce("", (a, b) -> a + b + "\n");

        final RecordSplitter.Chunk chunk = RecordSplitter.split(text, 1000, false);

        assertThat(chunk.format()).isEqualTo(RecordSplitter.XML_FRAGMENTS);
        assertThat(chunk.records()).hasSize(5);
        assertThat(chunk.records().get(2)).isEqualTo("<Event xmlns=\"urn:win\"><Id>2</Id></Event>");
    }

    @Test
    void theChildrenOfAnXmlRootAreRecordsEvenWhenItIsCutOff() {
        final String text = "<?xml version=\"1.0\"?><records><r><a>1</a></r><r><a>2</a></r><r><a>3";

        final RecordSplitter.Chunk chunk = RecordSplitter.split(text, 1000, true);

        assertThat(chunk.format()).isEqualTo(RecordSplitter.XML);
        assertThat(chunk.records()).containsExactly("<r><a>1</a></r>", "<r><a>2</a></r>");
        assertThat(chunk.root()).isEqualTo("<records>\n{records}\n</records>");
    }

    @Test
    void theItemsOfAJsonArrayAreRecordsUpToACutOffEnd() {
        final RecordSplitter.Chunk chunk = RecordSplitter.split("[{\"a\": 1},\n {\"a\": 2},\n {\"a\": 3", 1000,
                true);

        assertThat(chunk.format()).isEqualTo(RecordSplitter.JSON_ARRAY);
        assertThat(chunk.records()).containsExactly("{\"a\": 1}", "{\"a\": 2}");
    }

    @Test
    void theArrayOneObjectWrapsTheRecordsInHoldsThem() {
        // As AWS CloudTrail writes them.
        final RecordSplitter.Chunk chunk = RecordSplitter.split(
                "{\"Records\": [{\"e\": 1}, {\"e\": 2}, {\"e\": 3}], \"n\": [1]}", 1000, false);

        assertThat(chunk.format()).isEqualTo(RecordSplitter.JSON_OBJECT);
        assertThat(chunk.records()).containsExactly("{\"e\": 1}", "{\"e\": 2}", "{\"e\": 3}");
        assertThat(chunk.parsed().getFirst()).isEqualTo(Map.of("e", 1L));
    }

    @Test
    void aDelimitedStreamsHeaderIsGivenApartFromItsRecords() {
        // Seen in e2e (evaluate_and_fix): the header line was dropped from the records and given nowhere.
        final RecordSplitter.Chunk chunk = RecordSplitter.split(
                "time,user,host\n2026-09-28T10:00:00,alice,ws01\n2026-09-28T10:05:00,bob,ws02\n", 1000, false);

        assertThat(chunk.format()).isEqualTo(RecordSplitter.DELIMITED);
        assertThat(chunk.header()).isEqualTo("time,user,host");
        assertThat(chunk.records()).containsExactly("2026-09-28T10:00:00,alice,ws01", "2026-09-28T10:05:00,bob,ws02");
        assertThat(chunk.parsed().get(1)).isEqualTo(Map.of("time", "2026-09-28T10:05:00", "user", "bob",
                "host", "ws02"));
    }

    @Test
    void jsonLinesAreRecordsALine() {
        final RecordSplitter.Chunk chunk = RecordSplitter.split("{\"a\":1}\n{\"a\":2}\n", 1000, false);

        assertThat(chunk.format()).isEqualTo(RecordSplitter.JSON_LINES);
        assertThat(chunk.records()).containsExactly("{\"a\":1}", "{\"a\":2}");
    }

    @Test
    void otherTextIsALineARecordWithACutOffLastLineDropped() {
        final RecordSplitter.Chunk chunk = RecordSplitter.split("one thing\nanother thing\nhalf a th", 1000, true);

        assertThat(chunk.format()).isEqualTo(RecordSplitter.UNKNOWN_TEXT);
        assertThat(chunk.records()).isEqualTo(List.of("one thing", "another thing"));
    }

    @Test
    void cutOffHeadsKeepOnlyCompleteRecords() {
        final List<Object> items = new ArrayList<>();
        IntStream.range(0, 50).forEach(i -> items.add(SampleProfiler.map("n", (long) i, "action", "x")));
        final String array = PyJson.dumps(items);

        final RecordSplitter.Chunk json = RecordSplitter.split(array.substring(0, 500), 5000, true);

        assertThat(json.format()).isEqualTo("json array");
        assertThat(json.records()).hasSizeBetween(1, 49).allSatisfy(r -> assertThat(PyJson.loads(r)).isNotNull());
        final String xml = "<audit>" + IntStream.range(0, 50).mapToObj(i -> "<entry><op>read</op><n>" + i
                                                                            + "</n></entry>")
                .collect(Collectors.joining()) + "</audit>";
        final RecordSplitter.Chunk cutXml = RecordSplitter.split(xml.substring(0, 600), 5000, true);
        assertThat(cutXml.format()).isEqualTo("xml");
        assertThat(cutXml.records().getLast()).endsWith("</entry>");
        assertThat(cutXml.records()).hasSizeLessThan(50);
        final String lines = IntStream.range(0, 30).mapToObj(i -> "2026-09-28T15:00:0" + (i % 10) + "Z fw "
                                                                  + "action=allow src=10.0.0." + i + " dport=53\n")
                .collect(Collectors.joining());
        final RecordSplitter.Chunk cutLines = RecordSplitter.split(lines.substring(0, 700), 5000, true);
        assertThat(cutLines.records()).allMatch(r -> r.endsWith("dport=53"));
    }

    @Test
    void xmlFragmentsKeepTheirNamespaceInTheExamples() {
        // Seen in Ask Stroom AI: Windows events shown as <Event> without their xmlns, which the XSLT must match;
        // the agent read the stream again to find it.
        final RecordSplitter.Chunk chunk = RecordSplitter.split("<Event xmlns=\"" + WINDOWS + "\"><EventID>1000"
                                                                + "</EventID></Event>\n<Event xmlns=\"" + WINDOWS
                                                                + "\"><EventID>1001</EventID></Event>\n");

        assertThat(chunk.format()).isEqualTo("xml fragments");
        assertThat(chunk.records()).allMatch(r -> r.startsWith("<Event xmlns=\"" + WINDOWS + "\">"));
        final RecordSplitter.Chunk document = RecordSplitter.split("<Events xmlns=\"" + WINDOWS + "\"><Event>"
                                                                   + "<EventID>1</EventID></Event><Event><EventID>2"
                                                                   + "</EventID></Event></Events>");
        assertThat(document.records().getFirst()).isEqualTo("<Event><EventID>1</EventID></Event>");
        assertThat(document.root()).contains("xmlns=\"" + WINDOWS + "\"");
    }

    @Test
    void surveySplitsFragmentsIntoRecordsAndWritesThemBackAsLines() {
        final String sample = SampleFixtures.eval("12_xml_fragments_events").getFirst();

        final RecordSplitter.Chunk chunk = RecordSplitter.split(sample);

        assertThat(chunk.format()).isEqualTo("xml fragments");
        assertThat(chunk.records()).hasSize(3);
        assertThat(chunk.parsed().getFirst()).containsEntry("EventID", "4624");
        assertThat((String) chunk.parsed().getFirst().get("TimeCreated@SystemTime")).startsWith("2026-10-01");
        // The cut-off last fragment is dropped.
        assertThat(RecordSplitter.split(sample.substring(0, sample.length() - 40), 5000, true).records()).hasSize(2);
    }

    @Test
    void theSurveyReadsTheDataLinesUnderTheirColumns() {
        final RecordSplitter.Chunk chunk = RecordSplitter.split(TestSplitterDryRun.BLUECOAT);

        assertThat(chunk.records()).hasSize(4).noneMatch(r -> r.startsWith("#"));
        assertThat(chunk.parsed().get(1)).containsEntry("s-action", "TCP_DENIED");
    }

    @Test
    void aRecordThatEndsAtAMarkedLineIsOneRecord() {
        assertThat(RecordSplitter.split(TestSplitterDryRun.CRI_TEXT).records()).hasSize(3);
    }

    @Test
    void recordsOfSeveralLinesAreGroupedFromWhereEachStarts() {
        final String log = "2026-10-01 10:00:00 ERROR Failed\n\tat a.b(C.java:1)\n2026-10-01 10:00:01 INFO Fine\n"
                           + "2026-10-01 10:00:02 WARN Odd\n\tat d.e(F.java:2)\n";

        final RecordSplitter.Chunk chunk = RecordSplitter.split(log);

        assertThat(chunk.format()).isEqualTo("multi-line text");
        assertThat(chunk.records()).containsExactly("2026-10-01 10:00:00 ERROR Failed\n\tat a.b(C.java:1)",
                "2026-10-01 10:00:01 INFO Fine", "2026-10-01 10:00:02 WARN Odd\n\tat d.e(F.java:2)");
    }

    @Test
    void quotedValuesHoldingNewLinesAreOneRecord() {
        final String tickets = "id,opened,summary\n1,2026-10-01T10:00:00Z,\"Printer jams\non floor 2\"\n"
                               + "2,2026-10-01T11:00:00Z,\"VPN drops\"\n3,2026-10-01T12:00:00Z,\"Screen\nflickers\"\n";

        final RecordSplitter.Chunk chunk = RecordSplitter.split(tickets);

        assertThat(chunk.header()).isEqualTo("id,opened,summary");
        assertThat(chunk.records()).hasSize(3);
        assertThat(chunk.parsed().getFirst()).containsEntry("summary", "Printer jams\non floor 2");
    }

    @Test
    void keyValueLinesAreReadAsTheirPairs() {
        final RecordSplitter.Chunk chunk = RecordSplitter.split(SampleFixtures.format("kv_quoted"));

        assertThat(chunk.format()).isEqualTo("key=value");
        assertThat(chunk.parsed().get(1)).containsEntry("user", "bob smith").containsEntry("action", "deny");
    }

    @Test
    void anXmlDocumentsRecordsKeepTheirRootApart() {
        final RecordSplitter.Chunk xml = RecordSplitter.split("<audit xmlns=\"urn:x\"><entry><op>read</op></entry>"
                                                              + "</audit>");
        assertThat(xml.records().getFirst()).doesNotContain("xmlns");
        assertThat(xml.root()).startsWith("<audit xmlns=\"urn:x\">");
    }

    static List<String> allSamples() {
        return TestSampleProfiler.allSamples();
    }

    @ParameterizedTest
    @MethodSource("allSamples")
    void everySampleSourcesSendSplitsIntoRecords(final String name) {
        final RecordSplitter.Chunk chunk = RecordSplitter.split(TestSampleProfiler.sample(name));

        assertThat(chunk.records()).as(name).isNotEmpty();
        assertThat(chunk.parsed()).as(name).hasSameSizeAs(chunk.records());
    }
}
