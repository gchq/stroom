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
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.indexing.Discovery;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.index.shared.AddField;
import stroom.index.shared.IndexFieldImpl;
import stroom.index.shared.IndexResource;
import stroom.index.shared.LuceneIndexDoc;
import stroom.mcp.impl.BundledKnowledge;
import stroom.mcp.impl.tools.CreatePipelineTool.FromTemplate;
import stroom.mcp.impl.tools.PipelineShapes.Shape;
import stroom.mcp.impl.tools.WriteGuard.BuildDoc;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.query.api.datasource.FieldType;
import stroom.query.impl.QueryService;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/// create_indexing_pipeline (the Stroom MCP server's `tools/indexing.py` `create_indexing_pipeline`,
/// `_events_available` and `_missing_index_fields`): the cases of its `tests/test_indexing.py`,
/// `tests/test_index_rename.py` and `tests/test_authoring_guards.py`, and what `dev/e2e_lucene_indexing.py` and
/// `dev/e2e_elastic_handover.py` expect of it.
class TestCreateIndexingPipelineTool {

    private static final List<PlannedField> FIELDS = List.of(PlannedField.of("StreamId", "id", "@StreamId"),
            PlannedField.of("EventId", "id", "@EventId"),
            PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
            PlannedField.of("user.name", "keyword", "EventSource/User/Id"));

