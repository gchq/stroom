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
import stroom.ai.impl.tools.sample.SyslogHeaders;
import stroom.ai.impl.tools.sample.ToolJson;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmAtomicValue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Drafts of a source's syslog lines inside a wrapper: a relay's syslog header, a collector's words, a shipper's
/// JSON holding the line (or what follows the host in an RFC 5424 header), and JSON encoded twice. Each wrapper is
/// told by its format alone, never by the product that sent it.
class TestDraftWrappedSyslog extends DraftFixtures {

    private static final String TIME_CREATED = "EventTime/TimeCreated";
    private static final String HOST_NAME = "EventSource/Device/HostName";
    // The source's own lines, each with the kind of event in its pairs.
    private static final List<String> SOURCE = List.of(
            "<38>Nov  9 09:59:58 bastion01 sshd[2211]: user=alice action=login src=192.0.2.5",
            "<38>Nov  9 09:59:59 bastion01 sshd[2215]: user=bob action=failed src=192.0.2.9",
            "<38>Nov  9 10:00:00 bastion02 sshd[2219]: user=carol action=login src=192.0.2.7",
            "<38>Nov  9 10:00:04 bastion02 sshd[2223]: user=dave action=logout src=192.0.2.8");
    // A shipper's JSON array of records, the RFC 5424 header read up to the host into keys of their own and the rest
    // kept as one value (structured data whose quoted values hold escaped brackets, then a byte order mark before
    // the message).
    private static final String SHIPPED = """
            [{"priority": "14", "severity": "6", "facility": "1", "version": "1",
              "timestamp": "2026-11-09T21:32:22+11:00", "hostname": "nas01",
              "body": "Connection - - [app@32473 event_id=\\"0x0004\\" kind=\\"Connection\\" \
            user=\\"EXAMPLE\\\\\\\\alice\\" event=\\"User [EXAMPLE\\\\\\\\alice\\\\] from [192.0.2.10\\\\] \
            opened [Projects\\\\].\\"][meta sequenceId=\\"1\\"] \
            \uFEFFUser [EXAMPLE\\\\alice] opened [Projects]."},
             {"priority": "12", "severity": "4", "facility": "1", "version": "1",
              "timestamp": "2026-11-09T21:33:40+11:00", "hostname": "nas01",
              "body": "Connection - - [app@32473 event_id=\\"0x0011\\" kind=\\"Connection\\" user=\\"bob\\" \
            event=\\"User [bob\\\\] from [198.51.100.4\\\\] failed to log in.\\"][meta sequenceId=\\"2\\"] \
            \uFEFFUser [bob] from [198.51.100.4] failed to log in."},
             {"priority": "22", "severity": "6", "facility": "2", "version": "1",
              "timestamp": "2026-11-09T21:34:05+11:00", "hostname": "nas01",
              "body": "mailer 11886 - [journald PRIORITY=\\"6\\" SYSLOG_IDENTIFIER=\\"mailer\\" _PID=\\"11886\\"] \
            delivered 3 messages"},
             {"priority": "14", "severity": "6", "facility": "1", "version": "1",
              "timestamp": "2026-11-09T21:35:12+11:00", "hostname": "nas01",
              "body": "FileService - - [app@32473 kind=\\"FileService\\" user=\\"carol\\" event=\\"delete\\" \
            path=\\"/share/a.txt\\"] \uFEFFEvent: delete, Path: /share/a.txt"},
             {"priority": "14", "severity": "6", "facility": "1", "version": "1",
              "timestamp": "2026-11-09T21:36:30+11:00", "hostname": "nas01",
              "body": "Connection - - [app@32473 event_id=\\"0x0004\\" kind=\\"Connection\\" user=\\"dave\\" \
            event=\\"User [dave\\\\] from [192.0.2.11\\\\] opened [Projects\\\\].\\"][meta sequenceId=\\"3\\"] \
            \uFEFFUser [dave] from [192.0.2.11] opened [Projects]."}]
            """;

