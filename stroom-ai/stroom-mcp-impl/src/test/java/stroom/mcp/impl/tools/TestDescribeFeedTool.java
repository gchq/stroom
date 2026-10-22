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
import stroom.documentation.shared.DocumentationDoc;
import stroom.feed.shared.FeedDoc;
import stroom.mcp.impl.tools.GeneratedDocs.Found;
import stroom.mcp.impl.tools.IndexDocs.Index;
import stroom.pipeline.PipelineService;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.PipelineModelException;
import stroom.pipeline.shared.XsltDoc;
import stroom.processor.shared.ProcessorFilter;
import stroom.search.elastic.shared.ElasticIndexDoc;

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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

class TestDescribeFeedTool {

    private static final String UNKNOWN_PROPERTY = "Attempt to set property \"compressionLevel\" on element "
                                                   + "\"rollingFileAppender\" but property is unknown.";
    private static final String KEPT = "<!-- stroom-mcp translation mapping: kept by the server, do not edit or "
                                       + "delete\n{\"mapping\":{\"input\":\"data_splitter\",\"unmatched\":\"skip\","
                                       + "\"events\":[{\"name\":\"logon\",\"when\":[{\"field\":\"action\","
                                       + "\"equals\":\"LOGIN\"}],\"fields\":[{\"path\":\"EventDetail/TypeId\","
                                       + "\"value\":\"Logon\"},{\"path\":\"EventDetail/Authenticate/Action\","
                                       + "\"value\":\"Logon\"}]}]},\"schema_version\":\"4.1.0\"}\n-->";
    private static final DocRef FEED = new DocRef(FeedDoc.TYPE, "f", "ACME");

    private final FeedNames feedNames = Mockito.mock(FeedNames.class);
    private final ProcessorFilters processorFilters = Mockito.mock(ProcessorFilters.class);
    private final PipelineService pipelineService = PipelineLayers.service();
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final GeneratedDocs generatedDocs = Mockito.mock(GeneratedDocs.class);
    private final IndexDocs indexDocs = Mockito.mock(IndexDocs.class);
    private DescribeFeedTool tool;