    private final CreatePipelineTool pipelines = Mockito.mock(CreatePipelineTool.class);
    private final PipelineShapes shapes = Mockito.mock(PipelineShapes.class);
    private final StreamReader streamReader = Mockito.mock(StreamReader.class);
    private final IndexFields indexFields = Mockito.mock(IndexFields.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final WriteGuard guard = Mockito.mock(WriteGuard.class);
    private final BuildPlan plan = Mockito.mock(BuildPlan.class);
    private final IndexingInputs inputs = new IndexingInputs(streamReader, Mockito.mock(StreamSummaries.class), shapes,
            documentAccess, plan);
    private final CreateIndexingPipelineTool tool = new CreateIndexingPipelineTool(pipelines, shapes, inputs,
            indexFields, documentAccess, guard, plan);
    private final Map<String, Object> docs = new LinkedHashMap<>();

    TestCreateIndexingPipelineTool() {
        Mockito.when(documentAccess.read(any(DocRef.class), any(Class.class))).thenAnswer(inv -> {
            final Object doc = docs.get(((DocRef) inv.getArgument(0)).getType());
            return ((Class<?>) inv.getArgument(1)).isInstance(doc)
                    ? Optional.of(doc)
                    : Optional.empty();
        });
        Mockito.when(pipelines.fromTemplate(any(), any(), any())).thenReturn(Map.of("type", "Pipeline", "uuid", "p",
                "name", "n"));
        xslt("");
        // The build's own events pipeline will produce the Events (the Python tests patch _events_available).
        Mockito.when(guard.folderContents(anyString())).thenReturn(List.of(new BuildDoc(PipelineDoc.TYPE, "ev",
                "Acme-Events", List.of(), "/acme", "Events")));
        Mockito.when(shapes.shape("ev")).thenReturn(shape("translation", null, List.of(), List.of()));
    }

    @Test
    void anIndexingPipelineForElasticsearchSetsTheIndexNameAndTheOpenCluster() {
        template("elasticsearch", List.of(slot("xsltFilter", "XSLTFilter", "xslt"),
                slot("elasticIndexingFilter", "ElasticIndexingFilter", "indexName"),
                slot("elasticIndexingFilter", "ElasticIndexingFilter", "cluster")), List.of());

        final ToolResult refused = call("\"index_name\": \"stroom-acme-v1\"");
        final Map<String, Object> made = json(call("\"index_name\": \"stroom-acme-v1\", \"cluster_uuid\": \"c\""));

        assertThat(refused.text()).isEqualTo("The template leaves the cluster open: give cluster_uuid");
        assertThat(set(lastRequest())).containsExactly(Map.entry("xsltFilter.xslt", "x"),
                Map.entry("elasticIndexingFilter.indexName", "stroom-acme-v1"),
                Map.entry("elasticIndexingFilter.cluster", "c"));
        assertThat(made).containsEntry("uuid", "p").containsEntry("backend", "elasticsearch");
        // The parser is not checked against the raw sample: an indexing pipeline reads Events.
        assertThat(lastRequest().acceptParserMismatch()).isTrue();
        assertThat(lastRequest().build()).isEqualTo("b");
        assertThat(lastRequest().name()).isEqualTo("n");
        assertThat(lastRequest().template()).isEqualTo("t");
    }

    @Test
    void theIndexDocsClusterIsSetOverTheTemplatesOwn() {
        // Seen in Ask Stroom AI: the index doc on ES_DEV, the user's choice, and the pipeline left on the template's
        // ES_PROD, so its index template was drafted for production.
        template("elasticsearch", List.of(slot("xsltFilter", "XSLTFilter", "xslt"),
                        slot("elasticIndexingFilter", "ElasticIndexingFilter", "indexName")),
                List.of(shared("elasticIndexingFilter", "cluster", Map.of("type", "ElasticCluster", "uuid", "prod",
                        "name", "ES_PROD"))));
        elasticIndex("stroom-ecs-twitter-v1", "dev");

        json(call("\"index_uuid\": \"i\""));

        assertThat(set(lastRequest())).containsEntry("elasticIndexingFilter.cluster", "dev")
                .containsEntry("elasticIndexingFilter.indexName", "stroom-ecs-twitter-v1");

        // The template's own: nothing set over it.
        elasticIndex("stroom-ecs-twitter-v1", "prod");
        json(call("\"index_uuid\": \"i\""));

        assertThat(set(lastRequest())).doesNotContainKey("elasticIndexingFilter.cluster");
    }

    @Test
    void anIndexingPipelineTakesTheIndexItsDocNames() {
        template("elasticsearch", List.of(slot("xsltFilter", "XSLTFilter", "xslt"),
                slot("elasticIndexingFilter", "ElasticIndexingFilter", "indexName")), List.of());
        elasticIndex("acme-v1", "c");

        final ToolResult refused = call("\"index_uuid\": \"i\", \"index_name\": \"acme-test\"");

        assertThat(refused.text()).isEqualTo("Elastic Index doc 'ACME-ES' names index 'acme-v1', and index_name "
                                             + "'acme-test' doesn't write to it: leave index_name out (the doc's is "
                                             + "used). If the user wants 'acme-test', rename_index index_uuid=i "
                                             + "index_name='acme-test' first.");
        Mockito.verify(pipelines, Mockito.never()).fromTemplate(any(), any(), any());

        json(call("\"index_uuid\": \"i\", \"index_name\": \"acme-v1\""));

        assertThat(set(lastRequest())).containsEntry("elasticIndexingFilter.indexName", "acme-v1");
    }

    @Test
    void anElasticsearchTemplateNeedsAnIndexNameOrAnIndexDoc() {
        template("elasticsearch", List.of(slot("xsltFilter", "XSLTFilter", "xslt"),
                slot("elasticIndexingFilter", "ElasticIndexingFilter", "indexName")), List.of());

        assertThat(call("").text()).isEqualTo("This template indexes into Elasticsearch: give index_name, or "
                                              + "index_uuid (the Elastic Index doc, which names it)");
        assertThat(call("\"index_uuid\": \"nope\"").text()).isEqualTo("index_uuid nope is not an Elastic Index doc "
                                                                      + "this template can index into: give "
                                                                      + "index_name (and cluster_uuid)");
    }

    @Test
    void aTemplateThatIsNotAnIndexingOneIsRefused() {
        Mockito.when(shapes.shape("t")).thenReturn(shape("translation", null, List.of(), List.of()));

        assertThat(call("\"index_name\": \"acme-v1\"").text()).isEqualTo("That template is a translation template, "
                                                                         + "not an indexing one");
        Mockito.verify(pipelines, Mockito.never()).fromTemplate(any(), any(), any());
    }

    @Test
    void aLuceneTemplateNeedsTheIndexDoc() {
        template("lucene", List.of(slot("xsltFilter", "XSLTFilter", "xslt"),
                slot("indexingFilter", "IndexingFilter", "index")), List.of());

        assertThat(call("").text()).isEqualTo("This template indexes into Lucene: give index_uuid");
    }

    @Test
    void anIndexingPipelineGetsTheFieldsItsXsltWrites() {
        // Haiku created the Lucene index without its plan: every value was dropped with only a warning ("Attempt to
        // index unknown field"). The refusal that followed told it to call set_index_fields, which is not a tool;
        // now the plan kept with the XSLT supplies the fields, and only an XSLT written by hand is refused, naming
        // steps it can take.
        final FieldPlan lucene = FieldPlan.of("lucene", "acme", "EventTime", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("EventTime", "date", "EventTime/TimeCreated")));
        docs.put(LuceneIndexDoc.TYPE, LuceneIndexDoc.builder().uuid("i-1").name("ACME-INDEX").build());
        final String generated = KeptMapping.withMapping("", "index", lucene.toJson());

        luceneFields();
        xslt(generated, lucene.xslt());
        final CreateIndexingPipelineTool.MissingFields missing = tool.missingIndexFields("i-1", "x-1");
        assertThat(missing.missing()).containsExactly("StreamId", "EventTime");
        assertThat(missing.plan()).isEqualTo(lucene);

        luceneFields("StreamId");
        assertThat(tool.missingIndexFields("i-1", "x-1").missing()).containsExactly("EventTime");

        luceneFields();
        xslt("", lucene.xslt());
        assertThatThrownBy(() -> tool.missingIndexFields("i-1", "x-1"))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageStartingWith("Index 'ACME-INDEX' has no fields for ['StreamId', 'EventTime'], which the "
                                        + "indexing XSLT writes")
                .hasMessageContaining("save_xslt index_plan=")
                // Not a tool the agent has.
                .hasMessageNotContaining("set_index_fields");

        luceneFields("StreamId", "EventTime", "Extra");
        final CreateIndexingPipelineTool.MissingFields byHand = tool.missingIndexFields("i-1", "x-1");
        assertThat(byHand.missing()).isEmpty();
        assertThat(byHand.plan()).isNull();

        // Created (after confirmation), the pipeline's index gets the plan's fields.
        luceneFields();
        xslt(generated, lucene.xslt());
        template("lucene", List.of(slot("xsltFilter", "XSLTFilter", "xslt"), slot("indexingFilter", null, "index")),
                List.of());
        Mockito.when(indexFields.addLuceneFields(any(), any(), any())).thenReturn(Map.of("added",
                List.of("StreamId", "EventTime")));

        final Map<String, Object> made = json(call("\"build\": \"acme-v1\", \"index_uuid\": \"i-1\"", "x-1"));

        assertThat(made).containsEntry("index_fields_added", List.of("StreamId", "EventTime"))
                .containsEntry("backend", "lucene");
        final ArgumentCaptor<DocRef> index = ArgumentCaptor.forClass(DocRef.class);
        Mockito.verify(indexFields).addLuceneFields(any(ToolSession.class), index.capture(), eq(lucene));
        assertThat(index.getValue().getUuid()).isEqualTo("i-1");
        assertThat(set(lastRequest())).containsExactly(Map.entry("xsltFilter.xslt", "x-1"),
                Map.entry("indexingFilter.index", "i-1"));
    }

