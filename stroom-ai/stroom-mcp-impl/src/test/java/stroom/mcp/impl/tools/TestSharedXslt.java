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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestSharedXslt {

    private static final String SHARED = """
            <xsl:stylesheet xmlns="event-logging:3" xmlns:stroom="stroom" \
            xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="3.0">
              <xsl:template name="eventSourceDevice">
                <xsl:param name="ip" />
                <Device><HostName><xsl:value-of select="stroom:meta('MyHostName')" /></HostName>
                  <xsl:if test="$ip"><IPAddress><xsl:value-of select="$ip" /></IPAddress></xsl:if></Device>
              </xsl:template>
              <xsl:template name="eventMeta"><Meta><Source><Id><xsl:value-of select="stroom:meta('GUID')" /></Id>\
            </Source></Meta></xsl:template>
              <xsl:template name="unused"><xsl:param name="x" required="yes" /><Data Name="x" /></xsl:template>
              <xsl:template match="Event" mode="decorate"><xsl:copy-of select="." /></xsl:template>
            </xsl:stylesheet>""";
    private static final String CALLER = """
            <xsl:stylesheet xmlns="event-logging:3" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="3.0">
              <xsl:import href="Common-Event-V1" />
              <xsl:import href="Missing-V1" />
              <xsl:template match="record">
                <Event>
                  <xsl:call-template name="eventMeta" />
                  <EventSource><System><Name>Acme</Name></System>
                    <xsl:call-template name="eventSourceDevice"><xsl:with-param name="ip" \
            select="data[@name='ip']/@value" /></xsl:call-template>
                  </EventSource>
                </Event>
              </xsl:template>
            </xsl:stylesheet>""";
    private static final String SHARED_JSON = """
            <xsl:stylesheet xmlns="http://www.w3.org/2005/xpath-functions" xmlns:stroom="stroom"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="3.0">
              <xsl:template name="guid"><string key="guid">g-1</string></xsl:template>
              <xsl:template name="stroomMeta"><map key="stroom"><string key="feed">F</string></map></xsl:template>
            </xsl:stylesheet>""";

    @Test
    @SuppressWarnings("unchecked")
    void testASharedXsltIsReadForWhatEachTemplateWritesReadsAndTakes() {
        final Map<String, Object> found = SharedXslt.describe(SHARED);
        final Map<String, Object> templates = (Map<String, Object>) found.get("templates");
        final Map<String, Object> device = new LinkedHashMap<>();
        device.put("writes", List.of("Device"));
        device.put("paths", List.of("Device/HostName", "Device/IPAddress"));
        device.put("reads_meta", List.of("MyHostName"));
        device.put("params", Map.of("ip", "optional"));
        assertThat(templates.get("eventSourceDevice")).isEqualTo(device);
        assertThat(((Map<String, Object>) templates.get("eventMeta")).get("paths"))
                .isEqualTo(List.of("Meta/Source/Id"));
        assertThat(((Map<String, Object>) templates.get("unused")).get("params")).isEqualTo(Map.of("x", "required"));
        final Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("match", "Event");
        rule.put("mode", "decorate");
        rule.put("writes", List.of());
        assertThat(found.get("template_rules")).isEqualTo(List.of(rule));
    }

    @Test
    void testCallsArePlacedByTheElementsRoundThemWithTheParametersPassed() {
        assertThat(SharedXslt.importsOf(CALLER)).containsExactly("Common-Event-V1", "Missing-V1");
        final Map<String, String> shared = new HashMap<>();
        shared.put("Common-Event-V1", SHARED);
        shared.put("Missing-V1", null);
        final Map<Object, Map<String, Object>> by = new HashMap<>();
        SharedXslt.usage(CALLER, shared).forEach(use -> by.put(use.get("template"), use));
        assertThat(by.get("eventSourceDevice").get("at")).isEqualTo(List.of("EventSource/Device"));
        assertThat(by.get("eventMeta").get("at")).isEqualTo(List.of("Meta"));
        assertThat(by.get("eventSourceDevice").get("with_params")).isEqualTo(Map.of("ip", "data[@name='ip']/@value"));
        assertThat(by.get("eventSourceDevice").get("reads_meta")).isEqualTo(List.of("MyHostName"));
        final Map<String, Object> missing = new LinkedHashMap<>();
        missing.put("href", "Missing-V1");
        missing.put("template", null);
        missing.put("note", "not found as an XSLT document");
        assertThat(by.get(null)).isEqualTo(missing);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAnIndexingXsltsSharedTemplatesAreReadByTheirJsonKeys() {
        final Map<String, Object> templates = (Map<String, Object>) SharedXslt.describe(SHARED_JSON).get("templates");
        assertThat(((Map<String, Object>) templates.get("stroomMeta")).get("paths")).isEqualTo(List.of("stroom/feed"));
        assertThat(((Map<String, Object>) templates.get("guid")).get("writes")).isEqualTo(List.of("guid"));
    }

    @Test
    void testSharedFunctionsAreDescribedWithTheirNamespaceAndHowImportersCallThem() {
        final String shared = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                              + "xmlns:gs=\"urn:gs\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" version=\"3.0\">"
                              + "<xsl:function name=\"gs:isLocalIpAddress\" as=\"xs:boolean\">"
                              + "<xsl:param name=\"ipAddress\" as=\"xs:string\"/>"
                              + "<xsl:sequence select=\"starts-with($ipAddress, '10.')\"/></xsl:function>"
                              + "</xsl:stylesheet>";
        final Map<String, Object> function = new LinkedHashMap<>();
        function.put("prefix", "gs");
        function.put("namespace", "urn:gs");
        function.put("params", List.of("ipAddress as xs:string"));
        function.put("returns", "xs:boolean");
        assertThat(SharedXslt.describe(shared).get("functions")).isEqualTo(Map.of("gs:isLocalIpAddress", function));
        final String caller = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                              + "xmlns:gs=\"urn:gs\" version=\"3.0\"><xsl:import href=\"IP Lookup\"/>"
                              + "<xsl:template match=\"record\"><xsl:if test=\"not(gs:isLocalIpAddress($sourceIp))\"/>"
                              + "</xsl:template></xsl:stylesheet>";
        final List<Map<String, Object>> uses = SharedXslt.usage(caller, Map.of("IP Lookup", shared));
        assertThat(uses).hasSize(1);
        final Map<String, Object> use = uses.getFirst();
        assertThat(use.get("function")).isEqualTo("gs:isLocalIpAddress");
        assertThat(use.get("namespace")).isEqualTo("urn:gs");
        assertThat(use.get("calls")).isEqualTo(1);
        assertThat(use.get("example")).isEqualTo("not(gs:isLocalIpAddress($sourceIp))");
        assertThat(use).doesNotContainKey("own_copy");
    }

    @Test
    void testACallInAPartTemplateIsPlacedWhereThatTemplateIsApplied() {
        // Seen live: the house style calls shared templates inside a networkSource mode, applied in
        // Network/Connect/Source.
        final String caller = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                              + "xmlns=\"event-logging:3\" version=\"3.0\">"
                              + "<xsl:template match=\"node()\" mode=\"event\"><Event><EventDetail><Network><Connect>"
                              + "<Source><xsl:apply-templates select=\".\" mode=\"networkSource\"/></Source>"
                              + "</Connect></Network></EventDetail></Event></xsl:template>"
                              + "<xsl:template match=\"node()\" mode=\"networkSource\"><Device>"
                              + "<xsl:call-template name=\"ipAddressToLocation\"/></Device></xsl:template>"
                              + "</xsl:stylesheet>";
        final List<Map<String, Object>> calls = SharedXslt.callsOf(caller,
                Map.of("ipAddressToLocation", List.of("Location")));
        assertThat(calls).hasSize(1);
        assertThat(calls.getFirst().get("within")).isEqualTo("EventDetail/Network/Connect/Source/Device");
        assertThat(calls.getFirst().get("at")).isEqualTo(List.of("EventDetail/Network/Connect/Source/Device/Location"));
    }

    @Test
    void testASharedXsltThatDoesntParseIsNotAFailure() {
        final String xslt = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">"
                            + "<xsl:import href=\"Shared-V1\"/><xsl:template match=\"/\">"
                            + "<xsl:call-template name=\"x\"/></xsl:template></xsl:stylesheet>";
        assertThat(SharedXslt.usage(xslt, Map.of("Shared-V1", "<xsl:stylesheet not well formed")))
                .hasSize(1)
                .first()
                .satisfies(use -> assertThat(use.get("href")).isEqualTo("Shared-V1"));
        assertThat(SharedXslt.describe("<xsl:stylesheet not well formed")).isEqualTo(Map.of("unreadable", true));
    }
}
