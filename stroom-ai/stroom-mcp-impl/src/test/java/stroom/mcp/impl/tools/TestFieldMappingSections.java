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
import stroom.ai.impl.tools.SteppingWalks;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.fielddoc.Descriptions;
import stroom.ai.impl.tools.fielddoc.FieldDocs;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

/// The Field mapping section of a pipeline's documentation: what is said when no events were sampled, and the line
/// for the template the user agreed.
class TestFieldMappingSections {

    private final TranslationWorld world = new TranslationWorld();
    private final PipelineStepping stepping = Mockito.mock(PipelineStepping.class);
    private final StreamSummaries streamSummaries = Mockito.mock(StreamSummaries.class);
    private final FieldMappingSections sections = new FieldMappingSections(stepping,
            Mockito.mock(DraftCefMappingTool.class), Mockito.mock(PipelineShapes.class), world.eventVersions,
            Mockito.mock(GeneratedDrift.class), world.streamReader, streamSummaries, Mockito.mock(IndexValues.class));
    private final PipelineDoc pipeline = PipelineDoc.builder().uuid("p").name("ACME-Events").build();

    @Test
    void aFieldMappingWithNoSampledEventsIsNotWrittenAndSaysWhy() {
        // A section of "(not in the sample)" was written, given an Events stream where the raw input belongs.
        Mockito.when(world.streamReader.meta(873L)).thenReturn(Meta.builder().id(873L).feedName("ACME")
                .typeName("Events").build());
        final SteppedPipeline loaded = Mockito.mock(SteppedPipeline.class);
        Mockito.when(loaded.name()).thenReturn("ACME-Events");
        Mockito.when(stepping.load("p")).thenReturn(loaded);
        Mockito.when(stepping.outputs(any(), any(), any(), anyString(), any(), anyInt()))
                .thenReturn(new PipelineStepping.Outputs(Map.of("873:0", "<Events xmlns=\"event-logging:3\"/>"),
                        false));
        final SteppingWalks.Walk walk = Mockito.mock(SteppingWalks.Walk.class);
        final SteppingResult first = Mockito.mock(SteppingResult.class);
        Mockito.when(first.isFoundRecord()).thenReturn(true);
        Mockito.when(walk.first()).thenReturn(first);
        Mockito.when(stepping.open(any(), anyLong(), any())).thenReturn(walk);
        final KeptMappings.KeptXslt kept = kept("translation", Map.of("mapping", TranslationWorld.mapping()));

        assertThatThrownBy(() -> sections.fieldMappingSection(BuildToolCalls.context(), "b", pipeline, kept,
                List.of(873L), FieldDocs.BY_EVENT_TYPE, new Descriptions()))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("no events (1 records stepped). stream 873 (ACME, Events): its records "
                                      + "produced no Event. An events pipeline's stream_ids are its raw sample "
                                      + "streams");

        // An index plan's Events streams with nothing in them are refused the same way.
        Mockito.when(streamSummaries.summariseEvents(List.of(873L), FieldMappingSections.RECORDS))
                .thenReturn(Map.of("path_population", Map.of()));
        final KeptMappings.KeptXslt plan = kept("index", plan("lucene", "acme", "EventTime"));

        assertThatThrownBy(() -> sections.fieldMappingSection(BuildToolCalls.context(), "b", pipeline, plan,
                List.of(873L), FieldDocs.BY_EVENT_TYPE, new Descriptions()))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("streams ['873 (ACME, Events)'] hold no events");
    }

