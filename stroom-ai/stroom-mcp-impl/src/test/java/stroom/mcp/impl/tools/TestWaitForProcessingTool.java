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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.tools.FeedAccess;
import stroom.ai.impl.tools.ProcessorFilterAccess;
import stroom.ai.impl.tools.SessionLimits;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolRunContext;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.IndexResource;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.shared.ResultPage;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

/// wait_for_processing (the Stroom MCP server's `tests/test_processing.py`, its wait cases): each case was seen in
/// testing, as its comment says.
class TestWaitForProcessingTool {

    private static final long FEED_MADE = 1791580000000L;   // 2026-10-09T21:06:40Z
    private static final String BUILD = "acme-v1";
    private static final DocRef P1 = new DocRef(PipelineDoc.TYPE, "p1", "Acme-Events");

    private final BuildWorld world = new BuildWorld();
    private final ProcessorFilterAccess processorFilterAccess = Mockito.mock(ProcessorFilterAccess.class);
    private final FeedAccess feedAccess = Mockito.mock(FeedAccess.class);
    private final StreamReader streamReader = Mockito.mock(StreamReader.class);
    private final ProcessorTaskService taskService = Mockito.mock(ProcessorTaskService.class);
    private final AtomicLong clock = new AtomicLong(1_000_000L);
    private final AtomicInteger pauses = new AtomicInteger();
    private final List<String> said = new ArrayList<>();
    private List<ProcessorFilter> filters = List.of();
    private final Map<Long, List<Meta>> children = new LinkedHashMap<>();
    private final Map<Long, Meta> metas = new LinkedHashMap<>();
    // The tasks the wait reads for the streams (by Meta Id), on each read; and those processing_status counts.
    private Function<Integer, List<ProcessorTask>> streamTasks = read -> List.of();
    private List<ProcessorTask> filterTasks = List.of();
    private final AtomicInteger streamTaskReads = new AtomicInteger();
    private WaitForProcessingTool.Pause pause = duration -> {
        pauses.incrementAndGet();
        clock.addAndGet(duration.toMillis());
    };

    @BeforeEach
    void setUp() {
        elements("xsltFilter", "XSLTFilter", "streamAppender", "StreamAppender");
        Mockito.when(feedAccess.canView(anyString())).thenReturn(true);
        Mockito.when(processorFilterAccess.filtersOf(any())).thenAnswer(inv ->
                new ProcessorFilterAccess.Filters(filters, null));
        Mockito.when(streamReader.children(anyLong(), anyInt())).thenAnswer(inv -> children.getOrDefault(
                (Long) inv.getArgument(0), List.of()).stream().map(meta -> new MetaRow(meta, null, Map.of())).toList());
        Mockito.when(streamReader.metaRow(anyLong())).thenAnswer(inv -> Optional.ofNullable(metas.get(
                (Long) inv.getArgument(0))).map(meta -> new MetaRow(meta, null, Map.of())));
        Mockito.when(taskService.find(any(ExpressionCriteria.class))).thenAnswer(inv -> {
            final ExpressionCriteria criteria = inv.getArgument(0);
            if (criteria.getPageRequest().getOffset() > 0) {
                return ResultPage.empty();
            }
            return criteria.getExpression().toString().contains("Meta Id")
                    ? new ResultPage<>(streamTasks.apply(streamTaskReads.incrementAndGet()))
                    : new ResultPage<>(filterTasks);
        });
    }

    @Test
    void waitCountsOnlyTheGivenFiltersOutputs() {
        inBuildSteppedClean();
        filters = List.of(filter(9, true));
        filterTasks = List.of(task(TaskStatus.COMPLETE, null));
        children.put(5L, List.of(output(20, "Events", 3), output(30, "Events", 9)));

        final Map<String, Object> everything = call("{\"pipeline_uuid\": \"p1\", \"stream_ids\": [5], "
                                                    + "\"timeout_seconds\": 5}");
        final Map<String, Object> latest = call("{\"pipeline_uuid\": \"p1\", \"stream_ids\": [5], "
                                                + "\"timeout_seconds\": 5, \"filter_id\": 9}");

        assertThat(everything.get("gate")).isEqualTo("fail");
        assertThat(String.valueOf(((List<?>) everything.get("problems")).getFirst())).contains("filter_id");
        assertThat(latest.get("gate")).isEqualTo("pass");
        assertThat(latest.get("streams")).isEqualTo(List.of(Map.of("input", 5, "events", List.of(30),
                "errors", List.of())));
    }