    @Test
    void aRelayedLineIsTheSourcesOwnTimeAndHost() {
        final String relayed = SOURCE.stream()
                .map(line -> "<13>Nov  9 10:00:1" + SOURCE.indexOf(line) + " relay01 " + line)
                .collect(Collectors.joining("\n"));
        final Map<String, Object> draft = DraftMapping.draft(List.of(relayed), "Relayed", null, null, null, null,
                null);

        assertThat(common(draft).get(TIME_CREATED)).containsEntry("field", "time");
        assertThat(common(draft).get(HOST_NAME)).containsEntry("field", "host");
        // The relay's message is the source's whole line, read into its fields: not mapped.
        assertThat(String.valueOf(draft.get("unmapped_fields"))).doesNotContain("relay_message");
        assertThat(String.valueOf(mappingOf(draft))).doesNotContain("relay_message");
        assertThat(notes(draft)).anySatisfy(note -> assertThat(note).contains("wrapped")
                .contains("'relay_message'"));
    }

    @Test
    void aCollectorsTimeIsReadAfterTheSourcesOwn() {
        final String prefixed = SOURCE.stream()
                .map(line -> "2026-11-09T10:00:1" + SOURCE.indexOf(line) + ".250Z collector01 " + line)
                .collect(Collectors.joining("\n"));
        final Map<String, Object> draft = DraftMapping.draft(List.of(prefixed), "Collected", null, null, null, null,
                null);

        // The collector's time is finer than the source's, but when it passed the line on, not when it happened.
        assertThat(common(draft).get(TIME_CREATED)).containsEntry("field", "time");
        assertThat(String.valueOf(mappingOf(draft))).doesNotContain("relay_line");
    }

    @Test
    void theEndOfAnRfc5424HeaderAShipperKeptAsOneValueIsReadIntoItsParts() {
        final Map<String, Object> draft = DraftMapping.draft(List.of(SHIPPED), "Shipped", null, null, null, null,
                null);
        final Map<String, Object> mapping = mappingOf(draft);
        final List<Map<String, Object>> extracts = JsonValues.maps(mapping.get("extract"));

        assertThat(extracts).anySatisfy(extract -> assertThat(extract).containsEntry("field", "body")
                .containsEntry("regex", SyslogHeaders.TAIL_5424)
                .containsEntry("names", List.of("body.app", "body.pid", "body.msgid", "body.structured",
                        "body.message")));
        // The structured data's pairs are fields of their own, read from the part holding them.
        assertThat(extracts).anySatisfy(extract -> assertThat(extract).containsEntry("field", "body.structured")
                .containsEntry("names", List.of("event_id")));
        // The shipper's keys are the header's start: its time is the event's.
        assertThat(common(draft).get(TIME_CREATED)).containsEntry("field", "timestamp");
        assertThat(String.valueOf(draft.get("unmapped_fields"))).doesNotContain("'body'");
        assertThat(notes(draft)).anySatisfy(note -> assertThat(note).startsWith("'body' holds what follows the host"));

        final XsltGenerator.Generated generated = XsltGenerator.generate(TranslationMapping.fromJson(
                TestDraftMapping.decided(mapping)), DraftFixtures.SCHEMA_352, "3.5.2");
        assertThat(generated.problems()).isEmpty();
    }