    @Test
    void aWrittenByHandXsltIsRefusedBeforeThePipelineIsMade() {
        docs.put(LuceneIndexDoc.TYPE, LuceneIndexDoc.builder().uuid("i-1").name("ACME-INDEX").build());
        luceneFields("StreamId");
        xslt("", "<data name=\"StreamId\" value=\"{@StreamId}\"/><data name=\"UserId\" value=\"x\"/>"
                 + "<data name=\"UserId\" value=\"y\"/><data name=\"{$dynamic}\" value=\"z\"/>");
        template("lucene", List.of(slot("xsltFilter", "XSLTFilter", "xslt"),
                slot("indexingFilter", "IndexingFilter", "index")), List.of());

        final ToolResult refused = call("\"index_uuid\": \"i-1\"");

        // Each name once, a name made from values left out; the index has fields, just not this one.
        assertThat(refused.text()).startsWith("Index 'ACME-INDEX' has no field for ['UserId'], which the indexing "
                                              + "XSLT writes");
        Mockito.verify(pipelines, Mockito.never()).fromTemplate(any(), any(), any());
    }

    @Test
    void anIndexWithEveryFieldTheXsltWritesGetsNoneAdded() {
        // dev/e2e_lucene_indexing.py: the index doc made with the plan's fields, then the pipeline.
        final FieldPlan lucene = FieldPlan.of("lucene", "acme", "EventTime", List.of(
                PlannedField.of("StreamId", "id", "@StreamId")));
        docs.put(LuceneIndexDoc.TYPE, LuceneIndexDoc.builder().uuid("i-1").name("ACME-INDEX").build());
        luceneFields("StreamId");
        xslt(KeptMapping.withMapping("", "index", lucene.toJson()), lucene.xslt());
        template("lucene", List.of(slot("xsltFilter", "XSLTFilter", "xslt"),
                slot("indexingFilter", "IndexingFilter", "index")), List.of());

        final Map<String, Object> made = json(call("\"index_uuid\": \"i-1\""));

        assertThat(made).containsEntry("backend", "lucene").doesNotContainKey("index_fields_added");
        Mockito.verify(indexFields, Mockito.never()).addLuceneFields(any(), any(), any());
    }

