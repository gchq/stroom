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

import stroom.ai.impl.tools.validation.XsltChecks.FunctionProblems;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Guards against the mistakes a model makes when it writes an XSLT by hand: non-existent stroom: functions, no
/// input namespace, elements the event-logging schema has no place for, json-to-xml() unguarded, and XML sent
/// HTML-escaped. Ported from the Stroom MCP server's `tests/test_authoring_guards.py` and
/// `tests/test_validation.py` (the parts of `check_xslt` that need no Stroom).
class TestXsltChecks {

    private static final String HANDWRITTEN = """
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="event-logging:3"
             xmlns:stroom="stroom" version="3.0">
              <xsl:template match="/map">
                <Events Version="4.1.0"><xsl:apply-templates select="map"/></Events>
              </xsl:template>
              <xsl:template match="map">
                <Event>
                  <EventTime><TimeCreated><xsl:value-of select="stroom:format-date(string[@key='ts'])"/>\
            </TimeCreated></EventTime>
                  <EventSource><System><Name>x</Name></System><Generator>g</Generator><Device><HostName>h\
            </HostName></Device></EventSource>
                  <EventDetail><TypeId>t</TypeId>
                    <ServerEvent><Message><xsl:value-of select="stroom:json-parse(string[@key='message'])"/>\
            </Message></ServerEvent>
                  </EventDetail>
                </Event>
              </xsl:template>
            </xsl:stylesheet>""";
    private static final String XSLT = """
            <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="event-logging:3"
             xmlns:stroom="stroom" xpath-default-namespace="records:2" version="3.0">
              <xsl:import href="IP Lookup"/>
              <xsl:template match="record">
                <Event>
                  <EventTime><TimeCreated><xsl:value-of select="stroom:format-date(data[@name='time']/@value)"/>\
            </TimeCreated></EventTime>
                  <EventSource><System><Name>ACME</Name></System>
                    <User><Id><xsl:value-of select="data[@name='user']/@value"/></Id></User></EventSource>
                  <Data Name="host" Value="{data[@name='host']/@value}"/>
                  <xsl:variable name="m" select="stroom:lookup('USER_MAP', data[@name='user']/@value)"/>
                </Event>
              </xsl:template>
            </xsl:stylesheet>""";

    private static final EventSchema SCHEMA = EventSchema.parse(HeldSchemasWorld.XSD_4_1_0);

    @Test
    void testTheThreeHandwritingMistakesAreNamed() {
        final Element root = XmlTrees.parse(HANDWRITTEN);
        final FunctionProblems functions = XsltChecks.functionProblems(HANDWRITTEN, root);
        assertThat(functions.errors()).singleElement().asString()
                .startsWith("stroom:json-parse() is not a Stroom function")
                .contains("json-to-xml(text)");
        assertThat(functions.calls()).containsExactly("format-date", "json-parse");
        final List<String> namespaces = XsltChecks.namespaceProblems(root, null);
        assertThat(namespaces).singleElement().asString()
                .startsWith("match=\"/map\", select=\"map\", match=\"map\"")
                .contains("xpath-default-namespace=\"http://www.w3.org/2013/XSL/json\"");
        final List<String> elements = XsltChecks.eventElementProblems(root, SCHEMA);
        assertThat(elements).singleElement().asString()
                .startsWith("Event/EventDetail/ServerEvent is not in the event-logging schema")
                // What EventDetail does allow.
                .contains("'Authenticate'");
    }

    @Test
    void testAnUnknownFunctionComesBackWithTheNearestRealName() {
        final String xslt = XSLT.replace("stroom:lookup", "stroom:lookp");
        assertThat(XsltChecks.functionProblems(xslt, XmlTrees.parse(xslt)).errors()).containsExactly(
                "stroom:lookp() is not a Stroom function and will not compile; did you mean stroom:lookup()?");
    }

    @Test
    void testStroomFunctionsNeedTheirNamespaceDeclared() {
        final String xslt = XSLT.replace(" xmlns:stroom=\"stroom\"", "");
        assertThat(XsltChecks.functionProblems(xslt, XmlTrees.parse(xslt)).errors())
                .contains("stroom: functions are used but xmlns:stroom=\"stroom\" is not declared");
    }

    @Test
    void testAnExplicitEmptyNamespaceAndRealElementsPass() {
        final String fixed = HANDWRITTEN.replace("version=\"3.0\"", "version=\"3.0\" xpath-default-namespace=\"\"")
                .replace("stroom:json-parse(", "json-to-xml(")
                .replace("ServerEvent", "Unknown")
                .replace("Message", "Data");
        final Element root = XmlTrees.parse(fixed);
        assertThat(XsltChecks.functionProblems(fixed, root).errors()).isEmpty();
        assertThat(XsltChecks.namespaceProblems(root, null)).isEmpty();
        assertThat(XsltChecks.eventElementProblems(root, SCHEMA)).isEmpty();
    }

