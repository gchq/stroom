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
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.sample.SurveyDoc;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.pipeline.shared.stepping.StepLocation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/// The records the documentation is written from: the streams' first records and the build's survey examples beyond
/// them, kept for the session while nothing they depend on changes. Seen in chat 173: a stream of 10,000 records
/// documented from its first 200, and the same 200 stepped again in each of some 20 rounds of field descriptions.
class TestSteppedSamples {

    private static final DocRef XSLT = new DocRef(XsltDoc.TYPE, "x1");
    private static final String BUILD = "acme-v1";

    private final PipelineStepping stepping = Mockito.mock(PipelineStepping.class);
    private final WriteGuard guard = Mockito.mock(WriteGuard.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final SteppedSamples samples = new SteppedSamples(stepping, guard, () -> documentAccess, () -> 0L);
    private final SteppedPipeline pipeline = Mockito.mock(SteppedPipeline.class);
    private final AtomicReference<String> xsltVersion = new AtomicReference<>("v1");
    private final ToolCallContext context = BuildToolCalls.context();

    @BeforeEach
    void setUp() {
        final MergedPipeline merged = Mockito.mock(MergedPipeline.class);
        Mockito.when(merged.templateChain()).thenReturn(List.of());
        Mockito.when(merged.properties()).thenReturn(Map.of(new MergedPipeline.ElementProperty("translation",
                "xslt"), Map.of("type", XsltDoc.TYPE, "uuid", "x1", "name", "X")));
        Mockito.when(pipeline.merged()).thenReturn(merged);
        Mockito.when(pipeline.ref()).thenReturn(new DocRef(PipelineDoc.TYPE, "p1"));
        Mockito.when(pipeline.name()).thenReturn("ACME-Events");
        Mockito.when(documentAccess.read(new DocRef(PipelineDoc.TYPE, "p1"))).thenReturn(Optional.of(
                PipelineDoc.builder().uuid("p1").version("p-v1").build()));
        Mockito.when(documentAccess.read(XSLT)).thenAnswer(read -> Optional.of(XsltDoc.builder().uuid("x1")
                .version(xsltVersion.get()).build()));
        Mockito.when(guard.folderContents(BUILD)).thenReturn(List.of());
        Mockito.when(stepping.outputs(any(), any(), any(), anyString(), any(), anyInt()))
                .thenReturn(outputs(false, "7:0", "7:1"));
    }

    @Test
    void theSameRecordsAreSteppedOnceASessionWhileNothingChanges() {
        samples.stepped(context, pipeline, List.of(7L), "translation", null, 200, BUILD);
        samples.stepped(context, pipeline, List.of(7L), "translation", null, 200, BUILD);
        Mockito.verify(stepping, Mockito.times(1)).outputs(any(), any(), any(), anyString(), any(), anyInt());

        // The XSLT saved again: stepped afresh.
        xsltVersion.set("v2");
        samples.stepped(context, pipeline, List.of(7L), "translation", null, 200, BUILD);
        Mockito.verify(stepping, Mockito.times(2)).outputs(any(), any(), any(), anyString(), any(), anyInt());

        // Other code stepped in an element's place: stepped afresh too.
        samples.stepped(context, pipeline, List.of(7L), "translation", Map.of("translation", "<xsl/>"), 200, BUILD);
        Mockito.verify(stepping, Mockito.times(3)).outputs(any(), any(), any(), anyString(), any(), anyInt());
    }

    @Test
    void aSteppingTheCallsTimeCutShortIsNotKept() {
        Mockito.when(stepping.outputs(any(), any(), any(), anyString(), any(), anyInt()))
                .thenReturn(outputs(true, "7:0"));

        samples.stepped(context, pipeline, List.of(7L), "translation", null, 200, BUILD);
        samples.stepped(context, pipeline, List.of(7L), "translation", null, 200, BUILD);

        Mockito.verify(stepping, Mockito.times(2)).outputs(any(), any(), any(), anyString(), any(), anyInt());
    }

    @Test
    @SuppressWarnings("unchecked")
    void theSurveysExamplesBeyondTheFirstRecordsAreSteppedToo() {
        final WriteGuard.BuildDoc survey = new WriteGuard.BuildDoc(DocumentationDoc.TYPE, "s1",
                "ACME" + StroomBuildFacts.SURVEY, List.of(), BUILD, null);
        Mockito.when(guard.folderContents(BUILD)).thenReturn(List.of(survey));
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("feed", "ACME");
        result.put("format", "delimited");
        result.put("streams_read", List.of(7));
        result.put("saturated", true);
        result.put("records_read", 2);
        result.put("new_shapes", 2);
        final Map<String, Map<String, Object>> shapes = new LinkedHashMap<>();
        shapes.put("row:a", new LinkedHashMap<>(Map.of("signature", "row:a", "count", 1, "streams", List.of(7L),
                "examples", List.of(Map.of("text", "a", "location", Map.of("stream", 7, "part", 0, "record", 1))))));
        shapes.put("row:b", new LinkedHashMap<>(Map.of("signature", "row:b", "count", 1, "streams", List.of(7L),
                "examples", List.of(Map.of("text", "b", "location", Map.of("stream", 7, "part", 0,
                        "record", 4500))))));
        final Map<String, Object> state = SurveyDoc.merge(null, result, shapes, 2, "2026-11-12T00:00:00Z");
        final DocumentationDoc surveyDoc = DocumentationDoc.builder().uuid("s1").name(survey.name())
                .data(SurveyDoc.render(state)).build();
        Mockito.when(documentAccess.read(eq(survey.docRef()), eq(DocumentationDoc.class)))
                .thenReturn(Optional.of(surveyDoc));
        Mockito.when(stepping.outputsAt(any(), any(), anyString(), any())).thenReturn(outputs(false, "7:4500"));

        final PipelineStepping.Outputs stepped = samples.stepped(context, pipeline, List.of(7L), "translation", null,
                200, BUILD);

        final ArgumentCaptor<List<StepLocation>> located = ArgumentCaptor.forClass(List.class);
        Mockito.verify(stepping).outputsAt(any(), located.capture(), anyString(), any());
        // The example among the first records isn't stepped again.
        assertThat(located.getValue()).extracting(StepLocation::getRecordIndex).containsExactly(4500L);
        assertThat(stepped.outputs()).containsOnlyKeys("7:0", "7:1", "7:4500");
    }

    private static PipelineStepping.Outputs outputs(final boolean stopped, final String... keys) {
        final Map<String, String> out = new LinkedHashMap<>();
        for (final String key : keys) {
            out.put(key, "<Events/>");
        }
        return new PipelineStepping.Outputs(out, Map.of(), stopped);
    }
}
