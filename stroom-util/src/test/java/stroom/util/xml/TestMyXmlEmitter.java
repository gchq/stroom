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

package stroom.util.xml;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.AttributesImpl;

import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Templates;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestMyXmlEmitter {

    private static final String NUL = "\u0000";
    private static final String HOSTILE = "a" + NUL + "<x/>&b" + NUL + "<y/>";
    private static final String HOSTILE_WITHOUT_NUL = "a<x/>&b<y/>";

    // Maps the section sign to a line break element, written unescaped by the serialiser.
    private static final String CHARACTER_MAP_XSLT = """
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:output method="xml" omit-xml-declaration="yes" use-character-maps="map"/>
              <xsl:character-map name="map">
                <xsl:output-character character="§" string="&lt;br/&gt;"/>
              </xsl:character-map>
              <xsl:template match="@*|node()">
                <xsl:copy><xsl:apply-templates select="@*|node()"/></xsl:copy>
              </xsl:template>
            </xsl:stylesheet>
            """;

    // Writes the text of the element with output escaping disabled.
    private static final String DISABLE_ESCAPING_XSLT = """
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
              <xsl:output method="xml" omit-xml-declaration="yes"/>
              <xsl:template match="m">
                <m><xsl:value-of select="." disable-output-escaping="yes"/></m>
              </xsl:template>
            </xsl:stylesheet>
            """;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aNulInTextDoesNotSwitchOffEscaping(final boolean preventEscapeSwitching) throws Exception {
        final String xml = writeText(preventEscapeSwitching, HOSTILE);

        assertThat(xml).isEqualTo("<m>a&lt;x/&gt;&amp;b&lt;y/&gt;</m>");
        assertThat(parse(xml).getTextContent()).isEqualTo(HOSTILE_WITHOUT_NUL);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aNulInAnAttributeDoesNotSwitchOffEscaping(final boolean preventEscapeSwitching) throws Exception {
        final TransformerHandler th = XMLUtil.createTransformerHandler(null, false, preventEscapeSwitching);
        final StringWriter writer = new StringWriter();
        th.setResult(new StreamResult(writer));
        final AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", "v", "v", "CDATA", HOSTILE + "\"");

        th.startDocument();
        th.startElement("", "m", "m", atts);
        th.endElement("", "m", "m");
        th.endDocument();

        final String xml = stripDeclaration(writer.toString());
        assertThat(xml).isEqualTo("<m v=\"a&lt;x/&gt;&amp;b&lt;y/&gt;&#34;\"/>");
        assertThat(parse(xml).getAttribute("v")).isEqualTo(HOSTILE_WITHOUT_NUL + "\"");
    }

    @Test
    void aNulIsNeverWrittenAsACharacterReference() throws Exception {
        final String xml = writeText(true, NUL);

        assertThat(xml).doesNotContain("&#x0;", "&#0;", NUL);
        assertThat(xml).isEqualTo("<m></m>");
    }

    @Test
    void otherControlCharactersAreStillWrittenAsReferences() throws Exception {
        final String xml = writeText(false, "a\u0007" + NUL + "b");

        assertThat(xml).isEqualTo("<m>a&#x7;b</m>");
    }

    @Test
    void aNulInTextWithEscapingDisabledIsDropped() throws Exception {
        final String xml = transform(false, DISABLE_ESCAPING_XSLT, "<b>" + NUL + "</b>");

        assertThat(xml).isEqualTo("<m><b></b></m>");
    }

    @Test
    void aNulInACommentIsDropped() throws Exception {
        final TransformerHandler th = XMLUtil.createTransformerHandler(null, false, false);
        final StringWriter writer = new StringWriter();
        th.setResult(new StreamResult(writer));
        final char[] comment = ("a" + NUL + "b").toCharArray();

        th.startDocument();
        th.startElement("", "m", "m", new AttributesImpl());
        th.comment(comment, 0, comment.length);
        th.endElement("", "m", "m");
        th.endDocument();

        assertThat(stripDeclaration(writer.toString())).isEqualTo("<m><!--ab--></m>");
    }

    @Test
    void aCharacterMapIsStillWrittenUnescaped() throws Exception {
        final String xml = writeTextWithCharacterMap(false, "x§y<z/>");

        assertThat(xml).isEqualTo("<m>x<br/>y&lt;z/&gt;</m>");
    }

    @Test
    void aNulInDataCannotPassForACharacterMapMarker() throws Exception {
        final String xml = writeTextWithCharacterMap(false, "a" + NUL + "<x/>&§" + NUL + "<y/>");

        assertThat(xml).isEqualTo("<m>a&lt;x/&gt;&amp;<br/>&lt;y/&gt;</m>");
    }

    @Test
    void aCharacterMapInAnAttributeStillUsesMarkers() throws Exception {
        final SAXTransformerFactory stf = (SAXTransformerFactory) TransformerFactoryFactory.newInstance(false);
        final Templates templates = stf.newTemplates(new StreamSource(new StringReader(CHARACTER_MAP_XSLT)));
        final TransformerHandler th = stf.newTransformerHandler(templates);
        final StringWriter writer = new StringWriter();
        th.setResult(new StreamResult(writer));
        final AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("", "v", "v", "CDATA", NUL + "<x/>§");

        th.startDocument();
        th.startElement("", "m", "m", atts);
        th.endElement("", "m", "m");
        th.endDocument();

        assertThat(writer.toString()).isEqualTo("<m v=\"&lt;x/&gt;<br/>\"/>");
    }

    @Test
    void preventEscapeSwitchingEscapesACharacterMap() throws Exception {
        final String xml = writeTextWithCharacterMap(true, "x§y");

        assertThat(xml).isEqualTo("<m>x&lt;br/&gt;y</m>");
    }

    @Test
    void removeNulReturnsTheSameSequenceWhenThereIsNoNul() {
        final String chars = "abc";

        assertThat(MyXmlEmitter.removeNul(chars)).isSameAs(chars);
        assertThat(MyXmlEmitter.removeNul(null)).isNull();
    }

    @Test
    void removeNulRemovesEveryNul() {
        assertThat(MyXmlEmitter.removeNul(NUL + "a" + NUL + NUL + "b" + NUL).toString()).isEqualTo("ab");
        assertThat(MyXmlEmitter.removeNul(NUL).toString()).isEmpty();
    }

    private String writeText(final boolean preventEscapeSwitching, final String text) throws Exception {
        final TransformerHandler th = XMLUtil.createTransformerHandler(null, false, preventEscapeSwitching);
        final StringWriter writer = new StringWriter();
        th.setResult(new StreamResult(writer));
        writeElement(th, text);
        return stripDeclaration(writer.toString());
    }

    private String writeTextWithCharacterMap(final boolean preventEscapeSwitching,
                                             final String text) throws Exception {
        return transform(preventEscapeSwitching, CHARACTER_MAP_XSLT, text);
    }

    private String transform(final boolean preventEscapeSwitching,
                             final String xslt,
                             final String text) throws Exception {
        final SAXTransformerFactory stf = (SAXTransformerFactory) TransformerFactoryFactory
                .newInstance(preventEscapeSwitching);
        final Templates templates = stf.newTemplates(new StreamSource(new StringReader(xslt)));
        final TransformerHandler th = stf.newTransformerHandler(templates);
        final StringWriter writer = new StringWriter();
        th.setResult(new StreamResult(writer));
        writeElement(th, text);
        return writer.toString();
    }

    private void writeElement(final TransformerHandler th, final String text) throws Exception {
        th.startDocument();
        th.startElement("", "m", "m", new AttributesImpl());
        characters(th, text);
        th.endElement("", "m", "m");
        th.endDocument();
    }

    private void characters(final TransformerHandler th, final String text) throws Exception {
        final char[] chars = text.toCharArray();
        th.characters(chars, 0, chars.length);
    }

    private String stripDeclaration(final String xml) {
        return xml.replaceFirst("^<\\?xml[^>]*\\?>", "");
    }

    private Element parse(final String xml) throws Exception {
        final DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        final Document document = dbf.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        return document.getDocumentElement();
    }
}
