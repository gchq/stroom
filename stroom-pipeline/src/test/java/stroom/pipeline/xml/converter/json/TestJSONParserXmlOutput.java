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

package stroom.pipeline.xml.converter.json;

import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.FatalErrorReceiver;
import stroom.pipeline.writer.OutputStreamAppender;
import stroom.pipeline.writer.XMLWriter;
import stroom.util.xml.TransformerFactoryFactory;
import stroom.util.xml.XMLUtil;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/// JSON string values holding text that is hostile to XML (entities as typed, markup, CDATA, control characters)
/// taken the way a translation pipeline takes them: Stroom's JSONParser, an XSLT step (as the translationFilter)
/// and the pipeline's XMLWriter, whose output must be well-formed XML holding the value, escaped.
///
/// Seen in testing (an audit feed of JSON lines whose mail bodies were 20,000 to 46,000 characters): both Events
/// streams were not well-formed (an undeclared entity `&nbsp;`, an invalid character 0xC). The long-value test
/// below is expected to pass: the truncation path looks sound (see its comment). The NUL tests are expected to
/// FAIL on the current code: they reproduce what is believed to be the cause, raised here for a human to decide
/// whether to take upstream.
class TestJSONParserXmlOutput {

    // A mapping as the AI tools write one, reduced to the one field: each record's Content into an Event.
    private static final String MAPPING = """
            <xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xpath-default-namespace="http://www.w3.org/2013/XSL/json">
              <xsl:template match="/map">
                <Events>
                  <xsl:apply-templates select="map"/>
                </Events>
              </xsl:template>
              <xsl:template match="map">
                <Event><Content><xsl:value-of select="string[@key='Content']"/></Content></Event>
              </xsl:template>
            </xsl:stylesheet>
            """;

    private static final char NUL = 0;
    private static final char BELL = 7;
    private static final char FORM_FEED = 12;
    private static final char ESCAPE = 27;
    private static final String HOSTILE = "Entities as typed: &amp; &lt; &nbsp; &#x41; &unknown; & alone, a lone "
                                          + "< or >. <Event>not an element</Event> <![CDATA[ not a section ]]> "
                                          + "form feed " + FORM_FEED + ", bell " + BELL + ", escape " + ESCAPE
                                          + "[31m. ";
    private static final int DEFAULT_TRUNCATE_LENGTH = 10_000;

    @Test
    void aLongHostileValueIsCutAndWrittenEscapedOnce() throws Exception {
        // TruncatingStringWriter hands the ContentHandler jackson's own char[] (reused for each chunk), but every
        // receiver on this path copies what it is given (Saxon's ReceivingContentHandler, its ContentHandlerProxy,
        // the serialiser writes at once), and jackson's streaming readString() only calls write(char[], int, int),
        // which TruncatingStringWriter overrides. So the value should come out once, cut at 10,000, escaped.
        final String value = HOSTILE.repeat(25_000 / HOSTILE.length() + 1).substring(0, 25_000);

        final String xml = translate(json(value), false);

        // Throws, failing the test, if the output is not well-formed.
        final String content = contentOf(xml);
        assertThat(content)
                .as("the value cut at the default stringTruncateLength, once")
                .isEqualTo(value.substring(0, DEFAULT_TRUNCATE_LENGTH));
        assertThat(xml)
                .doesNotContain("&nbsp;")
                .doesNotContain("<![CDATA[")
                .doesNotContain(String.valueOf(FORM_FEED));
    }

    // SUSPECTED BUG: expected to fail. A NUL decoded from the JSON (U+0000, escaped in the source) reaches the
    // XMLWriter, whose preventEscapeSwitching is false by default: stroom.util.xml.MyXmlEmitter then takes the NUL
    // to switch output escaping off, and writes the rest of the text node (up to any next NUL) unescaped, so
    // `&nbsp;` and `<b>` come out as markup and a form feed as a raw 0xC, which XML 1.1 doesn't allow. Stroom's
    // InvalidXmlCharFilter takes control characters out of the raw input only; this one is made by the parser.
    @Test
    @Disabled("Reproduces a suspected core bug, to raise upstream: a NUL from data toggles MyXmlEmitter's "
            + "escape switching (or, prevented, is written as &#x0;). Enable once core is fixed.")
    void aValueHoldingANulIsWrittenWellFormed() throws Exception {
        final String value = "A NUL in text: before" + NUL + "after &nbsp; & alone, <b>bold</b>, form feed "
                             + FORM_FEED + ".";

        final String xml = translate(json(value), false);

        assertThatCode(() -> contentOf(xml))
                .as("well-formed output for a value holding a NUL, got: %s", xml)
                .doesNotThrowAnyException();
    }

