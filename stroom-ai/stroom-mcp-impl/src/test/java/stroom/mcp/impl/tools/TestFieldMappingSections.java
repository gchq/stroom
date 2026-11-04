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
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.SteppingWalks;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.fielddoc.Descriptions;
import stroom.ai.impl.tools.fielddoc.FieldDocs;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/// The Field mapping section of a pipeline's documentation: what is said when no events were sampled, and the line
/// for the template the user agreed.
class TestFieldMappingSections {

    private final TranslationWorld world = new TranslationWorld();
    private final PipelineStepping stepping = Mockito.mock(PipelineStepping.class);
    private final StreamSummaries streamSummaries = Mockito.mock(StreamSummaries.class);
    private final PipelineShapes pipelineShapes = Mockito.mock(PipelineShapes.class);
    private final GeneratedDrift drift = Mockito.mock(GeneratedDrift.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final FieldMappingSections sections = new FieldMappingSections(stepping,
            Mockito.mock(DraftCefMappingTool.class), pipelineShapes, world.eventVersions,
            drift, world.streamReader, streamSummaries, Mockito.mock(IndexValues.class),
            Mockito.mock(ReferenceEvaluation.class), () -> documentAccess);
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
    void anXsltWrittenByHandIsDocumentedByTheEventTypesItWrites() {
        // Seen in testing: the agent's own section for an XSLT written by hand had no event type's fields.
        final MergedPipeline merged = Mockito.mock(MergedPipeline.class);
        final DocRef xslt = new DocRef(XsltDoc.TYPE, "x", "ACME-Events");
        Mockito.when(merged.translationDocs("p")).thenReturn(List.of(new MergedPipeline.TranslationDoc(
                "translationFilter", "XSLTFilter", xslt, "p", false)));
        Mockito.when(pipelineShapes.merged("p")).thenReturn(merged);
        Mockito.when(documentAccess.read(xslt, XsltDoc.class)).thenReturn(Optional.of(XsltDoc.builder().uuid("x")
                .name("ACME-Events").data("<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                                          + "xmlns=\"event-logging:3\" version=\"3.0\"/>").build()));
        final SteppedPipeline loaded = Mockito.mock(SteppedPipeline.class);
        Mockito.when(stepping.load("p")).thenReturn(loaded);
        Mockito.when(stepping.outputs(any(), any(), any(), anyString(), any(), anyInt()))
                .thenReturn(new PipelineStepping.Outputs(Map.of(
                        "5:0", events("<TypeId>login</TypeId><Authenticate><Action>Logon</Action></Authenticate>"),
                        "5:1", events("<TypeId>file-read</TypeId><View><Document><File><Path>/etc/hosts</Path>"
                                      + "</File></Document></View>")), false));

        final String section = sections.writtenEventsSection(BuildToolCalls.context(), "b", "p", List.of(5L),
                FieldDocs.BY_EVENT_TYPE, new Descriptions());

        assertThat(section).startsWith("Counts are of 2 sample records (2 of stream 5).")
                .contains("### Common fields", "`EventTime/TimeCreated`", "#### login", "#### file-read",
                        "`EventDetail/Authenticate/Action`", "\"/etc/hosts\"");
        // Stepped with the XSLT as saved, not code of the tool's own.
        Mockito.verify(stepping).outputs(any(), any(), eq(List.of(5L)), eq("translationFilter"), eq(null),
                anyInt());
    }

    @Test
    void anXsltRewrittenByHandIsDocumentedByEventTypeNotByRulesThatNoLongerWriteIt() {
        // Most of its events no longer the mapping's (each record now gives two events where the mapping gave one):
        // the mapping's rules would document what it no longer writes.
        final KeptMappings.KeptXslt kept = kept("translation", Map.of("mapping", TranslationWorld.mapping()));
        Mockito.when(drift.drift(kept)).thenReturn(Optional.of("its XSLT was saved as written by hand"));
        final SteppedPipeline loaded = Mockito.mock(SteppedPipeline.class);
        Mockito.when(stepping.load("p")).thenReturn(loaded);
        final String marked = "<Events xmlns=\"event-logging:3\"><Event><?stroom-mcp-rule login?><EventTime>"
                              + "<TimeCreated>2026-11-04T01:00:00.000Z</TimeCreated></EventTime></Event></Events>";
        final String running = events("<TypeId>logon</TypeId><Authenticate><Action>Logon</Action></Authenticate>")
                .replace("</Events>", "")
                               + events("<TypeId>logoff</TypeId><Authenticate><Action>Logoff</Action>"
                                        + "</Authenticate>").replaceFirst("^<Events[^>]*>", "");
        Mockito.when(stepping.outputs(any(), any(), any(), anyString(), Mockito.isNotNull(), anyInt()))
                .thenReturn(new PipelineStepping.Outputs(Map.of("5:0", marked), false));
        Mockito.when(stepping.outputs(any(), any(), any(), anyString(), Mockito.isNull(), anyInt()))
                .thenReturn(new PipelineStepping.Outputs(Map.of("5:0", running), false));

        final String section = sections.fieldMappingSection(BuildToolCalls.context(), "b", pipeline, kept,
                List.of(5L), FieldDocs.BY_EVENT_TYPE, new Descriptions());

        assertThat(section).contains("The XSLT was written by hand", "#### logon, logoff",
                        "\"Logon\"", "\"Logoff\"")
                .contains("Note: Its XSLT was saved as written by hand.");
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

    private static String events(final String detail) {
        return "<Events xmlns=\"event-logging:3\"><Event><EventTime><TimeCreated>2026-11-04T01:00:00.000Z"
               + "</TimeCreated></EventTime><EventSource><System><Name>ACME</Name></System></EventSource>"
               + "<EventDetail>" + detail + "</EventDetail></Event></Events>";
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