    @Test
    void theXsltReadsTheTailsPartsAsTheDraftDoes() throws SaxonApiException {
        // The draft reads with Java's regex, the XSLT with XPath's analyze-string(): the same parts from both.
        final String body = "Connection - - [app@32473 user=\"EXAMPLE\\\\alice\" event=\"User [EXAMPLE\\\\alice\\] "
                            + "opened [Projects\\].\"][meta sequenceId=\"1\"] \uFEFFUser [EXAMPLE\\alice] opened "
                            + "[Projects].";
        final Matcher java = Pattern.compile(SyslogHeaders.TAIL_5424, Pattern.DOTALL).matcher(body);
        assertThat(java.find()).isTrue();
        final XPathCompiler xpath = new Processor(false).newXPathCompiler();
        xpath.declareVariable(new QName("text"));
        xpath.declareVariable(new QName("regex"));
        for (int nr = 1; nr <= SyslogHeaders.TAIL_5424_NAMES.size(); nr++) {
            final XPathSelector selector = xpath.compile("string((analyze-string($text, $regex, 's')//*:group[@nr = "
                                                         + nr + "])[1])").load();
            selector.setVariable(new QName("text"), new XdmAtomicValue(body));
            selector.setVariable(new QName("regex"), new XdmAtomicValue(SyslogHeaders.TAIL_5424));
            assertThat(selector.evaluateSingle().getStringValue()).as(SyslogHeaders.TAIL_5424_NAMES.get(nr - 1))
                    .isEqualTo(java.group(nr));
        }
        assertThat(java.group(5)).isEqualTo("User [EXAMPLE\\alice] opened [Projects].");
    }

    @Test
    void aSyslogLineInAJsonFieldIsReadIntoItsParts() {
        // A container runtime's JSON lines, each holding the source's syslog line.
        final String lines = SOURCE.stream()
                .map(line -> "{\"log\": \"" + line + "\", \"stream\": \"stdout\"}")
                .collect(Collectors.joining("\n"));
        final Map<String, Object> draft = DraftMapping.draft(List.of(lines), "Shipped lines", null, null, null, null,
                null);

        assertThat(JsonValues.maps(mappingOf(draft).get("extract"))).anySatisfy(extract -> assertThat(extract)
                .containsEntry("field", "log")
                .containsEntry("names", List.of("log.pri", "log.time", "log.host", "log.app", "log.pid",
                        "log.message")));
        assertThat(common(draft).get(TIME_CREATED)).containsEntry("field", "log.time");
        assertThat(common(draft).get(HOST_NAME)).containsEntry("field", "log.host");
        // The pairs in the line's message are read too.
        assertThat(JsonValues.maps(mappingOf(draft).get("extract"))).anySatisfy(extract -> assertThat(extract)
                .containsEntry("field", "log.message").containsEntry("names", List.of("user")));
    }

    @Test
    void xmlInASyslogLinesMessageIsReadInTheTranslation() {
        // Seen in testing: XML inside a syslog message, read by a regex per element in the Data Splitter. The message
        // is read out of the line first, so the XML is found in it, whole or after words of its own.
        for (final String prefix : List.of("", "EventLog: ")) {
            final String lines = SOURCE.stream()
                    .map(line -> line.substring(0, line.indexOf(": ") + 2) + prefix + "<Event><User>u"
                                 + SOURCE.indexOf(line) + "</User><Action>login</Action></Event>")
                    .map(line -> ToolJson.dumps(Map.of("log", line, "stream", "stdout")))
                    .collect(Collectors.joining("\n"));
            final Map<String, Object> draft = DraftMapping.draft(List.of(lines), "Shipped XML", null, null, null,
                    null, null);
            final Map<String, Object> mapping = mappingOf(draft);
            final String held = prefix.isEmpty()
                    ? "log.message"
                    : "log.message.xml";

            assertThat(mapping.get("xml_fields")).as(prefix).isEqualTo(List.of(held));
            if (!prefix.isEmpty()) {
                assertThat(JsonValues.maps(mapping.get("extract"))).anySatisfy(extract -> assertThat(extract)
                        .containsEntry("field", "log.message").containsEntry("names", List.of(held)));
            }
            // No pairs read from the XML, and its elements are fields of their own.
            assertThat(String.valueOf(mapping.get("extract"))).doesNotContain("Action=");
            assertThat(notes(draft)).anySatisfy(note -> assertThat(note).contains("XML")
                    .contains("'" + held + "/Event/User'").contains("not by regexes in the splitter"));
            final XsltGenerator.Generated generated = XsltGenerator.generate(TranslationMapping.fromJson(
                    TestDraftMapping.decided(mapping)), DraftFixtures.SCHEMA_352, "3.5.2");
            assertThat(generated.problems()).as(prefix).isEmpty();
        }
    }