    @Test
    void aConfirmationRoundIsReturnedAsItIs() {
        template("elasticsearch", List.of(slot("xsltFilter", "XSLTFilter", "xslt"),
                slot("elasticIndexingFilter", "ElasticIndexingFilter", "indexName")), List.of());
        Mockito.when(pipelines.fromTemplate(any(), any(), any())).thenReturn(Map.of("status", "needs_confirmation",
                "confirmation_id", "abc"));

        final Map<String, Object> asked = json(call("\"index_name\": \"acme-v1\", \"confirmation_id\": \"old\""));

        assertThat(asked).isEqualTo(Map.of("status", "needs_confirmation", "confirmation_id", "abc"));
        assertThat(lastRequest().confirmationId()).isEqualTo("old");
    }

    @Test
    void anXmlDiscoveryPipelineNeedsNoEventsThoughItsTemplateLooksLikeIndexing() {
        // XMLParser, XSLT, indexing filter: the same shape as indexing Events. The plan kept with the XSLT tells them
        // apart.
        template("elasticsearch", List.of(slot("xsltFilter", "XSLTFilter", "xslt"),
                slot("elasticIndexingFilter", "ElasticIndexingFilter", "indexName")), List.of());
        final FieldPlan discovery = FieldPlan.forDiscovery("d", new Discovery("xml", "logon", "when", false, null,
                Map.of(), List.of(), true, 1024, 2000));
        final FieldPlan events = FieldPlan.of("elasticsearch", "x", "@timestamp", FIELDS);

        xslt(KeptMapping.withMapping("", "index", discovery.toJson()));
        json(call("\"index_name\": \"d\""));
        Mockito.verify(guard, Mockito.never()).folderContents(anyString());

        xslt(KeptMapping.withMapping("", "index", events.toJson()));
        json(call("\"index_name\": \"d\""));
        Mockito.verify(guard).folderContents("b");
    }

