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

import stroom.ai.impl.tools.ToolRefusal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSampleProfiler {

    // A big single-document stream read only to its head (seen in production: a 54 MB <tweets> stream, read to 1 MB,
    // was profiled as CSV with no records, and the plan then asked for an XMLFragmentParser).
    static final String TWEETS_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<tweets>"
                                      + IntStream.range(0, 30).mapToObj(n -> "<tweet><data name=\"id\" value=\"" + n
                                                                             + "\" /><data name=\"text\" value=\"t "
                                                                             + n + "\" /><data name=\"user_mentions\">"
                                                                             + "<data><data name=\"screen_name\" "
                                                                             + "value=\"u" + n + "\" /></data></data>"
                                                                             + "</tweet>")
                                              .collect(Collectors.joining())
                                      + "<tweet><data name=\"id\" value=\"30\" /><data na";
    private static final String GOOD = "{\"time\":\"2026-10-11T08:00:0%dZ\",\"user\":\"u%d\",\"action\":\"login\"}\n";

    private static Map<String, Map<String, Object>> fields(final Map<String, Object> profile) {
        return SampleFixtures.fieldsByName(profile);
    }

    private static String good(final int from, final int to) {
        return IntStream.range(from, to).mapToObj(i -> String.format(GOOD, i, i)).collect(Collectors.joining());
    }

    @Test
    void csvWithHeader() {
        final Map<String, Object> result = SampleProfiler.profile("time,user,src_ip,result\n"
                                                                  + "2026-09-28T10:00:00Z,alice,10.0.0.1,ok\n"
                                                                  + "2026-09-28T10:05:00Z,bob,10.0.0.2,fail\n");

        assertThat(result).containsEntry("format", "delimited").containsEntry("delimiter", ",")
                .containsEntry("has_header", true);
        assertThat(fields(result).get("time")).containsEntry("type", "timestamp (yyyy-MM-dd'T'HH:mm:ssX)");
        assertThat(fields(result).get("src_ip")).containsEntry("type", "ip");
    }

    @Test
    void jsonLinesFlattenAndFlagEmbeddedJson() {
        final String lines = PyJson.dumps(SampleProfiler.map("ts", 1790600000L, "user", SampleProfiler.map("name",
                "a"), "body", "INFO x {\"type\":\"LOGIN\"}")) + "\n"
                             + PyJson.dumps(SampleProfiler.map("ts", 1790600001L, "user",
                SampleProfiler.map("name", "b")));

        final Map<String, Object> result = SampleProfiler.profile(lines);

        assertThat(result).containsEntry("format", "json lines");
        assertThat(fields(result).get("user.name")).containsEntry("fill_rate", 100L);
        assertThat(fields(result).get("body")).containsEntry("type", "embedded json after prefix")
                .containsEntry("fill_rate", 50L);
    }

    @Test
    void xmlRecords() {
        final Map<String, Object> result = SampleProfiler.profile(
                "<logs><entry><when>2026-09-28 10:00:00</when><who>alice</who></entry>"
                + "<entry><when>2026-09-28 10:01:00</when><who>bob</who></entry></logs>");

        assertThat(result).containsEntry("format", "xml").containsEntry("record_element", "entry")
                .containsEntry("records", 2);
        assertThat(fields(result).get("when")).containsEntry("type", "timestamp (yyyy-MM-dd HH:mm:ss)");
    }

    @Test
    void syslogAndKeyValue() {
        assertThat(SampleProfiler.profile("<34>1 2026-09-28T10:00:00Z host app 123 ID47 - message\n".repeat(3)))
                .containsEntry("format", "syslog rfc5424");
        assertThat(SampleProfiler.profile("<34>Sep 28 10:00:00 host sshd[1]: Accepted password\n".repeat(3)))
                .containsEntry("format", "syslog rfc3164");
        final Map<String, Object> kv = SampleProfiler.profile(
                "date=2026-09-28 time=10:00:00 srcip=10.0.0.1 action=accept\n".repeat(3));
        assertThat(fields(kv).get("srcip")).containsEntry("type", "ip");
    }

    @Test
    void valueTypes() {
        assertThat(SampleProfiler.valueType("1790600000")).isEqualTo("timestamp (epoch seconds)");
        assertThat(SampleProfiler.valueType("28/Sep/2026:10:00:00 +0000"))
                .isEqualTo("timestamp (dd/MMM/yyyy:HH:mm:ss Z)");
        assertThat(SampleProfiler.valueType("{\"a\": 1}")).isEqualTo("embedded json");
        assertThat(SampleProfiler.valueType("")).isEqualTo("empty");
    }

    @Test
    void aJsonArrayReadOnlyInPartIsStillAJsonArray() {
        // Seen: a 755 KB one-line array read to 20,000 characters profiled as key=value (its records' body field),
        // and create_pipeline refused the JSON parser the stream needed.
        final List<Object> records = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            records.add(SampleProfiler.map("timestamp", String.format("2026-10-02T09:00:%02dZ", i % 60),
                    "hostname", "gs-fw01", "body", "eventtime=" + i + " type=\"traffic\" action=\"deny\" srcip=10.0.0."
                                                   + (i % 250) + " dstport=443"));
        }
        final String text = PyJson.dumps(records);

        final Map<String, Object> cut = SampleProfiler.profile(text.substring(0, 20_000));

        assertThat(cut).containsEntry("format", "json array");
        assertThat((int) cut.get("records")).isBetween(1, 399);
        assertThat((String) cut.get("note")).contains("cut short");
        assertThat((String) cut.get("suggested_parser")).contains("JSONParser");
        assertThat(SampleProfiler.profile(text)).containsEntry("records", 400).doesNotContainKey("note");
        final String lines = records.subList(0, 5).stream().map(PyJson::dumps).collect(Collectors.joining("\n"));
        // The last line cut short.
        assertThat(SampleProfiler.profile(lines.substring(0, lines.length() - 10)))
                .containsEntry("format", "json lines");
    }

    @Test
    void textThatStartsAsJsonAndDoesNotParseIsSaidSo() {
        // Seen: a FortiOS JSON file retyped by the agent with \+ in it, profiled as key=value from its records'
        // body.
        final String retyped = "[{\"host\": \"gs-fw01\", \"body\": \"eventtime=1 tz=\\\"\\+1000\\\" srcip=10.0.0.1\"}]";

        assertThatThrownBy(() -> SampleProfiler.profile(retyped))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("starts as JSON but isn't valid JSON: Invalid \\escape");
        assertThat(SampleProfiler.profile(retyped.replace("\\+", "+"))).containsEntry("format", "json array");
        // Text that only starts with a bracket is not taken for JSON.
        assertThat(SampleProfiler.profile("[2026-10-01 08:00:00] INFO started\n[2026-10-01 08:00:01] INFO ok\n")
                .get("format")).isNotEqualTo("json array");
    }

    @Test
    void aDocumentReadOnlyToItsHeadIsXmlWithItsCompleteRecords() {
        final Map<String, Object> found = SampleProfiler.profile(TWEETS_HEAD);

        assertThat(found).containsEntry("format", "xml").containsEntry("root", "tweets")
                .containsEntry("record_element", "tweet").containsEntry("records", 30);
        assertThat((String) found.get("note")).contains("read only to here");
        assertThat(SampleProfiler.profile("<tweet><data name=\"id\" value=\"1\" /></tweet>\n"
                                          + "<tweet><data name=\"id\" value=\"2\" /></tweet>\n"))
                .containsEntry("format", "xml fragments");
        // A whole document, fragments, a syntax error: not a document cut short.
        assertThat(SampleProfiler.cutDocument("<r><a/></r>")).isNull();
        assertThat(SampleProfiler.cutDocument("<a/><a/>")).isNull();
        assertThat(SampleProfiler.cutDocument("<r><a/><b x=1>")).isNull();
    }

    @Test
    void jsonLinesWithDamagedLinesAreStillJsonLinesAndTheDamageIsSaid() {
        // Seen on the local Stroom: a line cut short stopped the JSONParser (fatal), losing every record after it;
        // and one damaged line made the profile call the file delimited, so the JSON template was refused.
        final String text = good(1, 6) + "{\"time\":\"2026-10-11T08:00:06Z\",\"user\":\"cut\n" + good(7, 10);

        final Map<String, Object> info = SampleProfiler.profile(text);

        assertThat(info).containsEntry("format", "json lines");
        assertThat(lines(info.get("malformed_lines"))).containsExactly(6);
        assertThat((String) info.get("note")).contains("stops the stream at the first such line")
                .contains("json_fields");
        assertThat(((Map<?, ?>) info.get("xslt_input")).get("mapping"))
                .isEqualTo(SampleProfiler.map("input", "data_splitter", "json_fields", List.of("json")));
        // A last line cut short by a read limit is not damage.
        assertThat(SampleProfiler.profile(good(1, 6) + "{\"time\":")).doesNotContainKey("malformed_lines");
    }

    private static List<Integer> lines(final Object malformed) {
        final List<Integer> lines = new ArrayList<>();
        ((List<?>) malformed).forEach(bad -> lines.add((Integer) ((Map<?, ?>) bad).get("line")));
        return lines;
    }

    @Test
    void csvAndKeyValueWithBadLinesAreRecognisedAndTheLinesNamed() {
        final String csv = "time,user,action,comment\n" + IntStream.range(1, 7)
                .mapToObj(i -> "2026-10-11T08:00:0" + i + "Z,u" + i + ",login,fine\n").collect(Collectors.joining())
                           + "2026-10-11T08:00:07Z,bob,login\n2026-10-11T08:00:08Z,carol,update,\"no closing quote\n";

        final Map<String, Object> info = SampleProfiler.profile(csv);

        assertThat(info).containsEntry("format", "delimited").containsEntry("delimiter", ",");
        final List<String> bad = new ArrayList<>();
        ((List<?>) info.get("malformed_lines")).forEach(line -> bad.add(((Map<?, ?>) line).get("line") + " "
                                                                       + ((Map<?, ?>) line).get("reason")));
        assertThat(bad).containsExactly("8 3 columns where the rest have 4", "9 a quote opened and not closed");
        final String kv = IntStream.range(0, 4).mapToObj(i -> "time=" + i + " user=u" + i + " action=login\n")
                                  .collect(Collectors.joining()) + "### garbage ###\n";
        final Map<String, Object> pairs = SampleProfiler.profile(kv);
        assertThat(pairs).containsEntry("format", "key=value");
        assertThat(((Map<?, ?>) ((List<?>) pairs.get("malformed_lines")).getFirst()).get("reason"))
                .isEqualTo("holds no key=value pairs");
    }

    @Test
    void theColumnsComeFromTheDirectiveInEffectAndDirectivesRideAlong() {
        final Map<String, Object> info = SampleProfiler.profile(TestSplitterDryRun.BLUECOAT);

        assertThat(info).containsEntry("format", "delimited").containsEntry("header_directive", "#Fields:")
                .containsEntry("delimiter", " ").doesNotContainKey("_records");
        assertThat(((List<?>) info.get("columns")).subList(0, 3)).isEqualTo(List.of("date", "time", "time-taken"));
        assertThat((List<Object>) info.get("directives")).contains("#Remark:");
    }

    @Test
    void zeekNamesItsColumnsWithATabSeparatedFieldsDirective() {
        final Map<String, Object> info = SampleProfiler.profile(TestSplitterDryRun.ZEEK);

        assertThat(info).containsEntry("delimiter", "\t").containsEntry("header_directive", "#fields");
        assertThat((List<Object>) info.get("columns")).hasSize(6);
    }

    @Test
    void aPlainCsvIsNotTakenForADirectiveLog() {
        assertThat(SampleProfiler.profile("user,action,host\nalice,login,ws01\nbob,logout,ws02\n"))
                .doesNotContainKey("header_directive");
        // A comment line that names nothing: still not one.
        assertThat(SampleProfiler.profile("# exported 2026-10-11\na b c\n1 2 3\n4 5 6\n"))
                .doesNotContainKey("header_directive");
    }

    @Test
    void profileRecognisesFragmentsAndSaysHowTheyAreRead() {
        final Map<String, Object> result = SampleProfiler.profile(SampleFixtures.eval("12_xml_fragments_events")
                .getFirst());

        assertThat(result).containsEntry("format", "xml fragments").containsEntry("record_element", "Event")
                .containsEntry("records", 3).containsEntry("namespace", null);
        assertThat((String) fields(result).get("TimeCreated@SystemTime").get("type")).startsWith("timestamp");
        assertThat(fields(result).get("Computer")).containsEntry("fill_rate", 100L);
        final Map<?, ?> converter = (Map<?, ?>) result.get("text_converter");
        assertThat(converter.get("type")).isEqualTo("XML_FRAGMENT");
        assertThat((String) converter.get("code")).contains("&fragment;");
        assertThat(((Map<?, ?>) result.get("xslt_input")).get("mapping")).isEqualTo(SampleProfiler.map(
                "input", "xml_fragments", "xml_namespace", "records:2", "record", "Event"));
        assertThat((String) result.get("parser")).contains("replace_parser");
        // One element is a document; a document stays a document.
        assertThat(SampleProfiler.profile("<logs><entry><who>a</who></entry></logs>")).containsEntry("format", "xml");
        // A source's own <Event> (Windows' System, EventData) is not event-logging.
        assertThat(result).doesNotContainKey("event_logging");
    }

    @Test
    void eventLoggingFragmentsTakeAnEventsWrapper() {
        final String events = ("<Event><EventTime><TimeCreated>2026-10-01T10:00:00.000Z</TimeCreated></EventTime>"
                               + "<EventSource><System><Name>App</Name><Environment>Prod</Environment></System>"
                               + "</EventSource><EventDetail><TypeId>1</TypeId></EventDetail></Event>").repeat(2);

        final Map<String, Object> profiled = SampleProfiler.profile(events);

        assertThat(profiled).containsEntry("event_logging", true);
        assertThat(((Map<?, ?>) profiled.get("text_converter")).get("root")).isEqualTo("Events");
        assertThat(((Map<?, ?>) profiled.get("xslt_input")).get("namespace")).isEqualTo("event-logging:3");
    }

    @Test
    void epochSecondsWithAFractionAreATimeWhateverTheSource() {
        // Seen with a Bluecoat log: 1791712800.123 was a plain decimal, so the time was mapped by hand without its
        // milliseconds. Text (a W3C column) or a JSON number alike.
        final Map<String, Object> profile = SampleProfiler.profile("{\"ts\": 1791712800.123, \"n\": 42}\n"
                                                                   + "{\"ts\": 1791712801, \"n\": 7}\n");
        final Map<String, Object> types = new LinkedHashMap<>();
        fields(profile).forEach((name, field) -> types.put(name, field.get("type")));

        assertThat(types).isEqualTo(SampleProfiler.map("ts", "timestamp (epoch seconds)", "n", "number"));
    }

    @Test
    void profileInfersUnknownTimestampShapesAndComparesFiles() {
        final Map<String, Object> one = SampleProfiler.profile(
                "when,who\n01 Oct 2026 10:00:00 GMT,alice\n02 Oct 2026 11:30:00 GMT,bob\n");
        assertThat(fields(one).get("when")).containsEntry("type", "timestamp (dd MMM yyyy HH:mm:ss z)");
        final List<String> samples = SampleFixtures.eval("14_csv_two_files_variants");
        final Map<String, String> files = new LinkedHashMap<>();
        files.put("old.csv", samples.get(0));
        files.put("new.csv", samples.get(1));

        final Map<String, Object> both = SampleProfiler.profileMany(files, SampleProfiler.MAX_RECORDS);

        assertThat(both).containsEntry("format", "delimited").containsEntry("records", 6L);
        final Map<String, Map<String, Object>> fields = fields(both);
        assertThat(fields.get("device")).containsEntry("only_in", List.of("new.csv"));
        assertThat(fields.get("time")).containsEntry("only_in", List.of("old.csv"));
        assertThat((List<?>) both.get("differences")).anyMatch(d -> ((String) d)
                .contains("'device' is only in ['new.csv']"));
        assertThat((String) both.get("hint")).contains("any_of");
    }

    @Test
    void aHeaderOfNamesOverTextColumnsIsAHeader() {
        // Seen: user,name,department,site over people's names, read as a record, its columns col1 ...
        assertThat(SampleProfiler.profile("user,name,department,site\nalice,Alice Anderson,Operations,HQ\n"
                                          + "bob,Bob Brown,Finance,HQ\n").get("columns"))
                .isEqualTo(List.of("user", "name", "department", "site"));
        assertThat(SampleProfiler.profile(SampleFixtures.format("csv_noheader"))).containsEntry("has_header", false);
    }

    @Test
    void profileSaysHowJsonReachesTheXslt() {
        final Map<String, Object> lines = SampleProfiler.profile("{\"a\": 1}\n{\"a\": 2}\n");

        assertThat((String) lines.get("text_converter")).startsWith("none");
        assertThat(lines.get("parser_properties")).isEqualTo(Map.of("jsonParser.addRootObject", true));
        final Map<?, ?> input = (Map<?, ?>) lines.get("xslt_input");
        assertThat(input.get("root")).isEqualTo("/map");
        assertThat(((Map<?, ?>) input.get("mapping")).get("json_layout")).isEqualTo("lines");
        final Map<String, Object> array = SampleProfiler.profile("[{\"a\": 1}, {\"a\": 2}]");
        assertThat(array.get("parser_properties")).isEqualTo(Map.of("jsonParser.addRootObject", false));
        assertThat(((Map<?, ?>) array.get("xslt_input")).get("root")).isEqualTo("/array");
    }

    @Test
    void oneObjectWrappingTheRecordsIsReadByItsArray() {
        // Seen: a CloudTrail file refused as "starts as JSON but isn't valid JSON", read a line at a time.
        final String trail = "{\"Records\": [{\"eventName\": \"ConsoleLogin\", \"eventTime\": "
                             + "\"2026-10-01T10:00:00Z\"}, {\"eventName\": \"GetObject\", \"eventTime\": "
                             + "\"2026-10-01T10:01:00Z\"}]}";

        final Map<String, Object> whole = SampleProfiler.profile(trail);
        final Map<String, Object> cut = SampleProfiler.profile(trail.substring(0, trail.length() - 30));

        assertThat(whole).containsEntry("format", "json object").containsEntry("records_key", "Records")
                .containsEntry("records", 2);
        assertThat(((Map<?, ?>) ((Map<?, ?>) whole.get("xslt_input")).get("mapping")).get("for_each"))
                .isEqualTo("Records");
        assertThat(cut).containsEntry("format", "json object").containsEntry("records", 1);
    }

    @Test
    void recordsOfSeveralLinesStartAtTheirTime() {
        final String log = "2026-10-01 10:00:00 ERROR Failed\njava.lang.IllegalStateException: x\n\tat a.b(C.java:1)\n"
                           + "2026-10-01 10:00:01 INFO Fine\n2026-10-01 10:00:02 WARN Odd\n\tat d.e(F.java:2)\n";

        final Map<String, Object> info = SampleProfiler.profile(log);

        assertThat(info).containsEntry("format", "multi-line text").containsEntry("records", 3);
        assertThat(((List<?>) info.get("examples")).getFirst())
                .isEqualTo("2026-10-01 10:00:00 ERROR Failed\njava.lang.IllegalStateException: x\n\tat a.b(C.java:1)");
    }

    @Test
    void quotedValuesHoldingNewLinesAreReadAsCsvOverTheWholeText() {
        final String tickets = "id,opened,summary\n1,2026-10-01T10:00:00Z,\"Printer jams\non floor 2\"\n"
                               + "2,2026-10-01T11:00:00Z,\"VPN drops\"\n3,2026-10-01T12:00:00Z,\"Screen\nflickers\"\n";

        final Map<String, Object> info = SampleProfiler.profile(tickets);

        assertThat(info).containsEntry("format", "delimited").containsEntry("quoted_newlines", true)
                .containsEntry("has_header", true).containsEntry("records", 3).doesNotContainKey("_records");
        assertThat(info.get("columns")).isEqualTo(List.of("id", "opened", "summary"));
    }

    @Test
    void aSeparatorTheSnifferDoesNotTryIsFound() {
        // Seen unknown text: '... ~ alice ~ LOGIN ~ OK ~ ...'.
        final String text = "2026-10-01T10:00:00Z ~ alice ~ LOGIN ~ OK ~ ws01\n"
                            + "2026-10-01T10:05:00Z ~ bob ~ LOGOUT ~ OK ~ ws02\n";

        final Map<String, Object> info = SampleProfiler.profile(text);

        assertThat(info).containsEntry("format", "delimited").containsEntry("delimiter", " ~ ")
                .containsEntry("has_header", false);
        assertThat((String) info.get("suggested_parser")).endsWith("columns separated by ' ~ '");
    }

    @Test
    void kubernetesCriLinesAreRecordsEndingAtTheirFullLine() {
        final Map<String, Object> info = SampleProfiler.profile(TestSplitterDryRun.CRI_TEXT);

        assertThat(info).containsEntry("format", "cri").containsEntry("records", 3)
                .containsEntry("record_end", SplitterDryRun.CRI_END);
    }

    static List<String> allSamples() {
        final List<String> names = new ArrayList<>();
        SampleFixtures.formatNames().forEach(name -> names.add("format:" + name));
        SampleFixtures.evalNames().forEach(name -> {
            for (int i = 0; i < SampleFixtures.eval(name).size(); i++) {
                names.add("eval:" + name + ":" + i);
            }
        });
        return names;
    }

    static String sample(final String name) {
        final String[] parts = name.split(":");
        return "format".equals(parts[0])
                ? SampleFixtures.format(parts[1])
                : SampleFixtures.eval(parts[1]).get(Integer.parseInt(parts[2]));
    }

    @ParameterizedTest
    @MethodSource("allSamples")
    void everySampleSourcesSendIsProfiledAsAKnownFormat(final String name) {
        final Map<String, Object> profile = SampleProfiler.profile(sample(name));

        assertThat(profile.get("format")).as(name).isNotEqualTo("unknown text");
        assertThat(profile).as(name).doesNotContainKey("_records");
    }
}
