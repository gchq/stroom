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

import stroom.ai.impl.tools.FeedAccess;
import stroom.ai.impl.tools.StreamReader;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/// The processing helpers for wait_for_processing and its write tools: which streams a filter selects, the outputs
/// and ended tasks of input streams, and why nothing will process one.
class TestStreamProcessing {

    private static final Meta RAW = Meta.builder().id(6).feedName("ACME").typeName("Raw Events").createMs(2_000)
            .build();

    private final StreamReader streamReader = Mockito.mock(StreamReader.class);
    private final FeedAccess feedAccess = Mockito.mock(FeedAccess.class);
    private final ProcessorTaskService taskService = Mockito.mock(ProcessorTaskService.class);
    private final StreamProcessing processing = new StreamProcessing(streamReader, feedAccess, () -> taskService);

    @Test
    void aFilterSelectsTheStreamsItsTermsName() {
        assertThat(StreamProcessing.selects(null, RAW)).isTrue();
        assertThat(StreamProcessing.selects(and(term("Id", "6")), RAW)).isTrue();
        assertThat(StreamProcessing.selects(and(term("Id", "7")), RAW)).isFalse();
        // Stroom matches feed names whatever their case.
        assertThat(StreamProcessing.selects(and(term("Feed", "acme"), term("Type", "Raw Events")), RAW)).isTrue();
        assertThat(StreamProcessing.selects(and(term("Feed", "acme"), term("Type", "Events")), RAW)).isFalse();
        assertThat(StreamProcessing.selects(ExpressionOperator.builder().op(Op.OR)
                .addTerm(term("Id", "7")).addTerm(term("Id", "6")).build(), RAW)).isTrue();
        assertThat(StreamProcessing.selects(and(ExpressionTerm.builder().field("Feed").condition(Condition.IS_DOC_REF)
                .docRef(new DocRef("Feed", "f", "ACME")).build()), RAW)).isTrue();
        // Any other term counts as selecting it, so a stream is only reported unselected when it plainly is.
        assertThat(StreamProcessing.selects(and(term("Status", "Locked")), RAW)).isTrue();
        assertThat(StreamProcessing.selects(ExpressionOperator.builder().op(Op.NOT).addTerm(term("Id", "6")).build(),
                RAW)).isTrue();
        // A disabled term is left out.
        assertThat(StreamProcessing.selects(and(term("Id", "7").copy().enabled(false).build()), RAW)).isTrue();
    }

    @Test
    void aFilterWillProcessAStreamOnlyEnabledAndWithinItsTimes() {
        final ProcessorFilter filter = ProcessorFilter.builder().id(1).enabled(true).minMetaCreateTimeMs(1_000L)
                .build();
        assertThat(StreamProcessing.willProcess(filter, RAW)).isTrue();
        assertThat(StreamProcessing.willProcess(filter.copy().enabled(false).build(), RAW)).isFalse();
        assertThat(StreamProcessing.willProcess(filter.copy().minMetaCreateTimeMs(3_000L).build(), RAW)).isFalse();
        assertThat(StreamProcessing.willProcess(filter.copy().maxMetaCreateTimeMs(1_500L).build(), RAW)).isFalse();
    }

    @Test
    void theFiltersWaitingOnTheUserAreThoseThatWouldProcessTheStream() {
        final ProcessorFilter disabled = ProcessorFilter.builder().id(3).enabled(false).build();
        final ProcessorFilter stopped = ProcessorFilter.builder().id(4).enabled(true)
                .processor(Processor.builder().id(70).enabled(false).build()).build();
        final ProcessorFilter other = ProcessorFilter.builder().id(5).enabled(false)
                .queryData(TestPipelineFeeds.filter(5, false, term("Id", "9")).getQueryData()).build();

        final StreamProcessing.Waiting waiting = StreamProcessing.waitingOn(List.of(disabled, stopped, other),
                List.of(RAW));

        assertThat(waiting.disabled()).containsExactly(3);
        assertThat(waiting.stopped()).containsExactly(4);
        assertThat(waiting.what()).isEqualTo("filter(s) [3] and the processor of filter(s) [4] (the pipeline's "
                                             + "processor row, above its filters)");
        // One enabled filter that will process it: nothing waits.
        assertThat(StreamProcessing.waitingOn(List.of(disabled, ProcessorFilter.builder().id(6).enabled(true).build()),
                List.of(RAW)).what()).isEmpty();
    }