    @Test
    void withNoEventsToShowTheWaitIsForTheStreamsOwnTask() {
        // Seen in e2e: a promoted indexing pipeline's earlier tasks were all complete before its filter made one for
        // the new stream, and the gate passed with nothing indexed.
        inBuildSteppedClean();
        filters = List.of(filter(9, true));
        filterTasks = List.of(task(TaskStatus.COMPLETE, 4L));
        streamTasks = read -> read > 1
                ? List.of(task(TaskStatus.COMPLETE, 5L))
                : List.of();
        selectable(5);

        final Map<String, Object> result = call("{\"pipeline_uuid\": \"p1\", \"stream_ids\": [5], "
                                                + "\"timeout_seconds\": 60, \"expect_events\": false, "
                                                + "\"filter_id\": 9}");

        assertThat(streamTaskReads.get()).isEqualTo(2);
        assertThat(result.get("finished")).isEqualTo(true);
        assertThat(result.get("gate")).isEqualTo("pass");

        streamTasks = read -> List.of();
        filterTasks = List.of(task(TaskStatus.PROCESSING, 5L));
        final Map<String, Object> waited = call("{\"pipeline_uuid\": \"p1\", \"stream_ids\": [5], "
                                                + "\"timeout_seconds\": 0, \"expect_events\": false}");
        assertThat(waited.get("finished")).isEqualTo(false);
        assertThat(waited.get("gate")).isEqualTo("fail");
    }

    @ParameterizedTest
    @ValueSource(strings = {"StandardKafkaProducer", "ElasticIndexingFilter"})
    void aPipelineThatWritesNoStreamIsDoneWhenItsTaskEnds(final String type) {
        // Seen in Stroom: an Events pipeline that writes only on error, and an indexing pipeline, each waited out
        // two timeouts at "0 of 3 streams have output; tasks: 6 complete".
        inBuildSteppedClean();
        elements("xsltFilter", "XSLTFilter", "out", type);
        endedTask();

        final Map<String, Object> result = call("{\"pipeline_uuid\": \"p1\", \"stream_ids\": [5], "
                                                + "\"timeout_seconds\": 60, \"expect_events\": true}");

        assertThat(pauses.get()).isZero();
        assertThat(result.get("gate")).isEqualTo("pass");
        assertThat(result.get("finished")).isEqualTo(true);
        assertThat(String.valueOf(result.get("output"))).contains("none expected");
        assertThat(result.get("streams")).isEqualTo(List.of(Map.of("input", 5, "events", List.of(),
                "errors", List.of())));
    }

    @Test
    void indexingThatPassesRestoresTheTemplatesBatchSize() {
        // Indexing completed without errors: the small development batch was only to see Elasticsearch's responses.
        inBuildSteppedClean();
        elements("xsltFilter", "XSLTFilter", "elasticIndexingFilter", "ElasticIndexingFilter");
        endedTask();
        world.docs.put(P1.getUuid(), PipelineDoc.builder().uuid(P1.getUuid()).name(P1.getName()).version("v0")
                .pipelineData(new PipelineDataBuilder()
                        .addProperty(new PipelineProperty("elasticIndexingFilter", "batchSize",
                                new PipelinePropertyValue(IndexingRuns.DEV_BATCH_SIZE)))
                        .build())
                .build());

        final Map<String, Object> result = call("{\"pipeline_uuid\": \"p1\", \"stream_ids\": [5], "
                                                + "\"timeout_seconds\": 60}");

        assertThat(result.get("gate")).isEqualTo("pass");
        assertThat(result.get("batch_size")).isEqualTo("restored to the template's default, now that indexing "
                                                       + "completed without errors");
        assertThat(PipelineDocs.addedProperties((PipelineDoc) world.docs.get(P1.getUuid()))).isEmpty();
    }

    @Test
    void anEndedTaskWithNoEventsFailsAtOnceRatherThanWaiting() {
        inBuildSteppedClean();
        endedTask();
        children.put(5L, List.of(output(7, "Error", 9)));

        final Map<String, Object> result = call("{\"pipeline_uuid\": \"p1\", \"stream_ids\": [5], "
                                                + "\"timeout_seconds\": 60}");

        assertThat(pauses.get()).isZero();
        assertThat(result.get("finished")).isEqualTo(true);
        assertThat(result.get("gate")).isEqualTo("fail");
        assertThat(result).doesNotContainKey("output");
        assertThat(result.get("problems")).isEqualTo(List.of("Stream 5 produced no Events stream, and its task has "
                                                             + "ended, so none is coming: summarise_streams "
                                                             + "(kind=errors) 5 (a failed task, a fatal error, or "
                                                             + "every record filtered out)"));
    }

