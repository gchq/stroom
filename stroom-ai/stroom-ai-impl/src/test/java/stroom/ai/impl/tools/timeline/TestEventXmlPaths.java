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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestEventXmlPaths {

    private static final String EVENTS = """
            <Events xmlns="event-logging:3"><Event><EventSource><Device><HostName> ws-01 </HostName></Device>\
            </EventSource><EventDetail><Send><Source/><Destination><User><Id>bob</Id></User></Destination>\
            <Payload><Resource><Subject>Q3</Subject></Resource></Payload></Send></EventDetail></Event></Events>""";

    @Test
    void aValueIsReadByItsPathWithAnyOneElementForAStar() {
        assertThat(EventXmlPaths.value(EVENTS, List.of("EventSource/Device/HostName"))).contains("ws-01");
        assertThat(EventXmlPaths.value(EVENTS, List.of("EventDetail/*/*/URL", "EventDetail/*/*/*/Subject")))
                .contains("Q3");
    }

    @Test
    void nothingForAPathNotHeldAnEmptyElementOrXmlThatDoesntParse() {
        assertThat(EventXmlPaths.value(EVENTS, List.of("EventSource/Device/IPAddress"))).isEmpty();
        assertThat(EventXmlPaths.value(EVENTS, List.of("EventDetail/Send/Source"))).isEmpty();
        assertThat(EventXmlPaths.value("<Event><broken", List.of("EventSource"))).isEmpty();
        assertThat(EventXmlPaths.value(EVENTS, List.of())).isEmpty();
        assertThat(EventXmlPaths.value(null, List.of("EventSource"))).isEmpty();
    }
}
