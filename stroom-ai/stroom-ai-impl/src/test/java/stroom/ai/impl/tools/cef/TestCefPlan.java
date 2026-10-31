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

package stroom.ai.impl.tools.cef;

import stroom.ai.impl.tools.fielddoc.Descriptions;
import stroom.ai.impl.tools.fielddoc.FieldDocFixtures;
import stroom.ai.impl.tools.fielddoc.FieldDocs;
import stroom.ai.impl.tools.generation.MappingValidationException;
import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.generation.XmlParser;
import stroom.ai.impl.tools.generation.XmlSerialiser;
import stroom.ai.impl.tools.generation.XsltStyle;
import stroom.ai.impl.tools.validation.XmlTrees;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The CEF output for ArcSight: the plan drafted from Events, the XSLT it generates, reading and reviewing CEF lines,
/// and what the standing instructions say.
class TestCefPlan {

    /// The coverage case's Events: one View.
    static final String COVERAGE_EVENTS = """
            <Events xmlns="event-logging:3">
            <Event><EventSource><User><Id>carol</Id></User></EventSource><EventDetail><TypeId>View</TypeId>
             <View><Resource><Type>Secret</Type><Name>Payroll</Name></Resource></View></EventDetail></Event>
            </Events>""";

    private static final String KAFKA_XSD = """
            <xs:schema xmlns:krec="kafka-records:1" xmlns:xs="http://www.w3.org/2001/XMLSchema" \
            elementFormDefault="qualified"
              targetNamespace="kafka-records:1">
              <xs:element name="kafkaRecords"><xs:complexType><xs:sequence><xs:element ref="krec:kafkaRecord"/>\
            </xs:sequence></xs:complexType></xs:element>
              <xs:element name="kafkaRecord"><xs:complexType><xs:sequence>
                <xs:element maxOccurs="unbounded" minOccurs="0" name="header"><xs:complexType><xs:sequence>\
            <xs:element name="key" type="xs:string"/>
                  <xs:element minOccurs="0" name="value" type="xs:anyType"/></xs:sequence></xs:complexType>\
            </xs:element>
                <xs:choice><xs:sequence><xs:element name="key" type="xs:anyType"/><xs:element minOccurs="0" \
            name="value" type="xs:anyType"/></xs:sequence>
                  <xs:sequence><xs:element name="value" type="xs:anyType"/></xs:sequence></xs:choice></xs:sequence>
                <xs:attribute name="topic" type="xs:string" use="required"/><xs:attribute name="partition" \
            type="xs:int"/>
                <xs:attribute name="timestamp"><xs:simpleType><xs:restriction base="xs:dateTime">
                  <xs:pattern value="[\\d]{4}-[\\d]{2}-[\\d]{2}T[\\d]{2}:[\\d]{2}:[\\d]{2}.[\\d]{3}Z"/>\
            </xs:restriction></xs:simpleType></xs:attribute>
              </xs:complexType></xs:element>
            </xs:schema>""";

    // Asked by the user: are spaces and equals signs escaped? Each value goes through the XSLT's own escaping
    // functions (read from an input document, so no string escaping in the way) and is read back by the parser, a
    // key after it.
    private static final Map<String, String> ESCAPED = escapedCases();

    @Test
    void aDraftPutsValuesInArcsightsStandardKeysFirstThenLabelledCustomSlots() {
        final CefPlan plan = CefFixtures.drafted().plan();
        assertThat(plan.problems()).isEmpty();
        final Map<String, Map<String, CefField>> fields = CefFixtures.byKind(plan);
        assertThat(fields.get("common").get("EventTime/TimeCreated").key()).isEqualTo("rt");
        assertThat(fields.get("common").get("EventTime/TimeCreated").transform()).isEqualTo("epoch_ms");
        assertThat(fields.get("common").get("EventSource/User/Id").key()).isEqualTo("suser");
        assertThat(fields.get("Authenticate").get("EventDetail/Authenticate/User/Id").key()).isEqualTo("duser");
        assertThat(fields.get("Authenticate").get("EventDetail/Authenticate/Outcome/Success").transform())
                .isEqualTo("outcome");
        final CefField session = fields.get("Authenticate").get(
                "EventDetail/Authenticate/Data[@Name='session_id']/@Value");
        // A number: a number slot.
        assertThat(session.key()).isEqualTo("cn1");
        assertThat(session.label()).isEqualTo("session_id");
        final CefField environment = fields.get("common").get("EventSource/System/Environment");
        assertThat(environment.key()).startsWith("cs");
        assertThat(environment.label()).isEqualTo("System Environment");
        // The header carries the system, the TypeId and the Description; they're not repeated as fields.
        assertThat(plan.product().source()).isEqualTo("EventSource/System/Name");
        assertThat(fields.get("common")).doesNotContainKey("EventDetail/TypeId");
        // Network's source is its own src (Client's is common, but no Network event has one); its action is Deny.
        assertThat(fields.get("Network").get("EventDetail/Network/*/Source/Device/IPAddress").key()).isEqualTo("src");
        assertThat(fields.get("Network").get("local-name(EventDetail/Network/*[1])").key()).isEqualTo("act");
        assertThat(fields.get("View").get("'View'").key()).isEqualTo("act");
    }

