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

import stroom.ai.impl.AiConfig;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.mcp.impl.tools.PipelineIndex.Entry;
import stroom.pipeline.PipelineService;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.PipelineModelException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestReferenceData {

    // As build_reference_xslt writes it, for two maps: one with named parts, one with a text value.
    private static final String REFERENCE_XSLT = """
            <?xml version='1.0' encoding='UTF-8'?>
            <xsl:stylesheet xmlns="reference-data:2" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:stroom="stroom" version="3.0"
                xpath-default-namespace="records:2" exclude-result-prefixes="stroom">
              <xsl:template match="records">
                <referenceData version="2.0.1"
                    xsi:schemaLocation="reference-data:2 file://reference-data-v2.0.1.xsd">
                  <xsl:apply-templates select="record"/>
                </referenceData>
              </xsl:template>
              <xsl:template match="*">
                <xsl:if test="normalize-space(data[@name='user']/@value)">
                  <reference>
                    <map>USER_TO_ORG</map>
                    <key><xsl:value-of select="data[@name='user']/@value"/></key>
                    <value>
                      <xsl:element name="details" namespace="">
                        <xsl:element name="org" namespace=""><xsl:value-of select="data[@name='org']/@value"/>\
            </xsl:element>
                        <xsl:element name="name" namespace=""><xsl:value-of select="data[@name='name']/@value"/>\
            </xsl:element>
                      </xsl:element>
                    </value>
                  </reference>
                </xsl:if>
                <xsl:if test="normalize-space(lower-case(data[@name='user']/@value))">
                  <reference>
                    <map>USER_TO_NAME</map>
                    <key><xsl:value-of select="lower-case(data[@name='user']/@value)"/></key>
                    <value><xsl:value-of select="data[@name='name']/@value"/></value>
                  </reference>
                </xsl:if>
              </xsl:template>
            </xsl:stylesheet>
            """.strip();

    private final PipelineIndex pipelineIndex = Mockito.mock(PipelineIndex.class);
    private final PipelineService pipelineService = PipelineLayers.service();
    private final Map<String, Entry> index = new LinkedHashMap<>();
    private ReferenceData referenceData;

    @BeforeEach
    void setUp() {
        Mockito.when(pipelineIndex.read()).thenReturn(index);
        referenceData = new ReferenceData(pipelineIndex, PipelineLayers.shapes(pipelineService, new AiConfig()));
    }

    @Test
    void testAReferenceXsltsMapsAreReadBack() {
        final Map<String, Object> toOrg = new LinkedHashMap<>();
        toOrg.put("map", "USER_TO_ORG");
        toOrg.put("key", "data[@name='user']/@value");
        toOrg.put("value", List.of("details", "name", "org"));
        toOrg.put("ranges", false);
        final Map<String, Object> toName = new LinkedHashMap<>();
        toName.put("map", "USER_TO_NAME");
        toName.put("key", "lower-case(data[@name='user']/@value)");
        toName.put("value", "text");
        toName.put("ranges", false);
        assertThat(ReferenceData.mapsInXslt(REFERENCE_XSLT)).containsExactly(toOrg, toName);
        assertThat(ReferenceData.mapsInXslt("not xml")).isEmpty();
    }

    @Test
    void testAMapNamedByAQuotedExpressionAndRangesAreRead() {
        final String xslt = "<xsl:stylesheet xmlns=\"reference-data:2\" "
                            + "xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"2.0\">"
                            + "<xsl:template match=\"*\"><reference><map><xsl:value-of select=\"'IP_TO_SITE'\"/>"
                            + "</map><range><from>1</from><to>2</to></range><value>x</value></reference>"
                            + "</xsl:template></xsl:stylesheet>";
        final Map<String, Object> map = ReferenceData.mapsInXslt(xslt).getFirst();
        assertThat(map.get("map")).isEqualTo("IP_TO_SITE");
        assertThat(map.get("ranges")).isEqualTo(true);
    }

    @Test
    void testAReferencesLoaderIsTheFeedsOrTheOnlyOneNeverAName() {
        add("l1", "Our loader", loader("l1"));
        assertThat(referenceData.resolveLoader("ACME-USERS")).isEqualTo("l1");

        add("l2", "Another loader", loader("l2"));
        assertThatThrownBy(() -> referenceData.resolveLoader("ACME-USERS"))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("several loaders: Our loader, Another loader");

        // The one other pipelines already load the feed with.
        final DocRef feed = new DocRef(FeedDoc.TYPE, "f", "ACME-USERS");
        final PipelineLayers events = PipelineLayers.layer("events")
                .chain("xmlParser", "XMLParser", "translationFilter", "XSLTFilter")
                .reference("translationFilter", feed, new DocRef(PipelineDoc.TYPE, "l2", "Another loader"));
        index.put("events", new Entry("events", "Events", "System", null, events.data()));
        PipelineLayers.given(pipelineService, "events", PipelineLayers.of(events));
        assertThat(referenceData.resolveLoader("ACME-USERS")).isEqualTo("l2");
    }

    @Test
    void testNoLoaderIsSaid() {
        assertThatThrownBy(() -> referenceData.resolveLoader("ACME-USERS"))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("This Stroom has no reference-data loader");
    }

    @Test
    void testALoaderStroomCantBuildOrThatLeavesItsXsltOpenIsNotALoader() {
        add("l1", "Our loader", loader("l1"));
        final PipelineLayers template = PipelineLayers.layer("t")
                .chain("parser", "CombinedParser", "xsltFilter", "XSLTFilter", "referenceDataFilter",
                        "ReferenceDataFilter");
        index.put("t", new Entry("t", "Reference Data", "System", null, template.data()));
        PipelineLayers.given(pipelineService, "t", PipelineLayers.of(template));
        final PipelineLayers broken = PipelineLayers.layer("b").chain("s", "StateFilter", "r", "ReferenceDataFilter");
        index.put("b", new Entry("b", "State loader", "System", null, broken.data()));
        Mockito.when(pipelineService.fetchPipelineLayers(new DocRef(PipelineDoc.TYPE, "b")))
                .thenThrow(new PipelineModelException("Element type \"StateFilter\" is unknown"));
        assertThat(referenceData.loaderPipelines(index)).extracting(loader -> loader.get("uuid"))
                .containsExactly("l1");
    }

    // A loader as Stroom's own is: a parser whose converter may be unset, and a ReferenceDataFilter.
    private PipelineLayers loader(final String uuid) {
        final PipelineLayers layer = PipelineLayers.layer(uuid)
                .chain("combinedParser", "CombinedParser", "referenceDataFilter", "ReferenceDataFilter");
        PipelineLayers.given(pipelineService, uuid, PipelineLayers.of(layer));
        return layer;
    }

    private void add(final String uuid, final String name, final PipelineLayers layer) {
        index.put(uuid, new Entry(uuid, name, "System/Standard Pipelines", null, layer.data()));
    }
}
