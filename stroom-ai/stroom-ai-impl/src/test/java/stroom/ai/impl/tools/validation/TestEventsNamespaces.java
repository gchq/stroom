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

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestEventsNamespaces {

    private static final String FORK = "file://xml/schema/accounting/events";

    @Test
    void anEventsDocumentIsToldByItsShapeInAnyNamespace() {
        assertThat(EventsNamespaces.of(root("<Events xmlns=\"event-logging:3\"><Event/></Events>")))
                .isEqualTo("event-logging:3");
        assertThat(EventsNamespaces.of(root("<Events xmlns=\"" + FORK + "\"><Event/></Events>"))).isEqualTo(FORK);
        assertThat(EventsNamespaces.of(root("<Event xmlns=\"" + FORK + "\"/>"))).isEqualTo(FORK);
        // No namespace at all is "".
        assertThat(EventsNamespaces.of(root("<Events><Event/></Events>"))).isEmpty();
    }

    @Test
    void otherXmlIsNotEvents() {
        final Element records = root("<records xmlns=\"records:2\"><record/></records>");

        assertThat(EventsNamespaces.isEvents(records)).isFalse();
        assertThat(EventsNamespaces.of(records)).isNull();
        assertThat(EventsNamespaces.isEvents(null)).isFalse();
    }

    @Test
    void onlyEventLoggingIsStandard() {
        assertThat(EventsNamespaces.isStandard("event-logging:3")).isTrue();
        assertThat(EventsNamespaces.isStandard(FORK)).isFalse();
        assertThat(EventsNamespaces.isStandard(null)).isFalse();
        assertThat(EventsNamespaces.orStandard(null)).isEqualTo("event-logging:3");
        assertThat(EventsNamespaces.orStandard(" ")).isEqualTo("event-logging:3");
        assertThat(EventsNamespaces.orStandard(FORK)).isEqualTo(FORK);
    }

    @Test
    void elementsAreComparedByNamespace() {
        final Element events = root("<Events xmlns=\"" + FORK + "\" xmlns:o=\"other\"><Event/><o:Event/></Events>");
        final Element event = XmlTrees.children(events).get(0);
        final Element other = XmlTrees.children(events).get(1);

        assertThat(EventsNamespaces.sameNamespace(event, events)).isTrue();
        assertThat(EventsNamespaces.sameNamespace(other, events)).isFalse();
    }

    @Test
    void theNamespaceAnXsltWritesEventsInIsFoundFromAnyOfItsElements() {
        // A shared import may write only part of an Event.
        final String shared = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">"
                              + "<xsl:template name=\"source\"><EventSource xmlns=\"" + FORK + "\"><Generator>g"
                              + "</Generator></EventSource></xsl:template></xsl:stylesheet>";
        final List<String> known = List.of("event-logging:3", FORK);

        assertThat(EventsNamespaces.writtenBy(shared, known)).contains(FORK);
        assertThat(EventsNamespaces.writtenBy(shared.replace(FORK, "event-logging:3"), known))
                .contains("event-logging:3");
        // A namespace not among those given, none at all, or not XML: nothing.
        assertThat(EventsNamespaces.writtenBy(shared.replace(FORK, "records:2"), known)).isEmpty();
        assertThat(EventsNamespaces.writtenBy("<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"/>",
                known)).isEmpty();
        assertThat(EventsNamespaces.writtenBy("<cut", known)).isEmpty();
        assertThat(EventsNamespaces.writtenBy(null, known)).isEmpty();
    }

    private static Element root(final String xml) {
        return XsltChecks.parse(xml, "XML");
    }
}