    @Test
    void aRecordKeyIsAPathFromTheEventWithNoPrefix() {
        // Seen: //d:EventSource/d:SessionId, its prefix declared nowhere, failing only once Stroom ran the XSLT.
        assertThat(CefPlan.kafkaKeyProblems("//d:EventSource/d:SessionId")).hasSize(2)
                .anySatisfy(problem -> assertThat(problem).contains("no namespace prefix ('d:')"))
                .anySatisfy(problem -> assertThat(problem).contains("a path from the Event, not from the document"));
        assertThat(CefPlan.kafkaKeyProblems("EventSource/SessionId")).isEmpty();
        // Functions, axes and the prefixes the XSLT declares are not prefixes to refuse.
        assertThat(CefPlan.kafkaKeyProblems("concat(EventSource/User/Id, '-', xs:string(EventSource/SessionId))"))
                .isEmpty();
        assertThat(CefPlan.kafkaKeyProblems("child::EventSource/SessionId")).isEmpty();
        assertThat(CefPlan.kafkaKeyProblems(null)).isEmpty();
    }

    @Test
    void aValueThatDoesNotFitItsStandardKeyGoesToASlot() {
        // One Client "IPAddress" is a host name: src takes IPv4 addresses only.
        final CefDraft.Drafted drafted = CefFixtures.drafted();
        assertThat(CefFixtures.byKind(drafted.plan()).get("common").get("EventSource/Client/IPAddress").key())
                .startsWith("cs");
        assertThat(drafted.notes()).anyMatch(n -> n.contains("don't fit src"));
    }

    @Test
    void withoutKeysOutsideTheDictionaryValuesPastTheSlotsAreNotSent() {
        final StringBuilder many = new StringBuilder();
        for (int n = 0; n < 12; n++) {
            many.append("<Data Name=\"d").append(n).append("\" Value=\"value ").append(n).append("\"/>");
        }
        final List<XmlElement> events = CefEvents.eventsOf(CefFixtures.EVENTS.replace("<Data Name=\"session_id\"",
                many + "<Data Name=\"session_id\""));
        final CefPlan closed = CefDraft.draft(events, false, List.of(), null, CefPlan.KAFKA, "t", null).plan();
        assertThat(closed.notSent()).isNotEmpty().allMatch(n -> n.get("why").contains("custom slots are all used"));
        final CefPlan opened = CefDraft.draft(events, true, List.of(), null, CefPlan.KAFKA, "t", null).plan();
        assertThat(opened.notSent()).isEmpty();
        assertThat(opened.events().get("Authenticate")).anyMatch(f -> !CefKeys.KEYS.containsKey(f.key()));
        assertThat(opened.problems()).isEmpty();
    }

    @Test
    void theDeviceVersionIsFromTheEventsSystemVersionHoweverFewHaveIt() {
        // Asked for by the user: a feed can mix systems, so an Event with a version sends it; seen: no sample Event
        // had one, and the plan's empty constant would have stayed empty for any that did.
        final String versions = "<Version>11.4</Version>";
        final String none = CefFixtures.EVENTS.replace(versions, "");
        final int first = CefFixtures.EVENTS.indexOf(versions) + versions.length();
        final String one = CefFixtures.EVENTS.substring(0, first)
                           + CefFixtures.EVENTS.substring(first).replace(versions, "");

        for (final String events : List.of(none, one)) {
            final CefPlan plan = CefDraft.draft(CefEvents.eventsOf(events), false, List.of(), null, CefPlan.KAFKA,
                    "t", null).plan();
            assertThat(plan.header("version")).isEqualTo(CefValue.from("EventSource/System/Version"));
            assertThat(plan.problems()).isEmpty();
        }
    }

