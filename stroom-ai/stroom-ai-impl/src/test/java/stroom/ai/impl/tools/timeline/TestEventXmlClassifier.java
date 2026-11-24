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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.tools.timeline.EventXmlClassifier.Classified;
import stroom.ai.impl.tools.timeline.EventXmlClassifier.Route;
import stroom.ai.impl.tools.timeline.TimelineSessions.Role;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The roles, labels and keys an event's Events XML gives, by the schema's elements and Actions alone.
class TestEventXmlClassifier {

    @ParameterizedTest
    @CsvSource({
            "Authenticate, Logon, IDLE_END",
            "Authenticate, ScreenUnlock, IDLE_END",
            "Authenticate, Connect, IDLE_END",
            "Authenticate, Reconnect, IDLE_END",
            "Authenticate, Logoff, IDLE_START",
            "Authenticate, ScreenLock, IDLE_START",
            "Authenticate, Disconnect, IDLE_START",
            "Authenticate, ChangePassword, ACTION",
            "Process, Execute, OPEN",
            "Process, Startup, OPEN",
            "Process, Terminate, CLOSE",
            "Process, Shutdown, CLOSE",
            "Process, Call, ACTION",
            "View, , ACTION",
            "Send, , ACTION",
            "Network, , ACTION"})
    void theElementAndItsActionGiveTheRole(final String element, final String action, final Role role) {
        assertThat(EventXmlClassifier.role(element, action)).isEqualTo(role);
    }

    @Test
    void anEventsXmlGivesItsTypeKindObjectHostAndKeysButNeverItsUserOrTime() {
        final Classified classified = EventXmlClassifier.classify("""
                <?xml version="1.1" encoding="UTF-8"?>
                <Events xmlns="event-logging:3" Version="4.1.0">
                  <Event>
                    <EventTime><TimeCreated>2001-01-01T00:00:00.000Z</TimeCreated></EventTime>
                    <EventSource>
                      <Device><HostName>WS-01</HostName></Device>
                      <User><Id>CORP\\JSMITH</Id></User>
                    </EventSource>
                    <EventDetail>
                      <TypeId>web-get</TypeId>
                      <Description>A page fetched</Description>
                      <View>
                        <Resource><URL>https://research.example.org/pricing</URL><Title>Pricing</Title></Resource>
                      </View>
                    </EventDetail>
                  </Event>
                </Events>""").orElseThrow();

        assertThat(classified.element()).isEqualTo("View");
        assertThat(classified.typeId()).isEqualTo("web-get");
        assertThat(classified.type()).isEqualTo("web-get");
        assertThat(classified.schemaType()).isEqualTo("View");
        assertThat(classified.role()).isEqualTo(Role.ACTION);
        assertThat(classified.kind()).isEqualTo("view");
        // The title says more than the URL.
        assertThat(classified.object()).isEqualTo("Pricing");
        assertThat(classified.host()).isEqualTo("WS-01");
        assertThat(classified.keys()).containsEntry("Resource/URL", "https://research.example.org/pricing")
                .containsEntry("Resource/Title", "Pricing");
        assertThat(classified.route()).isEqualTo(Route.XML);
        // The user is in the XML, but the timeline's is the index's: it is never read from here.
        assertThat(classified.toString()).doesNotContain("JSMITH").doesNotContain("2001");
    }

    @Test
    void aProcessGivesItsApplicationAndItsIdAsKeys() {
        final Classified classified = EventXmlClassifier.classify("""
                <Event><EventDetail><Process><Action>Execute</Action>
                  <Command>C:\\Program Files\\Office\\WINWORD.EXE</Command><ProcessId>4242</ProcessId>
                </Process></EventDetail></Event>""").orElseThrow();

        assertThat(classified.schemaType()).isEqualTo("Process/Execute");
        assertThat(classified.type()).isEqualTo("Process/Execute");
        assertThat(classified.role()).isEqualTo(Role.OPEN);
        assertThat(classified.application()).isEqualTo("WINWORD.EXE");
        assertThat(classified.keys()).containsEntry("ProcessId", "4242").doesNotContainKey("Action");
        assertThat(classified.isType("process/execute")).isTrue();
    }

    @Test
    void whatIsntAnEventWithAnEventDetailIsNotClassified() {
        assertThat(EventXmlClassifier.classify("<Events><Event><EventTime/></Event></Events>")).isEmpty();
        assertThat(EventXmlClassifier.classify("not xml")).isEmpty();
        assertThat(EventXmlClassifier.classify(null)).isEmpty();
        assertThat(EventXmlClassifier.classify("<Event><EventDetail><TypeId>x</TypeId></EventDetail></Event>"))
                .isEmpty();
    }

    @Test
    void anIndexsElementIsSpeltAsTheSchemaSpellsIt() {
        assertThat(EventXmlClassifier.isElement("authenticate")).isTrue();
        assertThat(EventXmlClassifier.isElement("Logon")).isFalse();
        final Classified classified = EventXmlClassifier.of("authenticate", "logoff", null, null, null, null,
                Map.of(), Route.INDEX_FIELDS);
        assertThat(classified.schemaType()).isEqualTo("Authenticate/logoff");
        assertThat(classified.role()).isEqualTo(Role.IDLE_START);
    }

    @Test
    void aCommandsBaseNameIsItsLastPartWithoutArguments() {
        assertThat(EventXmlClassifier.baseName("C:\\Program Files\\Office\\WINWORD.EXE /n")).isEqualTo("WINWORD.EXE");
        assertThat(EventXmlClassifier.baseName("/usr/bin/vim -R notes.txt")).isEqualTo("vim");
        assertThat(EventXmlClassifier.baseName("\"C:\\Apps\\My App.exe\" /x")).isEqualTo("My App.exe");
        assertThat(EventXmlClassifier.baseName("notepad.exe a.txt")).isEqualTo("notepad.exe");
        assertThat(EventXmlClassifier.baseName(" ")).isNull();
    }
}
