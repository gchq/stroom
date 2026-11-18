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

package stroom.ai.impl.workspace;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/// The events file fetch_events writes: XML 1.1, as Stroom writes its Events streams, so an event holding a control
/// character reference is kept as XML, as stored.
class TestEventsFile {

    private static final String HEADER = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>"
                                         + "<Events xmlns=\"event-logging:3\">";
    private static final String FOOTER = "</Events>";

    @Test
    void anXml11EventHoldingAFormFeedReferenceIsKeptAsXml() {
        // Seen in chat 181: the event was written as escaped text (&amp;#xc;), so no grep for &#xc; found it.
        final String element = EventsFile.element(73288, 219, HEADER
                                                              + "<Event><Data Value=\"a&#xc;b\">x&#x7;y&#x1b;z</Data>"
                                                              + "</Event>" + FOOTER);

        assertThat(element)
                .startsWith("  <stroom-event streamId=\"73288\" eventId=\"219\"><Events xmlns=\"event-logging:3\">")
                .contains("Value=\"a&#xc;b\"")
                .contains("x&#x7;y&#x1b;z")
                .doesNotContain("format=\"text\"")
                .doesNotContain("<?xml");
        final Element data = only(parse(file(element)), "Data");
        assertThat(data.getAttribute("Value")).isEqualTo("a\fb");
        assertThat(data.getTextContent()).isEqualTo("x\u0007y\u001bz");
    }

    @Test
    void theFileIsXml11() {
        assertThat(EventsFile.start()).isEqualTo("<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n<stroom-events>\n");
        assertThat(EventsFile.startsAsOne(EventsFile.start())).isTrue();
    }

    @Test
    void anXml10EventHoldingAFormFeedReferenceIsNotWellFormedSoIsKeptAsText() {
        final String element = EventsFile.element(1, 2, HEADER.replace("1.1", "1.0")
                                                        + "<Event><Data>a&#xc;b</Data></Event>" + FOOTER);

        assertThat(element).contains("format=\"text\"").contains("&amp;#xc;");
        assertThat(only(parse(file(element)), "stroom-event").getTextContent()).contains("<Data>a&#xc;b</Data>");
    }

    @Test
    void anXml10EventReadsTheSameInTheFile() {
        // Characters XML 1.0 holds as they are but XML 1.1 doesn't: C1 controls (NEL among them, which 1.1 reads
        // as a new line), and the line separator.
        final String element = EventsFile.element(1, 2, HEADER.replace("1.1", "1.0")
                                                        + "<Event><Data V=\"p\u0080q\">a\u0085b c</Data></Event>"
                                                        + FOOTER);

        assertThat(element).doesNotContain("format=\"text\"").contains("a&#x85;b&#x2028;c").contains("p&#x80;q");
        final Element data = only(parse(file(element)), "Data");
        assertThat(data.getTextContent()).isEqualTo("a\u0085b c");
        assertThat(data.getAttribute("V")).isEqualTo("p\u0080q");
    }

    @Test
    void aTextRecordIsEscapedForXml11() {
        // A raw line with an ANSI colour code, a NUL and a next line: the controls are kept as references, the NUL
        // (which no XML holds) left out.
        final String element = EventsFile.element(1, 2, "a <b> & \u001b[31mred\u0000\u0085end\tx");

        assertThat(element).contains("format=\"text\"")
                .contains("a &lt;b&gt; &amp; &#x1b;[31mred&#x85;end\tx");
        assertThat(only(parse(file(element)), "stroom-event").getTextContent())
                .isEqualTo("a <b> & \u001b[31mred\u0085end\tx");
    }

    private static String file(final String element) {
        return EventsFile.start() + element + EventsFile.end();
    }

    private static Document parse(final String text) {
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(text)));
        } catch (final Exception e) {
            throw new AssertionError("The events file doesn't parse: " + e.getMessage(), e);
        }
    }

    private static Element only(final Document document, final String localName) {
        final NodeList nodes = document.getElementsByTagNameNS("*", localName);
        assertThat(nodes.getLength()).isEqualTo(1);
        return (Element) nodes.item(0);
    }
}