    @Test
    void overridesAndStandingInstructionsTakePrecedence() {
        final CefPlan plan = CefFixtures.drafted(false, FieldDocFixtures.json("""
                [{"path": "EventDetail/Authenticate/User/Id", "key": "deviceCustomString6", "label": "Target user"},
                 {"path": "EventDetail/Authenticate/Data[@Name='note']/@Value", "drop": true}]"""),
                CefPlan.KAFKA).plan();
        final CefField target = CefFixtures.byKind(plan).get("Authenticate").get("EventDetail/Authenticate/User/Id");
        assertThat(target.key()).isEqualTo("cs6");
        assertThat(target.label()).isEqualTo("Target user");
        assertThat(plan.notSent()).contains(Map.of("path", "EventDetail/Authenticate/Data[@Name='note']/@Value",
                "event_type", "Authenticate", "why", "left out as instructed"));
        final Map<String, Object> said = CefInstructions.fromInstructions(List.of(
                "## CEF output\nCustom CEF keys are not allowed.\nKafka topic: arcsight-secretserver\n"
                + "- EventDetail/Authorise/User/Name -> deviceCustomString6 (label: 'Authorised user')\n"
                + "- EventSource/System/Environment -> cs1\nPipeline template: CEF to ArcSight\n"));
        assertThat(said.get(CefInstructions.CUSTOM_KEYS)).isEqualTo(false);
        assertThat(said.get(CefInstructions.TOPIC)).isEqualTo("arcsight-secretserver");
        assertThat(said.get(CefInstructions.TEMPLATE)).isEqualTo("CEF to ArcSight");
        @SuppressWarnings("unchecked") final List<CefOverride> overrides =
                (List<CefOverride>) said.get(CefInstructions.OVERRIDES);
        assertThat(overrides).containsExactly(
                CefOverride.to("EventDetail/Authorise/User/Name", "cs6", "Authorised user"),
                CefOverride.to("EventSource/System/Environment", "cs1", null));
    }

