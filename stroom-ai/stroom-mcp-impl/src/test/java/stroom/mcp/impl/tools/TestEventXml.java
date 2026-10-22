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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestEventXml {

    static final String OUTPUT = "<?xml version=\"1.1\"?><Events xmlns=\"event-logging:3\" Version=\"4.1.0\"><Event>"
                                 + "<EventSource><User><Id>a</Id></User></EventSource></Event>\n<Event><EventSource>"
                                 + "<User><Id>b</Id></User></EventSource></Event></Events>";

    @Test
    void theNthEventKeepsTheWrapperAndNamespace() {
        final String second = EventXml.nthEvent(OUTPUT, 2);

        assertThat(second).startsWith("<Events xmlns=\"event-logging:3\">").contains("<Id>b</Id>")
                .doesNotContain("<Id>a</Id>");
        assertThat(EventXml.nthEvent(OUTPUT, 3)).isNull();
        assertThat(EventXml.nthEvent(OUTPUT, 0)).isNull();
        assertThat(EventXml.nthEvent("not xml", 1)).isNull();
    }

    @Test
    void eventsAreCountedWithOrWithoutAPrefix() {
        assertThat(EventXml.countEvents(OUTPUT)).isEqualTo(2);
        assertThat(EventXml.countEvents("<evt:Events><evt:Event/><evt:Event>x</evt:Event></evt:Events>"))
                .isEqualTo(2);
        assertThat(EventXml.countEvents("<Events><EventTime/></Events>")).isZero();
        assertThat(EventXml.countEvents(null)).isZero();
    }

    @Test
    void valuesAreKeyedByAReadablePathNamedByTheirNameAttribute() {
        final Map<String, List<String>> values = EventXml.fieldValues("<Events xmlns=\"event-logging:3\"><Event>"
                + "<EventSource><User><Id>a</Id></User></EventSource><Data Name=\"host\" Value=\"ws01\"/>"
                + "<Data Name=\"port\" Value=\"22\"/></Event></Events>");

        assertThat(values).containsEntry("Event/EventSource/User/Id", List.of("a"))
                .containsEntry("Event/Data[host]/@Value", List.of("ws01"))
                .containsEntry("Event/Data[port]/@Value", List.of("22"));
        // Reordering them does not show as a change.
        assertThat(EventXml.fieldValues("<Events xmlns=\"event-logging:3\"><Event><Data Name=\"port\" Value=\"22\"/>"
                + "<Data Name=\"host\" Value=\"ws01\"/><EventSource><User><Id>a</Id></User></EventSource></Event>"
                + "</Events>")).isEqualTo(values);
    }

    @Test
    void outputThatDoesntParseIsSaidSo() {
        assertThat(EventXml.fieldValues("<Events><cut")).containsOnlyKeys(EventXml.UNPARSEABLE);
    }
}