    @Test
    void withEveryTaskEndedAndNoneForTheStreamWaitingAgainIsNotSuggested() {
        inBuildSteppedClean();
        endedTask();
        selectable(6);

        final Map<String, Object> result = call("{\"pipeline_uuid\": \"p1\", \"stream_ids\": [6], "
                                                + "\"timeout_seconds\": 5}");

        assertThat(result.get("finished")).isEqualTo(false);
        assertThat(String.valueOf(result.get("hint"))).startsWith("Every task of the pipeline's filters has ended, "
                                                                  + "but none for stream(s) [6]: waiting again "
                                                                  + "won't change that.");
    }

    @Test
    void waitFailsWhileTheCurrentCodeHasNotSteppedClean() {
        // Seen: a filter made after a clean step went on processing once the XSLT was replaced by one that never
        // stepped clean (Unknown nobody agreed to), and the agent documented and indexed its output.
        world.inBuild(BUILD, PipelineDoc.TYPE, P1.getName(), P1.getUuid());
        filters = List.of(filter(9, true));
        filterTasks = List.of(task(TaskStatus.COMPLETE, null));
        children.put(5L, List.of(output(30, "Events", 9)));

        final Map<String, Object> result = call("{\"pipeline_uuid\": \"p1\", \"stream_ids\": [5], "
                                                + "\"timeout_seconds\": 5, \"filter_id\": 9}");

        assertThat(result.get("gate")).isEqualTo("fail");
        assertThat(((Map<?, ?>) ((List<?>) result.get("streams")).getFirst()).get("events")).isEqualTo(List.of(30));
        assertThat(result.get("problems")).isEqualTo(List.of("The pipeline's current code has not stepped clean: "
                                                             + "its output may not be what was checked. step_sample "
                                                             + "over the sample streams until the verdict is clean, "
                                                             + "then reprocess_streams"));
    }

    @Test
    void aPromotedPipelineKeepsNoRecordOfItsStepsAndIsNotGated() {
        // Seen in e2e: production v1 failed the gate on new data.
        world.add(PipelineDoc.TYPE, P1.getName(), world.folder("System/Feeds").uuid, P1.getUuid(),
                WriteGuard.GENERATED);
        filters = List.of(filter(9, true));
        filterTasks = List.of(task(TaskStatus.COMPLETE, null));
        children.put(5L, List.of(output(30, "Events", 9)));

        final Map<String, Object> result = call("{\"pipeline_uuid\": \"p1\", \"stream_ids\": [5], "
                                                + "\"timeout_seconds\": 5, \"filter_id\": 9}");

        assertThat(result.get("gate")).isEqualTo("pass");
        assertThat(result.get("problems")).isEqualTo(List.of());
    }