    @Test
    void anIndexingPipelineNeedsEventsFirst() {
        Mockito.when(guard.folderContents("b")).thenReturn(List.of(new BuildDoc(PipelineDoc.TYPE, "x", "x", List.of(),
                "p", null)));
        Mockito.when(shapes.shape("x")).thenReturn(shape("indexing", "lucene", List.of(), List.of()));
        Mockito.when(streamReader.meta(anyLong())).thenReturn(Meta.builder().id(7L).typeName("Raw Events").build());

        assertThatThrownBy(() -> tool.eventsAvailable("b", List.of()))
                .hasMessageContaining("build the events pipeline first");
        assertThatThrownBy(() -> tool.eventsAvailable("b", List.of(7L)))
                .hasMessageContaining("is 'Raw Events', not Events");

        // The build's own events pipeline will produce them.
        Mockito.when(shapes.shape("x")).thenReturn(shape("translation", null, List.of(), List.of()));
        tool.eventsAvailable("b", List.of());

        // Existing Events from another pipeline.
        Mockito.when(guard.folderContents("b")).thenReturn(List.of());
        Mockito.when(streamReader.meta(anyLong())).thenReturn(Meta.builder().id(7L).typeName("Events").build());
        tool.eventsAvailable("b", List.of(7L));
    }

    @Test
    void aBuildWithoutEventsIsRefusedBeforeThePipelineIsMade() {
        Mockito.when(guard.folderContents("b")).thenReturn(List.of());
        template("elasticsearch", List.of(slot("xsltFilter", "XSLTFilter", "xslt"),
                slot("elasticIndexingFilter", "ElasticIndexingFilter", "indexName")), List.of());

        final ToolResult refused = call("\"index_name\": \"acme-v1\"");

        assertThat(refused.text()).startsWith("Build 'b' has no events pipeline, and no events_stream_ids were "
                                              + "given.");
        Mockito.verify(pipelines, Mockito.never()).fromTemplate(any(), any(), any());
    }

    @Test
    void itIsAnIndexingToolThatDoesNotConfirmItself() {
        assertThat(tool.category()).isEqualTo(AiToolCategory.INDEXING);
        assertThat(tool.isReadOnly()).isFalse();
        // The pipeline is confirmed in create_pipeline's form; the index fields added after it are not.
        assertThat(tool.confirmsItself()).isFalse();
        assertThat(tool.definition().description()).startsWith("Onboarding step ")
                .contains("(indexing_pipeline): Stage 2: create an indexing pipeline as a child of an indexing "
                          + "template");
        assertThat(tool.definition().parameters().required()).containsExactly("build", "name", "template_uuid",
                "xslt_uuid");
        assertThat(tool.definition().parameters().properties()).containsOnlyKeys("build", "name", "template_uuid",
                "xslt_uuid", "index_uuid", "index_name", "cluster_uuid", "events_stream_ids", "confirmation_id");
    }

