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
import stroom.ai.impl.tools.FeedAccess;
import stroom.ai.impl.tools.ProcessorFilterAccess;
import stroom.ai.impl.tools.StreamReader;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorFilterTrackerStatus;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestProcessingStatusTool {

    private final ProcessorFilterAccess processorFilterAccess = Mockito.mock(ProcessorFilterAccess.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final FeedAccess feedAccess = Mockito.mock(FeedAccess.class);
    private final ProcessorTaskService taskService = Mockito.mock(ProcessorTaskService.class);
    private final ProcessingStatusTool tool = new ProcessingStatusTool(processorFilterAccess, documentAccess,
            feedAccess, () -> taskService, new PipelineFeeds(processorFilterAccess, documentAccess,
            Mockito.mock(StreamReader.class)));
    private ProcessorFilter done;
    private ProcessorFilter busy;

    @BeforeEach
    void setUp() {
        Mockito.when(documentAccess.findDocRef("p")).thenReturn(Optional.of(new DocRef(PipelineDoc.TYPE, "p",
                "Acme-Events")));
        Mockito.when(feedAccess.canView(Mockito.anyString())).thenAnswer(invocation ->
                !"SECRET".equals(invocation.getArgument(0)));
        done = ProcessorFilter.builder()
                .id(1)
                .enabled(true)
                .priority(10)
                .queryData(TestPipelineFeeds.filter(1, true, TestPipelineFeeds.term("Feed", Condition.EQUALS,
                        "ACME", null)).getQueryData())
                .processorFilterTracker(ProcessorFilterTracker.builder()
                        .status(ProcessorFilterTrackerStatus.COMPLETE)
                        .metaCount(2L)
                        .eventCount(40L)
                        .build())
                .build();
        busy = ProcessorFilter.builder().id(2).enabled(false).priority(5).build();
        Mockito.when(processorFilterAccess.filtersOf(Mockito.any()))
                .thenReturn(new ProcessorFilterAccess.Filters(List.of(done, busy), null));
        Mockito.when(taskService.find(Mockito.any(ExpressionCriteria.class))).thenAnswer(invocation -> {
            final ExpressionCriteria criteria = invocation.getArgument(0);
            if (criteria.getPageRequest().getOffset() > 0) {
                return ResultPage.empty();
            }
            return criteria.getExpression().toString().contains("= 1")
                    ? new ResultPage<>(List.of(task(TaskStatus.COMPLETE, "ACME"), task(TaskStatus.COMPLETE, "ACME"),
                    // A task of a feed the user can't view isn't counted.
                    task(TaskStatus.QUEUED, "SECRET")))
                    : new ResultPage<>(List.of(task(TaskStatus.COMPLETE, "ACME"), task(TaskStatus.PROCESSING,
                    "ACME")));
        });
    }

    @Test
    void eachFiltersProgressSaysWhetherItHasFinished() {
        final Map<String, Object> result = PortedToolCalls.json(tool.execute(
                PortedToolCalls.call(ProcessingStatusTool.NAME, "{\"pipeline_uuid\": \"p\"}"),
                PortedToolCalls.context()).text());

        final List<?> filters = (List<?>) result.get("filters");
        final Map<?, ?> first = (Map<?, ?>) filters.get(0);
        assertThat(first.get("tasks")).isEqualTo(Map.of("COMPLETE", 2));
        assertThat(first.get("finished")).isEqualTo(true);
        assertThat(first.get("selects")).isEqualTo(List.of("Feed EQUALS ACME"));
        assertThat(first.get("feeds")).isEqualTo(List.of("ACME"));
        assertThat(first.get("tracker")).isEqualTo(Map.of("status", "COMPLETE", "streams_matched", 2,
                "events", 40));
        final Map<?, ?> second = (Map<?, ?>) filters.get(1);
        assertThat(second.get("tasks")).isEqualTo(Map.of("COMPLETE", 1, "PROCESSING", 1));
        assertThat(second.get("finished")).isEqualTo(false);
        assertThat(result.get("feeds")).isEqualTo(List.of("ACME"));
        assertThat(result.get("hint")).isNull();
    }

    @Test
    void tasksThatCantBeReadLeaveTheFiltersWorthHaving() {
        Mockito.doThrow(new IllegalStateException("no tasks")).when(taskService)
                .find(Mockito.any(ExpressionCriteria.class));

        final Map<String, Object> result = tool.status("p", List.of(done));

        assertThat(result.get("tasks_unreadable")).isEqualTo("no tasks");
        assertThat(((Map<?, ?>) ((List<?>) result.get("filters")).getFirst()).get("finished")).isEqualTo(false);
    }

    @Test
    void filtersWithNothingMovingPointToTheDiagnosis() {
        // Asked for by the user: no progress hands over to explain_processing.
        Mockito.when(taskService.find(Mockito.any(ExpressionCriteria.class))).thenReturn(ResultPage.empty());

        final Map<String, Object> result = tool.status("p", List.of(done));

        assertThat(String.valueOf(result.get("hint"))).startsWith("No filter has finished or has a task processing.")
                .contains("explain_processing pipeline_uuid=p");
    }

    @Test
    void aPipelineWithNoFiltersSaysSo() {
        assertThat(tool.status("p", List.of()).get("hint")).isEqualTo("No processor filters for this pipeline.");
    }

    @Test
    void filtersThatCantBeListedAreRefusedSayingWhy() {
        Mockito.doReturn(new ProcessorFilterAccess.Filters(List.of(), "You need the Manage Processors "
                                                                     + "permission to list them"))
                .when(processorFilterAccess).filtersOf(Mockito.any());

        assertThat(tool.execute(PortedToolCalls.call(ProcessingStatusTool.NAME, "{\"pipeline_uuid\": \"p\"}"),
                PortedToolCalls.context()).text())
                .startsWith("Error: Stroom refused to list this pipeline's processor filters: You need the Manage "
                            + "Processors permission");
    }

    private static ProcessorTask task(final TaskStatus status, final String feed) {
        return ProcessorTask.builder().id(1).status(status).feedName(feed).build();
    }
}