    @Test
    void textThatIsNotASyslogHeaderIsLeftAlone() {
        final String lines = """
                {"msg": "User alice logged - in from the office", "level": "info"}
                {"msg": "User bob logged - out of the office", "level": "info"}
                {"msg": "User carol changed - her password today", "level": "warn"}
                """;
        final Map<String, Object> draft = DraftMapping.draft(List.of(lines), "Plain", null, null, null, null, null);

        assertThat(String.valueOf(mappingOf(draft).get("extract"))).doesNotContain("msg.app");
    }

    @Test
    void jsonEncodedTwiceInAFieldIsReadAsJson() {
        // Each detail a JSON string whose text is the JSON object.
        final String lines = Stream.of("login", "logout")
                .map(action -> ToolJson.dumps(Map.of("user", "u-" + action, "detail", ToolJson.dumps(ToolJson.dumps(
                        Map.of("action", action, "src", "192.0.2.5"))))))
                .collect(Collectors.joining("\n"));
        final Map<String, Object> draft = DraftMapping.draft(List.of(lines), "Encoded", null, null, null, null, null);

        assertThat(mappingOf(draft)).containsEntry("json_fields", List.of("detail"));
        assertThat(String.valueOf(draft.get("fields_seen"))).contains("detail.action", "detail.src");
    }

    @Test
    void jsonLinesEncodedTwiceAreReadAsRecords() {
        final String lines = """
                "{\\"user\\": \\"alice\\", \\"action\\": \\"login\\"}"
                "{\\"user\\": \\"bob\\", \\"action\\": \\"logout\\"}"
                "{\\"user\\": \\"carol\\", \\"action\\": \\"login\\"}"
                """;
        final Map<String, Object> draft = DraftMapping.draft(List.of(lines), "Encoded lines", null, null, null, null,
                null);

        // Read a line a record by a Data Splitter, the JSON each holds parsed by the mapping (json_fields decodes
        // it again).
        assertThat(mappingOf(draft)).containsEntry("input", "data_splitter")
                .containsEntry("json_fields", List.of("json"));
        assertThat(String.valueOf(draft.get("fields_seen"))).contains("json.user", "json.action");
    }

    @Test
    void relayedAndDirectLinesAreDraftedWithTheSourcesOwnTimeAndHost() {
        final String relayed = SOURCE.subList(0, 2).stream()
                .map(line -> "<13>Nov  9 10:00:1" + SOURCE.indexOf(line) + " relay01 " + line)
                .collect(Collectors.joining("\n"));
        final String text = relayed + "\n" + String.join("\n", SOURCE.subList(2, 4));
        final Map<String, Object> draft = DraftMapping.draft(List.of(text), "Some relayed", null, null, null, null,
                null);

        // One rule a kind of event, however its line came.
        assertThat(String.valueOf(((Map<?, ?>) draft.get("splitter")).get("kind"))).isEqualTo("regex");
        assertThat(rules(draft).keySet()).doesNotContain("syslog_relayed_login");
        assertThat(rules(draft).keySet().stream().filter(name -> name.contains("login"))).hasSize(1);
        // Every variant's time and host are the source's own header's.
        final String mapping = String.valueOf(mappingOf(draft));
        // The relay's own fields are kept as Data; its message, the source's whole line, isn't mapped.
        assertThat(mapping).doesNotContain("TimeCreated, field=relay_").doesNotContain("HostName, field=relay_")
                .doesNotContain("relay_message");
        assertThat(mapping).contains("path=EventTime/TimeCreated, field=time")
                .contains("path=EventSource/Device/HostName, field=host");
    }
}
