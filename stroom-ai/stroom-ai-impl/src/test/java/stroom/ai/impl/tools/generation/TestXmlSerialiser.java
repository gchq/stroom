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

package stroom.ai.impl.tools.generation;

import stroom.ai.impl.tools.validation.XmlTrees;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The small XML tree written as the golden XSLTs have it: the expected texts fix where it declares namespaces, the
/// prefixes it makes up, escaping and pretty-printing.
class TestXmlSerialiser {

    private static final String XSL = XmlTrees.XSL;
    private static final String EVT = XsltGenerator.EVT;

    @Test
    void anElementOnItsOwnDeclaresItsNamespaceAndEscapesAsLxml() {
        final XmlElement function = XmlElement.create(XSL, "function", "name", "mcp:data");
        function.append(new XmlComment(" hi "));
        final XmlElement test = function.addElement(XSL, "if", "test", "a < b & c > \"d\" 'e'\n\tx\r");
        test.addElement(EVT, "Data", "Name", "{$name}");
        function.addElement(EVT, "Data", "Name", "x").setText("a < & > \" ' \r é");
        assertThat(XmlSerialiser.toXml(function)).isEqualTo(
                "<xsl:function xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" name=\"mcp:data\"><!-- hi -->"
                + "<xsl:if test=\"a &lt; b &amp; c &gt; &quot;d&quot; 'e'&#10;&#9;x&#13;\"><ns0:Data "
                + "xmlns:ns0=\"event-logging:3\" Name=\"{$name}\"/></xsl:if><ns1:Data xmlns:ns1=\"event-logging:3\" "
                + "Name=\"x\">a &lt; &amp; &gt; \" ' &#13; é</ns1:Data></xsl:function>");
    }

    @Test
    void movedIntoAStylesheetItTakesTheStylesheetsDeclarationsAndPrettyPrints() {
        final Map<String, String> namespaces = new LinkedHashMap<>();
        namespaces.put(null, EVT);
        namespaces.put("xsl", XSL);
        namespaces.put("xsi", MappingFacts.XSI);
        namespaces.put("stroom", "stroom");
        namespaces.put("xs", MappingFacts.XS);
        namespaces.put("gs", "urn:gs");
        final XmlElement sheet = XmlElement.create(XSL, "stylesheet", namespaces);
        sheet.set("version", "3.0");
        sheet.set("xpath-default-namespace", "records:2");
        final XmlElement function = XmlElement.create(XSL, "function", "name", "mcp:data");
        function.append(new XmlComment(" hi "));
        function.addElement(XSL, "if", "test", "t").addElement(EVT, "Data", "Name", "{$name}");
        function.addElement(EVT, "Data", "Name", "x").setText("text");
        sheet.append(function);
        final XmlElement events = sheet.addElement(EVT, "Events", "Version", "4");
        events.set(MappingFacts.XSI, "schemaLocation", "x");
        events.addElement(XSL, "apply-templates", "select", "r");
        assertThat(XmlSerialiser.toPrettyDocument(sheet)).isEqualTo("""
                <?xml version='1.0' encoding='UTF-8'?>
                <xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" \
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" \
                xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:gs="urn:gs" version="3.0" \
                xpath-default-namespace="records:2">
                  <xsl:function name="mcp:data">
                    <!-- hi -->
                    <xsl:if test="t">
                      <Data Name="{$name}"/>
                    </xsl:if>
                    <Data Name="x">text</Data>
                  </xsl:function>
                  <Events Version="4" xsi:schemaLocation="x">
                    <xsl:apply-templates select="r"/>
                  </Events>
                </xsl:stylesheet>
                """);
    }

    @Test
    void anElementInsertedIntoAFragmentKeepsItsOwnDeclaration() {
        final XmlElement fragment = XmlElement.create(null, "fragment");
        final XmlElement test = fragment.addElement(XSL, "if", "test", "t");
        test.addElement(EVT, "A").addElement(EVT, "B");
        fragment.addElement(EVT, "C");
        fragment.insert(0, XmlElement.create(XSL, "variable", "name", "v"));
        assertThat(XmlSerialiser.toXml(fragment)).isEqualTo(
                "<fragment><xsl:variable xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" name=\"v\"/><xsl:if "
                + "xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" test=\"t\"><ns0:A xmlns:ns0=\"event-logging:3\">"
                + "<ns0:B/></ns0:A></xsl:if><ns1:C xmlns:ns1=\"event-logging:3\"/></fragment>");
    }

    @Test
    void blankTextIsLeftOutAsLibxml2LeavesItOut() {
        final XmlElement root = XmlParser.parse("<a>\n  <b> </b>\n  <!-- c -->\n  <d>x <e/> </d>\n</a>", true, true);
        assertThat(XmlSerialiser.toPrettyXml(root)).isEqualTo("<a>\n  <b> </b>\n  <d>x <e/> </d>\n</a>\n");
    }

    @Test
    void anElementWrittenAloneDeclaresTheNamespacesOfItsNameAndAttributesFirst() {
        // The element copied, declaring what it uses, then its ancestors' declarations added: seen reading back an
        // xsl:for-each, whose text the reader compares.
        final XmlElement root = XmlParser.parse("<a xmlns=\"d\" xmlns:x=\"X\" xmlns:y=\"Y\"><b><x:c y:at=\"1\" "
                                                + "q=\"2\"><e/></x:c></b></a>");
        final XmlElement c = root.childElements().getFirst().childElements().getFirst();
        assertThat(XmlSerialiser.toXml(c))
                .isEqualTo("<x:c xmlns:x=\"X\" xmlns:y=\"Y\" xmlns=\"d\" y:at=\"1\" q=\"2\"><e/></x:c>");
        assertThat(XmlSerialiser.toXml(root.childElements().getFirst()))
                .isEqualTo("<b xmlns=\"d\" xmlns:x=\"X\" xmlns:y=\"Y\"><x:c y:at=\"1\" q=\"2\"><e/></x:c></b>");
    }
}
