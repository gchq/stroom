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

package stroom.ai.impl.tools.validation;

import stroom.ai.impl.tools.ToolRefusal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Event XML checked against the schema this Stroom holds and against the quality rules. Ported from the Stroom
/// MCP server's `tests/test_validation.py`.
class TestEventChecks {

    static final String EVENT = """
            <?xml version="1.1" encoding="UTF-8"?><Events xmlns="event-logging:3"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="event-logging:3 file://event-logging-v9.9.9.xsd" Version="9.9.9"><Event>
            <EventTime><TimeCreated>2026-09-28T10:00:00.000Z</TimeCreated></EventTime>
            <EventSource><System><Name>ACME</Name><Environment>Dev</Environment></System><Generator>g</Generator>
            <Device><HostName>ws01</HostName></Device></EventSource>
            <EventDetail><TypeId>Logon</TypeId><Authenticate><Action>Logon</Action></Authenticate></EventDetail>
            </Event></Events>""";
    // A cut-down stand-in for the event-logging XSD: just enough structure to prove validation.
    static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="event-logging:3"
             xmlns="event-logging:3" elementFormDefault="qualified">
             <xs:element name="Events"><xs:complexType><xs:sequence>
              <xs:element name="Event" maxOccurs="unbounded"><xs:complexType><xs:sequence>
               <xs:element name="EventTime"><xs:complexType><xs:sequence>
               <xs:element name="TimeCreated" type="xs:dateTime"/>
               </xs:sequence></xs:complexType></xs:element>
               <xs:any processContents="skip" maxOccurs="unbounded"/>
              </xs:sequence></xs:complexType></xs:element>
             </xs:sequence><xs:anyAttribute processContents="skip"/></xs:complexType></xs:element>
            </xs:schema>""";

    private final EventChecks checks = new EventChecks(new HeldSchemasWorld()
            .hold("file://event-logging-v9.9.9.xsd", "event-logging:3", XSD)
            .heldSchemas());

    @Test
    @SuppressWarnings("unchecked")
    void testValidateEventsUsesTheDeclaredSchemaFromStroom() {
        assertThat(checks.validateEvents(EVENT, null).get("valid")).isEqualTo(true);
        final Map<String, Object> bad = checks.validateEvents(EVENT.replace("2026-09-28T10:00:00.000Z", "not-a-date"),
                null);
        assertThat(bad.get("valid")).isEqualTo(false);
        assertThat(bad.get("schema")).isEqualTo("file://event-logging-v9.9.9.xsd");
        // One fault is one error, at its line and element.
        assertThat(bad.get("error_count")).isEqualTo(1);
        final Map<String, Object> error = ((List<Map<String, Object>>) bad.get("errors")).getFirst();
        assertThat((String) error.get("message")).contains("TimeCreated").contains("not-a-date");
        assertThat(error.get("path")).isEqualTo("/Events/Event/EventTime/TimeCreated");
        assertThat(error.get("line")).isEqualTo(4);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTheErrorsPathCountsRepeatedElements() {
        final String two = EVENT.replace("</Event></Events>", "</Event><Event><EventTime><TimeCreated>bad"
                                                              + "</TimeCreated></EventTime></Event></Events>");
        final Map<String, Object> result = checks.validateEvents(two, null);
        assertThat(((List<Map<String, Object>>) result.get("errors")).getFirst().get("path"))
                .isEqualTo("/Events/Event[2]/EventTime/TimeCreated");
    }

    @Test
    void testUnknownSchemaVersionListsWhatStroomHas() {
        assertThatThrownBy(() -> checks.validateEvents(EVENT, "3.5.2"))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("versions it holds, newest first: 9.9.9");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testXmlThatIsNotEventsIsSaidToBeSo() {
        final Map<String, Object> result = checks.validateEvents("<records><record/></records>", null);
        assertThat(result.get("valid")).isEqualTo(false);
        assertThat(result.get("schema")).isNull();
        assertThat((String) ((List<Map<String, Object>>) result.get("errors")).getFirst().get("message"))
                .startsWith("Not an Events document: its root is <records> in no namespace");
    }

    @Test
    void testXmlThatIsNotWellFormedIsRefused() {
        assertThatThrownBy(() -> checks.validateEvents("<Events", null))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageStartingWith("Events XML is not well-formed XML: ");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testQualityRulesPassACompleteEventAndFlagGaps() {
        assertThat(EventChecks.checkEventQuality(EVENT).get("ok")).isEqualTo(true);
        final String gappy = EVENT.replace("<Generator>g</Generator>", "")
                .replace("2026-09-28T10:00:00.000Z", "2026-09-28 10:00");
        final Map<String, Object> rules = (Map<String, Object>) EventChecks.checkEventQuality(gappy).get("rules");
        assertThat(rules).containsOnlyKeys("generator", "time_created");
        final Map<String, Object> time = (Map<String, Object>) rules.get("time_created");
        assertThat(time.get("events_failing")).isEqualTo(1);
        assertThat(time.get("examples")).isEqualTo(List.of(Map.of("event", 0, "message",
                "EventTime/TimeCreated is '2026-09-28 10:00', not yyyy-MM-ddTHH:mm:ss.SSSZ")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testEmptyElementsAndActionsAreFlagged() {
        final String empty = EVENT.replace("<Authenticate><Action>Logon</Action></Authenticate>",
                "<Authenticate><Action>Logon</Action></Authenticate><View/>");
        final Map<String, Object> rules = (Map<String, Object>) EventChecks.checkEventQuality(empty).get("rules");
        assertThat(rules).containsOnlyKeys("action", "empty_element");
        assertThat(rules.get("action").toString()).contains("found ['Authenticate', 'View']");
        assertThat(rules.get("empty_element").toString()).contains("Event/EventDetail/View is empty");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testUnknownEventsAreNotedWithoutFailing() {
        assertThat(EventChecks.checkEventQuality(EVENT)).doesNotContainKey("notes");
        final String unknown = EVENT.replace("<Authenticate><Action>Logon</Action></Authenticate>",
                "<Unknown><Data Name=\"a\" Value=\"b\"/></Unknown>");
        final Map<String, Object> quality = EventChecks.checkEventQuality(unknown);
        assertThat(quality.get("ok")).isEqualTo(true);
        assertThat(((List<String>) quality.get("notes")).getFirst())
                .startsWith("1 of 1 events (e.g. [0]) have EventDetail/Unknown");
    }
}
