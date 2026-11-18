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
import stroom.ai.impl.tools.SteppingWalks;
import stroom.ai.impl.tools.StreamReader;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.stepping.SharedStepData;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class TestEventLocations {

    static final PipelineDoc PIPELINE = PipelineDoc.builder()
            .uuid("p")
            .name("Acme-Events")
            .pipelineData(new PipelineDataBuilder()
                    .addElement(new PipelineElement("parser", "XMLParser"))
                    .addElement(new PipelineElement("out", "XSLTFilter"))
                    .build())
            .build();

    final StreamReader streamReader = Mockito.mock(StreamReader.class);
    final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    final SteppingWalks steppingWalks = Mockito.mock(SteppingWalks.class);
    final SteppingWalks.Walk walk = Mockito.mock(SteppingWalks.Walk.class);
    final Map<Long, Meta> metas = new HashMap<>();
    final EventLocations eventLocations = new EventLocations(streamReader, documentAccess, steppingWalks);

    @BeforeEach
    void setUp() {
        Mockito.when(streamReader.meta(Mockito.anyLong())).thenAnswer(invocation -> {
            final Meta meta = metas.get(invocation.<Long>getArgument(0));
            if (meta == null) {
                throw new StreamReader.StreamReadException("No stream with id " + invocation.getArgument(0));
            }
            return meta;
        });
        Mockito.when(streamReader.children(Mockito.anyLong(), Mockito.anyInt())).thenAnswer(invocation ->
                metas.values().stream()
                        .filter(meta -> Long.valueOf(invocation.<Long>getArgument(0)).equals(meta.getParentMetaId()))
                        .map(meta -> new MetaRow(meta, null, Map.of()))
                        .toList());
        Mockito.when(documentAccess.findDocRef("p")).thenReturn(Optional.of(PIPELINE.asDocRef()));
        Mockito.when(documentAccess.read(Mockito.any(DocRef.class), Mockito.eq(PipelineDoc.class)))
                .thenReturn(Optional.of(PIPELINE));
        Mockito.when(steppingWalks.open(Mockito.any(), Mockito.anyLong(), Mockito.any(), Mockito.any()))
                .thenReturn(walk);
        add(6, "Raw Events", null, null);
        add(8, "Events", 6L, "p");
        add(9, "Error", 6L, "p");
    }

    @Test
    void eventsAreLocatedOnePassPerRawStream() {
        // Proving a fix on exactly what a search found: each hit to its raw record, however many events a record
        // gives. Record 1 gives event 1, record 2 none, record 3 events 2 and 3.
        steps("<Events><Event/></Events>", "<Events/>", "<Events><Event/><Event/></Events>",
                "<Events><Event/></Events>");

        final List<Map<String, Object>> found = eventLocations.eventLocations(List.of(
                new EventLocations.EventRef(8, 3), new EventLocations.EventRef(8, 1)));

        assertThat(found).extracting(f -> f.get("event_id"), f -> f.get("record"), f -> f.get("raw"))
                .containsExactly(tuple(1L, 1L, 6L), tuple(3L, 3L, 6L));
        Mockito.verify(steppingWalks, Mockito.times(1)).open(Mockito.any(), Mockito.eq(6L), Mockito.any(),
                Mockito.any());
        Mockito.verify(walk).close();
    }

    @Test
    void anEventNotFoundSaysThePipelineMayHaveChanged() {
        steps("<Events><Event/></Events>");

        assertThatThrownBy(() -> eventLocations.eventLocations(List.of(new EventLocations.EventRef(8, 5))))
                .hasMessageContaining("Event(s) [5] of Events stream 8 were not found in the first 1 records of raw "
                                      + "stream 6 (1 events produced)");
    }

    @Test
    void anEventOfAStreamThatIsntAnEventsStreamIsRefused() {
        assertThatThrownBy(() -> eventLocations.eventLocations(List.of(new EventLocations.EventRef(9, 1))))
                .hasMessage("Stream 9 is not an Events stream: give each event's Events stream (StreamId)");
    }

    @Test
    void whateverStreamWasReportedTracesBackToTheRawStreamAndPipeline() {
        final EventLocations.Resolved fromError = eventLocations.resolve(9, null);
        assertThat(List.of(fromError.raw(), fromError.pipeline(), fromError.events())).containsExactly(6L, "p", 8L);
        final EventLocations.Resolved fromRaw = eventLocations.resolve(6, null);
        assertThat(List.of(fromRaw.raw(), fromRaw.pipeline(), fromRaw.events())).containsExactly(6L, "p", 8L);
        assertThat(fromRaw.reported()).isEqualTo(Map.of("id", 6L, "type", "Raw Events"));
    }

    @Test
    void aRawStreamProcessedBySeveralPipelinesNeedsOneNamed() {
        add(10, "Events", 6L, "q");

        assertThatThrownBy(() -> eventLocations.resolve(6, null))
                .hasMessage("Stream 6 was processed by several pipelines [p, q]; give pipeline_uuid");
        assertThat(eventLocations.resolve(6, "q").events()).isEqualTo(10L);
    }

    @Test
    void aRawStreamWithNoOutputNeedsAPipelineToStepIt() {
        add(20, "Raw Events", null, null);

        assertThatThrownBy(() -> eventLocations.resolve(20, null))
                .hasMessage("Stream 20 (Raw Events) has no Events output; give pipeline_uuid to step it");
        assertThat(eventLocations.resolve(20, "p").events()).isNull();
    }

    @Test
    void aDeletedOutputIsNotTracedTo() {
        metas.put(8L, Meta.builder().id(8).typeName("Events").parentDataId(6L).pipelineUuid("p")
                .status(Status.DELETED).build());

        assertThatThrownBy(() -> eventLocations.resolve(6, null)).hasMessageContaining("has no Events output");
    }

    void add(final long id, final String type, final Long parent, final String pipeline) {
        metas.put(id, Meta.builder().id(id).feedName("ACME").typeName(type).parentDataId(parent)
                .pipelineUuid(pipeline).status(Status.UNLOCKED).build());
    }

    /// Steps that give these outputs from the `out` element, records 1, 2 and so on.
    void steps(final String... outputs) {
        final SteppingResult[] results = new SteppingResult[outputs.length + 1];
        for (int i = 0; i < outputs.length; i++) {
            results[i] = step(i + 1, outputs[i], "<record " + (i + 1) + "/>");
        }
        results[outputs.length] = new SteppingResult("s", null, null, null, null, null, false, null, false, true);
        Mockito.when(walk.first()).thenReturn(results[0]);
        final SteppingResult[] rest = new SteppingResult[outputs.length];
        System.arraycopy(results, 1, rest, 0, outputs.length);
        Mockito.when(walk.forward(Mockito.any())).thenReturn(rest[0],
                Arrays.copyOfRange(rest, 1, rest.length));
    }

    static SteppingResult step(final long record, final String output, final String input) {
        return new SteppingResult("s", null, null, new StepLocation(6L, 0L, record), new SharedStepData(null,
                Map.of("out", new SharedElementData(input, output, null, false, false, true),
                        "parser", new SharedElementData(input, input, null, false, false, true))),
                null, true, null, false, true);
    }
}
