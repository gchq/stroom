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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// What a translation XSLT does. Ported from the Stroom MCP server's `tests/test_validation.py`
/// (`test_describe_translation_maps_outputs_to_inputs`), with the indexing XSLT case its `index_fields` docstring
/// records.
class TestTranslationDescriptions {

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

    @Test
    @SuppressWarnings("unchecked")
    void testDescribeTranslationMapsOutputsToInputs() {
        final Map<String, Object> result = TranslationDescriptions.describeTranslation(XSLT, null);
        final Map<String, String> mapped = new LinkedHashMap<>();
        for (final Map<String, String> mapping : (List<Map<String, String>>) result.get("mappings")) {
            mapped.put(mapping.get("output"), mapping.get("source"));
        }
        assertThat(mapped.get("[record] Event/EventSource/User/Id")).isEqualTo("data[@name='user']/@value");
        assertThat(mapped.get("[record] Event/Data/@Value")).isEqualTo("{data[@name='host']/@value}");
        assertThat(mapped.get("[record] Event/EventSource/System/Name")).isEqualTo("ACME");
        assertThat(result.get("input_fields")).isEqualTo(List.of("host", "time", "user"));
        assertThat(result.get("imports")).isEqualTo(List.of("IP Lookup"));
        assertThat(result.get("lookups")).isEqualTo(List.of("USER_MAP"));
        assertThat(result.get("dictionaries")).isEqualTo(List.of());
        assertThat(result).containsEntry("xslt", null);
        assertThat((Map<String, Integer>) result.get("mapping_counts")).containsEntry("constant", 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAnIndexingXsltIsDescribedByItsFields() {
        // Seen in VS Code: an indexing XSLT described as '[Event] map/map/string' outputs, 36 of them, which say
        // nothing of the fields.
        final String indexing = """
                <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" \
                xmlns="http://www.w3.org/2005/xpath-functions" xpath-default-namespace="event-logging:3" \
                version="3.0">
                  <xsl:template match="Event">
                    <xsl:variable name="user" select="EventSource/User/Id"/>
                    <map><map key="user"><string key="id"><xsl:value-of select="$user"/></string></map>
                    <string key="host"><xsl:value-of select="EventSource/Device/HostName"/></string></map>
                    <xsl:call-template name="stroomMeta"/>
                  </xsl:template>
                  <xsl:template match="Authenticate" mode="action">
                    <string key="outcome"><xsl:value-of select="Outcome/Success"/></string>
                  </xsl:template>
                </xsl:stylesheet>""";
        final Map<String, Object> result = TranslationDescriptions.describeTranslation(indexing, "Index XSLT");
        assertThat(result.get("xslt")).isEqualTo("Index XSLT");
        assertThat(result.get("kind")).isEqualTo("indexing");
        assertThat(result.get("field_count")).isEqualTo(4);
        final List<Map<String, String>> fields = (List<Map<String, String>>) result.get("index_fields");
        assertThat(fields.get(0)).isEqualTo(Map.of("field", "user.id", "source", "EventSource/User/Id",
                "type", "string"));
        assertThat(fields.get(1).get("field")).isEqualTo("host");
        assertThat(fields.get(2)).isEqualTo(Map.of("field", "(written by the shared template stroomMeta)",
                "source", ""));
        assertThat(fields.get(3).get("field")).isEqualTo("[action] outcome");
    }

    @Test
    void testALuceneRecordsDataIsAField() {
        final String lucene = """
                <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="records:2" \
                xpath-default-namespace="event-logging:3" version="3.0">
                  <xsl:template match="Event"><record>\
                <data name="UserId" value="{EventSource/User/Id}"/></record></xsl:template>
                </xsl:stylesheet>""";
        assertThat(TranslationDescriptions.indexFields(XmlTrees.parse(lucene)))
                .containsExactly(Map.of("field", "UserId", "source", "EventSource/User/Id"));
    }
}