    @BeforeEach
    void setUp() {
        Mockito.when(feedNames.resolve(anyString(), anyString(), any(), any())).thenReturn("ACME");
        Mockito.when(feedNames.feedsNamed("ACME")).thenReturn(List.of(FEED));
        Mockito.when(documentAccess.read(FEED, FeedDoc.class)).thenReturn(Optional.of(FeedDoc.builder()
                .uuid("f").name("ACME").streamType("Raw Events").build()));
        Mockito.when(documentAccess.canView(any())).thenReturn(true);
        Mockito.when(documentAccess.path(any())).thenReturn("/Feeds");
        Mockito.when(documentAccess.read(any(), eq(XsltDoc.class))).thenReturn(Optional.of(XsltDoc.builder()
                .uuid("x").description(KEPT).build()));
        Mockito.when(processorFilters.canManageProcessors()).thenReturn(true);
        Mockito.when(processorFilters.feeds(any(), any())).thenReturn(Set.of("acme"));
        // Which pipelines read the feed, worked out from the filters above.
        Mockito.when(processorFilters.pipelinesReading(anyString())).thenCallRealMethod();
        Mockito.when(generatedDocs.generatedDoc(anyString(), any())).thenReturn(Optional.empty());
        final PipelineShapes shapes = PipelineLayers.shapes(pipelineService, new AiConfig());
        tool = new DescribeFeedTool(feedNames, processorFilters, shapes, new KeptMappings(shapes, documentAccess),
                generatedDocs, indexDocs, documentAccess);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAPipelineStroomCantLoadIsListedAndTheRestStillDescribed() {
        // Seen in production: one pipeline on the feed set a property its element type no longer has; Stroom
        // refused to load it, and describe_feed failed.
        Mockito.when(processorFilters.all()).thenReturn(List.of(filter("bad", "Old-Events"),
                filter("good", "ACME-Events")));
        pipeline("bad", "Old-Events");
        pipeline("good", "ACME-Events");
        Mockito.when(pipelineService.fetchPipelineLayers(new DocRef(PipelineDoc.TYPE, "bad")))
                .thenThrow(new PipelineModelException(UNKNOWN_PROPERTY));
        PipelineLayers.given(pipelineService, "good", PipelineLayers.of(PipelineLayers.layer("good")
                .chain("dsParser", "DSParser", "translationFilter", "XSLTFilter")
                .property("translationFilter", "xslt", new DocRef(XsltDoc.TYPE, "x"))));

        final Map<String, Object> out = describe(Map.of("feed", "acme"));

        final List<Map<String, Object>> pipelines = (List<Map<String, Object>>) out.get("pipelines");
        assertThat(pipelines).hasSize(2);
        final Map<String, Object> bad = pipelines.get(0);
        assertThat(bad.get("kind")).isEqualTo("unknown");
        assertThat(bad.get("problem")).isEqualTo("Stroom couldn't load this pipeline, so it isn't described: "
                                                 + UNKNOWN_PROPERTY);
        assertThat(String.valueOf(bad.get("link"))).contains("docUuid=bad");
        final Map<String, Object> good = new LinkedHashMap<>();
        good.put("pipeline", "ACME-Events");
        good.put("uuid", "good");
        good.put("kind", "events");
        assertThat(pipelines.get(1)).isEqualTo(good);
        // The event kinds from the kept mapping, read from under 'mapping' (passed whole, none were ever listed).
        assertThat(((List<String>) out.get("event_kinds")).getFirst()).startsWith("logon: Authenticate (");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAFieldIsExplainedFromTheDocsAndTheIndexsOwnFields() {
        Mockito.when(processorFilters.all()).thenReturn(List.of(filter("idx", "ACME-Index")));
        pipeline("idx", "ACME-Index");
        PipelineLayers.given(pipelineService, "idx", PipelineLayers.of(PipelineLayers.layer("idx")
                .chain("xmlParser", "XMLParser", "xsltFilter", "XSLTFilter", "elasticIndexingFilter",
                        "ElasticIndexingFilter")
                .property("elasticIndexingFilter", "indexName", "ecs-vpn-v1")));
        final DocRef indexRef = new DocRef(ElasticIndexDoc.TYPE, "es", "ecs-vpn-v1");
        final Index index = new Index(indexRef, "ecs-vpn-v1", ElasticIndexDoc.builder().uuid("es")
                .name("ecs-vpn-v1").indexName("ecs-vpn-v1").build());
        Mockito.when(indexDocs.written(any())).thenReturn(List.of(index));
        Mockito.when(indexDocs.fields(index)).thenReturn(List.of(
                field("User.DomainName", "text", "text"), field("User.DomainName.keyword", "keyword", "keyword"),
                field("User.Id", "keyword", "keyword")));
        final DocRef docRef = new DocRef(DocumentationDoc.TYPE, "doc", "ecs-vpn-v1");
        Mockito.when(generatedDocs.generatedDoc(eq("ecs-vpn-v1"), any()))
                .thenReturn(Optional.of(new Found(docRef, true, "System/Feeds")));
        Mockito.when(generatedDocs.text(docRef)).thenReturn(TestFeedDocumentation.INDEX_DOC);

        final Map<String, Object> out = describe(Map.of("feed", "ACME", "field", "User.DomainName"));

        final Map<String, Object> about = (Map<String, Object>) out.get("field");
        final Map<String, Object> hit = ((List<Map<String, Object>>) about.get("in_indexes")).getFirst();
        assertThat(hit.get("type")).isEqualTo("text");
        assertThat(hit.get("sub_fields")).isEqualTo(List.of("User.DomainName.keyword (keyword)"));
        assertThat(String.join(" ", (List<String>) hit.get("how_to_search")))
                .contains("Its keyword sub-field User.DomainName.keyword");
        assertThat(about.get("event_logging_paths")).isEqualTo(List.of("EventDetail/*/User/Domain"));
        assertThat((List<?>) about.get("documented")).hasSize(1);
        final Map<String, Object> indexOut = ((List<Map<String, Object>>) out.get("indexes")).getFirst();
        assertThat(indexOut.get("fields")).isEqualTo(3);
        assertThat(((Map<String, Object>) indexOut.get("documentation")).get("link"))
                .isEqualTo("?action=open-doc&docType=Documentation&docUuid=doc");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testAFieldNoOneNamesIsAnsweredWithSimilarFields() {
        Mockito.when(processorFilters.all()).thenReturn(List.of(filter("idx", "ACME-Index")));
        pipeline("idx", "ACME-Index");
        PipelineLayers.given(pipelineService, "idx", PipelineLayers.of(PipelineLayers.layer("idx")
                .chain("xmlParser", "XMLParser", "indexingFilter", "IndexingFilter")));
        final Index index = new Index(new DocRef("Index", "lucene", "acme"), null, null);
        Mockito.when(indexDocs.written(any())).thenReturn(List.of(index));
        Mockito.when(indexDocs.fields(index)).thenReturn(List.of(field("UserDomain", "text", null)));

        final Map<String, Object> out = describe(Map.of("feed", "ACME", "field", "User.DomainName"));

        final Map<String, Object> about = (Map<String, Object>) out.get("field");
        assertThat(about.get("found")).isEqualTo(false);
        assertThat(((List<Map<String, Object>>) out.get("indexes")).getFirst().get("similar_fields"))
                .isEqualTo(List.of("UserDomain"));
    }

    @Test
    void testAFeedNoPipelineReadsSaysItIsNotDocumented() {
        Mockito.when(processorFilters.all()).thenReturn(List.of());
        final Map<String, Object> out = describe(Map.of("feed", "ACME"));
        assertThat(String.valueOf(out.get("note"))).contains("what the feed holds is not documented yet");
        assertThat(out.get("hint")).isEqualTo(DescribeFeedTool.HINT);
    }

    @Test
    void testAFeedThatIsntHereIsRefused() {
        Mockito.when(feedNames.resolve(anyString(), anyString(), any(), any()))
                .thenThrow(new ToolRefusal("No feed named 'X', nor any like it, in this Stroom."));
        final ToolResult result = tool.execute(new ToolCall("1", DescribeFeedTool.NAME, "{\"feed\":\"X\"}"),
                context());
        assertThat(result.isError()).isTrue();
        assertThat(result.text()).startsWith("No feed named 'X'");
    }

    private Map<String, Object> describe(final Map<String, Object> args) {
        final ToolResult result = tool.execute(new ToolCall("1", DescribeFeedTool.NAME,
                stroom.util.json.JsonUtil.writeValueAsString(args, false)), context());
        assertThat(result.isError()).as(result.text()).isFalse();
        return result.structuredContent();
    }

    private static ToolCallContext context() {
        return ToolCallContext.builder(ToolSession.chat(1)).build();
    }

    private void pipeline(final String uuid, final String name) {
        Mockito.when(documentAccess.read(new DocRef(PipelineDoc.TYPE, uuid), PipelineDoc.class))
                .thenReturn(Optional.of(PipelineDoc.builder().uuid(uuid).name(name).build()));
    }

    private static ProcessorFilter filter(final String pipelineUuid, final String pipelineName) {
        return ProcessorFilter.builder().id(1).pipelineUuid(pipelineUuid).pipelineName(pipelineName).build();
    }

    private static Map<String, Object> field(final String name, final String type, final String elasticType) {
        final Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", name);
        field.put("type", type);
        if (elasticType != null) {
            field.put("elasticsearch_type", elasticType);
        }
        return field;
    }
}
