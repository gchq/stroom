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
import stroom.ai.impl.tools.documents.KeptMapping;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
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
    void aProductionVersionImportingADeletedModuleIsSaid() {
        // Seen in chat 192: production's 'Messaging V1.0' XSLT imported the feed's common module, deleted with its
        // folder by the user; the build made a new one for V1.1 and nothing said V1.0 would fail.
        final String common = "2033ac23-1111-4222-8333-944455556666";
        final String folder = world.folder("System/Feeds/Azure").uuid;
        world.add(XsltDoc.TYPE, "Azure-Audit-V1.0-EVENTS Messaging V1.0", folder, "x10");
        world.docs.put("x10", XsltDoc.builder().uuid("x10").name("Azure-Audit-V1.0-EVENTS Messaging V1.0")
                .data("<xsl:stylesheet><xsl:import href=\"uuid='" + common + "', name='Azure-Audit-V1.0-EVENTS "
                      + "common'\"/></xsl:stylesheet>").build());
        world.add(PipelineDoc.TYPE, "Azure-Audit-V1.0-EVENTS Messaging V1.0", folder, "p10");
        PipelineLayers.given(world.pipelineService, "p10", PipelineLayers.of(
                PipelineLayers.layer("t").chain("dsParser", "DSParser", "translationFilter", "XSLTFilter"),
                PipelineLayers.layer("p10").property("translationFilter", "xslt", new DocRef(XsltDoc.TYPE, "x10",
                        "Azure-Audit-V1.0-EVENTS Messaging V1.0"))));
        world.records().recordPassed(new DocRef(PipelineDoc.TYPE, "p", "Acme"), BuildRecords.STEPPED, null, null);
        final List<Map<String, Object>> docs = List.of(FakeBuildFacts.doc(PipelineDoc.TYPE, "p", "Acme",
                "version_of", "p10"), FakeBuildFacts.doc(DocumentationDoc.TYPE, "d", "Acme"));

        // The pipelines' XSLTs read as the user reads them.
        final PipelineShapes shapes = PipelineLayers.shapes(world.pipelineService, new AiConfig(),
                world.documentAccess, Mockito.mock(DocFinder.class));
        final BuildChecks checks = new BuildChecks(world.records(), shapes, agreements, keptMappings,
                world.documentAccess, Mockito.mock(GeneratedDrift.class));

        assertThat(checks.checks(docs)).singleElement().asString()
                .contains("Production pipeline 'Azure-Audit-V1.0-EVENTS Messaging V1.0', which 'Acme' replaces",
                        "imports 'Azure-Audit-V1.0-EVENTS common' (" + common + ")",
                        "fails until it is restored")
                .doesNotEndWith(BuildChecks.BLOCKS);

        // Restored: nothing to say.
        world.add(XsltDoc.TYPE, "Azure-Audit-V1.0-EVENTS common", folder, common);
        world.docs.put(common, XsltDoc.builder().uuid(common).name("Azure-Audit-V1.0-EVENTS common")
                .data("<xsl:stylesheet/>").build());
        assertThat(checks.checks(docs)).isEmpty();
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
    void aModuleEditedByHandIsSaidOnceForThePipelinesImportingItAndOnlyWarns() {
        // Decided by the user (2026-11-24): promotion's checks include the drift of the modules each pipeline's XSLT
        // imports, not only its own XSLT.
        world.records().recordPassed(new DocRef(PipelineDoc.TYPE, "p", "Acme"), BuildRecords.STEPPED, null, null);
        world.records().recordPassed(new DocRef(PipelineDoc.TYPE, "q", "Other"), BuildRecords.STEPPED, null, null);
        final String hidden = "<!-- stroom-mcp translation mapping: kept by the server, do not edit or delete\n"
                              + "{\"mapping\":{}}\n-->";
        world.docs.put("x", XsltDoc.builder().uuid("x").name("Acme").description(hidden)
                .data("<xsl:stylesheet>v1</xsl:stylesheet>").build());
        final GeneratedDrift drift = Mockito.mock(GeneratedDrift.class);
        final String why = "its imported module 'ACME common' was edited by hand in Stroom since the tools saved it";
        Mockito.when(drift.moduleEdits("<xsl:stylesheet>v1</xsl:stylesheet>")).thenReturn(Map.of("m", why));
        final List<Map<String, Object>> docs = List.of(
                FakeBuildFacts.doc(PipelineDoc.TYPE, "p", "Acme", "working_copy_of", "prod"),
                FakeBuildFacts.doc(PipelineDoc.TYPE, "q", "Other", "working_copy_of", "prod"));

        assertThat(checks(drift).checks(docs)).containsExactly("Pipeline 'Acme': " + why);
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
