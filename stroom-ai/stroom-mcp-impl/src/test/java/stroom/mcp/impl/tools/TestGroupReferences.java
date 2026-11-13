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

/// The reference data an aggregated feed's group pipelines gain at promotion: a pipeline of the build running a
/// group's XSLT, and a production pipeline running a group the build changes through a working copy, each gaining
/// the group's reference data it lacks; a pipeline loading it already, or running no group, gains nothing.
class TestGroupReferences {

    private static final String BUILD = PipelineWorld.BUILD;

    private final PipelineWorld pipelines = new PipelineWorld();
    private final SourceTypeGroups groups = Mockito.mock(SourceTypeGroups.class);
    private final PipelineIndex index = Mockito.mock(PipelineIndex.class);
    private final GroupReferences references = new GroupReferences(groups, pipelines.writes, pipelines.pipelineDocs,
            () -> index);

    @BeforeEach
    void setUp() {
        pipelines.productionPipeline(PipelineWorld.LOADER, "Reference Loader", null,
                PipelineLayers.layer(PipelineWorld.LOADER));
        pipelines.feeds.feedAt("System/Feeds", "ACME-USERS", "f-users");
        Mockito.when(pipelines.referenceData.resolveLoader("ACME-USERS")).thenReturn(PipelineWorld.LOADER);
        Mockito.when(groups.groupOf(Mockito.eq(BUILD), Mockito.anyString())).thenReturn(Optional.empty());
    }

    @Test
    void aBuildsGroupPipelineGainsItsGroupsReferenceData() {
        pipelines.xslt("g1", "ACME mail", Map.of());
        Mockito.when(groups.groupOf(BUILD, "g1")).thenReturn(Optional.of(mail("g1")));
        pipelines.buildPipeline("p1", "ACME ACME mail", null, PipelineLayers.layer("p1")
                .property("translationFilter", "xslt", new DocRef(XsltDoc.TYPE, "g1", "ACME mail")));

        final List<GroupReferences.Change> changes = references.changes(BUILD, List.of(doc(PipelineDoc.TYPE, "p1",
                "ACME ACME mail", null)));

        assertThat(changes).singleElement().satisfies(change -> {
            assertThat(change.pipeline().getUuid()).isEqualTo("p1");
            assertThat(change.said()).contains("pipeline 'ACME ACME mail'", "group 'ACME mail'", "ACME-USERS");
            assertThat(change.added()).singleElement().satisfies(reference ->
                    assertThat(reference.getElement()).isEqualTo("translationFilter"));
        });
        references.apply(changes);
        // Loading it now, it gains nothing more.
        assertThat(references.changes(BUILD, List.of(doc(PipelineDoc.TYPE, "p1", "ACME ACME mail", null))))
                .isEmpty();
    }

    @Test
    void theProductionPipelineOfAGroupChangedThroughAWorkingCopyGainsItsReferenceData() {
        pipelines.xslt("copy", "ACME mail", Map.of());
        Mockito.when(groups.groupOf(BUILD, "copy")).thenReturn(Optional.of(mail("copy")));
        pipelines.productionPipeline("prod", "ACME ACME mail", null, PipelineLayers.layer("prod")
                .property("translationFilter", "xslt", new DocRef(XsltDoc.TYPE, "o-mail", "ACME mail")));
        pipelines.productionPipeline("other", "Other", null, PipelineLayers.layer("other")
                .property("translationFilter", "xslt", new DocRef(XsltDoc.TYPE, "o-other", "Other")));
        Mockito.when(index.containing(PipelineDoc.TYPE, "o-mail")).thenReturn(List.of(
                new DocRef(PipelineDoc.TYPE, "prod", "ACME ACME mail"), new DocRef(PipelineDoc.TYPE, "other",
                        "Other")));

        final List<GroupReferences.Change> changes = references.changes(BUILD, List.of(doc(XsltDoc.TYPE, "copy",
                "ACME mail", "o-mail")));

        assertThat(changes).singleElement().satisfies(change ->
                assertThat(change.pipeline().getUuid()).isEqualTo("prod"));
    }

    @Test
    void aPipelineRunningNoGroupGainsNothing() {
        pipelines.xslt("x1", "Acme-Events", Map.of());
        pipelines.buildPipeline("p1", "Acme-Events", null, PipelineLayers.layer("p1")
                .property("translationFilter", "xslt", new DocRef(XsltDoc.TYPE, "x1", "Acme-Events")));

        assertThat(references.changes(BUILD, List.of(doc(PipelineDoc.TYPE, "p1", "Acme-Events", null)))).isEmpty();
    }

    // The mail group, looking users up, its XSLT the one given.
    private static SourceTypeGroups.GroupRun mail(final String xslt) {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "MailIn");
        row.put("group", "ACME mail");
        row.put("references", List.of(Map.of("feed", "ACME-USERS")));
        return new SourceTypeGroups.GroupRun("ACME", "ACME mail", xslt, List.of(row));
    }

    private static Map<String, Object> doc(final String type,
                                           final String uuid,
                                           final String name,
                                           final String copyOf) {
        final Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("type", type);
        doc.put("uuid", uuid);
        doc.put("name", name);
        if (copyOf != null) {
            doc.put("working_copy_of", copyOf);
        }
        return doc;
    }
}
