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
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSession;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.feed.shared.FeedDoc;
import stroom.mcp.impl.tools.PipelineIndex.Entry;
import stroom.pipeline.PipelineService;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.PipelineModelException;
import stroom.pipeline.shared.XsltDoc;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

class TestFindReferenceDataTool {

    private static final DocRef LOADER = new DocRef(PipelineDoc.TYPE, "loader", "Reference Loader");
    private static final DocRef USERS = new DocRef(FeedDoc.TYPE, "users", "ACME-USERS");
    private static final String USERS_XSLT = "<xsl:stylesheet xmlns=\"reference-data:2\" "
                                             + "xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"2.0\">"
                                             + "<xsl:template match=\"record\"><reference><map>USER_TO_ORG</map>"
                                             + "<key><xsl:value-of select=\"data[@name='user']/@value\"/></key>"
                                             + "<value><xsl:value-of select=\"data[@name='org']/@value\"/></value>"
                                             + "</reference></xsl:template></xsl:stylesheet>";

    private final PipelineIndex pipelineIndex = Mockito.mock(PipelineIndex.class);
    private final PipelineService pipelineService = PipelineLayers.service();
    private final ProcessorFilters processorFilters = Mockito.mock(ProcessorFilters.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final Map<String, Entry> index = new LinkedHashMap<>();
    private FindReferenceDataTool tool;

    @BeforeEach
    void setUp() {
        Mockito.when(pipelineIndex.read()).thenReturn(index);
        Mockito.when(pipelineIndex.containing(XsltDoc.TYPE, ReferenceData.REF_NS)).thenReturn(List.of(
                new DocRef(XsltDoc.TYPE, "ux", "Users"),
                new DocRef(XsltDoc.TYPE, "other", "Other"), new DocRef(XsltDoc.TYPE, "gone", "Gone")));
        Mockito.when(documentAccess.read(any(), any())).thenReturn(Optional.empty());
        Mockito.when(documentAccess.read(new DocRef(XsltDoc.TYPE, "ux"), XsltDoc.class))
                .thenReturn(Optional.of(XsltDoc.builder().uuid("ux").name("Users").data(USERS_XSLT).build()));
        Mockito.when(documentAccess.read(new DocRef(XsltDoc.TYPE, "other"), XsltDoc.class))
                .thenReturn(Optional.of(XsltDoc.builder().uuid("other").name("Other").data("<x/>").build()));

        // The loader, the child that runs the users XSLT, and an events pipeline naming them as a reference.
        final PipelineLayers loader = PipelineLayers.layer("loader")
                .chain("combinedParser", "CombinedParser", "xsltFilter", "XSLTFilter", "referenceDataFilter",
                        "ReferenceDataFilter")
                .property("xsltFilter", "xslt", new DocRef(XsltDoc.TYPE, "ux", "Users"));
        add("loader", "Reference Loader", loader);
        final PipelineLayers events = PipelineLayers.layer("events")
                .chain("jsonParser", "JSONParser", "translationFilter", "XSLTFilter")
                .reference("translationFilter", USERS, LOADER);
        add("events", "ACME-Events", events);
        index.put("broken", new Entry("broken", "Broken", "System", null, PipelineLayers.layer("broken")
                .reference("x", USERS, LOADER).data()));
        Mockito.when(pipelineService.fetchPipelineLayers(new DocRef(PipelineDoc.TYPE, "broken")))
                .thenThrow(new PipelineModelException("Element type \"StateFilter\" is unknown"));

        Mockito.when(processorFilters.all()).thenReturn(List.of(ProcessorFilter.builder()
                .pipelineUuid("loader")
                .queryData(QueryData.builder().expression(ExpressionOperator.builder()
                        .addTerm("Feed", Condition.EQUALS, "ACME-USERS").build()).build())
                .build()));
        final PipelineShapes shapes = PipelineLayers.shapes(pipelineService, new AiConfig());
        final PipelineTemplates templates = new PipelineTemplates(pipelineIndex, shapes, processorFilters,
                documentAccess, () -> Mockito.mock(DocFinder.class));
        tool = new FindReferenceDataTool(pipelineIndex, shapes, templates, new ReferenceData(pipelineIndex, shapes),
                processorFilters, documentAccess);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTheMapsTheirLoadersAndWhoUsesThemAreFound() {
        final ToolResult result = tool.execute(new ToolCall("1", FindReferenceDataTool.NAME, "{}"),
                ToolCallContext.builder(ToolSession.chat(1)).build());
        final Map<String, Object> out = result.structuredContent();

        final Map<String, Object> map = ((List<Map<String, Object>>) out.get("maps")).getFirst();
        assertThat(map).containsEntry("map", "USER_TO_ORG")
                .containsEntry("key", "data[@name='user']/@value")
                .containsEntry("xslt", "Users")
                .containsEntry("loaded_by", List.of("Reference Loader"))
                .containsEntry("feeds", List.of("ACME-USERS"))
                .containsEntry("used_by", List.of("ACME-Events"))
                .containsEntry("loader", "Reference Loader");
        final Map<String, Object> use = ((List<Map<String, Object>>) out.get("references_in_use")).getFirst();
        assertThat(use).containsEntry("feed", "ACME-USERS").containsEntry("loader_uuid", "loader");
        assertThat((List<Map<String, Object>>) out.get("loaders")).extracting(l -> l.get("uuid"))
                .containsExactly("loader");
        // One document Stroom can't give is left out and said, not the whole answer lost.
        assertThat((List<String>) out.get("unreadable")).containsExactly(
                "XSLT 'Gone': the document cannot be read (deleted, or no permission)",
                "pipeline 'Broken': Element type \"StateFilter\" is unknown");
        assertThat(out.get("reference_data_template")).isNull();
        assertThat(out.get("hint")).isEqualTo(FindReferenceDataTool.HINT);
    }

    private void add(final String uuid, final String name, final PipelineLayers layer) {
        index.put(uuid, new Entry(uuid, name, "System/Acme", null, layer.data()));
        PipelineLayers.given(pipelineService, uuid, PipelineLayers.of(layer));
    }
}