    @Test
    void testAFragmentInANamedTemplateIsCheckedWhereTheSchemaAllowsIt() {
        final String sheet = """
                <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="event-logging:3" \
                version="3.0" xpath-default-namespace="records:2">
                  <xsl:template name="who"><User><Id>a</Id><Nickname>b</Nickname></User></xsl:template>
                </xsl:stylesheet>""";
        assertThat(XsltChecks.eventElementProblems(XmlTrees.parse(sheet), SCHEMA)).singleElement().asString()
                .startsWith("Nickname is not allowed below any of ['Event/EventSource/User', "
                            + "'Event/EventDetail/Authenticate/User'")
                .endsWith("] in the event-logging schema");
    }

    @Test
    void testAnElementTheSchemaHasNowhereIsSaidToBeSo() {
        final String sheet = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                             + "xmlns=\"event-logging:3\" version=\"3.0\"><xsl:template name=\"x\"><Nowhere/>"
                             + "</xsl:template></xsl:stylesheet>";
        assertThat(XsltChecks.eventElementProblems(XmlTrees.parse(sheet), SCHEMA))
                .containsExactly("No element 'Nowhere' exists anywhere in the event-logging schema");
    }

    @Test
    void testASourcesOwnRecordsAreNotTakenForADataSplittersOutput() {
        final String sheet = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">"
                             + "<xsl:template match=\"records/record\"><xsl:value-of select=\"data\"/>"
                             + "</xsl:template></xsl:stylesheet>";
        final Element root = XmlTrees.parse(sheet);
        assertThat(XsltChecks.namespaceProblems(root, null)).singleElement().asString()
                .contains("in namespace records:2 (a Data Splitter's output, or XML that declares records:2)");
        // input_namespace '': the build's sample is XML in no namespace.
        assertThat(XsltChecks.namespaceProblems(root, "")).isEmpty();
    }

    @Test
    void testJsonToXmlWithoutAGuardIsAWarning() {
        // Seen in a test environment: "empty sequence", a fatal error that stopped processing.
        final String sheet = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">"
                             + "<xsl:template match=\"/\"><xsl:variable name=\"j\" select=\"json-to-xml(.)\"/>"
                             + "<xsl:variable name=\"g\" select=\"if (normalize-space(.)) then json-to-xml(.) "
                             + "else ()\"/><xsl:try><xsl:sequence select=\"json-to-xml(.)\"/><xsl:catch/>"
                             + "</xsl:try></xsl:template></xsl:stylesheet>";
        assertThat(XsltChecks.unguardedJson(XmlTrees.parse(sheet))).singleElement().asString()
                .startsWith("json-to-xml without a guard (select=\"json-to-xml(.)\"): ")
                .contains("build_translation_xslt does this itself.");
    }

    @Test
    void testXmlSentHtmlEscapedIsReadUnescapedAndSaidSo() {
        // Qwen in VS Code sent &lt;?xml ... and was told only "Start tag expected, '<' not found".
        final String escaped = XSLT.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        final XsltChecks.Unescaped read = XsltChecks.unescaped(escaped);
        assertThat(read.text()).isEqualTo(XSLT);
        assertThat(read.warnings()).containsExactly(XsltChecks.ESCAPED);
        // Escaped text inside real XML is left.
        assertThat(XsltChecks.unescaped("<a>&lt;b&gt;</a>").text()).isEqualTo("<a>&lt;b&gt;</a>");
        assertThat(XsltChecks.unescaped("<a>&lt;b&gt;</a>").warnings()).isEmpty();
    }

    @Test
    void testXmlThatIsNotWellFormedIsRefusedSayingWhat() {
        assertThatThrownBy(() -> XsltChecks.parse("<xsl:stylesheet", "XSLT"))
                .isInstanceOf(ValidationException.class)
                .hasMessageStartingWith("XSLT is not well-formed XML: ");
    }

    @Test
    void testTheDefaultNamespaceInForceIsFoundOnTheElementOrAbove() {
        final Element root = XmlTrees.parse("<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                                            + "xpath-default-namespace=\"records:2\"><xsl:template/>"
                                            + "</xsl:stylesheet>");
        assertThat(XsltChecks.defaultNamespace(XmlTrees.children(root).getFirst())).isEqualTo("records:2");
        assertThat(XsltChecks.defaultNamespace(XmlTrees.parse("<a/>"))).isNull();
    }
}
