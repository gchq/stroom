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

import stroom.ai.api.ElasticClusterTests;
import stroom.ai.api.ElasticIndexTemplates;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.validation.HeldSchemas;
import stroom.docref.DocRef;
import stroom.index.shared.IndexResource;
import stroom.mcp.impl.BundledKnowledge;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.query.impl.QueryService;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.shared.ResultPage;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/// The indexing tools' world: the processing world's explorer, pipelines and streams ([ProcessingWorld]), the
/// bundled conventions, Elastic Index and Cluster docs found by name, and Stroom's field lists, Events summaries,
/// template reads and cluster tests as mocks.
final class IndexingWorld {

    static final String BUILD = ProcessingWorld.BUILD;

    final ProcessingWorld processing = new ProcessingWorld();
    final BuildWorld world = processing.world;
    final BundledKnowledge knowledge = new BundledKnowledge();
    final StreamSummaries summaries = Mockito.mock(StreamSummaries.class);
    final HeldSchemas heldSchemas = Mockito.mock(HeldSchemas.class);
    final QueryService queryService = Mockito.mock(QueryService.class);
    final IndexResource indexResource = Mockito.mock(IndexResource.class);
    final ElasticIndexTemplates templates = Mockito.mock(ElasticIndexTemplates.class);
    final ElasticClusterTests clusterTests = Mockito.mock(ElasticClusterTests.class);
    final PipelineTemplates pipelineTemplates = Mockito.mock(PipelineTemplates.class);
    final IndexingMemory memory = new IndexingMemory(processing.feeds.securityContext);
    final PipelineIndex pipelineIndex = new PipelineIndex(() -> processing.feeds.docFinder, world.documentAccess);
    final KeptMappings keptMappings = new KeptMappings(world.pipelineShapes, world.documentAccess);
    final IndexingInputs inputs = new IndexingInputs(processing.feeds.streamReader, summaries, world.pipelineShapes,
            world.documentAccess, processing.feeds.plan);
    final IndexPlans plans = new IndexPlans(memory, keptMappings, heldSchemas, knowledge);
    final IndexExamples examples = new IndexExamples(world.documentAccess, pipelineIndex, () -> templates,
            () -> queryService, world.guard(), processing.feeds.plan, memory, new DocumentWrites(world.documentAccess));
    final BuildIndexes buildIndexes = new BuildIndexes(world.guard(), world.pipelineShapes, world.documentAccess,
            processing.pipelineDocs, pipelineIndex, processing.feeds.plan);
    final IndexFields indexFields = new IndexFields(() -> queryService, () -> indexResource, world.documentAccess,
            keptMappings, inputs, world.guard(), plans, List.of());
    /// Every form's question, in the order asked.
    final List<String> asked = new ArrayList<>();

    IndexingWorld() {
        for (final String type : List.of(ElasticIndexDoc.TYPE, ElasticClusterDoc.TYPE, XsltDoc.TYPE,
                PipelineDoc.TYPE, "Index")) {
            Mockito.when(processing.feeds.docFinder.findByName(eq(type), anyString(), anyBoolean())).thenAnswer(inv ->
                    named(type, inv.getArgument(1)));
        }
        Mockito.when(queryService.findFields(any())).thenReturn(ResultPage.empty());
        Mockito.when(indexResource.findFields(any())).thenReturn(ResultPage.empty());
        Mockito.when(pipelineTemplates.find(anyString())).thenReturn(Map.of("candidates", List.of()));
        Mockito.when(summaries.summariseEvents(anyList(), anyInt())).thenReturn(Map.of("path_population", Map.of()));
    }

    /// @return get_field_conventions, on this world.
    GetFieldConventionsTool conventionsTool() {
        return new GetFieldConventionsTool(knowledge, pipelineTemplates, pipelineIndex, world.documentAccess,
                () -> queryService, processing.feeds.consent, memory, processing.feeds.plan);
    }

