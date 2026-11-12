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

import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.generation.DispatcherSpec;
import stroom.ai.impl.tools.generation.DispatcherXslt;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// The reference data an aggregated feed's pipelines load at promotion: the union of the reference data of the
/// groups each one's dispatcher routes, the ones a pipeline lacks said and then added; nothing for a pipeline that
/// loads them all already, or runs no dispatcher.
class TestDispatcherReferences {

    private static final String BUILD = PipelineWorld.BUILD;
    private static final String TEMPLATE = PipelineWorld.TEMPLATE_UUID;

    private final PipelineWorld pipelines = new PipelineWorld();
    private final SourceTypeGroups groups = Mockito.mock(SourceTypeGroups.class);
    private final PipelineIndex pipelineIndex = Mockito.mock(PipelineIndex.class);
    private final DispatcherReferences references = new DispatcherReferences(groups, pipelines.writes,
            pipelines.pipelineDocs, () -> pipelineIndex);

    @BeforeEach
    void setUp() {
        pipelines.template(TEMPLATE, "Event Data (JSON)", PipelineLayers.layer(TEMPLATE).chain("jsonParser",
                "JSONParser", "translationFilter", "XSLTFilter", "streamAppender", "StreamAppender"));
        // The feed's dispatcher, its working copy in the build, run by the production pipeline and the build's
        // working copy of it.
        final DispatcherSpec spec = new DispatcherSpec("ACME", "acme_dispatch", Map.of("input", "json"), "kind",
                null, List.of(), List.of(), Map.of());
        pipelines.xslt("d1", "ACME dispatcher", null);
        pipelines.world.docs.put("d1", ((XsltDoc) pipelines.world.docs.get("d1")).copy()
                .description(KeptMapping.withBlock("", DispatcherXslt.SPEC_BLOCK, spec.toJson())).build());
        final DocRef template = new DocRef(PipelineDoc.TYPE, TEMPLATE, "Event Data (JSON)");
        pipelines.productionPipeline("prod", "ACME-Events", template, PipelineLayers.layer("prod")
                .property("translationFilter", "xslt", new DocRef(XsltDoc.TYPE, "d0", "ACME dispatcher")));
        pipelines.buildPipeline("wc", "ACME-Events", template, PipelineLayers.layer("wc")
                .property("translationFilter", "xslt", new DocRef(XsltDoc.TYPE, "d1", "ACME dispatcher")));
        pipelines.productionPipeline(PipelineWorld.LOADER, "Reference Loader", null,
                PipelineLayers.layer(PipelineWorld.LOADER));
        pipelines.feeds.feedAt("System/Feeds", "ACME-USERS", "f-users");
        Mockito.when(pipelines.referenceData.resolveLoader("ACME-USERS")).thenReturn(PipelineWorld.LOADER);
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "Mail");
        row.put("group", "ACME mail");
        row.put("references", List.of(Map.of("feed", "ACME-USERS")));
        Mockito.when(groups.routedBy(BUILD, "d1")).thenReturn(Optional.of(new SourceTypeGroups.Routed("ACME",
                List.of(row))));
    }

    @Test
    void aWorkingCopysOriginalGainsTheReferenceDataOfTheGroupsItsDispatcherRoutes() {
        final List<Map<String, Object>> docs = List.of(FakeBuildFacts.doc(PipelineDoc.TYPE, "wc", "ACME-Events",
                "working_copy_of", "prod"));

        final List<DispatcherReferences.Change> changes = references.changes(BUILD, docs);

        assertThat(changes).singleElement().satisfies(change -> {
            assertThat(change.pipeline().getUuid()).isEqualTo("prod");
            assertThat(change.said()).isEqualTo("pipeline 'ACME-Events' loads reference data for its groups' "
                                                + "lookups: ACME-USERS via Reference Loader on translationFilter");
        });
        assertThat(references.apply(changes)).hasSize(1);
        assertThat(pipelines.pipelineDoc("prod").getPipelineData().getAddedPipelineReferences()).singleElement()
                .satisfies(reference -> {
                    assertThat(reference.getElement()).isEqualTo("translationFilter");
                    assertThat(reference.getFeed().getUuid()).isEqualTo("f-users");
                });
        // Loaded already: nothing more to add.
        assertThat(references.changes(BUILD, docs)).isEmpty();
    }

    @Test
    void aProductionPipelineRunningTheDispatchersOriginalGainsTheReferenceDataThoughNotInTheBuild() {
        // A later build adds a group with lookups to the feed's pipeline: only the dispatcher's working copy is in
        // the build, and the production pipeline running its original must load the group's reference data.
        Mockito.when(pipelineIndex.containing(PipelineDoc.TYPE, "d0")).thenReturn(List.of(
                new DocRef(PipelineDoc.TYPE, "prod", "ACME-Events")));
        final List<Map<String, Object>> docs = List.of(FakeBuildFacts.doc(XsltDoc.TYPE, "d1", "ACME dispatcher",
                "working_copy_of", "d0"));

        final List<DispatcherReferences.Change> changes = references.changes(BUILD, docs);

        assertThat(changes).singleElement().satisfies(change -> {
            assertThat(change.pipeline().getUuid()).isEqualTo("prod");
            assertThat(change.said()).contains("pipeline 'ACME-Events'", "ACME-USERS");
        });
        assertThat(references.apply(changes)).hasSize(1);
        assertThat(pipelines.pipelineDoc("prod").getPipelineData().getAddedPipelineReferences()).hasSize(1);
        assertThat(references.changes(BUILD, docs)).isEmpty();
    }

    @Test
    void aPipelineRunningNoDispatcherIsLeftAsItIs() {
        pipelines.xslt("x1", "Acme-Events", Map.of());
        pipelines.buildPipeline("other", "Other", new DocRef(PipelineDoc.TYPE, TEMPLATE, "Event Data (JSON)"),
                PipelineLayers.layer("other").property("translationFilter", "xslt",
                        new DocRef(XsltDoc.TYPE, "x1", "Acme-Events")));

        assertThat(references.changes(BUILD, List.of(FakeBuildFacts.doc(PipelineDoc.TYPE, "other", "Other"))))
                .isEmpty();
        Mockito.verifyNoInteractions(groups);
    }
}
