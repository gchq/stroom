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
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.docref.DocRef;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;

/// rebuild_mapping, the tool: the mapping read back from the XSLT, proven by stepping both XSLTs over the sample,
/// and saved only once the outputs agree (or the user accepts the differences) and the user confirms (the Stroom MCP
/// server's `tools/rebuild.py`, whose flow `dev/e2e_restyle.py` covers against a live Stroom; the reader itself is
/// tested in stroom-ai-impl's `TestXsltReader`).
class TestRebuildMappingTool {

    private static final String BUILD = "acme-v1";
    private static final String EVENT = "<Events xmlns=\"event-logging:3\"><Event><EventSource><Generator>vpnd"
                                        + "</Generator></EventSource></Event></Events>";

    private final TranslationWorld world = new TranslationWorld();
    private final PipelineIndex pipelineIndex = Mockito.mock(PipelineIndex.class);
    private final SampleStreams sampleStreams = Mockito.mock(SampleStreams.class);
    private final RebuildMappingTool tool = new RebuildMappingTool(world.docs, world.world.guard(),
            world.eventVersions, world.consent, world.stepping, world.world.pipelineShapes, pipelineIndex,
            sampleStreams, () -> world.docFinder, world.world.documentAccess);
    private String uuid;
    private String after = EVENT;

    @BeforeEach
    void setUp() {
        final Map<String, Object> saved = world.docs.createXslt(world.context(), BUILD, "ACME-Events",
                generated(TranslationWorld.parsed()), TranslationDocs.Source.of(TranslationWorld.parsed()), null);
        uuid = String.valueOf(saved.get("uuid"));
        PipelineLayers.given(world.world.pipelineService, "p", PipelineLayers.of(
                PipelineLayers.layer("t").chain("dsParser", "DSParser", "translationFilter", "XSLTFilter"),
                PipelineLayers.layer("p").property("translationFilter", "xslt",
                        new DocRef(XsltDoc.TYPE, uuid, "ACME-Events"))));
        final SteppedPipeline pipeline = Mockito.mock(SteppedPipeline.class);
        Mockito.when(pipeline.name()).thenReturn("ACME");
        Mockito.when(world.stepping.load(anyString())).thenReturn(pipeline);
        Mockito.when(world.stepping.outputs(any(), any(), any(), anyString(), isNull(), anyInt())).thenAnswer(inv ->
                new PipelineStepping.Outputs(Map.of("7:1", EVENT), false));
        Mockito.when(world.stepping.outputs(any(), any(), any(), anyString(), any(Map.class), anyInt()))
                .thenAnswer(inv -> new PipelineStepping.Outputs(Map.of("7:1", after), false));
    }

    @Test
    void anXsltThatIsWhatItsMappingGeneratesIsInStep() {
        final Map<String, Object> result = call(Map.of());

        assertThat(result).containsEntry("status", "in_step");
        assertThat(result.get("hint")).isEqualTo("The XSLT is what its kept mapping generates: nothing to rebuild.");
        Mockito.verifyNoInteractions(world.stepping);
    }

    @Test
    void aHandEditIsProvenOnTheSampleConfirmedThenSaved() {
        world.editByHand(uuid, TestRebuildMappingTool::environmentChanged);

        final Map<String, Object> asked = call(Map.of());
        assertThat(asked).containsEntry("status", "needs_confirmation")
                .containsEntry("mode", "out of step with the kept mapping");
        assertThat(String.valueOf(asked.get("summary"))).startsWith("Save the mapping rebuilt from XSLT "
                                                                    + "'ACME-Events' (carrying hand edits into the "
                                                                    + "mapping), and the XSLT regenerated from it: "
                                                                    + "it wrote the same output as the XSLT for all "
                                                                    + "1 records stepped");
        assertThat(asked.get("proven_on")).isEqualTo(Map.of("records", 1, "streams", List.of(7), "which",
                "the streams given"));
        final String before = world.xslt(uuid).getData();

        final Map<String, Object> done = call(Map.of("confirmation_id", asked.get("confirmation_id")));
        assertThat(done).containsKey("saved").containsKey("next");
        final XsltDoc saved = world.xslt(uuid);
        // The XSLT is the one regenerated from the rebuilt mapping, kept with it: the two agree again.
        final TranslationMapping kept = TranslationMapping.parse(KeptMapping.readMapping(saved.getDescription())
                .orElseThrow().payload().get("mapping"));
        assertThat(XsltCode.normalise(saved.getData())).isEqualTo(XsltCode.normalise(generated(kept)));
        assertThat(saved.getData()).contains("<Environment>Test</Environment>");
        assertThat(XsltCode.normalise(before)).isEqualTo(XsltCode.normalise(saved.getData()));
    }

    @Test
    void outputsThatDifferAreShownAndNothingIsSavedUnlessAccepted() {
        world.editByHand(uuid, TestRebuildMappingTool::environmentChanged);
        after = EVENT.replace("vpnd", "other");

        final Map<String, Object> refused = call(Map.of());
        assertThat(refused).doesNotContainKey("saved").doesNotContainKey("status");
        assertThat(String.valueOf(refused.get("hint"))).startsWith("The regenerated XSLT writes these records "
                                                                   + "differently from the XSLT: nothing was saved.");
        assertThat((List<?>) refused.get("differences")).singleElement().satisfies(difference ->
                assertThat(((Map<?, ?>) difference).get("path") + " " + ((Map<?, ?>) difference).get("records"))
                        .isEqualTo("Event/EventSource/Generator 1"));

        final Map<String, Object> asked = call(Map.of("accept_differences", true));
        assertThat(asked).containsEntry("status", "needs_confirmation");
        assertThat(String.valueOf(asked.get("summary"))).endsWith("1 output paths differ, as shown");
    }

    @Test
    void aPipelineThatDoesNotRunTheXsltIsRefused() {
        world.editByHand(uuid, TestRebuildMappingTool::environmentChanged);
        PipelineLayers.given(world.world.pipelineService, "q", PipelineLayers.of(
                PipelineLayers.layer("t").chain("dsParser", "DSParser", "translationFilter", "XSLTFilter")));

        assertThat(call(Map.of("pipeline_uuid", "q")).get("error"))
                .isEqualTo("Pipeline q doesn't run XSLT 'ACME-Events'");
    }

    private Map<String, Object> call(final Map<String, Object> more) {
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("uuid", uuid);
        arguments.put("pipeline_uuid", "p");
        arguments.put("stream_ids", List.of(7));
        arguments.putAll(more);
        return world.call(tool, arguments);
    }

    // A constant changed by hand in Stroom's editor.
    private static String environmentChanged(final String code) {
        return code.replaceFirst("<Environment>Dev</Environment>", "<Environment>Test</Environment>");
    }

    private static String generated(final TranslationMapping mapping) {
        return stroom.ai.impl.tools.generation.XsltGenerator.generate(mapping, TranslationWorld.SCHEMA,
                TranslationWorld.VERSION).xslt();
    }
}