    @Test
    void aLuceneIndexingPipelineIsMadeAsAChildOfTheTemplateWithTheIndexAndThePlansFields() {
        // dev/e2e_lucene_indexing.py, on create_pipeline's own path: confirmed, then made with the XSLT and the index,
        // the index given the fields its plan writes.
        final PipelineWorld world = new PipelineWorld();
        final IndexResource indexResource = Mockito.mock(IndexResource.class);
        final List<String> have = new ArrayList<>();
        Mockito.when(indexResource.findFields(any())).thenAnswer(inv -> ResultPage.createUnboundedList(have.stream()
                .map(name -> IndexFieldImpl.builder().fldName(name).fldType(FieldType.TEXT).build())
                .toList()));
        Mockito.when(indexResource.addField(any())).thenAnswer(inv -> {
            have.add(((AddField) inv.getArgument(0)).getIndexField().getFldName());
            return true;
        });
        final CreateIndexingPipelineTool made = worldTool(world, indexResource);
        world.template(PipelineWorld.TEMPLATE_UUID, "Indexing", PipelineLayers.layer(PipelineWorld.TEMPLATE_UUID)
                .chain("xmlParser", "XMLParser", "xsltFilter", "XSLTFilter", "indexingFilter", "IndexingFilter"));
        final FieldPlan lucene = FieldPlan.of("lucene", "ACME-INDEX", "EventTime", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"), PlannedField.of("EventId", "id", "@EventId"),
                PlannedField.of("EventTime", "date", "EventTime/TimeCreated"),
                PlannedField.of("UserId", "keyword", "EventSource/User/Id")));
        world.world.inBuild(PipelineWorld.BUILD, XsltDoc.TYPE, "ACME-INDEX-XSLT", "x-1");
        world.world.docs.put("x-1", XsltDoc.builder().uuid("x-1").name("ACME-INDEX-XSLT").version("v0")
                .data(lucene.xslt()).description(KeptMapping.withMapping("", "index", lucene.toJson())).build());
        final DocRef index = world.world.inBuild(PipelineWorld.BUILD, LuceneIndexDoc.TYPE, "ACME-INDEX", "i-1");
        Mockito.when(world.feeds.streamReader.meta(7L)).thenReturn(Meta.builder().id(7L).typeName("Events").build());
        final String arguments = "{\"build\": \"acme-v1\", \"name\": \"ACME-INDEX - Indexing\", \"template_uuid\": \""
                                 + PipelineWorld.TEMPLATE_UUID + "\", \"xslt_uuid\": \"x-1\", \"index_uuid\": \"i-1\", "
                                 + "\"events_stream_ids\": [7]";
        final ToolCallContext context = PortedToolCalls.context();

        final Map<String, Object> asked = PipelineWorld.json(made.execute(PortedToolCalls.call(
                CreateIndexingPipelineTool.NAME, arguments + "}"), context));

        assertThat(asked.get("status")).isEqualTo("needs_confirmation");
        assertThat(asked.get("summary")).isEqualTo("Create pipeline 'ACME-INDEX - Indexing' from template "
                                                   + "'Indexing'");
        assertThat(world.pipelineNamed("ACME-INDEX - Indexing")).isNull();
        assertThat(have).isEmpty();

        final Map<String, Object> result = PipelineWorld.json(made.execute(PortedToolCalls.call(
                CreateIndexingPipelineTool.NAME, arguments + ", \"confirmation_id\": \"" + asked.get("confirmation_id")
                                                 + "\"}"), context));

