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
    // The stage after a flattener: its pairs in event-logging:3 in, the flattened line in a CefEvent out.
    static final String CEF_EVENT_STAGE = """
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="event-logging:3"
             xpath-default-namespace="event-logging:3" version="3.0">
              <xsl:template match="/records"><CefEvent><xsl:value-of select="record/data/@value"/></CefEvent>\
            </xsl:template>
            </xsl:stylesheet>""";

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

    @Test
    void anIntermediateStageInAnEventsNamespaceWritesNoEvents() {
        // A CEF forwarder's stages mirror the namespace the shared Kafka XSLT reads: a flattener's pairs and a
        // CefEvent wrapping the flattened line, both in event-logging:3, are not Events.
        final List<String> known = List.of("event-logging:3", FORK);

        assertThat(EventsNamespaces.writtenBy(flattener("event-logging:3"), known)).isEmpty();
        assertThat(EventsNamespaces.writtenBy(flattener(FORK), known)).isEmpty();
        assertThat(EventsNamespaces.writtenBy(CEF_EVENT_STAGE, known)).isEmpty();
        assertThat(EventsNamespaces.writesEventsOrEvent(root(CEF_EVENT_STAGE))).isFalse();
        assertThat(EventsNamespaces.readsEvents(root(CEF_EVENT_STAGE), known)).isTrue();
    }

    @Test
    void theNamespaceOfTheEventsWrittenIsTakenOverOtherElements() {
        // Another element in an Events namespace before the Event doesn't name the Events' namespace.
        final String translation = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                                   + "xpath-default-namespace=\"records:2\" version=\"3.0\">"
                                   + "<xsl:template name=\"note\"><records xmlns=\"event-logging:3\"/>"
                                   + "</xsl:template><xsl:template match=\"record\"><Event xmlns=\"" + FORK + "\">"
                                   + "<EventTime/></Event></xsl:template></xsl:stylesheet>";

        assertThat(EventsNamespaces.writtenBy(translation, List.of("event-logging:3", FORK))).contains(FORK);
        // A decoration copying its Events still writes them.
        final String decoration = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" xmlns=\""
                                  + FORK + "\" xpath-default-namespace=\"" + FORK + "\" version=\"3.0\">"
                                  + "<xsl:template match=\"@*|node()\"><xsl:copy><xsl:apply-templates "
                                  + "select=\"@*|node()\"/></xsl:copy></xsl:template>"
                                  + "<xsl:template match=\"EventDetail\"><xsl:copy><xsl:apply-templates/><Extra/>"
                                  + "</xsl:copy></xsl:template></xsl:stylesheet>";
        assertThat(EventsNamespaces.writtenBy(decoration, List.of("event-logging:3", FORK))).contains(FORK);
    }

    @Test
    void anEventsNamespaceIsReadWhereAnyElementDeclaresIt() {
        final List<String> known = List.of("event-logging:3", FORK);

        assertThat(EventsNamespaces.readsEvents(root("<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/"
                                                     + "Transform\" version=\"3.0\"><xsl:template match=\"x\" "
                                                     + "xpath-default-namespace=\"" + FORK + "\"/>"
                                                     + "</xsl:stylesheet>"), known)).isTrue();
        assertThat(EventsNamespaces.readsEvents(root("<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/"
                                                     + "Transform\" xmlns:e=\"event-logging:3\" version=\"3.0\">"
                                                     + "<xsl:template match=\"e:Event\"/></xsl:stylesheet>"), known))
                .isTrue();
        assertThat(EventsNamespaces.readsEvents(root("<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/"
                                                     + "Transform\" xpath-default-namespace=\"records:2\" "
                                                     + "version=\"3.0\"/>"), known)).isFalse();
    }

    // A flattener of Events in the namespace given: data pairs in that namespace, for the next XSLT.
    static String flattener(final String namespace) {
        return """
                <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="%1$s"
                 xpath-default-namespace="%1$s" version="3.0">
                  <xsl:template match="/Events"><records><xsl:apply-templates select="Event"/></records>\
                </xsl:template>
                  <xsl:template match="Event">
                    <record><data name="user" value="{EventSource/User/Id}"/></record>
                  </xsl:template>
                </xsl:stylesheet>""".formatted(namespace);
    }

    private static Element root(final String xml) {
        return XsltChecks.parse(xml, "XML");
    }
}