    // SUSPECTED BUG, the narrower test: expected to fail. MyXmlEmitter (the serialiser behind XMLWriter) with
    // escape switching allowed, as XMLWriter's default is: the text after the NUL is written unescaped.
    @Test
    @Disabled("Reproduces a suspected core bug, to raise upstream: a NUL from data toggles MyXmlEmitter's "
            + "escape switching (or, prevented, is written as &#x0;). Enable once core is fixed.")
    void theSerialiserEscapesTextAfterANul() throws Exception {
        final String xml = serialise("before" + NUL + "after &nbsp; <b>", false);

        assertThat(xml).contains("after &amp;nbsp; &lt;b&gt;");
    }

    // SUSPECTED BUG, the narrower test with preventEscapeSwitching set: expected to fail too. The NUL is then
    // written as the character reference &#x0;, which neither XML 1.0 nor XML 1.1 allows, so the switch doesn't
    // make the output well-formed either.
    @Test
    @Disabled("Reproduces a suspected core bug, to raise upstream: a NUL from data toggles MyXmlEmitter's "
            + "escape switching (or, prevented, is written as &#x0;). Enable once core is fixed.")
    void theSerialiserWritesWellFormedXmlForANulWithEscapeSwitchingPrevented() throws Exception {
        final String xml = serialise("before" + NUL + "after", true);

        assertThatCode(() -> contentOf(xml))
                .as("well-formed output for text holding a NUL, got: %s", xml)
                .doesNotThrowAnyException();
    }

    // JSON lines of one record: the parser's root map (addRootObject, the pipeline's default) holds it.
    private static String translate(final String json, final boolean preventEscapeSwitching)
            throws IOException, SAXException, TransformerConfigurationException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());
        final XMLWriter xmlWriter = new XMLWriter(
                errorReceiverProxy,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        xmlWriter.setPreventEscapeSwitching(preventEscapeSwitching);
        xmlWriter.setTarget(new OutputStreamAppender(errorReceiverProxy, out));

        // The translation step, as XsltFilter runs it: a TransformerHandler whose result is the next filter.
        final SAXTransformerFactory factory = (SAXTransformerFactory) TransformerFactoryFactory.newInstance(true);
        final Templates templates = factory.newTemplates(new StreamSource(new StringReader(MAPPING)));
        final TransformerHandler translation = factory.newTransformerHandler(templates);
        translation.setResult(new SAXResult(xmlWriter));

        final JSONParser parser = new JSONParser(JSONFactoryConfig.builder().build(), true);
        parser.setContentHandler(translation);

        xmlWriter.startProcessing();
        try {
            parser.parse(new InputSource(new StringReader(json)));
        } finally {
            xmlWriter.endProcessing();
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    // One Content element holding the text, through the serialiser XMLWriter uses.
    private static String serialise(final String text, final boolean preventEscapeSwitching)
            throws SAXException, TransformerConfigurationException {
        final StringWriter out = new StringWriter();
        final TransformerHandler serialiser = XMLUtil.createTransformerHandler(null, false, preventEscapeSwitching);
        serialiser.setResult(new StreamResult(out));
        final char[] chars = text.toCharArray();
        serialiser.startDocument();
        serialiser.startElement("", "Content", "Content", new AttributesImpl());
        serialiser.characters(chars, 0, chars.length);
        serialiser.endElement("", "Content", "Content");
        serialiser.endDocument();
        return out.toString();
    }

    // The text of the Content elements; throws if the XML is not well-formed.
    private static String contentOf(final String xml)
            throws ParserConfigurationException, SAXException, IOException {
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        final ContentCollector collector = new ContentCollector();
        factory.newSAXParser().parse(new InputSource(new StringReader(xml)), collector);
        return collector.content.toString();
    }

    // One record of JSON lines, the value escaped as JSON escapes it (control characters as \\uXXXX).
    private static String json(final String value) {
        final StringBuilder out = new StringBuilder("{\"Id\": \"1\", \"Content\": \"");
        for (final char c : value.toCharArray()) {
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c < 0x20) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.append("\"}\n").toString();
    }


    // --------------------------------------------------------------------------------


    private static class ContentCollector extends DefaultHandler {

        private final StringBuilder content = new StringBuilder();
        private boolean inContent;

        @Override
        public void startElement(final String uri,
                                 final String localName,
                                 final String qName,
                                 final Attributes attributes) {
            inContent = "Content".equals(localName);
        }

        @Override
        public void endElement(final String uri, final String localName, final String qName) {
            inContent = false;
        }

        @Override
        public void characters(final char[] ch, final int start, final int length) {
            if (inContent) {
                content.append(ch, start, length);
            }
        }
    }
}