    @Test
    void withNoProcessorFilterThereIsNothingToWaitFor() {
        // Seen in VS Code: create_processor_filter was refused, and the agent waited out 200 seconds of "tasks still
        // running" for a pipeline with nothing set to process.
        inBuildSteppedClean();

        final Map<String, Object> result = call("{\"pipeline_uuid\": \"p1\", \"stream_ids\": [5], "
                                                + "\"timeout_seconds\": 60, \"expect_events\": false}");

        assertThat(pauses.get()).isZero();
        Mockito.verify(streamReader, Mockito.never()).children(anyLong(), anyInt());
        assertThat(result.get("gate")).isEqualTo("fail");
        assertThat(String.valueOf(((List<?>) result.get("problems")).getFirst())).contains("no processor filter");
        assertThat(String.valueOf(result.get("hint"))).startsWith("create_processor_filter first")
                .contains("don't wait");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void waitingEndsAtOnceWhenNoFilterWillProcessTheStream(final boolean tooEarly) {
        // Seen (Gemma, VS Code): three timeouts of "tasks still running" for a filter whose tracker matched nothing.
        waitingOn(tooEarly
                ? feedFilter(true, "acme", FEED_MADE + 3_600_000)
                : feedFilter(true, "OTHER", null));

        final Map<String, Object> result = call("{\"pipeline_uuid\": \"p1\", \"stream_ids\": [6], "
                                                + "\"timeout_seconds\": 60}");

        final String reason = tooEarly
                ? "filter 775 selects only streams created after 2026-10-09T22:06:40.000Z"
                : "filter 775 selects Feed EQUALS OTHER, Type EQUALS Raw Events";
        assertThat(pauses.get()).isZero();
        assertThat(result.get("gate")).isEqualTo("fail");
        assertThat(result.get("problems")).isEqualTo(List.of("No processor filter of this pipeline will process "
                                                             + "stream 6 (ACME, Raw Events, created "
                                                             + "2026-10-09T21:07:40.000Z): " + reason));
        assertThat(String.valueOf(result.get("hint"))).startsWith("Nothing will process these streams");
    }

    @Test
    void waitingOnADisabledFilterAsksForTheUserToEnableIt() {
        // Filters are created disabled: nothing runs until the user enables one, so there is nothing to wait out.
        waitingOn(idFilter(false, true));

        final ToolResult toolResult = tool().execute(PortedToolCalls.call(WaitForProcessingTool.NAME,
                "{\"pipeline_uuid\": \"p1\", \"stream_ids\": [6], \"timeout_seconds\": 60}"), context());
        final Map<String, Object> result = PortedToolCalls.json(toolResult.text());

        assertThat(pauses.get()).isZero();
        assertThat(result.get("gate")).isEqualTo("waiting");
        assertThat(result.get("waiting_for")).isEqualTo("the user to enable filter(s) [775]");
        assertThat(String.valueOf(result.get("pipeline_link"))).contains("docUuid=p1");
        assertThat(String.valueOf(result.get("hint"))).contains("Processors tab");
        // As a button too (asked for by the user: Stroom shows user_actions on the tool call's card).
        final List<?> actions = (List<?>) result.get("user_actions");
        assertThat(actions).hasSize(1);
        final Map<?, ?> action = (Map<?, ?>) actions.getFirst();
        assertThat(action.get("action")).isEqualTo("enable_processor_filter");
        assertThat(action.get("filter_id")).isEqualTo(775);
        assertThat(action.get("doc")).isEqualTo(Map.of("type", "Pipeline", "uuid", "p1", "name", "Acme-Events"));
        assertThat(String.valueOf(action.get("link"))).contains("docUuid=p1");
        assertThat(toolResult.userActions()).hasSize(1);
        assertThat(toolResult.userActions().getFirst().action()).isEqualTo("enable_processor_filter");
        assertThat(toolResult.userActions().getFirst().arguments().get("filter_id")).isEqualTo(775);
    }

    @Test
    void waitingOnADisabledProcessorAsksForTheUserToEnableIt() {
        // A disabled processor runs none of its filters, enabled or not (seen on the local stack: the filter
        // enabled, the processor Stroom made with it still disabled, and nothing processed).
        waitingOn(idFilter(true, false));

        final Map<String, Object> result = call("{\"pipeline_uuid\": \"p1\", \"stream_ids\": [6], "
                                                + "\"timeout_seconds\": 60}");

        assertThat(result.get("gate")).isEqualTo("waiting");
        assertThat(String.valueOf(result.get("waiting_for")))
                .startsWith("the user to enable the processor of filter(s) [775]");
        // No button enables a processor: the pipeline is opened for the user to.
        assertThat(((List<?>) result.get("user_actions")).stream()
                .map(a -> String.valueOf(((Map<?, ?>) a).get("action")))
                .toList()).containsExactly("open_document");
    }

    @Test
    void waitingGoesOnWhileAFilterSelectsTheStream() {
        // Feed names match whatever their case, as in Stroom.
        inBuildSteppedClean();
        waitingOn(feedFilter(true, "acme", FEED_MADE));

        final Map<String, Object> result = call("{\"pipeline_uuid\": \"p1\", \"stream_ids\": [6], "
                                                + "\"timeout_seconds\": 5}");

        assertThat(pauses.get()).isPositive();
        assertThat(result.get("hint")).isEqualTo("Tasks were still running at the timeout; call again.");
    }

    @Test
    void waitingStopsBeforeTheSessionsTimeLimitRunsOut() {
        // As the Stroom MCP server stopped before the caller's token ran out (seen with Stroom's internal identity
        // provider: a wait of three minutes failed part way): it stops in time and says to call again.
        inBuildSteppedClean();
        waitingOn(feedFilter(true, "acme", FEED_MADE));
        final AiWorkspaceConfig limits = Mockito.mock(AiWorkspaceConfig.class);
        Mockito.when(limits.getQueryTimeLimit()).thenReturn(StroomDuration.ofSeconds(15));
        final long started = clock.get();

        final Map<String, Object> result = PortedToolCalls.json(tool().execute(PortedToolCalls.call(
                        WaitForProcessingTool.NAME, "{\"pipeline_uuid\": \"p1\", \"stream_ids\": [6], "
                                                    + "\"timeout_seconds\": 180}"),
                contextBuilder().limits(SessionLimits.of(limits)).build()).text());

        assertThat(clock.get() - started).isLessThan(Duration.ofSeconds(20).toMillis());
        assertThat(String.valueOf(result.get("hint"))).startsWith("Stopped waiting, with tasks still running, "
                                                                  + "before this session's time limit for a call "
                                                                  + "(15 s) ran out. Call again");
    }

    @Test
    void waitingSaysWhereProcessingIsNowAndThen() {
        // Seen: three minutes with nothing shown until the timeout.
        inBuildSteppedClean();
        waitingOn(feedFilter(true, "acme", FEED_MADE));

        call("{\"pipeline_uuid\": \"p1\", \"stream_ids\": [6], \"timeout_seconds\": 5}");

        assertThat(said).hasSize(1);
        assertThat(said.getFirst()).startsWith("Waiting for processing: 0 of 1 streams processed, 0 with output; "
                                               + "tasks: ");
    }

    @Test
    void aWaitTheClientGaveUpOnStopsAtOnce() {
        // StroomMcpServer interrupts the thread of a call its client cancels.
        inBuildSteppedClean();
        waitingOn(feedFilter(true, "acme", FEED_MADE));
        pause = duration -> {
            pauses.incrementAndGet();
            throw new InterruptedException();
        };

        final ToolResult result = tool().execute(PortedToolCalls.call(WaitForProcessingTool.NAME,
                "{\"pipeline_uuid\": \"p1\", \"stream_ids\": [6], \"timeout_seconds\": 900}"), context());

        // Cleared, as the thread is the test's.
        assertThat(Thread.interrupted()).isTrue();
        assertThat(pauses.get()).isEqualTo(1);
        assertThat(result.isError()).isTrue();
        assertThat(result.text()).startsWith("Stopped waiting: the call was cancelled.");
    }

    @Test
    void aStoppedRunStopsTheWait() {
        inBuildSteppedClean();
        waitingOn(feedFilter(true, "acme", FEED_MADE));
        final ToolRunContext run = Mockito.mock(ToolRunContext.class);
        Mockito.when(run.getCancelled()).thenReturn(() -> true);

        final ToolResult result = tool().execute(PortedToolCalls.call(WaitForProcessingTool.NAME,
                        "{\"pipeline_uuid\": \"p1\", \"stream_ids\": [6], \"timeout_seconds\": 900}"),
                contextBuilder().runContext(run).build());

        assertThat(pauses.get()).isZero();
        assertThat(result.text()).startsWith("Stopped waiting: the call was cancelled.");
    }

    @Test
    void callsWithoutAPipelineOrStreamsAreRefused() {
        assertThat(tool().execute(PortedToolCalls.call(WaitForProcessingTool.NAME, "{\"stream_ids\": [5]}"),
                context()).text()).startsWith("Give pipeline_uuid");
        assertThat(tool().execute(PortedToolCalls.call(WaitForProcessingTool.NAME, "{\"pipeline_uuid\": \"p1\"}"),
                context()).text()).startsWith("Give stream_ids");
        assertThat(tool().execute(PortedToolCalls.call(WaitForProcessingTool.NAME,
                "{\"pipeline_uuid\": \"p1\", \"stream_ids\": [\"x\"]}"), context()).text())
                .isEqualTo("stream_ids are stream ids (numbers): 'x' is not one");
    }

    @Test
    void filtersStroomWontListAreRefusedSayingWhy() {
        Mockito.when(processorFilterAccess.filtersOf(any())).thenReturn(new ProcessorFilterAccess.Filters(List.of(),
                "You need the Manage Processors permission to list them"));

        final ToolResult result = tool().execute(PortedToolCalls.call(WaitForProcessingTool.NAME,
                "{\"pipeline_uuid\": \"p1\", \"stream_ids\": [5]}"), context());

        assertThat(result.isError()).isTrue();
        assertThat(result.text()).isEqualTo("Stroom refused to list this pipeline's processor filters: You need the "
                                            + "Manage Processors permission to list them.");
    }

    private Map<String, Object> call(final String arguments) {
        return PortedToolCalls.json(tool().execute(PortedToolCalls.call(WaitForProcessingTool.NAME, arguments),
                context()).text());
    }

    private WaitForProcessingTool tool() {
        final PipelineFeeds pipelineFeeds = new PipelineFeeds(processorFilterAccess, world.documentAccess,
                streamReader);
        final ProcessingStatusTool status = new ProcessingStatusTool(processorFilterAccess, world.documentAccess,
                feedAccess, () -> taskService, pipelineFeeds);
        final BuildPlan plan = new BuildPlan(new FakeBuildFacts(), world.guard(),
                new Consent(() -> Mockito.mock(AiAuditLog.class)),
                new BuildMemory());
        final StreamProcessing streamProcessing = new StreamProcessing(streamReader, feedAccess, () -> taskService);
        final IndexingRuns indexingRuns = new IndexingRuns(world.pipelineShapes, new PipelineDocs(world.documentAccess),
                world.guard(), streamProcessing, () -> Mockito.mock(IndexResource.class), streamId -> "");
        return new WaitForProcessingTool(world.documentAccess, processorFilterAccess, status, streamProcessing,
                new PipelineOutputs(world.pipelineShapes), world.guard(), world.records(), plan, indexingRuns,
                clock::get, duration -> pause.pause(duration));
    }

    private ToolCallContext context() {
        return contextBuilder().build();
    }

    private ToolCallContext.Builder contextBuilder() {
        return ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "mcp-session-1",
                        new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client")))
                .userQuestions(UserQuestions.NONE)
                .progress(said::add);
    }

    // p1, in a build, with a clean step of its current code recorded.
    private void inBuildSteppedClean() {
        world.inBuild(BUILD, PipelineDoc.TYPE, P1.getName(), P1.getUuid());
        assertThat(world.records().recordPassed(P1, BuildRecords.STEPPED, null, null)).contains("recorded");
    }

    // p1's elements, as ids and types in pairs.
    private void elements(final String... ids) {
        PipelineLayers.given(world.pipelineService, P1.getUuid(), PipelineLayers.of(PipelineLayers.layer(
                P1.getUuid()).chain(ids)));
    }

    // Filter 9's task for stream 5 complete.
    private void endedTask() {
        filters = List.of(filter(9, true));
        filterTasks = List.of(task(TaskStatus.COMPLETE, 5L));
        streamTasks = read -> List.of(task(TaskStatus.COMPLETE, 5L));
        selectable(5);
    }

    // Stream 6 of feed ACME, made a minute after the feed, with no output yet; p1 has the one filter given.
    private void waitingOn(final ProcessorFilter filter) {
        if (!world.nodes.containsKey(P1.getUuid())) {
            world.add(PipelineDoc.TYPE, P1.getName(), world.folder("System/Feeds").uuid, P1.getUuid());
        }
        filters = List.of(filter);
        metas.put(6L, Meta.builder().id(6).feedName("ACME").typeName("Raw Events").createMs(FEED_MADE + 60_000)
                .build());
    }

    // A stream any filter selects.
    private void selectable(final long id) {
        metas.put(id, Meta.builder().id(id).feedName("ACME").typeName("Raw Events").createMs(FEED_MADE).build());
    }

    private static ProcessorFilter filter(final int id, final boolean enabled) {
        return ProcessorFilter.builder().id(id).pipelineUuid("p1").enabled(enabled).build();
    }

    private static ProcessorFilter feedFilter(final boolean enabled, final String feed, final Long min) {
        return TestPipelineFeeds.filter(775, enabled,
                        TestPipelineFeeds.term("Feed", Condition.EQUALS, feed, null),
                        TestPipelineFeeds.term("Type", Condition.EQUALS, "Raw Events", null))
                .copy()
                .pipelineUuid("p1")
                .processor(Processor.builder().id(70).enabled(true).build())
                .minMetaCreateTimeMs(min)
                .build();
    }

    private static ProcessorFilter idFilter(final boolean enabled, final boolean processorEnabled) {
        return TestPipelineFeeds.filter(775, enabled, TestPipelineFeeds.term("Id", Condition.EQUALS, "6", null))
                .copy()
                .pipelineUuid("p1")
                .processor(Processor.builder().id(70).enabled(processorEnabled).build())
                .build();
    }

    private static Meta output(final long id, final String type, final int filterId) {
        return Meta.builder().id(id).pipelineUuid("p1").typeName(type).processorFilterId(filterId)
                .status(Status.UNLOCKED).build();
    }

    private static ProcessorTask task(final TaskStatus status, final Long metaId) {
        return ProcessorTask.builder().id(1).status(status).metaId(metaId).feedName("ACME").build();
    }
}