        final PipelineDoc doc = world.pipelineNamed("ACME-INDEX - Indexing");
        assertThat(PipelineDocs.addedProperties(doc)).containsExactly(
                new PipelineProperty("xsltFilter", "xslt", new PipelinePropertyValue(new DocRef(XsltDoc.TYPE, "x-1",
                        "ACME-INDEX-XSLT"))),
                new PipelineProperty("indexingFilter", "index", new PipelinePropertyValue(index)));
        assertThat(world.world.documentAccess.path(doc.asDocRef())).endsWith("/" + PipelineWorld.BUILD
                                                                             + "/Indexing");
        assertThat(result).containsEntry("uuid", doc.getUuid()).containsEntry("backend", "lucene")
                .containsEntry("sets", List.of("xsltFilter.xslt", "indexingFilter.index"))
                .containsEntry("index_fields_added", List.of("StreamId", "EventId", "EventTime", "UserId"))
                .containsKey("next");
        assertThat(have).containsExactly("StreamId", "EventId", "EventTime", "UserId");
    }

    // The tool on the pipeline world, with Stroom's index service as given.
    private CreateIndexingPipelineTool worldTool(final PipelineWorld world, final IndexResource indexResource) {
        final DocumentAccess access = world.world.documentAccess;
        final KeptMappings keptMappings = new KeptMappings(world.world.pipelineShapes, access);
        final IndexingInputs worldInputs = new IndexingInputs(world.feeds.streamReader,
                Mockito.mock(StreamSummaries.class), world.world.pipelineShapes, access, world.feeds.plan);
        final IndexPlans plans = new IndexPlans(new IndexingMemory(world.feeds.securityContext), keptMappings,
                Mockito.mock(HeldSchemas.class), new BundledKnowledge());
        final IndexFields fields = new IndexFields(() -> Mockito.mock(QueryService.class), () -> indexResource, access,
                keptMappings, worldInputs, world.world.guard(), plans, List.of());
        return new CreateIndexingPipelineTool(world.createTool(), world.world.pipelineShapes, worldInputs, fields,
                access, world.world.guard(), world.feeds.plan);
    }

    private void template(final String backend,
                          final List<Map<String, Object>> open,
                          final List<Map<String, Object>> shared) {
        Mockito.when(shapes.shape("t")).thenReturn(shape("indexing", backend, open, shared));
    }

    private static Shape shape(final String stage,
                               final String backend,
                               final List<Map<String, Object>> open,
                               final List<Map<String, Object>> shared) {
        return new Shape(stage, backend, List.of(), null, open, shared, List.of(), Map.of());
    }

    private static Map<String, Object> slot(final String element, final String type, final String property) {
        final Map<String, Object> slot = new LinkedHashMap<>();
        slot.put("element", element);
        if (type != null) {
            slot.put("type", type);
        }
        slot.put("property", property);
        return slot;
    }

    private static Map<String, Object> shared(final String element, final String property, final Object value) {
        final Map<String, Object> slot = new LinkedHashMap<>(slot(element, "ElasticIndexingFilter", property));
        slot.put("value", value);
        return slot;
    }

    private void xslt(final String description) {
        xslt(description, "");
    }

    private void xslt(final String description, final String data) {
        docs.put(XsltDoc.TYPE, XsltDoc.builder().uuid("x").name("Acme-Indexing-XSLT").description(description)
                .data(data).build());
    }

    private void elasticIndex(final String indexName, final String cluster) {
        docs.put(ElasticIndexDoc.TYPE, ElasticIndexDoc.builder().uuid("i").name("ACME-ES").indexName(indexName)
                .clusterRef(new DocRef("ElasticCluster", cluster)).build());
    }

    private void luceneFields(final String... names) {
        final List<Map<String, Object>> fields = new ArrayList<>();
        for (final String name : names) {
            fields.add(Map.of("name", name, "type", "text"));
        }
        Mockito.when(indexFields.indexFields(any())).thenReturn(fields);
    }

    private FromTemplate lastRequest() {
        final ArgumentCaptor<FromTemplate> request = ArgumentCaptor.forClass(FromTemplate.class);
        Mockito.verify(pipelines, Mockito.atLeastOnce()).fromTemplate(any(), any(), request.capture());
        return request.getValue();
    }

    // What the request sets: each property's value or document, by element.name.
    private static Map<String, Object> set(final FromTemplate request) {
        final Map<String, Object> out = new LinkedHashMap<>();
        request.properties().forEach(property -> out.put(property.dotted(), property.value() != null
                ? property.value()
                : property.docUuid()));
        return out;
    }

    private ToolResult call(final String arguments) {
        return call(arguments, "x");
    }

    // A call with the arguments, the build b unless they name one.
    private ToolResult call(final String arguments, final String xsltUuid) {
        final String given;
        if (arguments.contains("\"build\"")) {
            given = arguments;
        } else {
            given = arguments.isEmpty()
                    ? "\"build\": \"b\""
                    : "\"build\": \"b\", " + arguments;
        }
        final ToolCall call = PortedToolCalls.call(CreateIndexingPipelineTool.NAME, "{" + given + ", \"name\": "
                                                                                   + "\"n\", \"template_uuid\": \"t\", "
                                                                                   + "\"xslt_uuid\": \"" + xsltUuid
                                                                                   + "\"}");
        return tool.execute(call, PortedToolCalls.context());
    }

    private static Map<String, Object> json(final ToolResult result) {
        return PipelineWorld.json(result);
    }
}
