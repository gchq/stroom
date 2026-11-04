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

package stroom.ai.impl.tools.fielddoc;

import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.validation.EventSchema;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The Field mapping section of a translation XSLT written by hand, from the Events it writes. Seen in testing: the
/// agent's own section for one had the common fields and none of each event type's own.
class TestWrittenEventDocs {

    private static final EventSchema SCHEMA = FieldDocFixtures.SCHEMA;

    private static final String LOGIN = event("2026-11-04T01:00:00.000Z", "<IPAddress>10.0.0.1</IPAddress>", """
            <TypeId>login</TypeId><Description>A user logged in</Description>
            <Authenticate><Action>Logon</Action><User><Id>alice</Id></User></Authenticate>""");
    private static final String LOGOUT = event("2026-11-04T02:00:00.000Z", "", """
            <TypeId>logout</TypeId>
            <Authenticate><Action>Logoff</Action><User><Id>bob</Id></User></Authenticate>""");
    private static final String FILE_READ = event("2026-11-04T03:00:00.000Z", "<IPAddress>10.0.0.9</IPAddress>",
            """
            <TypeId>file-read</TypeId>
            <View><Document><File><Path>/etc/hosts</Path></File></Document>\
            <Data Name="size" Value="120"/></View>""");
    // No TypeId: its type is its action element.
    private static final String NETWORK = event("2026-11-04T04:00:00.000Z", "", """
            <Network><Open><Source><Device><IPAddress>10.0.0.2</IPAddress></Device></Source></Open></Network>""");

    @Test
    void theCommonFieldsThenEachEventTypesOwnFields() {
        final String section = WrittenEventDocs.writtenEventsMarkdown(SCHEMA, events(LOGIN, LOGOUT, FILE_READ,
                NETWORK, FILE_READ), FieldDocs.BY_EVENT_TYPE, null);

        assertThat(section).startsWith("The XSLT was written by hand, so these tables are of the Events it writes")
                .contains("for the sample's 5 events, by event type");
        final String common = between(section, "### Common fields", "### Event types");
        // Every type has these; an IP address only some have is each one's own.
        assertThat(common).contains("`EventTime/TimeCreated`", "`EventSource/System/Name`",
                "`EventSource/Device/HostName`").doesNotContain("IPAddress");
        assertThat(common).contains("\"acme-host\"");

        // Login and logout write the same fields: one section, with each one's count.
        final String authenticate = between(section, "#### login, logout", "####");
        assertThat(authenticate).contains("login: 1 of the sample's 5 events; logout: 1 of the sample's 5 events.")
                .contains("`EventDetail/TypeId`", "`EventDetail/Authenticate/Action`",
                        "`EventDetail/Authenticate/User/Id`", "\"Logon\"", "\"Logoff\"")
                .contains("`EventSource/Device/IPAddress`");
        final String fileRead = between(section, "#### file-read", "####");
        assertThat(fileRead).contains("2 of the sample's 5 events.")
                .contains("`EventDetail/View/Document/File/Path`", "\"/etc/hosts\"")
                .contains("`EventDetail/View/Data[@Name='size']/@Value`", "\"120\"")
                .contains("`EventSource/Device/IPAddress`");
        assertThat(section).contains("#### Network")
                .contains("`EventDetail/Network/Open/Source/Device/IPAddress`");
        // No mapping: no From column.
        assertThat(section).contains("| XPath | Description | Sample values |").doesNotContain("| From |");
    }

    @Test
    void anEventTypeIsItsTypeIdElseItsActionElement() {
        final List<XmlElement> events = events(LOGIN, NETWORK, event("2026-11-04T05:00:00.000Z", "",
                "<TypeId> </TypeId>"));

        assertThat(events).extracting(WrittenEventDocs::typeOf)
                .containsExactly("login", "Network", WrittenEventDocs.NO_TYPE);
    }

    @Test
    void theFieldsNotYetDescribedAreAskedFor() {
        final Descriptions asking = Descriptions.asking(true);

        WrittenEventDocs.writtenEventsMarkdown(SCHEMA, events(FILE_READ), FieldDocs.BY_EVENT_TYPE, asking);

        final Map<String, Map<String, Object>> wanted = asking.wanted();
        assertThat(wanted).containsKeys("EventDetail/View/Document/File/Path",
                "EventDetail/View/Data[@Name='size']/@Value");
        assertThat(wanted.get("EventDetail/View/Document/File/Path"))
                .containsEntry("from", WrittenEventDocs.WRITTEN);

        // Described, nothing is asked.
        final Descriptions given = new Descriptions(Map.of("EventDetail/View/Document/File/Path",
                "The file the user read."), true);
        final String section = WrittenEventDocs.writtenEventsMarkdown(SCHEMA, events(FILE_READ),
                FieldDocs.BY_EVENT_TYPE, given);
        assertThat(section).contains("The file the user read.");
        assertThat(given.wanted()).doesNotContainKey("EventDetail/View/Document/File/Path");
    }

    @Test
    void compactIsOneRowPerEventType() {
        final String section = WrittenEventDocs.writtenEventsMarkdown(SCHEMA, events(LOGIN, FILE_READ, FILE_READ),
                FieldDocs.COMPACT, null);

        assertThat(section).contains("### Common fields", "| Event type | Events | EventDetail |")
                .contains("| **login** | 1 |", "| **file-read** | 2 |")
                .contains("`View/Document/File/Path` = \"/etc/hosts\"")
                .doesNotContain("#### ");
    }

    private static String event(final String time, final String deviceExtra, final String detail) {
        return "<Event><EventTime><TimeCreated>" + time + "</TimeCreated></EventTime><EventSource><System><Name>ACME"
               + "</Name><Environment>Test</Environment></System><Generator>acme</Generator><Device><HostName>"
               + "acme-host</HostName>" + deviceExtra + "</Device></EventSource><EventDetail>" + detail
               + "</EventDetail></Event>";
    }

    private static List<XmlElement> events(final String... events) {
        return EventFieldDocs.sampledEvents(List.of("<Events xmlns=\"event-logging:3\">" + String.join("", events)
                                                    + "</Events>"));
    }

    private static String between(final String text, final String start, final String end) {
        final int from = text.indexOf(start);
        assertThat(from).as("'%s' in the section", start).isNotNegative();
        final int to = text.indexOf(end, from + start.length());
        return to < 0
                ? text.substring(from)
                : text.substring(from, to);
    }
}