    @Test
    void theKafkaXsltWritesOneValidRecordAnEventItsValueTheEscapedCefLine() throws Exception {
        final CefPlan plan = CefFixtures.drafted().plan();
        final String out = CefFixtures.run(plan.xslt(), CefFixtures.EVENTS);
        final XmlElement records = XmlParser.parse(out);
        final List<String> lines = CefLines.linesIn(out);
        assertThat(lines).hasSize(3);
        assertThat(records.childElements().getFirst().get("topic")).isEqualTo("arcsight-cef");
        assertThat(records.childElements().getFirst().get("timestamp")).isEqualTo("2026-09-27T23:48:10.000Z");
        final CefLines.Parsed first = CefLines.parse(lines.getFirst());
        assertThat(first.header()).containsExactly("CEF:0", "Delinea", "SecretServer", "11.4", "User-Login",
                "User logged in", "3");
        final Map<String, String> pairs = pairs(first);
        assertThat(pairs.get("outcome")).isEqualTo("failure");
        assertThat(pairs.get("rt")).isEqualTo("1790552890000");
        // = and \ escaped, the line break as \n, | as it is.
        assertThat(lines.getFirst()).contains("a\\=b|c\\\\d\\nsecond line");
        assertThat(first.extension().stream()
                .filter(p -> p.getKey().startsWith("cs") && p.getValue().contains("b|c"))
                .map(Map.Entry::getValue)
                .findFirst()).contains("a=b|c\\d\nsecond line");
        final CefLines.Parsed second = CefLines.parse(lines.get(1));
        assertThat(second.header().subList(4, 6)).containsExactly("Secret-View", "View");
        assertThat(pairs(second).values()).contains("Firewall | Edge");
        // kafka-records:1 takes one record a document: with one Event a record (the split), each is valid.
        final Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new StreamSource(new StringReader(KAFKA_XSD)));
        for (final XmlElement event : CefFixtures.events()) {
            final String single = "<Events xmlns=\"event-logging:3\">" + XmlSerialiser.toXmlWithoutTail(event)
                                  + "</Events>";
            assertThat(validates(schema, CefFixtures.run(plan.xslt(), single))).isTrue();
        }
        assertThat(CefLines.review(lines, CefFixtures.events(), false).get("problems")).isEqualTo(List.of());
    }

    @Test
    void theTextXsltWritesALineAnEvent() {
        final CefPlan plan = CefFixtures.drafted(false, List.of(), CefPlan.TEXT).plan();
        final List<String> lines = List.of(CefFixtures.run(plan.xslt(), CefFixtures.EVENTS).strip().split("\n"));
        assertThat(lines).hasSize(3).allMatch(line -> line.startsWith("CEF:0|Delinea|SecretServer|"));
    }

    @Test
    void aReviewFindsWhatArcsightWouldNotTakeAndWhatEachEventLeavesOut() {
        final List<XmlElement> events = CefFixtures.events().subList(0, 1);
        final String line = "CEF:0|Delinea|SecretServer|11.4|User-Login|User logged in|3|src=not-an-ip cs1=Prod "
                            + "myField=x suser=priya.patel rt=1790552890000";
        final Map<String, Object> reviewed = CefLines.review(List.of(line), events, false);
        @SuppressWarnings("unchecked") final String said = String.join(" ", (List<String>) reviewed.get("problems"));
        assertThat(said).contains("cs1 has no cs1Label", "src holds 'not-an-ip'",
                "key 'myField' is not in ArcSight's CEF dictionary (keys outside it aren't allowed");
        @SuppressWarnings("unchecked") final Map<String, List<Map<String, String>>> mapping =
                (Map<String, List<Map<String, String>>>) reviewed.get("implied_mapping");
        final Map<String, String> implied = mapping.get("Authenticate").stream()
                .collect(Collectors.toMap(m -> m.get("key"), m -> m.get("path")));
        assertThat(implied.get("suser")).isIn("EventSource/User/Id", "EventDetail/Authenticate/User/Id");
        assertThat(implied.get("rt")).isEqualTo("EventTime/TimeCreated");
        assertThat(implied.get("cs1")).isEqualTo("EventSource/System/Environment");
        @SuppressWarnings("unchecked") final List<Map<String, String>> notSent =
                (List<Map<String, String>>) reviewed.get("not_sent");
        assertThat(notSent).anyMatch(n -> n.get("path").equals(
                "EventDetail/Authenticate/Data[@Name='session_id']/@Value"));
        @SuppressWarnings("unchecked") final List<String> broken = (List<String>) CefLines.review(List.of(
                "CEF:0|Delinea|SecretServer|only five|3|x=1"), List.of(), null).get("problems");
        assertThat(broken).anyMatch(p -> p.contains("a header of"));
    }

    @Test
    void theDocumentationNamesEachKeyItsArcsightFieldAndItsLabel() {
        final CefPlan plan = CefFixtures.drafted().plan();
        final List<String> lines = CefLines.linesIn(CefFixtures.run(plan.xslt(), CefFixtures.EVENTS));
        final Map<CefDocs.KindKey, String> examples = CefLines.examplesFrom(plan, lines, CefFixtures.events());
        final String text = plan.markdown(examples, FieldDocs.BY_EVENT_TYPE, null);
        assertThat(text).contains("### Header", "| 2 | Device Vendor | `EventSource/System/Organisation` | Delinea |",
                "| `EventDetail/Authenticate/User/Id` | duser | destinationUserName |",
                "labelled 'session_id' (cn1Label = deviceCustomNumber1Label)", "| 6857 |",
                "| the event's kind (View) | act | deviceAction |", "on topic `arcsight-cef`, one Event a record");
        // By event type (the default, asked for by the user): the keys every event gets, then a table per kind.
        assertThat(text).contains("### Every event", "### View events").doesNotContain("| Event type |");
        // Compact (an AGENTS doc's Documentation section, or the user): one table with an Event type column.
        final String compact = plan.markdown(examples, FieldDocs.COMPACT, null);
        assertThat(compact).doesNotContain("### Every event", "### View events")
                .contains("| View | the event's kind (View) | act | deviceAction |", "| every event | ");
    }

    @Test
    void withTheSampleEventsEachSectionSaysItsTypeIdsExamplesAndFieldsNotIncluded() {
        // Asked for by the user, for a pipeline's administrators: an example for every field, the TypeIds of each
        // event type, and the fields not sent at the bottom of each, with a sample of each.
        final CefPlan plan = CefFixtures.drafted().plan().withEvents(Map.of("Authenticate", List.of(
                CefField.of("EventDetail/Authenticate/User/Id", "duser"))));
        final List<Map<String, String>> notSent = new ArrayList<>(plan.notSent());
        notSent.add(Map.of("path", "EventDetail/Authenticate/Data[@Name='session_id']/@Value", "event_type",
                "Authenticate", "why", "left out by the user"));
        final CefPlan withNotSent = plan.withNotSent(notSent);
        final List<XmlElement> events = CefFixtures.events();

        final String text = withNotSent.markdown(Map.of(), events, FieldDocs.BY_EVENT_TYPE, null);

        final String logons = text.split("### Authenticate events")[1].split("\n### ")[0];
        assertThat(logons).contains("TypeIds: `User-Login` (", "| `EventDetail/Authenticate/User/Id` | duser |");
        // An example from the Events, though no line gave one.
        assertThat(logons.lines().filter(line -> line.startsWith("| `EventDetail/Authenticate/User/Id`")))
                .singleElement().asString().doesNotEndWith("|  |");
        assertThat(logons).contains("#### Fields not included",
                "| `EventDetail/Authenticate/Data[@Name='session_id']/@Value` | left out by the user | 6857 |");
        // Placed in its section: not again at the end.
        assertThat(text.split("### Not sent", -1).length == 1
                   || !text.split("### Not sent")[1].contains("session_id")).isTrue();
        // Without the Events: the Not sent section alone.
        assertThat(withNotSent.markdown(Map.of(), FieldDocs.BY_EVENT_TYPE, null))
                .doesNotContain("#### Fields not included", "TypeIds:").contains("### Not sent");
    }

    @Test
    void aPlanWithKeysOutsideTheDictionaryOrUnlabelledSlotsHasProblems() {
        final CefPlan broken = CefFixtures.drafted().plan().withTopic(null).withEvents(Map.of("View", List.of(
                CefField.of("EventDetail/View/Resource/Name", "myField"),
                CefField.of("EventDetail/View/Resource/Id", "cs2"))));
        final String said = String.join(" ", broken.problems());
        assertThat(said).contains("needs topic", "'myField' is not a key in ArcSight's CEF dictionary",
                "cs2: a custom slot needs a label");
    }

    @Test
    void aTextPipelinesLinesLoseStroomsXmlDeclarationAndAnUnescapedPipeIsNamed() {
        // Seen stepping a CEF pipeline written by hand: its text output came with an XML declaration before it, and
        // its name's | (unescaped) was reported as an invalid severity.
        final String out = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>CEF:0|D|S|1|User-Login|User logged in|3|suser=a\n"
                           + "CEF:0|D|S|1|Secret-View|Secret viewed: a=b|c|3|suser=b\n"
                           + "<134>Oct 10 09:00:00 fw CEF:0|D|S|1|X|Y|3|suser=c\n";
        final List<String> lines = CefLines.linesIn(out);
        // The syslog prefix is kept.
        assertThat(lines.get(0)).startsWith("CEF:0|D|S|1|User-Login");
        assertThat(lines.get(2)).startsWith("<134>Oct 10");
        @SuppressWarnings("unchecked") final List<String> problems = (List<String>) CefLines.review(lines, List.of(),
                false).get("problems");
        assertThat(problems).hasSize(1);
        assertThat(problems.getFirst()).startsWith("an unescaped | in the header's name or class id (in 'Secret "
                                                   + "viewed: a=b|c')");
    }

    @ParameterizedTest
    @ValueSource(strings = {"spaces and an equals sign", "ending with a backslash", "a backslash then an equals sign",
            "line breaks", "what looks like another key", "a pipe, needing no escape here"})
    void extensionValuesAreEscapedSoTheyReadBackWholeAndTheNextKeyIsFound(final String name) {
        final String value = ESCAPED.get(name);
        final String written = escaped(Map.of(name, value)).values().get(name);
        // One line: its breaks written as \n.
        assertThat(written).doesNotContain("\n", "\r");
        final Map<String, String> back = pairs(CefLines.parse("CEF:0|V|P|1|id|name|5|" + written + " dvchost=h1"));
        // Spaces are not escaped in CEF: a value runs to the next ' key=', which an escaped = can't start.
        assertThat(back.get("msg")).isEqualTo(value.replace("\r\n", "\n"));
        assertThat(back.get("dvchost")).isEqualTo("h1");
    }

    @Test
    void headerFieldsEscapePipesAndBackslashesButNotEqualsSigns() {
        final String header = escaped(Map.of("header", "Secret | viewed \\ by a=b")).header();
        assertThat(header).isEqualTo("Secret \\| viewed \\\\ by a=b");
        assertThat(CefLines.parse("CEF:0|V|P|1|id|" + header + "|5|act=View").header().subList(5, 7))
                .containsExactly("Secret | viewed \\ by a=b", "5");
    }

    @Test
    void theCefXsltFollowsTheEventsXsltStyle() {
        // Asked for by the user: CEF XSLTs written as the Events translation is (templates, variables, naming).
        final CefPlan text = CefFixtures.drafted().plan().withOutput(CefPlan.TEXT);
        final List<String> lines = CefLines.linesIn(CefFixtures.run(text.xslt(), CefFixtures.EVENTS));
        final String xslt = text.xslt();
        assertThat(xslt).contains(
                "<xsl:template match=\"Event[EventDetail/Authenticate]\" mode=\"cef_extension\" as=\"xs:string*\">",
                "<xsl:template match=\"Event\" mode=\"cef_common\" as=\"xs:string*\">",
                "<!-- cef_common: every event: dvchost from EventSource/Device/HostName");
        // The same lines whatever the layout and naming.
        for (final Map<String, Object> style : List.<Map<String, Object>>of(Map.of("layout", "named", "naming",
                "camelCase"), Map.of("layout", "inline"), Map.of("variables", "top"))) {
            final CefPlan styled = text.withStyle(XsltStyle.fromJson(style));
            assertThat(CefLines.linesIn(CefFixtures.run(styled.xslt(), CefFixtures.EVENTS))).as(style.toString())
                    .isEqualTo(lines);
        }
        final String named = text.withStyle(XsltStyle.fromJson(Map.of("layout", "named", "naming", "camelCase")))
                .xslt();
        assertThat(named).contains("<xsl:call-template name=\"cefCommon\"/>",
                "<xsl:template name=\"cefLine\" as=\"xs:string\">");
        final String inline = text.withStyle(XsltStyle.fromJson(Map.of("layout", "inline"))).xslt();
        assertThat(inline).doesNotContain("cef_extension").contains("<xsl:when test=\"EventDetail/Authenticate\">");
        // A header field mapped from its source's values reads it in each test: read once, into a variable.
        final Map<String, String> severities = new LinkedHashMap<>();
        severities.put("low", "3");
        severities.put("medium", "6");
        severities.put("high", "9");
        final CefPlan mapped = text.withHeader("severity", new CefValue(null, "EventDetail/Alert/Severity",
                severities, "3"));
        assertThat(mapped.xslt()).contains("<xsl:variable name=\"severity\" select=\"EventDetail/Alert/Severity\"/>",
                "if (string(($severity)[1]) = 'low')");
        assertThat(CefLines.linesIn(CefFixtures.run(mapped.xslt(), CefFixtures.EVENTS))).isEqualTo(lines);
    }

    @Test
    void kindsWithTheSameKeysAreDocumentedTogether() {
        // Asked for by the user: Send and Receive under one heading, one row per key, each kind's example where
        // they differ.
        final CefPlan plan = CefPlan.fromJson(FieldDocFixtures.json("""
                {"vendor": {"value": "Acme"}, "product": {"value": "Mail"}, "version": {"value": "1"},
                 "signature": {"source": "EventDetail/TypeId"}, "name": {"source": "EventDetail/Description"},
                 "severity": {"value": "3"},
                 "common": [{"path": "EventTime/TimeCreated", "key": "rt", "transform": "epoch_ms"}],
                 "events": {"Send": [{"path": "EventDetail/Send/Source/User/Id", "key": "suser"},
                                     {"path": "EventDetail/Send/Destination/User/Id", "key": "duser"}],
                            "Receive": [{"path": "EventDetail/Receive/Source/User/Id", "key": "suser"},
                                        {"path": "EventDetail/Receive/Destination/User/Id", "key": "duser"}],
                            "View": [{"path": "EventDetail/View/Document/Title", "key": "fname"}]},
                 "output": "text"}"""));
        final Map<CefDocs.KindKey, String> examples = new LinkedHashMap<>();
        examples.put(new CefDocs.KindKey("Send", "suser"), "alice");
        examples.put(new CefDocs.KindKey("Receive", "suser"), "bob");
        examples.put(new CefDocs.KindKey("Send", "duser"), "carol");
        examples.put(new CefDocs.KindKey("Receive", "duser"), "carol");
        final String text = plan.markdown(examples, FieldDocs.BY_EVENT_TYPE, null);
        assertThat(text).contains("### Send, Receive events", "### View events").doesNotContain("### Send events");
        assertThat(text).contains("| `EventDetail/(Send\\|Receive)/Source/User/Id` | suser |",
                "| alice (Send); bob (Receive) |");
        assertThat(text.split("duser")[1].stripTrailing().split("\n")[0]).endsWith("| carol |");
        // The Every event table has no Event type column.
        assertThat(text).contains("| `EventTime/TimeCreated` | rt |");
    }

    @Test
    void cefFieldsAreDescribedByWhatTheyHoldHere() {
        // Asked for by the user: a custom slot (deviceCustomString1) means what this source puts in it; ArcSight's
        // dictionary words are the fallback, and shown to the agent to build on.
        final CefPlan plan = CefPlan.fromJson(FieldDocFixtures.json("""
                {"vendor": {"value": "Acme"}, "product": {"value": "Vault"}, "version": {"value": "1"},
                 "signature": {"source": "EventDetail/TypeId"}, "name": {"source": "EventDetail/Description"},
                 "severity": {"value": "3"},
                 "common": [{"path": "EventTime/TimeCreated", "key": "rt", "transform": "epoch_ms"}],
                 "events": {"View": [{"path": "EventDetail/View/Data[@Name='folder']/@Value", "key": "cs1",
                                      "label": "folder"}]},
                 "output": "text"}"""));
        final Map<CefDocs.KindKey, String> examples = Map.of(new CefDocs.KindKey("View", "cs1"), "Finance/Payroll");
        final Descriptions asking = Descriptions.asking(true);
        String text = plan.markdown(examples, FieldDocs.BY_EVENT_TYPE, asking);
        // By kind: a slot means what each kind puts in it.
        assertThat(asking.wanted().keySet()).containsExactlyInAnyOrder("View: cs1", "rt");
        final Map<String, Object> asked = asking.wanted().get("View: cs1");
        assertThat(asked.get("values")).isEqualTo(List.of("Finance/Payroll"));
        assertThat((String) asked.get("from")).contains("labelled 'folder'");
        @SuppressWarnings("unchecked") final Map<String, String> schemasSay =
                (Map<String, String>) asked.get("schemas_say");
        // The fallback.
        assertThat(schemasSay).containsKey("ArcSight");
        assertThat(text).contains(schemasSay.get("ArcSight"));
        final Descriptions given = new Descriptions(Map.of("View: cs1", "The vault folder holding the secret viewed."),
                true);
        text = plan.markdown(examples, FieldDocs.BY_EVENT_TYPE, given);
        assertThat(text).contains("| The vault folder holding the secret viewed.; labelled 'folder' (cs1Label = "
                                  + "deviceCustomString1Label)");
    }

    @Test
    void aCefPlanWithAnUnsafeExpressionIsAProblemAndWritesNoXslt() {
        final CefPlan plan = CefFixtures.drafted().plan()
                .withHeader("vendor", CefValue.from("environment-variable('STROOM_MCP_REQUEST_STATE_KEYS')"))
                .withKafkaKey("doc('http://169.254.169.254/')");
        final List<String> problems = plan.problems();
        assertThat(problems).anyMatch(p -> p.startsWith("header Device Vendor")
                                           && p.contains("environment-variable()"));
        assertThat(problems).anyMatch(p -> p.startsWith("kafka_key") && p.contains("doc()"));
        assertThatThrownBy(plan::xslt).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(
                "isn't written");
    }

    @ParameterizedTest
    @ValueSource(strings = {CefPlan.KAFKA, CefPlan.TEXT})
    void cefXsltsWriteTheirImportsFirst(final String output) {
        final Map<String, CefValue> header = new LinkedHashMap<>();
        header.put("vendor", CefValue.constant("Acme"));
        header.put("product", CefValue.constant("P"));
        header.put("version", CefValue.constant("1"));
        header.put("signature", CefValue.from("EventDetail/TypeId"));
        header.put("name", CefValue.constant("n"));
        final String xslt = CefPlan.of(output, "t", false, header, List.of(CefField.of("EventSource/User/Id", "suser")),
                Map.of()).withImports(List.of("Cef-Common-V1")).xslt();
        assertThat(xslt.split("<xsl:template", 2)[0]).contains("<xsl:import href=\"Cef-Common-V1\"/>");
    }

    @Test
    void aCefPlanIsExtendedWithoutMovingWhatItHas() {
        final Map<String, CefValue> header = new LinkedHashMap<>();
        header.put("vendor", CefValue.constant("Acme"));
        header.put("product", CefValue.constant("P"));
        header.put("version", CefValue.constant("1"));
        header.put("signature", CefValue.from("EventDetail/TypeId"));
        header.put("name", CefValue.constant("n"));
        final CefPlan plan = CefPlan.of(CefPlan.KAFKA, "t", false, header,
                List.of(CefField.of("EventSource/User/Id", "suser")),
                Map.of("View", List.of(new CefField("EventDetail/View/Document/Title", "cs1", "Title", null))));
        final List<Map<String, String>> added = CefDraft.extend(plan, CefEvents.eventsOf(COVERAGE_EVENTS)).added();
        final Map<String, Map<String, String>> byPath = new LinkedHashMap<>();
        added.forEach(a -> byPath.put(a.get("path"), a));
        // cs1 is the plan's (for a value these Events don't hold): the new ones take other slots.
        assertThat(byPath.get("EventDetail/View/Resource/Name").get("key")).isNotIn("cs1", "suser");
        assertThat(added.stream()
                .filter(a -> a.get("path").startsWith("EventDetail/View/Resource"))
                .map(a -> a.get("key"))).isSubsetOf("cs2", "cs3", "cs4", "cs5", "cs6");
        assertThat(added).allMatch(a -> "View".equals(a.get("event_type")));
        assertThat(byPath).doesNotContainKey("EventSource/User/Id");
    }

    @Test
    void aPlanThatDoesNotValidateSaysWhereAsPydanticDoes() {
        final Map<String, Object> json = FieldDocFixtures.json("""
                {"output": "syslog", "vendor": {"value": "A", "source": "B"}, "product": {"value": "P"},
                 "version": {"value": "1"}, "name": {"value": "n"},
                 "common": [{"path": "EventSource/User/Id"}, {"path": "x", "key": "y", "transform": "upper"}]}""");
        assertThatThrownBy(() -> CefPlan.fromJson(json))
                .isInstanceOfSatisfying(MappingValidationException.class, e -> assertThat(e.errors().stream()
                        .map(error -> error.dotted() + ": " + error.message())
                        .toList()).containsExactly(
                        "output: Input should be 'kafka' or 'text'",
                        "vendor: Value error, " + CefValue.ONE_OF,
                        "signature: Field required",
                        "common.0.key: Field required",
                        "common.1.transform: Input should be 'text', 'epoch_ms' or 'outcome'"));
    }

    @Test
    void aFullKeyNameIsKeptByItsShortNameAndATimeKeyAsMilliseconds() {
        assertThat(CefField.of("EventDetail/Authorise/User/Name", "deviceCustomString6").key()).isEqualTo("cs6");
        assertThat(CefField.of("EventTime/TimeCreated", "end").transform()).isEqualTo(CefField.EPOCH_MS);
        assertThat(CefKeys.described("cs6", "Target user")).isEqualTo(
                "cs6 (deviceCustomString6): Custom string, named by its label, labelled 'Target user' (cs6Label)");
        assertThat(CefKeys.described("myKey", null)).isEqualTo("myKey: Key outside the CEF dictionary");
    }

    @Test
    void timesAreReadInIsoFormat() {
        assertThat(CefEvents.isTime("2026-09-27T23:48:10.000Z")).isTrue();
        assertThat(CefEvents.isTime("20260927T234810")).isTrue();
        assertThat(CefEvents.isTime("2026-W39-1T10:00")).isTrue();
        assertThat(CefEvents.isTime("2026-09-27")).isFalse();
        assertThat(CefEvents.isTime("2026-09-27T24:00")).isFalse();
        assertThat(CefEvents.isTime("2026-02-30T10:00")).isFalse();
        assertThat(CefEvents.isTime("2026-09-27T23:48:1")).isFalse();
        assertThat(CefEvents.epochMillis("2026-09-27T23:48:10.000Z")).contains("1790552890000");
        assertThat(CefEvents.isIpv4("10.0.0.1")).isTrue();
        assertThat(CefEvents.isIpv4("010.0.0.1")).isFalse();
        assertThat(CefEvents.isIpv4("ss-client.domain.com")).isFalse();
    }

    @Test
    void theXsltsFunctionsAreTheServers() {
        assertThat(CefXslt.FUNCTIONS).startsWith("  <!-- A header field, escaped: backslash and pipe, on one line. -->")
                .endsWith("  </xsl:function>\n");
        assertThat(XmlTrees.XSL).isEqualTo("http://www.w3.org/1999/XSL/Transform");
    }

    private static Map<String, String> pairs(final CefLines.Parsed parsed) {
        final Map<String, String> out = new LinkedHashMap<>();
        parsed.extension().forEach(p -> out.put(p.getKey(), p.getValue()));
        return out;
    }

    private static boolean validates(final Schema schema, final String xml) throws IOException {
        try {
            schema.newValidator().validate(new StreamSource(new StringReader(xml)));
            return true;
        } catch (final SAXException e) {
            return false;
        }
    }

    // The values run through the XSLT's functions: the first as a header field, each as an msg value.
    private static Escaped escaped(final Map<String, String> values) {
        final String doc = "<v>" + values.entrySet().stream()
                .map(e -> "<i n=\"" + e.getKey() + "\">" + e.getValue().replace("&", "&amp;").replace("<", "&lt;")
                          + "</i>")
                .collect(Collectors.joining()) + "</v>";
        final String xslt = "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                            + "xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" xmlns:cef=\"cef\">" + CefXslt.FUNCTIONS
                            + "<xsl:template match=\"/\"><out><h><xsl:value-of select=\"cef:h(v/i[1], 63)\"/></h>"
                            + "<xsl:for-each select=\"v/i\"><kv><xsl:value-of select=\"cef:kv('msg', ., 1023)\"/>"
                            + "</kv></xsl:for-each></out></xsl:template></xsl:stylesheet>";
        final XmlElement root = XmlParser.parse(CefFixtures.run(xslt, doc));
        final Map<String, String> written = new LinkedHashMap<>();
        final List<XmlElement> kvs = new ArrayList<>(root.childElements(null, "kv"));
        final List<String> names = new ArrayList<>(values.keySet());
        for (int i = 0; i < kvs.size(); i++) {
            written.put(names.get(i), kvs.get(i).text());
        }
        return new Escaped(root.childElements(null, "h").getFirst().text(), written);
    }

    private static Map<String, String> escapedCases() {
        final Map<String, String> cases = new LinkedHashMap<>();
        cases.put("spaces and an equals sign", "user bob logged in from host=ws01 now");
        cases.put("ending with a backslash", "C:\\temp\\");
        cases.put("a backslash then an equals sign", "a\\=b");
        cases.put("line breaks", "first\nsecond\r\nthird");
        cases.put("what looks like another key", "x act=deny y");
        cases.put("a pipe, needing no escape here", "a|b");
        return cases;
    }


    // --------------------------------------------------------------------------------


    // What the functions wrote: the first value as a header field, and each as a msg value.
    private record Escaped(String header, Map<String, String> values) {

    }
}
