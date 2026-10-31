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
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// What promotion checks: a clean step of the pipeline's current code (recorded in the build's record by stepping),
/// and docs.
class TestBuildChecks {

    private static final String BUILD = "acme-v1";

    private final BuildWorld world = new BuildWorld();
    private final IndexTemplateAgreements agreements = new IndexTemplateAgreements(world.pipelineShapes,
            world.documentAccess);
    private final KeptMappings keptMappings = new KeptMappings(world.pipelineShapes, world.documentAccess);

    @BeforeEach
    void setUp() {
        for (final String uuid : List.of("p", "q")) {
            PipelineLayers.given(world.pipelineService, uuid, PipelineLayers.of(
                    PipelineLayers.layer("t")
                            .chain("dsParser", "DSParser", "translationFilter", "XSLTFilter")
                            .property("dsParser", "textConverter", new DocRef(TextConverterDoc.TYPE, "tc", "shared")),
                    PipelineLayers.layer(uuid)
                            .property("translationFilter", "xslt", new DocRef(XsltDoc.TYPE, "x", "Acme"))));
        }
        world.docs.put("x", XsltDoc.builder().uuid("x").name("Acme").data("<xsl:stylesheet>v1</xsl:stylesheet>")
                .build());
        world.inBuild(BUILD, PipelineDoc.TYPE, "Acme", "p");
        world.inBuild(BUILD, PipelineDoc.TYPE, "Other", "q");
    }

    @Test
    void buildChecksNameWhatIsMissing() {
        world.records().recordPassed(new DocRef(PipelineDoc.TYPE, "p", "Acme"), BuildRecords.STEPPED, null, null);
        final List<Map<String, Object>> docs = List.of(
                FakeBuildFacts.doc(PipelineDoc.TYPE, "p", "Acme"),
                FakeBuildFacts.doc(PipelineDoc.TYPE, "q", "Other"),
                FakeBuildFacts.doc(DocumentationDoc.TYPE, "d", "Other"));
        assertThat(checks(Mockito.mock(GeneratedDrift.class)).checks(docs)).containsExactly(
                "Pipeline 'Acme': no documentation (write_documentation)",
                "Pipeline 'Other': no clean step_sample or step_records of its current code is recorded");
    }

    @Test
    void aWorkingCopyNeedsNoDocumentationOfItsOwn() {
        world.records().recordPassed(new DocRef(PipelineDoc.TYPE, "p", "Acme"), BuildRecords.STEPPED, null, null);
        assertThat(checks(Mockito.mock(GeneratedDrift.class)).checks(List.of(FakeBuildFacts.doc(PipelineDoc.TYPE, "p",
                "Acme", "working_copy_of", "prod")))).isEmpty();
    }

    @Test
    void aFieldMappingThatPredatesTheMappingIsSaidAndHeldUpPromotion() {
        world.records().recordPassed(new DocRef(PipelineDoc.TYPE, "p", "Acme"), BuildRecords.STEPPED, null, null);
        final String hidden = "<!-- stroom-mcp translation mapping: kept by the server, do not edit or delete\n"
                              + "{\"mapping\":{}}\n-->";
        world.docs.put("x", XsltDoc.builder().uuid("x").name("Acme").description(hidden)
                .data("<xsl:stylesheet>v1</xsl:stylesheet>").build());
        world.docs.put("d", DocumentationDoc.builder().uuid("d").name("Acme")
                .data("<!-- stroom-mcp field-mapping 0000000000000000 -->").build());
        final List<Map<String, Object>> docs = List.of(FakeBuildFacts.doc(PipelineDoc.TYPE, "p", "Acme"),
                FakeBuildFacts.doc(DocumentationDoc.TYPE, "d", "Acme"));
        assertThat(checks(Mockito.mock(GeneratedDrift.class)).checks(docs)).singleElement().satisfies(problem ->
                assertThat(problem).startsWith("Pipeline 'Acme': the documentation's Field mapping predates the "
                                               + "current mapping or XSLT (write_documentation build=<this build> "
                                               + "pipeline_uuid='p'")
                        .endsWith(BuildChecks.BLOCKS));
        // Documented from this mapping and XSLT: nothing to say.
        final KeptMappings.KeptXslt kept = keptMappings.keptXslt("p").orElseThrow();
        world.docs.put("d", DocumentationDoc.builder().uuid("d").name("Acme")
                .data("<!-- stroom-mcp field-mapping " + BuildChecks.mappingDigest(kept) + " -->").build());
        assertThat(checks(Mockito.mock(GeneratedDrift.class)).checks(docs)).isEmpty();
        // An XSLT edited by hand is said, and only warns.
        world.docs.put("d", DocumentationDoc.builder().uuid("d").name("Acme").data("").build());
        final GeneratedDrift drifted = Mockito.mock(GeneratedDrift.class);
        Mockito.when(drifted.drift(Mockito.any())).thenReturn(Optional.of("its XSLT was edited by hand"));
        assertThat(checks(drifted).checks(docs)).hasSize(2)
                .first().isEqualTo("Pipeline 'Acme': its XSLT was edited by hand");
        assertThat(checks(drifted).checks(docs).get(1)).doesNotEndWith(BuildChecks.BLOCKS);
        assertThat(kept.kept()).isEqualTo(new KeptMapping.Kept("translation", Map.of("mapping", Map.of())));
    }

    @Test
    void aPipelineStroomCantLoadIsOneProblemSaid() {
        Mockito.when(world.pipelineService.fetchPipelineLayers(new DocRef(PipelineDoc.TYPE, "q")))
                .thenThrow(new IllegalStateException("Unknown property 'foo'"));
        assertThat(checks(Mockito.mock(GeneratedDrift.class)).checks(List.of(FakeBuildFacts.doc(PipelineDoc.TYPE, "q",
                "Other", "working_copy_of", "prod")))).containsExactly(
                "Pipeline 'Other': no clean step_sample or step_records of its current code is recorded",
                "Pipeline 'Other': Stroom can't load it: Unknown property 'foo'");
    }

    private BuildChecks checks(final GeneratedDrift drift) {
        return new BuildChecks(world.records(), world.pipelineShapes, agreements, keptMappings, world.documentAccess,
                drift);
    }
}
