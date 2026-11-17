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

import stroom.ai.api.ElasticIndexPresence.Presence;
import stroom.ai.api.ElasticIndexPresence.State;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.ToolResult;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/// A production Elasticsearch index is never processed into, nor put up for deletion, by a build: production is
/// decided by the index name, written by a pipeline outside the build or named by an Elastic Index doc outside it.
/// Seen in a user's test: after a feed was promoted, its indexing pipeline was copied into a build under the same
/// names, and the user was prompted to delete the production index (and did) before the build's copy was refused.
class TestProductionIndexes {

    private static final String BUILD = ProcessingWorld.BUILD;
    private static final String PRODUCTION_INDEX = "acme-events-v1";
    private static final DocRef PROMOTED = new DocRef(PipelineDoc.TYPE, "promoted", "Acme-Events-V1.0-Indexing");
    private static final DocRef PROMOTED_INDEX_DOC = new DocRef(ElasticIndexDoc.TYPE, "es1", "Acme-Events-V1.0");
    private static final DocRef P1 = new DocRef(PipelineDoc.TYPE, "p1", "Acme-Events-V1.0-Indexing");

    private ProcessingWorld w;

    @BeforeEach
    void setUp() {
        w = new ProcessingWorld();
        // Every pipeline and Elastic Index doc the user can view is listed, as Stroom's doc finder lists them.
        for (final String type : List.of(PipelineDoc.TYPE, ElasticIndexDoc.TYPE)) {
            Mockito.when(w.feeds.docFinder.findByName(eq(type), anyString(), anyBoolean())).thenAnswer(inv ->
                    w.world.nodes.values().stream()
                            .filter(node -> type.equals(node.type))
                            .filter(node -> "*".equals(inv.getArgument(1)) || node.name.equals(inv.getArgument(1)))
                            .map(BuildWorld.Node::docRef)
                            .toList());
        }
        promoted();
        w.presences.put(PRODUCTION_INDEX, new Presence(State.PRESENT, List.of(PRODUCTION_INDEX), List.of(), List.of(),
                4321L, null));
    }

