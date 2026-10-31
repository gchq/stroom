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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class TestDescribeTemplateTool {

    private static final DocRef TEMPLATE = new DocRef(PipelineDoc.TYPE, "t", "Event Data (Text)");
    private static final String OWN = "<xsl:stylesheet xmlns=\"event-logging:3\" "
                                      + "xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"2.0\">"
                                      + "<xsl:import href=\"Common-Event-V1\"/><xsl:template match=\"record\">"
                                      + "<Event><xsl:call-template name=\"eventMeta\"/></Event></xsl:template>"
                                      + "</xsl:stylesheet>";
    private static final String COMMON = "<xsl:stylesheet xmlns=\"event-logging:3\" "
                                         + "xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"2.0\">"
                                         + "<xsl:template name=\"eventMeta\"><Meta/></xsl:template>"
                                         + "</xsl:stylesheet>";

    private final PipelineIndex pipelineIndex = Mockito.mock(PipelineIndex.class);
    private final PipelineService pipelineService = PipelineLayers.service();
    private final ProcessorFilters processorFilters = Mockito.mock(ProcessorFilters.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final DocFinder docFinder = Mockito.mock(DocFinder.class);
    private DescribeTemplateTool tool;

    @BeforeEach
    void setUp() {
        final Map<String, Entry> index = new LinkedHashMap<>();
        index.put("t", new Entry("t", TEMPLATE.getName(), "System/Template Pipelines", null, null));
        final PipelineLayers child = PipelineLayers.layer("c")
                .property("translationFilter", "xslt", new DocRef(XsltDoc.TYPE, "own", "Acme-Events"));
        index.put("c", new Entry("c", "Acme-Events", "System/Feeds/Acme", "t", child.data()));
        Mockito.when(pipelineIndex.read()).thenReturn(index);
        final PipelineLayers template = PipelineLayers.layer("t")
                .chain("dsParser", "DSParser", "translationFilter", "XSLTFilter", "schemaFilter", "SchemaFilter")
                .property("schemaFilter", "schemaGroup", "EVENTS");
        PipelineLayers.given(pipelineService, "t", PipelineLayers.of(template));
        PipelineLayers.given(pipelineService, "c", PipelineLayers.of(template, child));

        Mockito.when(documentAccess.findDocRef("t")).thenReturn(Optional.of(TEMPLATE));
        Mockito.when(documentAccess.findDocRef("feed")).thenReturn(Optional.of(new DocRef(FeedDoc.TYPE, "feed",
                "ACME")));
        Mockito.when(documentAccess.canView(any())).thenReturn(true);
        Mockito.when(documentAccess.read(any(), eq(XsltDoc.class))).thenReturn(Optional.empty());
        xslt("own", "Acme-Events", OWN);
        xslt("common", "Common-Event-V1", COMMON);
        Mockito.when(docFinder.findByName(XsltDoc.TYPE, "Common-Event-V1"))
                .thenReturn(List.of(new DocRef(XsltDoc.TYPE, "common", "Common-Event-V1")));
        final List<ProcessorFilter> filters = List.of(ProcessorFilter.builder()
                .pipelineUuid("c")
                .queryData(QueryData.builder().expression(ExpressionOperator.builder()
                        .addDocRefTerm("Feed", Condition.IS_DOC_REF, new DocRef(FeedDoc.TYPE, "f", "ACME"))
                        .build()).build())
                .build());
        Mockito.when(processorFilters.all()).thenReturn(filters);
        // As PipelineFeeds reads them (see TestProcessorFilters).
        Mockito.when(processorFilters.feedsByPipeline(filters)).thenReturn(Map.of("c", Set.of("ACME")));
        final PipelineTemplates templates = new PipelineTemplates(pipelineIndex,
                PipelineLayers.shapes(pipelineService, new AiConfig()), processorFilters, documentAccess,
                () -> docFinder);
        tool = new DescribeTemplateTool(templates, documentAccess);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testATemplateIsDescribedByItsChildrenContractAndSharedXslts() {
        final ToolResult result = call("{\"template_uuid\": \"t\"}");
        assertThat(result.isError()).isFalse();
        final Map<String, Object> out = result.structuredContent();
        final Map<String, Object> child = ((List<Map<String, Object>>) out.get("children")).getFirst();
        assertThat(child).containsEntry("name", "Acme-Events")
                .containsEntry("sets", List.of("translationFilter.xslt"))
                .containsEntry("feeds", List.of("ACME"));
        assertThat(out.get("schema_group")).isEqualTo("EVENTS");
        assertThat(out.get("child_must_supply")).asList().hasSize(2);
        final Map<String, Object> call = ((List<Map<String, Object>>) out.get("shared_xslt")).getFirst();
        assertThat(call).containsEntry("href", "Common-Event-V1")
                .containsEntry("template", "eventMeta")
                .containsEntry("at", List.of("Meta"))
                .containsEntry("used_by", List.of("Acme-Events"));
        assertThat(String.valueOf(out.get("shared_xslt_hint"))).contains("do not map the elements they write");
        Mockito.verify(documentAccess).logView(any(), eq(TEMPLATE), any());
    }

    @Test
    void testADocumentThatIsntAPipelineOrIsntVisibleIsRefused() {
        assertThat(call("{\"template_uuid\": \"nope\"}").text()).isEqualTo(DocumentAccess.NO_PERMISSION);
        final ToolResult feed = call("{\"template_uuid\": \"feed\"}");
        assertThat(feed.isError()).isTrue();
        assertThat(feed.text()).contains("is a Feed, not a pipeline template");
        assertThat(call("{}").isError()).isTrue();
    }

    private ToolResult call(final String arguments) {
        return tool.execute(new ToolCall("1", DescribeTemplateTool.NAME, arguments),
                ToolCallContext.builder(ToolSession.chat(1)).build());
    }

    private void xslt(final String uuid, final String name, final String text) {
        Mockito.when(documentAccess.read(new DocRef(XsltDoc.TYPE, uuid), XsltDoc.class))
                .thenReturn(Optional.of(XsltDoc.builder().uuid(uuid).name(name).data(text).build()));
    }
}