    @Test
    void whyNoEventsCameIsSteppedWithinTheTimeLimit() {
        // Before, each stream was stepped again to say why, whatever time the call had left.
        Mockito.when(world.streamReader.meta(anyLong())).thenAnswer(invocation -> Meta.builder()
                .id(invocation.<Long>getArgument(0)).feedName("ACME").typeName("Raw Events").build());
        final SteppedPipeline loaded = Mockito.mock(SteppedPipeline.class);
        Mockito.when(stepping.load("p")).thenReturn(loaded);
        Mockito.when(stepping.outputs(any(), any(), any(), anyString(), any(), anyInt()))
                .thenReturn(new PipelineStepping.Outputs(Map.of("873:0", "<Events xmlns=\"event-logging:3\"/>"),
                        false));
        final SteppingWalks.Walk walk = Mockito.mock(SteppingWalks.Walk.class);
        final SteppingResult first = Mockito.mock(SteppingResult.class);
        Mockito.when(first.isFoundRecord()).thenReturn(true);
        Mockito.when(walk.first()).thenReturn(first).thenThrow(new ToolTimeoutException());
        Mockito.when(stepping.open(any(), anyLong(), any())).thenReturn(walk);
        final KeptMappings.KeptXslt kept = kept("translation", Map.of("mapping", TranslationWorld.mapping()));

        // The second stream's step runs out of time: it and the third are named, not stepped.
        assertThatThrownBy(() -> sections.fieldMappingSection(BuildToolCalls.context(), "b", pipeline, kept,
                List.of(873L, 874L, 875L), FieldDocs.BY_EVENT_TYPE, new Descriptions()))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("stream 873 (ACME, Raw Events): its records produced no Event; streams 874, "
                                      + "875 not stepped: the time for stepping ran out.");
        Mockito.verify(stepping, Mockito.times(2)).open(any(), anyLong(), any());
    }

    @Test
    void theIndexSectionNamesTheAgreedElasticsearchTemplate() {
        final Map<String, Object> agreed = Map.of("name", "stroom-door-v1", "index", "stroom-door-v1", "cluster",
                "ES", "component_templates", List.of("stroom-base"), "xslt", "x", "agreed", "2026-10-04T01:00:00Z",
                "dev_tools", "PUT _index_template/stroom-door-v1\n{}");
        final PipelineDoc agreedPipeline = pipeline.copy().description(KeptMapping.withAgreedTemplate("Doors.",
                agreed)).build();
        final KeptMappings.KeptXslt kept = kept("index", plan("elasticsearch", "stroom-door-v1", "@timestamp"));

        final String section = sections.fieldMappingSection(BuildToolCalls.context(), "b", agreedPipeline, kept,
                List.of(), FieldDocs.BY_EVENT_TYPE, new Descriptions());

        assertThat(section).contains("Elasticsearch index template `stroom-door-v1`, agreed with the user "
                                     + "2026-10-04, composed of `stroom-base`.")
                .endsWith(KeptMapping.DOC_MARK.replace("{digest}", BuildChecks.mappingDigest(kept)));
    }

    @Test
    void aTemplateTheUserCommittedThemselvesIsSaidAsTheirs() {
        final PipelineDoc theirs = pipeline.copy().description(KeptMapping.withAgreedTemplate("", Map.of(
                "by_user", true, "agreed", "2026-10-05T09:00:00Z"))).build();

        assertThat(FieldMappingSections.agreedLine(theirs)).isEqualTo("\nElasticsearch index template: the user's "
                                                                       + "own, committed to the cluster (confirmed "
                                                                       + "by them 2026-10-05, not agreed here).\n");
        assertThat(FieldMappingSections.agreedLine(pipeline)).isEmpty();
    }

    private static KeptMappings.KeptXslt kept(final String kind, final Map<String, Object> payload) {
        return new KeptMappings.KeptXslt(new KeptMapping.Kept(kind, payload), "xsltFilter",
                XsltDoc.builder().uuid("x").name("ACME").data("").build());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> plan(final String backend, final String index, final String time) {
        return JsonUtil.readValue("{\"backend\": \"" + backend + "\", \"index_name\": \"" + index + "\", "
                                  + "\"time_field\": \"" + time + "\", \"fields\": [{\"name\": \"User.Id\", "
                                  + "\"type\": \"keyword\", \"source\": \"EventSource/User/Id\"}]}", Map.class);
    }
}
