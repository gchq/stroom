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

package stroom.ai.impl.extraction;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XsltTransformer;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import javax.xml.transform.stream.StreamSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestExtractionXslt {

    // One event, as Stroom's id enrichment gives it to an extraction's XSLT: split from its stream, with its ids.
    static final String EVENT = """
            <Events xmlns="event-logging:3" Version="3.2.3">
              <Event StreamId="7" EventId="2">
                <EventSource>
                  <User><Id>jsmith</Id><Organisation>Finance &amp; Ops</Organisation></User>
                  <Device><IPAddress>10.0.0.1</IPAddress><IPAddress>10.0.0.2</IPAddress></Device>
                </EventSource>
                <EventDetail><TypeId>logon</TypeId><Data Name="Bytes" Value="512"/></EventDetail>
              </Event>
            </Events>""";

    @Test
    void eachEventsIdsAndFieldsAreWrittenAsTheSearchOutputReadsThem() {
        final String xslt = ExtractionXslt.xslt("event-logging:3", List.of(
                new ExtractionField("Organisation", "EventSource/User/Organisation", null),
                // The first of several values.
                new ExtractionField("IP", "EventSource/Device/IPAddress", null),
                new ExtractionField("Bytes", "EventDetail/Data[@Name = \"Bytes\"]/@Value", "number"),
                new ExtractionField("Type", "upper-case(EventDetail/TypeId)", null),
                new ExtractionField("Missing", "EventSource/User/Email", null)));

        assertThat(ExtractionPreview.values(transform(xslt, EVENT))).containsExactly(
                Map.entry("StreamId", "7"),
                Map.entry("EventId", "2"),
                Map.entry("Organisation", "Finance & Ops"),
                Map.entry("IP", "10.0.0.1"),
                Map.entry("Bytes", "512"),
                Map.entry("Type", "LOGON"));
    }

    @Test
    void eventsWithNoNamespaceAreReadToo() {
        final String xslt = ExtractionXslt.xslt("", List.of(new ExtractionField("Host", "Host", null)));

        assertThat(xslt).doesNotContain("xpath-default-namespace");
        assertThat(ExtractionPreview.values(transform(xslt,
                "<Records><Record StreamId=\"3\" EventId=\"1\"><Host>web1</Host></Record></Records>")))
                .containsEntry("Host", "web1")
                .containsEntry("EventId", "1");
    }

    @Test
    void theProbeGivesTheEventsNamespace() {
        assertThat(ExtractionPreview.values(transform(ExtractionXslt.NAMESPACE_PROBE, EVENT)))
                .containsExactly(Map.entry(ExtractionXslt.PROBE_NAMESPACE, "event-logging:3"));
    }

    @Test
    void whatAnXsltWasWrittenFromIsReadBackUnlessItWasChanged() {
        // Including an XPath with a "--", which can't be in an XML comment as it is.
        final List<ExtractionField> fields = List.of(
                new ExtractionField("Organisation", "EventSource/User/Organisation", null),
                new ExtractionField("Odd", "Data[@Name = 'a--b']/@Value", "text"));
        final String xslt = ExtractionXslt.xslt("event-logging:3", fields);

        assertThat(ExtractionXslt.read(xslt)).contains(new ExtractionXslt.Generated("event-logging:3", fields));
        assertThat(ExtractionXslt.read(xslt.replace("\n", "\r\n") + "\n")).isPresent();
        // Changed by hand: its own now.
        assertThat(ExtractionXslt.read(xslt.replace("<record>", "<record><!-- mine -->"))).isEmpty();
        assertThat(ExtractionXslt.read("<xsl:stylesheet/>")).isEmpty();
        assertThat(ExtractionXslt.read(null)).isEmpty();
        // Well formed, with the comment still valid XML.
        assertThat(transform(xslt, EVENT)).contains("records:2");
    }

    @Test
    void namesAndXPathsAreEscaped() {
        final String xslt = ExtractionXslt.xslt("a\"b", List.of(new ExtractionField("A & B", "x[. < 3]", null)));

        assertThat(xslt)
                .contains("xpath-default-namespace=\"a&quot;b\"")
                .contains("<data name=\"A &amp; B\">")
                .contains("select=\"string((x[. &lt; 3])[1])\"");
    }

    static String transform(final String xslt, final String xml) {
        try {
            final Processor processor = new Processor(false);
            final XsltTransformer transformer = processor.newXsltCompiler()
                    .compile(new StreamSource(new StringReader(xslt)))
                    .load();
            transformer.setSource(new StreamSource(new StringReader(xml)));
            final StringWriter out = new StringWriter();
            final Serializer serializer = processor.newSerializer(out);
            transformer.setDestination(serializer);
            transformer.transform();
            return out.toString();
        } catch (final SaxonApiException e) {
            throw new IllegalStateException(e);
        }
    }
}