    /// @return draft_index_mapping, on this world.
    DraftIndexMappingTool draftTool() {
        return new DraftIndexMappingTool(knowledge, conventionsTool(), inputs, plans, examples, memory,
                processing.feeds.consent, processing.feeds.streamReader, pipelineIndex, world.pipelineShapes,
                world.documentAccess);
    }

    /// @return create_index_doc, on this world.
    CreateIndexDocTool createTool() {
        return new CreateIndexDocTool(plans, buildIndexes, indexFields, world.guard(), world.documentAccess,
                processing.feeds.consent, processing.feeds.plan, () -> clusterTests);
    }

    /// @return rename_index, on this world.
    RenameIndexTool renameTool() {
        return new RenameIndexTool(world.documentAccess, world.guard(), buildIndexes, processing.pipelineDocs,
                processing.processorFilterAccess, processing.feeds.consent, processing.feeds.plan);
    }

    /// Adds an Elastic Index doc, in the build or elsewhere.
    ElasticIndexDoc elasticIndex(final String uuid,
                                 final String name,
                                 final String indexName,
                                 final DocRef cluster,
                                 final boolean inBuild) {
        if (inBuild) {
            world.inBuild(BUILD, ElasticIndexDoc.TYPE, name, uuid);
        } else {
            world.add(ElasticIndexDoc.TYPE, name, world.folder("System/Elastic Indices").uuid, uuid);
        }
        final ElasticIndexDoc doc = ElasticIndexDoc.builder().uuid(uuid).name(name).version("v0")
                .indexName(indexName).clusterRef(cluster).build();
        world.docs.put(uuid, doc);
        return doc;
    }

    /// Adds an Elastic Cluster doc.
    DocRef cluster(final String uuid, final String name) {
        world.add(ElasticClusterDoc.TYPE, name, world.folder("System/Elastic Clusters").uuid, uuid);
        world.docs.put(uuid, ElasticClusterDoc.builder().uuid(uuid).name(name).version("v0").build());
        return new DocRef(ElasticClusterDoc.TYPE, uuid, name);
    }

    /// The Events streams' summary: how often each path is populated, and the Data names by path.
    void populated(final Map<String, ? extends Number> population, final Map<String, Map<String, Double>> names) {
        populated(population, names, Map.of());
    }

    /// The Events streams' summary: how often each path is populated, the Data names by path, and the values read
    /// of each path.
    void populated(final Map<String, ? extends Number> population,
                   final Map<String, Map<String, Double>> names,
                   final Map<String, List<String>> values) {
        final Map<String, Object> summary = new LinkedHashMap<>();
        summary.put(StreamSummaries.VALUES_BY_PATH, values);
        final Map<String, Double> shares = new LinkedHashMap<>();
        population.forEach((path, share) -> shares.put(path, share.doubleValue()));
        summary.put("path_population", shares);
        summary.put("data_names", names);
        Mockito.when(summaries.summariseEvents(anyList(), anyInt())).thenReturn(summary);
    }

    /// @return A call's context from a client that shows no forms.
    static ToolCallContext noForms() {
        return PortedToolCalls.context();
    }

    /// @param answers What the user answers each form with, by its question.
    /// @return A call's context from a client that shows forms, the questions kept in [#asked].
    ToolCallContext forms(final Function<String, Map<String, Object>> answers) {
        return ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "mcp-session-1",
                        new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client")))
                .userQuestions(new UserQuestions() {
                    @Override
                    public boolean canAsk() {
                        return true;
                    }

                    @Override
                    public ElicitResult ask(final String toolName,
                                            final String callId,
                                            final String intro,
                                            final Map<String, Object> form) {
                        asked.add(intro);
                        return new ElicitResult(ElicitResult.Action.ACCEPT, answers.apply(intro));
                    }
                })
                .build();
    }

    private List<DocRef> named(final String type, final String name) {
        return world.nodes.values().stream()
                .filter(node -> type.equals(node.type))
                .filter(node -> "*".equals(name) || node.name.equals(name))
                .map(BuildWorld.Node::docRef)
                .toList();
    }
}
