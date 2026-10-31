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

import stroom.ai.impl.tools.StreamReader;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaRow;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;

/// The streams a rebuilt mapping is proven on ([SampleStreams]).
class TestSampleStreams {

    private final ProcessorFilters processorFilters = Mockito.mock(ProcessorFilters.class);
    private final PipelineFeeds pipelineFeeds = Mockito.mock(PipelineFeeds.class);
    private final StreamReader streamReader = Mockito.mock(StreamReader.class);
    private final BuildWorld world = new BuildWorld();
    private final SampleStreams sampleStreams = new SampleStreams(processorFilters, pipelineFeeds, streamReader,
            world.guard());
    private final List<Long> gone = new ArrayList<>();

    @Test
    void theProofRunsOnThePipelinesOriginalSamplesElseItsFeedsNewestStreams() {
        // Asked for by the user: prove on the original sample data, without the agent having to find it.
        Mockito.when(streamReader.metaRow(anyLong())).thenAnswer(inv -> {
            final long id = inv.getArgument(0);
            return gone.contains(id)
                    ? Optional.empty()
                    : Optional.of(new MetaRow(Meta.builder().id(id).typeName("Raw Events").build(), null, Map.of()));
        });
        Mockito.when(streamReader.find(any(), anyInt(), anyBoolean())).thenReturn(new ResultPage<>(List.of(
                row(901), row(900))));
        Mockito.when(pipelineFeeds.filterFeeds(any(), any(), anyBoolean())).thenAnswer(inv -> {
            final List<ExpressionTerm> terms = inv.getArgument(0);
            return terms.stream().anyMatch(term -> "Feed".equals(term.getField()))
                    ? Set.of("ACME")
                    : Set.of();
        });
        // Stepped but never processed: the feeds of the build the pipeline is in.
        world.inBuild("acme", PipelineDoc.TYPE, "ACME-Events", "p-1");
        world.inBuild("acme", FeedDoc.TYPE, "ACME", null);

        final ExpressionTerm id101 = idTerm("101");
        final ExpressionTerm id102 = idTerm("102");
        final ExpressionTerm feed = ExpressionTerm.builder().field("Feed").condition(Condition.EQUALS).value("ACME")
                .build();

        expect(List.of(filter(id101, id102)), List.of(101L, 102L), "original sample streams");
        gone.addAll(List.of(101L, 102L));
        expect(List.of(filter(id101, id102), filter(feed)), List.of(901L, 900L),
                "feed 'ACME' (its sample streams are gone)");
        gone.clear();
        expect(List.of(filter(feed)), List.of(901L, 900L), "feed 'ACME' (its sample processor filters are gone)");
        expect(List.of(), List.of(901L, 900L),
                "feed 'ACME' (it has no sample processor filters: only ever stepped)");
    }

    private void expect(final List<ProcessorFilter> filters, final List<Long> streams, final String said) {
        Mockito.when(processorFilters.all()).thenReturn(filters);
        final SampleStreams.Found found = sampleStreams.sampleStreams("p-1");
        assertThat(found.streamIds()).isEqualTo(streams);
        assertThat(found.which()).contains(said);
    }

    private static ProcessorFilter filter(final ExpressionTerm... terms) {
        final ExpressionOperator.Builder expression = ExpressionOperator.builder().op(Op.AND);
        for (final ExpressionTerm term : terms) {
            expression.addTerm(term);
        }
        return ProcessorFilter.builder().id(7)
                .processor(Processor.builder().id(70).enabled(true).build())
                .pipelineUuid("p-1")
                .queryData(QueryData.builder().expression(expression.build()).build())
                .build();
    }

    private static ExpressionTerm idTerm(final String id) {
        return ExpressionTerm.builder().field("Id").condition(Condition.EQUALS).value(id).build();
    }

    private static MetaRow row(final long id) {
        return new MetaRow(Meta.builder().id(id).feedName("ACME").typeName("Raw Events").build(), null, Map.of());
    }
}
