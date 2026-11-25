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

package stroom.ai.impl.tools.streams;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.security.api.SecurityContext;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/// Each Events stream traced to the feeding pipeline that indexed it: by its tasks, else the candidates' processor
/// filters, read as the processing user; only the candidates named.
class TestIndexingTrace {

    private static final DocRef V1 = new DocRef(PipelineDoc.TYPE, "v1-uuid", "Endpoint indexing V1");
    private static final DocRef V2 = new DocRef(PipelineDoc.TYPE, "v2-uuid", "Endpoint indexing V2");
    private static final DocRef OTHER = new DocRef(PipelineDoc.TYPE, "other-uuid", "Not a candidate");

    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final ProcessorTaskService taskService = Mockito.mock(ProcessorTaskService.class);
    private final ProcessorFilterService filterService = Mockito.mock(ProcessorFilterService.class);
    private final MetaService metaService = Mockito.mock(MetaService.class);
    private final List<ProcessorTask> tasks = new ArrayList<>();
    // The streams each filter selects, by its uuid.
    private final Map<String, Set<Long>> selects = new LinkedHashMap<>();
    private final List<String> asProcessingUser = new ArrayList<>();
    private IndexingTrace trace;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Mockito.when(securityContext.asProcessingUserResult(Mockito.any())).thenAnswer(invocation -> {
            asProcessingUser.add("read");
            return ((Supplier<?>) invocation.getArgument(0)).get();
        });
        Mockito.when(taskService.find(Mockito.any(ExpressionCriteria.class))).thenAnswer(invocation -> {
            final Set<Long> asked = ids(invocation.<ExpressionCriteria>getArgument(0).getExpression());
            return ResultPage.createUnboundedList(tasks.stream().filter(task -> asked.contains(task.getMetaId()))
                    .toList());
        });
        Mockito.when(filterService.find(Mockito.any(DocRef.class))).thenAnswer(invocation -> {
            final DocRef pipeline = invocation.getArgument(0);
            return ResultPage.createUnboundedList(selects.keySet().stream()
                    .filter(uuid -> uuid.startsWith(pipeline.getUuid()))
                    .map(uuid -> ProcessorFilter.builder().uuid(uuid).pipelineUuid(pipeline.getUuid())
                            .queryData(QueryData.builder().expression(ExpressionOperator.builder()
                                    .addTextTerm(MetaFields.FEED, ExpressionTerm.Condition.EQUALS, uuid).build())
                                    .build())
                            .build())
                    .toList());
        });
        Mockito.when(metaService.find(Mockito.any(FindMetaCriteria.class))).thenAnswer(invocation -> {
            final ExpressionOperator expression = invocation.<FindMetaCriteria>getArgument(0).getExpression();
            final Set<Long> asked = ids(expression);
            final String filter = feedOf(expression);
            return ResultPage.createUnboundedList(selects.getOrDefault(filter, Set.of()).stream()
                    .filter(asked::contains)
                    .map(id -> Meta.builder().id(id).build())
                    .toList());
        });
        trace = new IndexingTrace(securityContext, () -> taskService, () -> filterService, () -> metaService);
    }

    @Test
    void aStreamsTasksTellItsPipelineExactlyTheCandidatesOnly() {
        task(21, V1);
        // A task of a pipeline that doesn't feed the index (e.g. another index's) is no evidence.
        task(21, OTHER);
        task(22, V2);

        final IndexingTrace.Traced traced = trace.trace(List.of(21L, 22L, 22L), List.of(V1, V2));

        assertThat(traced.byTasks()).containsExactlyInAnyOrderEntriesOf(Map.of(21L, V1, 22L, V2));
        assertThat(traced.byFilters()).isEmpty();
        assertThat(traced.ambiguous()).isEmpty();
        assertThat(asProcessingUser).hasSize(1);
        // The filters aren't read where every stream's tasks were found.
        Mockito.verifyNoInteractions(filterService, metaService);
    }

    @Test
    void withoutTasksTheOneCandidateWhoseFilterSelectsTheStreamIsItsPipelineAndTwoAreAmbiguous() {
        selects.put("v1-uuid-filter", Set.of(31L, 33L));
        selects.put("v2-uuid-filter", Set.of(32L, 33L));

        final IndexingTrace.Traced traced = trace.trace(Set.of(31L, 32L, 33L, 34L), List.of(V1, V2));

        assertThat(traced.byFilters()).containsExactlyInAnyOrderEntriesOf(Map.of(31L, V1, 32L, V2));
        assertThat(traced.ambiguous()).containsExactly(33L);
        // A stream no candidate's filter selects is left out.
        assertThat(traced.byTasks()).isEmpty();
    }

    @Test
    void twoCandidatesTasksOnOneStreamAreAmbiguous() {
        task(41, V1);
        task(41, V2);

        final IndexingTrace.Traced traced = trace.trace(List.of(41L), List.of(V1, V2));

        assertThat(traced.ambiguous()).containsExactly(41L);
        assertThat(traced.byTasks()).isEmpty();
    }

    @Test
    void nothingToTraceOrAFailedReadTracesNothing() {
        assertThat(trace.trace(List.of(), List.of(V1))).isEqualTo(IndexingTrace.Traced.NONE);
        assertThat(trace.trace(List.of(1L), List.of())).isEqualTo(IndexingTrace.Traced.NONE);
        Mockito.when(taskService.find(Mockito.any(ExpressionCriteria.class))).thenThrow(
                new RuntimeException("no tasks"));
        assertThat(trace.trace(List.of(1L), List.of(V1))).isEqualTo(IndexingTrace.Traced.NONE);
    }

    private void task(final long stream, final DocRef pipeline) {
        tasks.add(ProcessorTask.builder().id(tasks.size() + 1).metaId(stream).processorFilter(
                ProcessorFilter.builder().pipelineUuid(pipeline.getUuid()).build()).build());
    }

    // The ids an expression's IN term asks about.
    private static Set<Long> ids(final ExpressionOperator expression) {
        for (final ExpressionItem item : expression.getChildren()) {
            if (item instanceof final ExpressionTerm term && term.getCondition() == ExpressionTerm.Condition.IN) {
                final Set<Long> ids = new LinkedHashSet<>();
                for (final String id : term.getValue().split(",")) {
                    ids.add(Long.parseLong(id.strip()));
                }
                return ids;
            }
        }
        return Set.of();
    }

    // The filter (by uuid, as each test filter selects its own feed) a stream search is for.
    private static String feedOf(final ExpressionOperator expression) {
        for (final ExpressionItem item : expression.getChildren()) {
            if (item instanceof final ExpressionOperator operator) {
                for (final ExpressionItem child : operator.getChildren()) {
                    if (child instanceof final ExpressionTerm term) {
                        return term.getValue();
                    }
                }
            }
        }
        return null;
    }
}