    @Test
    void aCopyOfAPromotedIndexingPipelineIsRefusedBeforeAnyIndexIsLookedUpOrAsked() {
        copyInBuild(PRODUCTION_INDEX);

        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));
        final ToolResult result = create(Map.of("stream_ids", List.of(6), "source_pipeline_uuid", "ev"), user);

        refusedAsProduction(result, user);
    }

    @Test
    void theBuildsOwnIndexDocNamingTheProductionIndexDoesNotMakeItTheBuilds() {
        // The agent made (or copied) an Elastic Index doc in the build for the production index name: the index is
        // still production's.
        copyInBuild(PRODUCTION_INDEX);
        w.world.inBuild(BUILD, ElasticIndexDoc.TYPE, "Acme-Events-V1.0", "es-build");
        w.world.docs.put("es-build", ElasticIndexDoc.builder().uuid("es-build").name("Acme-Events-V1.0")
                .version("v0").indexName(PRODUCTION_INDEX).clusterRef(ProcessingWorld.CLUSTER).build());

        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));
        final ToolResult result = create(Map.of("stream_ids", List.of(6), "source_pipeline_uuid", "ev"), user);

        refusedAsProduction(result, user);
    }

    @Test
    void aBuildPipelineThatIsNoCopyWritingToTheProductionIndexIsRefused() {
        // Made in the build (create_indexing_pipeline with the production Elastic Index doc), not recorded as a
        // copy: the name is production's all the same.
        copyInBuild(PRODUCTION_INDEX);
        w.world.records().update(BUILD, P1, entry -> entry.remove("copy_of"));

        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));
        final ToolResult result = create(Map.of("stream_ids", List.of(6), "source_pipeline_uuid", "ev"), user);

        refusedAsProduction(result, user);
    }

    @Test
    void reprocessingACopyOfAPromotedIndexingPipelineNeverAsksForTheProductionIndexToBeDropped() {
        copyInBuild(PRODUCTION_INDEX);
        w.world.records().update(BUILD, P1, entry -> entry.remove("copy_of"));
        // It ran before (stream 6): the drop_first round would come next.
        w.idFilter("p1", 3, 6);

        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));
        final ToolResult result = w.reprocessTool().execute(BuildToolCalls.call(ReprocessStreamsTool.NAME,
                JsonUtil.writeValueAsString(Map.of("pipeline_uuid", "p1", "stream_ids", List.of(6),
                        "source_pipeline_uuid", "ev"))), user.context());

        refusedAsProduction(result, user);
        // Said dropped (the agent's word): still refused, and nothing asked.
        final ToolResult again = w.reprocessTool().execute(BuildToolCalls.call(ReprocessStreamsTool.NAME,
                JsonUtil.writeValueAsString(Map.of("pipeline_uuid", "p1", "stream_ids", List.of(6),
                        "source_pipeline_uuid", "ev", "index_dropped", true))), user.context());
        refusedAsProduction(again, user);
    }

    @Test
    void aBuildPipelineWritingToAFreeIndexNameOfItsOwnIsProcessed() {
        // A new version's index (acme-events-v2), which nothing outside the build writes to or names.
        copyInBuild("acme-events-v2");
        w.world.records().update(BUILD, P1, entry -> entry.remove("copy_of"));

        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));
        final ToolResult result = create(Map.of("stream_ids", List.of(6), "source_pipeline_uuid", "ev"), user);

        assertThat(result.isError()).as(result.text()).isFalse();
        assertThat(ProcessingWorld.json(result)).containsEntry("enabled", true);
        assertThat(w.created).hasSize(1);
    }

    @Test
    void aNewVersionsFirstRunLooksItsIndexUpAgainAndIsRefusedWhenItHasBeenTakenSince() {
        // copy_pipeline found 'acme-events-v2' free; something has made it since (even empty, it isn't the new
        // version's own).
        copyInBuild("acme-events-v2");
        w.world.records().update(BUILD, P1, entry -> {
            entry.remove("copy_of");
            entry.put("version_of", PROMOTED.getUuid());
        });
        w.presences.put("acme-events-v2", new Presence(State.PRESENT, List.of(), List.of("acme-events-v2"),
                List.of(), 0L, null));
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));

        final ToolResult taken = create(Map.of("stream_ids", List.of(6), "source_pipeline_uuid", "ev"), user);

        assertThat(taken.isError()).as(taken.text()).isTrue();
        assertThat(taken.text()).contains("is a new version whose index name has been taken since it was made")
                .contains("it matches an index (acme-events-v2), holding 0 document(s)")
                .contains("Nothing was processed")
                .contains("'acme-events-v3' is free")
                .doesNotContain("DELETE");
        assertThat(user.forms()).isEmpty();
        assertThat(w.created).isEmpty();

        // A lookup that fails is never taken as free, in reprocess_streams too (stream 6 processed before, with no
        // filter of the pipeline's left).
        w.presences.put("acme-events-v2", Presence.unknown("unauthorized"));
        w.output(6, 99, "Error", "p1", 3);
        final ToolResult unknown = w.reprocessTool().execute(BuildToolCalls.call(ReprocessStreamsTool.NAME,
                JsonUtil.writeValueAsString(Map.of("pipeline_uuid", "p1", "stream_ids", List.of(6),
                        "source_pipeline_uuid", "ev"))), user.context());
        assertThat(unknown.isError()).isTrue();
        assertThat(unknown.text()).contains("couldn't be looked up on cluster ES_DEV (unauthorized)");

        // Free again: the first run goes ahead, into the new version's own index.
        w.presences.remove("acme-events-v2");
        w.streams.remove(99L);
        final ToolResult free = create(Map.of("stream_ids", List.of(6), "source_pipeline_uuid", "ev"), user);
        assertThat(free.isError()).as(free.text()).isFalse();
        assertThat(w.created).hasSize(1);
    }

    @Test
    void aNameMatchesWhatAPipelineWritesWithVariablesAsAnyValueButNotAnIndexDocsSearchPattern() {
        final DocRef pipeline = new DocRef(PipelineDoc.TYPE, "x", "X");
        final DocRef indexDoc = new DocRef(ElasticIndexDoc.TYPE, "y", "Y");

        assertThat(ProductionIndexes.matches(new ProductionIndexes.Claim(pipeline, "acme-events-{year}"),
                "acme-events-2026")).isTrue();
        assertThat(ProductionIndexes.matches(new ProductionIndexes.Claim(pipeline, "acme-events-v1"),
                "acme-{type}-v1")).isTrue();
        assertThat(ProductionIndexes.matches(new ProductionIndexes.Claim(pipeline, "ACME-EVENTS-V1"),
                "acme-events-v1")).isTrue();
        assertThat(ProductionIndexes.matches(new ProductionIndexes.Claim(pipeline, "acme-events-v1"),
                "acme-events-v2")).isFalse();
        assertThat(ProductionIndexes.matches(new ProductionIndexes.Claim(indexDoc, "acme-events-v1"),
                "acme-events-{n}")).isTrue();
        // A doc searching every version names none of them for its own.
        assertThat(ProductionIndexes.matches(new ProductionIndexes.Claim(indexDoc, "acme-events-*"),
                "acme-events-v2")).isFalse();
        assertThat(ProductionIndexes.matches(new ProductionIndexes.Claim(indexDoc, "acme-events-*"),
                "acme-events-{n}")).isTrue();
    }

    @Test
    void aTemplatesIndexNameClaimsNothing() {
        // A template sets {feed}-events for its children: it writes nothing itself, so neither a build pipeline
        // writing acme-events nor one inheriting the template's name is production by it.
        w.world.add(PipelineDoc.TYPE, "Events Indexing", w.world.folder("System/Template Pipelines").uuid, "tpl");
        w.world.docs.put("tpl", PipelineDoc.builder().uuid("tpl").name("Events Indexing").version("v0")
                .pipelineData(elasticLayer("tpl", "{feed}-events").data()).build());
        Mockito.when(w.templates.templateReason("tpl")).thenReturn("it is one of the template pipelines");
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));

        for (final String index : List.of("acme-events", "{feed}-events")) {
            copyInBuild(index);
            w.world.records().update(BUILD, P1, entry -> entry.remove("copy_of"));

            final ToolResult result = create(Map.of("stream_ids", List.of(6), "source_pipeline_uuid", "ev"), user);

            assertThat(result.isError()).as(index + ": " + result.text()).isFalse();
            w.filters.clear();
        }
    }

    @Test
    void aWorkingCopyOfAPipelineInheritingItsTemplatesIndexNameIsProduction() {
        // The original sets no index name of its own; its template's {feed}-events writes its feed's index, which
        // the build's copy of it writes too.
        w.world.add(PipelineDoc.TYPE, "Events Indexing", w.world.folder("System/Template Pipelines").uuid, "tpl");
        Mockito.when(w.templates.templateReason("tpl")).thenReturn("it is one of the template pipelines");
        w.productionPipeline(new DocRef(PipelineDoc.TYPE, "child", "Acme-Indexing"), PipelineLayers.layer("child"));
        PipelineLayers.given(w.world.pipelineService, "child", PipelineLayers.of(elasticLayer("tpl", "{feed}-events"),
                PipelineLayers.layer("child")));
        copyInBuild("{feed}-events");
        w.world.records().update(BUILD, P1, entry -> entry.put("copy_of", "child"));
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("value", true));

        final ToolResult result = create(Map.of("stream_ids", List.of(6), "source_pipeline_uuid", "ev"), user);

        assertThat(result.isError()).as(result.text()).isTrue();
        assertThat(result.text()).contains("which is a production index (pipeline 'Acme-Indexing' writes to "
                                           + "'{feed}-events')")
                .contains("Nothing was processed")
                .doesNotContain("DELETE");
        assertThat(user.forms()).isEmpty();
        assertThat(w.lookedUp).isEmpty();
    }

    // What every refusal of the production index must be: before anything is looked up or asked, nothing made,
    // and no request to delete the production index anywhere in the reply.
    private void refusedAsProduction(final ToolResult result, final BuildToolCalls.FormUser user) {
        assertThat(result.isError()).as(result.text()).isTrue();
        assertThat(result.text()).contains("production")
                .contains("'" + PRODUCTION_INDEX + "'")
                .contains("Nothing was processed")
                .doesNotContain("DELETE");
        assertThat(user.forms()).isEmpty();
        assertThat(w.lookedUp).isEmpty();
        assertThat(w.created).isEmpty();
    }

    // The promoted feed: its Elasticsearch indexing pipeline and Elastic Index doc, outside any build.
    private void promoted() {
        w.world.add(PipelineDoc.TYPE, PROMOTED.getName(), w.world.folder("System/Feeds/Acme").uuid,
                PROMOTED.getUuid());
        w.world.docs.put(PROMOTED.getUuid(), PipelineDoc.builder().uuid(PROMOTED.getUuid()).name(PROMOTED.getName())
                .version("v0").pipelineData(elasticLayer(PROMOTED.getUuid(), PRODUCTION_INDEX).data()).build());
        w.world.add(ElasticIndexDoc.TYPE, PROMOTED_INDEX_DOC.getName(), w.world.folder("System/Feeds/Acme").uuid,
                PROMOTED_INDEX_DOC.getUuid());
        w.world.docs.put(PROMOTED_INDEX_DOC.getUuid(), ElasticIndexDoc.builder().uuid(PROMOTED_INDEX_DOC.getUuid())
                .name(PROMOTED_INDEX_DOC.getName()).version("v0").indexName(PRODUCTION_INDEX)
                .clusterRef(ProcessingWorld.CLUSTER).build());
    }

    // The build's copy of the promoted pipeline, under its name, writing to the index, with an agreed template for
    // it and stepped clean; and the build's events pipeline, whose Events stream 6 it indexes.
    private void copyInBuild(final String index) {
        w.pipeline(P1, true, KeptMapping.withAgreedTemplate("Indexes Acme.", agreed(index)));
        w.layers(P1.getUuid(), elasticLayer(P1.getUuid(), index));
        w.world.records().update(BUILD, P1, entry -> entry.put("copy_of", PROMOTED.getUuid()));
        w.steppedClean(P1);
        w.pipeline(ProcessingWorld.EV, true, null);
        w.translation("ev");
        w.stream(6, "ACME-EVENTS", "Events", "ev", 4L);
    }

    private PipelineLayers elasticLayer(final String uuid, final String index) {
        return w.layer(uuid, "xsltFilter", "XSLTFilter", "elasticIndexingFilter", "ElasticIndexingFilter")
                .property("elasticIndexingFilter", "indexName", index)
                .property("elasticIndexingFilter", "cluster", ProcessingWorld.CLUSTER);
    }

    private static Map<String, Object> agreed(final String index) {
        final Map<String, Object> agreed = new LinkedHashMap<>(ProcessingWorld.agreed("PUT _index_template/" + index
                                                                                      + "\n{\"data_stream\": {}}"));
        agreed.put("name", index);
        agreed.put("index", index);
        return agreed;
    }

    private ToolResult create(final Map<String, Object> arguments, final BuildToolCalls.FormUser user) {
        final Map<String, Object> all = new LinkedHashMap<>(arguments);
        all.put("pipeline_uuid", "p1");
        return w.createTool().execute(BuildToolCalls.call(CreateProcessorFilterTool.NAME,
                JsonUtil.writeValueAsString(all)), user.context());
    }
}
