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

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.ai.impl.tools.validation.ValidationException;
import stroom.ai.impl.tools.validation.XsltChecks;
import stroom.ai.impl.tools.validation.XsltCompileCheck;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/// check_xslt, the tool: its checks wired together, its import lookup and the schemas it reads. Ported from the
/// Stroom MCP server's `tests/test_validation.py`, `tests/test_saxon.py`, `tests/test_schema_locations.py` and
/// `tests/test_formats.py` (the cases that call check_xslt; the checks themselves are [XsltChecks]'s, tested with
/// it).
class TestCheckXsltTool {

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

    private final HeldSchemas heldSchemas = Mockito.mock(HeldSchemas.class);
    private final DocFinder docFinder = Mockito.mock(DocFinder.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final XsltCompileCheck compileCheck = Mockito.mock(XsltCompileCheck.class);
    private final CheckXsltTool tool = new CheckXsltTool(heldSchemas, () -> docFinder, documentAccess,
            compileCheck);

    @BeforeEach
    void setUp() {
        // This Stroom holds no event-logging schema.
        Mockito.when(heldSchemas.translationVersion(any(), any()))
                .thenThrow(new ValidationException(HeldSchemas.NO_EVENT_LOGGING));
        Mockito.when(heldSchemas.eventSchema(anyString()))
                .thenThrow(new ValidationException(HeldSchemas.NO_EVENT_LOGGING));
        Mockito.when(documentAccess.canView(any())).thenReturn(true);
        Mockito.when(compileCheck.compile(anyString())).thenReturn(new XsltCompileCheck.Compiled(List.of()));
    }

    @Test
    void anXsltThatPassesTheChecksIsCompiledAsStroomCompilesIt() {
        Mockito.when(docFinder.findByName(eq(XsltDoc.TYPE), eq("IP Lookup"), eq(false))).thenReturn(List.of(
                new DocRef(XsltDoc.TYPE, "x1", "IP Lookup")));
        Mockito.when(compileCheck.compile(anyString())).thenReturn(new XsltCompileCheck.Compiled(List.of(
                "XPST0008 Variable kv has not been declared")));

        final Map<String, Object> result = call(XSLT);

        assertThat(result.get("ok")).isEqualTo(false);
        assertThat(result.get("errors")).isEqualTo(List.of(
                "Does not compile: XPST0008 Variable kv has not been declared"));
    }

    @Test
    void anXsltWithOtherErrorsIsNotCompiled() {
        // Its missing import would only be reported again.
        call(XSLT);

        Mockito.verifyNoInteractions(compileCheck);
    }

    @Test
    void checkXsltReportsMissingImportsAndUnknownFunctions() {
        final Map<String, Object> result = call(XSLT.replace("stroom:lookup", "stroom:lookp"));

        assertThat(result.get("ok")).isEqualTo(false);
        // An unknown function is an error (it would not compile), with the nearest real name.
        assertThat(result.get("errors")).isEqualTo(List.of(
                "stroom:lookp() is not a Stroom function and will not compile; did you mean stroom:lookup()?",
                "xsl:import/include targets not found as XSLT documents: IP Lookup"));
        // Stroom here has no event-logging schema, so element names go unchecked and the check says so.
        assertThat(result.get("warnings")).isEqualTo(List.of("Event-logging element names not checked: This Stroom "
                                                             + "holds no event-logging XML schema (System/XML "
                                                             + "Schemas): ask the user to import Stroom's "
                                                             + "event-logging schema pack first."));
        assertThat(result.get("imports")).isEqualTo(List.of("IP Lookup"));
        assertThat(result.get("stroom_functions")).isEqualTo(List.of("format-date", "lookp"));
    }

    @Test
    void anImportIsFoundByItsExactNameAmongTheXsltsTheUserCanView() {
        Mockito.when(docFinder.findByName(eq(XsltDoc.TYPE), eq("IP Lookup"), eq(false))).thenReturn(List.of(
                new DocRef(XsltDoc.TYPE, "x1", "IP Lookup")));
        assertThat(call(XSLT).get("errors")).isEqualTo(List.of());

        // One the user can't view isn't there, for them.
        Mockito.when(documentAccess.canView(any())).thenReturn(false);
        assertThat(call(XSLT).get("errors")).isEqualTo(List.of("xsl:import/include targets not found as XSLT "
                                                              + "documents: IP Lookup"));

        // Stroom imports by the exact name.
        Mockito.when(documentAccess.canView(any())).thenReturn(true);
        Mockito.when(docFinder.findByName(eq(XsltDoc.TYPE), eq("IP Lookup"), eq(false))).thenReturn(List.of(
                new DocRef(XsltDoc.TYPE, "x1", "ip lookup")));
        assertThat(call(XSLT).get("errors")).isEqualTo(List.of("xsl:import/include targets not found as XSLT "
                                                              + "documents: IP Lookup"));
    }

    @Test
    void checkXsltRejectsMalformedXml() {
        final Map<String, Object> result = call("<xsl:stylesheet");

        assertThat(result.get("ok")).isEqualTo(false);
        assertThat(String.valueOf(((List<?>) result.get("errors")).getFirst())).contains("Not well-formed");
    }

    @Test
    void xmlSentHtmlEscapedIsReadUnescapedAndSaidSo() {
        // Qwen in VS Code sent &lt;?xml ... and was told only "Start tag expected, '<' not found".
        final Map<String, Object> plain = call(XSLT);
        final Map<String, Object> escaped = call(XSLT.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;"));

        assertThat(escaped.get("errors")).isEqualTo(plain.get("errors"));
        assertThat(((List<?>) escaped.get("warnings")).getFirst()).isEqualTo(XsltChecks.ESCAPED);
    }

    @Test
    void anXsltThatReadsAFileIsRefused() {
        final Map<String, Object> result = call(XSLT.replace("<xsl:import href=\"IP Lookup\"/>", "")
                .replace("<Data Name", "<xsl:value-of select=\"document('lookup.xml')/x\"/><Data Name"));

        assertThat(result.get("ok")).isEqualTo(false);
        assertThat((List<?>) result.get("errors")).anySatisfy(error ->
                assertThat(String.valueOf(error)).contains("document()"));
    }

    @Test
    void anXsltReadingItsOwnRecordsBareIsFineForXmlInNoNamespace() {
        final String hand = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"2.0\">"
                            + "<xsl:template match=\"records\"><xsl:apply-templates select=\"record\"/>"
                            + "</xsl:template></xsl:stylesheet>";

        final List<?> flagged = (List<?>) call(hand).get("errors");
        final List<?> fine = (List<?>) PortedToolCalls.json(tool.execute(PortedToolCalls.call(CheckXsltTool.NAME,
                        JsonUtil.writeValueAsString(Map.of("xslt", hand, "input_namespace", ""), false)),
                PortedToolCalls.context()).text()).get("errors");

        assertThat(flagged).anySatisfy(error -> assertThat(String.valueOf(error)).contains("namespace records:2"));
        assertThat(fine).noneSatisfy(error -> assertThat(String.valueOf(error)).contains("namespace records:2"));
    }

    @Test
    void aSchemaLocationThisStroomDoesntHoldIsRefused() {
        Mockito.when(heldSchemas.locationProblems(any())).thenReturn(List.of("xsi:schemaLocation names "
                                                                             + "file:///event-logging-v4.1.0.xsd: "
                                                                             + "write it with two slashes"));
        final String located = XSLT.replace("<Event>", "<Event xmlns:xsi=\"http://www.w3.org/2001/"
                                                       + "XMLSchema-instance\" xsi:schemaLocation=\"event-logging:3 "
                                                       + "file:///event-logging-v4.1.0.xsd\">");

        final Map<String, Object> result = call(located);

        assertThat(result.get("ok")).isEqualTo(false);
        assertThat((List<?>) result.get("errors")).anySatisfy(error ->
                assertThat(String.valueOf(error)).contains("two slashes"));

        Mockito.when(heldSchemas.locationProblems(any())).thenThrow(new ValidationException("no schemas"));
        assertThat(String.valueOf(call(located).get("warnings"))).contains("Schema locations not checked: no schemas");
    }

    @Test
    void theEventLoggingVersionIsTheOneAskedForElseTheOneTheXsltWrites() {
        call(XSLT.replace("<Event>", "<Events Version=\"4.1.0\"><Event>").replace("</Event>", "</Event></Events>"));
        Mockito.verify(heldSchemas).eventSchema("4.1.0");

        PortedToolCalls.json(tool.execute(PortedToolCalls.call(CheckXsltTool.NAME, JsonUtil.writeValueAsString(
                Map.of("xslt", XSLT, "schema_version", "3.5.2"), false)), PortedToolCalls.context()).text());
        Mockito.verify(heldSchemas).eventSchema("3.5.2");
    }

    @Test
    void anXsltIsNeededToCheck() {
        final ToolResult result = tool.execute(PortedToolCalls.call(CheckXsltTool.NAME, "{}"),
                PortedToolCalls.context());

        assertThat(result.isError()).isTrue();
        assertThat(result.text()).startsWith("Give xslt");
    }

    private Map<String, Object> call(final String xslt) {
        return PortedToolCalls.json(tool.execute(PortedToolCalls.call(CheckXsltTool.NAME,
                JsonUtil.writeValueAsString(Map.of("xslt", xslt), false)), PortedToolCalls.context()).text());
    }
}