    @Test
    void aStreamNoFilterWillProcessIsSaidWithEachFiltersReason() {
        final ProcessorFilter disabled = ProcessorFilter.builder().id(3).enabled(false).build();
        final ProcessorFilter late = ProcessorFilter.builder().id(4).enabled(true).maxMetaCreateTimeMs(1_000L).build();

        assertThat(StreamProcessing.unselected(List.of(disabled, late), List.of(RAW))).containsExactly(
                "No processor filter of this pipeline will process stream 6 (ACME, Raw Events, created "
                + "1970-01-01T00:00:02.000Z): filter 3 is disabled; filter 4 selects only streams created before "
                + "1970-01-01T00:00:01.000Z");
        assertThat(StreamProcessing.unselected(List.of(disabled, ProcessorFilter.builder().id(5).enabled(true)
                .build()), List.of(RAW))).isEmpty();
    }

    @Test
    void outputsAreThePipelinesStreamsFromTheInputThatArentDeleted() {
        Mockito.when(streamReader.children(6, StreamProcessing.MAX_OUTPUTS)).thenReturn(List.of(
                row(Meta.builder().id(20).pipelineUuid("p1").processorFilterId(3).status(Status.UNLOCKED).build()),
                row(Meta.builder().id(21).pipelineUuid("p1").processorFilterId(9).status(Status.DELETED).build()),
                row(Meta.builder().id(22).pipelineUuid("other").processorFilterId(9).status(Status.UNLOCKED).build()),
                row(Meta.builder().id(23).pipelineUuid("p1").processorFilterId(9).status(Status.UNLOCKED).build())));

        assertThat(processing.outputs(6, "p1", null)).extracting(Meta::getId).containsExactly(20L, 23L);
        assertThat(processing.outputs(6, "p1", 9)).extracting(Meta::getId).containsExactly(23L);
    }

    @Test
    void theStreamsWhoseTasksEndedAreThoseCompleteOrFailedOfFeedsTheUserMayView() {
        Mockito.when(feedAccess.canView(anyString())).thenAnswer(inv -> !"SECRET".equals(inv.getArgument(0)));
        Mockito.when(taskService.find(any(ExpressionCriteria.class))).thenAnswer(inv -> {
            final ExpressionCriteria criteria = inv.getArgument(0);
            return criteria.getPageRequest().getOffset() > 0
                    ? ResultPage.empty()
                    : new ResultPage<>(List.of(task(TaskStatus.COMPLETE, 5L, "ACME"),
                            task(TaskStatus.FAILED, 6L, "ACME"), task(TaskStatus.PROCESSING, 7L, "ACME"),
                            task(TaskStatus.COMPLETE, 8L, "SECRET")));
        });

        final StreamProcessing.Tasks tasks = processing.tasks(List.of(9), List.of(5L, 6L, 7L, 8L));
        assertThat(tasks.ended()).containsExactly(5L, 6L);
        assertThat(tasks.open()).containsExactly(7L);
    }

    private static ExpressionOperator and(final ExpressionTerm... terms) {
        return ExpressionOperator.builder().op(Op.AND).addTerms(terms).build();
    }

    private static ExpressionTerm term(final String field, final String value) {
        return TestPipelineFeeds.term(field, Condition.EQUALS, value, null);
    }

    private static MetaRow row(final Meta meta) {
        return new MetaRow(meta, new DocRef(PipelineDoc.TYPE, "p1"), Map.of());
    }

    private static ProcessorTask task(final TaskStatus status, final Long metaId, final String feed) {
        return ProcessorTask.builder().id(1).status(status).metaId(metaId).feedName(feed).build();
    }
}
