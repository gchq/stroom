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

package stroom.ai.impl.tools;

import stroom.ai.impl.workspace.WorkspaceException;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.DataException;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.feed.shared.FeedDoc;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.node.api.NodeService;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.shared.AppPermission;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import event.logging.OtherObject;
import event.logging.ViewEventAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestProcessorTasksTool {

    private static final DocRef PIPELINE = new DocRef(PipelineDoc.TYPE, "pipe-uuid", "Firewall pipeline");
    private static final DocRef OTHER_PIPELINE = new DocRef(PipelineDoc.TYPE, "other-pipe-uuid", "DNS pipeline");
    private static final DocRef SECRET_PIPELINE = new DocRef(PipelineDoc.TYPE, "secret-pipe-uuid", "Secret pipe");
    private static final DocRef FEED = new DocRef(FeedDoc.TYPE, "feed-uuid", "FIREWALL-EVENTS");
    private static final DocRef OTHER_FEED = new DocRef(FeedDoc.TYPE, "other-feed-uuid", "DNS-EVENTS");
    private static final DocRef SECRET_FEED = new DocRef(FeedDoc.TYPE, "secret-feed-uuid", "SECRET-EVENTS");
    private static final long NOW = System.currentTimeMillis();
    private static final long HOUR = Duration.ofHours(1).toMillis();

    @TempDir
    Path tempDir;

    private ToolTestFixture fixture;
    private final ProcessorTaskService taskService = Mockito.mock(ProcessorTaskService.class);
    private final ProcessorFilterService processorFilterService = Mockito.mock(ProcessorFilterService.class);
    private final MetaService metaService = Mockito.mock(MetaService.class);
    private final Store store = Mockito.mock(Store.class);
    private final NodeService nodeService = Mockito.mock(NodeService.class);
    /// The tasks, as the database holds them.
    private final List<ProcessorTask> tasks = new ArrayList<>();
    /// The streams, as the database holds them.
    private final List<Meta> metas = new ArrayList<>();
    /// The attributes (meta values) of the streams, by id.
    private final Map<Long, Map<String, String>> attributes = new HashMap<>();
    /// What each stream holds, by id; a stream without any can't be read.
    private final Map<Long, String> contents = new HashMap<>();
    /// The meta searches made, as [FindMetaCriteria].
    private final List<FindMetaCriteria> metaSearches = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        fixture = new ToolTestFixture(tempDir);
        fixture.add(PIPELINE, null, true);
        fixture.add(OTHER_PIPELINE, null, true);
        fixture.add(SECRET_PIPELINE, null, false);
        fixture.add(FEED, null, true);
        fixture.add(OTHER_FEED, null, true);
        fixture.add(SECRET_FEED, null, false);
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION))
                .thenReturn(true);
        Mockito.when(nodeService.findNodeNames(Mockito.any())).thenReturn(List.of("node1", "node2", "node3"));
        Mockito.when(taskService.find(Mockito.any())).thenAnswer(invocation -> findTasks(invocation.getArgument(0)));
        Mockito.when(taskService.findSummary(Mockito.any()))
                .thenAnswer(invocation -> summarise(invocation.getArgument(0)));
        Mockito.when(metaService.find(Mockito.any())).thenAnswer(invocation -> {
            final FindMetaCriteria criteria = invocation.getArgument(0);
            metaSearches.add(criteria);
            return new ResultPage<>(findMetas(criteria));
        });
        Mockito.when(metaService.findRows(Mockito.any())).thenAnswer(invocation -> {
            final FindMetaCriteria criteria = invocation.getArgument(0);
            metaSearches.add(criteria);
            return new ResultPage<>(findMetas(criteria).stream()
                    .map(meta -> new MetaRow(meta, null, attributes.getOrDefault(meta.getId(), Map.of())))
                    .toList());
        });
        Mockito.when(store.openSource(Mockito.anyLong())).thenAnswer(invocation -> source(invocation.getArgument(0)));
    }

    @Test
    void refusesWithoutTheManageProcessorsPermission() {
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION))
                .thenReturn(false);

        assertThat(run("{\"mode\":\"list\"}")).isEqualTo(ProcessorTasksTool.NEEDS_MANAGE_PROCESSORS);
        assertThat(run("{}")).isEqualTo(ProcessorTasksTool.NEEDS_MANAGE_PROCESSORS);
        Mockito.verifyNoInteractions(taskService, metaService, store, nodeService);
    }

    @Test
    void listsOnlyTasksWhosePipelineAndFeedTheUserCanView() {
        task(1, TaskStatus.COMPLETE, "node1", 100, PIPELINE, FEED, NOW - HOUR);
        task(2, TaskStatus.COMPLETE, "node1", 101, SECRET_PIPELINE, FEED, NOW - HOUR);
        task(3, TaskStatus.COMPLETE, "node1", 102, PIPELINE, SECRET_FEED, NOW - HOUR);
        task(4, TaskStatus.FAILED, "node2", 103, OTHER_PIPELINE, OTHER_FEED, NOW - HOUR);

        final String result = run("{\"mode\":\"list\"}");

        assertThat(result)
                .contains("newest first: 2\n")
                .contains("- Task 4: Failed on node node2, [stream 103](?action=open-stream&id=103) of feed "
                          + "DNS-EVENTS, Pipeline [DNS pipeline](?action=open-doc&docType=Pipeline&docUuid="
                          + "other-pipe-uuid), filter 4")
                .contains("- Task 1: Complete on node node1, [stream 100]")
                .doesNotContain("Task 2")
                .doesNotContain("Task 3")
                .doesNotContain("Secret")
                .doesNotContain("SECRET")
                .doesNotContain("hidden")
                .doesNotContain("can't see");
        // Task 4 is listed first, as the newest.
        assertThat(result.indexOf("Task 4")).isLessThan(result.indexOf("Task 1"));
    }

    @Test
    void listFiltersByNodeStatusFeedAndStream() {
        task(1, TaskStatus.COMPLETE, "node1", 100, PIPELINE, FEED, NOW - HOUR);
        task(2, TaskStatus.FAILED, "node1", 101, PIPELINE, FEED, NOW - HOUR);
        task(3, TaskStatus.FAILED, "node2", 102, PIPELINE, FEED, NOW - HOUR);
        task(4, TaskStatus.FAILED, "node1", 103, OTHER_PIPELINE, OTHER_FEED, NOW - HOUR);

        assertThat(run("{\"mode\":\"list\",\"nodes\":[\"node1\"],\"statuses\":[\"failed\"],"
                       + "\"feeds\":[\"FIREWALL-EVENTS\"]}"))
                .contains("newest first: 1\n")
                .contains("Task 2:");
        assertThat(run("{\"mode\":\"list\",\"streams\":[\"102\"]}"))
                .contains("newest first: 1\n")
                .contains("Task 3:");
        assertThat(run("{\"mode\":\"list\",\"since\":\"-30m\"}")).contains("none you can see.");
    }

    @Test
    void listGivesEachTasksErrorStreamsFromOneSearch() {
        task(1, TaskStatus.COMPLETE, "node1", 100, PIPELINE, FEED, NOW - HOUR);
        task(2, TaskStatus.FAILED, "node1", 101, PIPELINE, FEED, NOW - HOUR);
        task(3, TaskStatus.COMPLETE, "node1", 102, PIPELINE, FEED, NOW - HOUR);
        errorStream(900, 100, PIPELINE, FEED, 1L, NOW - HOUR, counts(1, 3, 0), "");
        // Its meta values have aged off.
        errorStream(902, 102, PIPELINE, FEED, 3L, NOW - HOUR, null, "");

        final String result = run("{\"mode\":\"list\"}");

        assertThat(result)
                .contains("Task 1: Complete")
                .contains("; Error stream(s) [stream 900](?action=open-stream&id=900) (1 fatal, 3 error(s), 0 "
                          + "warning(s))")
                .contains("; no Error stream (a Failed task's run threw, so it usually writes none; that doesn't "
                          + "mean nothing went wrong)")
                .contains("; Error stream(s) [stream 902](?action=open-stream&id=902) (counts unavailable)");
        assertThat(metaSearches).hasSize(1);
        assertThat(terms(metaSearches.getFirst().getExpression()))
                .anyMatch(term -> term.getField().equals(MetaFields.PARENT_ID.getFldName())
                                  && term.getCondition() == Condition.IN
                                  && Arrays.asList(term.getValue().split(",")).containsAll(List.of("100", "101",
                        "102")));
    }

    @Test
    void listWritesTheTasksToOut() {
        task(1, TaskStatus.COMPLETE, "node1", 100, PIPELINE, FEED, NOW - HOUR);
        errorStream(900, 100, PIPELINE, FEED, 1L, NOW - HOUR, counts(0, 2, 1), "");

        final String result = run("{\"mode\":\"list\",\"out\":\"/outputs/tasks.csv\"}");

        assertThat(result).contains("Wrote 1 row(s) to /outputs/tasks.csv");
        assertThat(fixture.written.get("/outputs/tasks.csv"))
                .startsWith(String.join(",", ProcessorTasksTool.TASK_COLUMNS))
                .contains("1,Complete,node1,100,FIREWALL-EVENTS,Firewall pipeline,pipe-uuid,1,")
                .contains(",900,0,2,1");
    }

    @Test
    void refusesPipelinesFeedsAndArgumentsItCantUse() {
        assertThat(run("{\"mode\":\"list\",\"pipelines\":[\"secret-pipe-uuid\"]}"))
                .isEqualTo("Error: No pipeline or analytic rule was found with the UUID secret-pipe-uuid, or you "
                           + "don't have permission to view it.");
        assertThat(run("{\"mode\":\"list\",\"feeds\":[\"SECRET-EVENTS\"]}"))
                .isEqualTo("Error: No feed named 'SECRET-EVENTS' was found, or you don't have permission to view "
                           + "it.");
        assertThat(run("{\"mode\":\"list\",\"statuses\":[\"Sleeping\"]}")).startsWith("Error: 'Sleeping' isn't a "
                                                                                      + "task status.");
        assertThat(run("{\"mode\":\"sideways\"}")).startsWith("Error: The mode must be one of");
        assertThat(run("{\"mode\":\"summary\",\"group_by\":[\"colour\"]}")).startsWith("Error: Can't group by");
        Mockito.verifyNoInteractions(taskService);
    }

    @Test
    void summaryCountsByNodeAndStatusInTheDatabase() {
        task(1, TaskStatus.COMPLETE, "node1", 100, PIPELINE, FEED, NOW - HOUR);
        task(2, TaskStatus.FAILED, "node1", 101, PIPELINE, FEED, NOW - HOUR);
        task(3, TaskStatus.FAILED, "node1", 102, OTHER_PIPELINE, OTHER_FEED, NOW - HOUR);
        task(4, TaskStatus.PROCESSING, "node2", 103, PIPELINE, FEED, NOW - HOUR);
        task(5, TaskStatus.QUEUED, null, 104, PIPELINE, FEED, NOW - HOUR);
        task(6, TaskStatus.QUEUED, "node2", 105, SECRET_PIPELINE, FEED, NOW - HOUR);

        final String result = run("{\"mode\":\"summary\",\"group_by\":[\"node\",\"status\"]}");

        assertThat(result)
                .contains("counted by node, status:\n")
                .contains("- node node1: 1 Complete, 2 Failed (3 in all)\n")
                .contains("- node node2: 1 Processing (1 in all)")
                .contains("- no node yet: 1 Queued (1 in all)")
                .doesNotContain("Secret");
        // One summary for all, and one per node, rather than reading every task.
        Mockito.verify(taskService, Mockito.times(4)).findSummary(Mockito.any());
        Mockito.verify(taskService, Mockito.never()).find(Mockito.any());
    }

    @Test
    void summaryCountsByPipelineAndByFilter() {
        task(1, TaskStatus.COMPLETE, "node1", 100, PIPELINE, FEED, NOW - HOUR);
        task(2, TaskStatus.COMPLETE, "node1", 101, PIPELINE, FEED, NOW - HOUR);
        task(3, TaskStatus.QUEUED, null, 102, OTHER_PIPELINE, OTHER_FEED, NOW - HOUR);

        assertThat(run("{\"mode\":\"summary\"}"))
                .contains("counted by pipeline, status:\n")
                .contains("- Pipeline [Firewall pipeline](?action=open-doc&docType=Pipeline&docUuid=pipe-uuid): "
                          + "2 Complete (2 in all)\n")
                .contains("- Pipeline [DNS pipeline](?action=open-doc&docType=Pipeline&docUuid=other-pipe-uuid): "
                          + "1 Queued (1 in all)");
        assertThat(run("{\"mode\":\"summary\",\"group_by\":[\"filter\"]}"))
                .contains("- filter 1: 1 task(s)\n")
                .contains("- filter 3: 1 task(s)")
                .doesNotContain("Pipeline");
    }

    @Test
    void failuresGroupsTheFailedTasksAndSaysWhereTheReasonIs() {
        task(1, TaskStatus.FAILED, "node1", 100, PIPELINE, FEED, NOW - 3 * HOUR);
        task(2, TaskStatus.FAILED, "node1", 101, PIPELINE, FEED, NOW - 2 * HOUR);
        task(3, TaskStatus.FAILED, "node1", 102, PIPELINE, FEED, NOW - HOUR);
        task(4, TaskStatus.FAILED, "node2", 103, OTHER_PIPELINE, OTHER_FEED, NOW - HOUR);
        task(5, TaskStatus.COMPLETE, "node2", 104, OTHER_PIPELINE, OTHER_FEED, NOW - HOUR);
        task(6, TaskStatus.FAILED, "node3", 105, SECRET_PIPELINE, FEED, NOW - HOUR);
        // Outside the window.
        task(7, TaskStatus.FAILED, "node2", 106, PIPELINE, FEED, NOW - 30 * HOUR);

        final String result = run("{}");

        assertThat(result)
                .startsWith("Processing across the cluster (status changed from ")
                .contains("5 task(s): 1 Complete, 4 Failed.")
                .contains("- node node1: 3 task(s), 3 Failed (100.0%), 75.0% of all the failed tasks")
                .contains("Most of the failed tasks (75.0%) were on node node1")
                .contains("## Failed tasks\n4 failed task(s). " + ProcessorTasksTool.FAILED_REASON_NOTE)
                .contains("- Pipeline [Firewall pipeline](?action=open-doc&docType=Pipeline&docUuid=pipe-uuid), "
                          + "feed FIREWALL-EVENTS, node node1: 3 failed, first ")
                .contains("E.g. task 3 ([stream 102](?action=open-stream&id=102)")
                .contains("no Error stream (a Failed task's run threw")
                .doesNotContain("task 6")
                .doesNotContain("task 7")
                .doesNotContain("Secret");
    }

    @Test
    void failuresListsTasksThatLookStuckByNode() {
        task(1, TaskStatus.PROCESSING, "node3", 100, PIPELINE, FEED, NOW - 3 * HOUR);
        task(2, TaskStatus.PROCESSING, "node1", 101, PIPELINE, FEED, NOW - Duration.ofMinutes(5).toMillis());

        final String result = run("{}");

        assertThat(result)
                .contains("## Possibly stuck tasks\nTasks still Assigned or Processing whose status hasn't changed "
                          + "for over 1h")
                .contains("- node node3: 1 task(s), oldest status time ")
                .contains("task 1 ([stream 100](?action=open-stream&id=100)")
                .doesNotContain("node node1: 1 task(s), oldest");
        assertThat(run("{\"stuck_after\":\"2m\"}")).contains("node node1: 1 task(s), oldest");
    }

    @Test
    void failuresLooksForErrorStreamsPerPipelineWithTasks() {
        task(1, TaskStatus.COMPLETE, "node1", 100, PIPELINE, FEED, NOW - HOUR);
        task(2, TaskStatus.COMPLETE, "node1", 101, SECRET_PIPELINE, FEED, NOW - HOUR);
        errorStream(900, 100, PIPELINE, FEED, 1L, NOW - HOUR, counts(0, 1, 0), "[1:1 xslt] ERROR: Bad\n");

        run("{}");

        final List<FindMetaCriteria> pipelineSearches = metaSearches.stream()
                .filter(criteria -> terms(criteria.getExpression()).stream()
                        .anyMatch(term -> term.getField().equals(MetaFields.PIPELINE.getFldName())))
                .toList();
        // Only the pipeline the user can see that had tasks, never a cluster-wide search of Error streams.
        assertThat(pipelineSearches).hasSize(1);
        assertThat(terms(pipelineSearches.getFirst().getExpression()))
                .anyMatch(term -> PIPELINE.equals(term.getDocRef()))
                .anyMatch(term -> term.getField().equals(MetaFields.TYPE.getFldName())
                                  && StreamTypeNames.ERROR.equals(term.getValue()))
                .anyMatch(term -> term.getField().equals(MetaFields.CREATE_TIME.getFldName()));
        assertThat(metaSearches).noneMatch(criteria -> terms(criteria.getExpression()).stream()
                .anyMatch(term -> SECRET_PIPELINE.equals(term.getDocRef())));
    }

    @Test
    void failuresSpreadsTheSampleAcrossPipelinesAndFeeds() {
        task(1, TaskStatus.COMPLETE, "node1", 100, PIPELINE, FEED, NOW - HOUR);
        task(2, TaskStatus.COMPLETE, "node1", 101, OTHER_PIPELINE, OTHER_FEED, NOW - HOUR);
        for (int i = 0; i < 6; i++) {
            errorStream(900 + i, 100, PIPELINE, FEED, 1L, NOW - HOUR, counts(0, 1, 0),
                    "[1:1 xslt] ERROR: Noisy " + i + "\n");
        }
        errorStream(950, 101, OTHER_PIPELINE, OTHER_FEED, 2L, NOW - HOUR, counts(0, 1, 0),
                "[1:1 xslt] ERROR: Quiet\n");
        errorStream(951, 101, OTHER_PIPELINE, OTHER_FEED, 2L, NOW - HOUR, counts(0, 1, 0),
                "[1:1 xslt] ERROR: Quiet\n");

        final String result = run("{\"sample\":4}");

        // The two newest of the noisy pipeline, and both of the quiet one.
        Mockito.verify(store).openSource(905L);
        Mockito.verify(store).openSource(904L);
        Mockito.verify(store).openSource(951L);
        Mockito.verify(store).openSource(950L);
        Mockito.verify(store, Mockito.times(4)).openSource(Mockito.anyLong());
        assertThat(result)
                .contains("- Pipeline [Firewall pipeline](?action=open-doc&docType=Pipeline&docUuid=pipe-uuid), "
                          + "feed FIREWALL-EVENTS: 6 stream(s); of the 2 sampled: 0 fatal, 2 error(s), 0 "
                          + "warning(s)")
                .contains("Coverage: read 4 of the 8 Error stream(s) found (a sample of up to 4");
    }

    @Test
    void failuresGroupsMarkersIntoFailureModes() {
        task(1, TaskStatus.COMPLETE, "node1", 100, PIPELINE, FEED, NOW - HOUR);
        task(2, TaskStatus.COMPLETE, "node2", 101, PIPELINE, FEED, NOW - HOUR);
        meta(100, PIPELINE, FEED, null, "Raw Events", NOW - 2 * HOUR);
        errorStream(900, 100, PIPELINE, FEED, 1L, NOW - HOUR, counts(0, 2, 0), """
                [2:14 xsltFilter] ERROR: Value '12' is not a number
                [3:9 xsltFilter] ERROR: Value 'abc' is not a number
                """);
        errorStream(901, 101, PIPELINE, FEED, 2L, NOW - HOUR, counts(1, 1, 0), """
                [5:1 xsltFilter] ERROR: Value 'x y' is not a number
                [1:1 splitter] FATAL: Unexpected end of file
                """);

        final String result = run("{}");

        assertThat(result)
                .contains("1. FATAL in element 'splitter': `Unexpected end of file` — 1 marker(s) in 1 stream(s); "
                          + "Pipeline [Firewall pipeline](?action=open-doc&docType=Pipeline&docUuid=pipe-uuid); "
                          + "feed(s) FIREWALL-EVENTS; node(s) node2; first ")
                .contains("2. ERROR in element 'xsltFilter': `Value '<value>' is not a number` — 3 marker(s) in 2 "
                          + "stream(s)")
                .contains("node(s) node1, node2")
                // The source stream is linked only if the user can see it, which 101 isn't here.
                .contains("e.g. \"Value '12' is not a number\" ([Error stream 900](?action=open-stream&id=900), "
                          + "in [stream 100](?action=open-stream&id=100) at line 2, column 14)")
                .contains("e.g. \"Unexpected end of file\" ([Error stream 901](?action=open-stream&id=901) at "
                          + "line 1, column 1)")
                .contains("parsed 4 marker(s) into 2 mode(s)");
    }

    @Test
    void failuresLogsAViewForEachErrorStreamRead() {
        task(1, TaskStatus.COMPLETE, "node1", 100, PIPELINE, FEED, NOW - HOUR);
        errorStream(900, 100, PIPELINE, FEED, 1L, NOW - HOUR, counts(0, 1, 0), "[1:1 xslt] ERROR: Bad\n");
        errorStream(901, 100, PIPELINE, FEED, 1L, NOW - HOUR, counts(0, 1, 0), "[1:1 xslt] ERROR: Bad\n");
        // Can't be read, so isn't logged as viewed.
        errorStream(902, 100, PIPELINE, FEED, 1L, NOW - HOUR, counts(0, 1, 0), null);

        run("{}");

        final List<ViewEventAction> views = fixture.logged(ViewEventAction.class);
        assertThat(views).hasSize(2);
        assertThat(views).allSatisfy(view -> assertThat(view.getObjects()).hasSize(1));
        assertThat(views.stream().map(view -> ((OtherObject) view.getObjects().getFirst()).getId()).toList())
                .containsExactlyInAnyOrder("900", "901");
    }

    @Test
    void failuresReportsStreamsThatCantBeReadInTheCoverage() {
        task(1, TaskStatus.COMPLETE, "node1", 100, PIPELINE, FEED, NOW - HOUR);
        errorStream(900, 100, PIPELINE, FEED, 1L, NOW - HOUR, counts(0, 1, 0), "[1:1 xslt] ERROR: Bad\n");
        errorStream(901, 100, PIPELINE, FEED, 1L, NOW - HOUR, counts(0, 1, 0), null);
        errorStream(902, 100, PIPELINE, FEED, 1L, NOW - HOUR, counts(0, 1, 0),
                "[1:1 xslt] ERROR: " + "x".repeat(ProcessorTasksTool.MAX_BYTES_PER_STREAM) + "\n");

        final String result = run("{}");

        assertThat(result)
                .contains("Coverage: read 2 of the 3 Error stream(s) found")
                .contains("1 stream(s) were larger than that, so only their start was read.")
                .contains("1 stream(s) couldn't be read (they may have been deleted).");
    }

    @Test
    void failuresSaysWhenItCappedOrLeftOutPipelines() {
        task(1, TaskStatus.COMPLETE, "node1", 100, PIPELINE, FEED, NOW - HOUR);
        for (int i = 0; i <= ProcessorTasksTool.MAX_ERROR_STREAMS_PER_PIPELINE; i++) {
            errorStream(10_000 + i, 100, PIPELINE, FEED, 1L, NOW - HOUR, null, "[1:1 xslt] ERROR: Bad\n");
        }
        for (int i = 0; i < ProcessorTasksTool.MAX_PIPELINES_QUERIED; i++) {
            final DocRef pipeline = new DocRef(PipelineDoc.TYPE, "many-" + i, "Many " + i);
            fixture.add(pipeline, null, true);
            task(100 + i, TaskStatus.COMPLETE, "node1", 200 + i, pipeline, FEED, NOW - HOUR);
        }

        final String result = run("{\"sample\":2}");

        assertThat(result)
                .contains(ProcessorTasksTool.MAX_ERROR_STREAMS_PER_PIPELINE + "+, by pipeline and feed")
                .contains("For 1 pipeline(s) only the newest " + ProcessorTasksTool.MAX_ERROR_STREAMS_PER_PIPELINE
                          + " Error streams were found, so there are more")
                .contains("Error streams were looked for of the " + ProcessorTasksTool.MAX_PIPELINES_QUERIED
                          + " pipeline(s) with the most failures and tasks; 1 more with tasks in the window weren't "
                          + "looked at")
                // Their meta values have aged off.
                .contains("counts unavailable for 2 (their meta values have aged off; their content still holds "
                          + "the markers)")
                .contains("Coverage: read 2 of the " + ProcessorTasksTool.MAX_ERROR_STREAMS_PER_PIPELINE);
    }

    @Test
    void failuresNeedsViewDataToReadErrorStreams() {
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)).thenReturn(false);
        task(1, TaskStatus.COMPLETE, "node1", 100, PIPELINE, FEED, NOW - HOUR);
        errorStream(900, 100, PIPELINE, FEED, 1L, NOW - HOUR, counts(0, 1, 0), "[1:1 xslt] ERROR: Bad\n");

        assertThat(run("{}"))
                .contains("1 stream(s); of the 1 sampled: 0 fatal, 1 error(s), 0 warning(s)")
                .contains("Reading Error streams needs the View Data permission");
        Mockito.verifyNoInteractions(store);
    }

    @Test
    void failuresKeepsToTheNodesAskedFor() {
        task(1, TaskStatus.COMPLETE, "node1", 100, PIPELINE, FEED, NOW - HOUR);
        task(2, TaskStatus.COMPLETE, "node2", 101, PIPELINE, FEED, NOW - HOUR);
        errorStream(900, 100, PIPELINE, FEED, 1L, NOW - HOUR, counts(0, 1, 0), "[1:1 xslt] ERROR: One\n");
        errorStream(901, 101, PIPELINE, FEED, 2L, NOW - HOUR, counts(0, 1, 0), "[1:1 xslt] ERROR: Two\n");

        final String result = run("{\"nodes\":[\"node2\"]}");

        assertThat(result)
                .startsWith("Processing (node is node2, ")
                .contains("`Two`")
                .doesNotContain("`One`");
    }

    @Test
    void parsesTimesAndDurations() {
        assertThat(ProcessorTasksTool.time("-7d", "since"))
                .isBetween(System.currentTimeMillis() - Duration.ofDays(7).toMillis() - 60_000,
                        System.currentTimeMillis() - Duration.ofDays(7).toMillis() + 60_000);
        assertThat(ProcessorTasksTool.time("now()-1h", "since"))
                .isBetween(System.currentTimeMillis() - 2 * HOUR, System.currentTimeMillis());
        assertThat(ProcessorTasksTool.time("2026-10-01", "since")).isEqualTo(1_790_812_800_000L);
        assertThat(ProcessorTasksTool.time("2026-10-01T12:00:00Z", "since")).isEqualTo(1_790_856_000_000L);
        assertThatThrownBy(() -> ProcessorTasksTool.time("-soon", "since")).isInstanceOf(WorkspaceException.class);
        assertThat(ProcessorTasksTool.duration("2h")).isEqualTo(Duration.ofHours(2));
        assertThat(ProcessorTasksTool.duration("PT30M")).isEqualTo(Duration.ofMinutes(30));
        assertThatThrownBy(() -> ProcessorTasksTool.duration("soon")).isInstanceOf(WorkspaceException.class);
        assertThatThrownBy(() -> ProcessorTasksTool.duration("0m")).isInstanceOf(WorkspaceException.class);
    }

    // --------------------------------------------------------------------------------
    // The world.
    // --------------------------------------------------------------------------------

    private String run(final String arguments) {
        final ProcessorTasksTool tool = new ProcessorTasksTool(fixture.workspace,
                fixture.processorFilterAccess(processorFilterService), fixture.documentAccess(),
                fixture.feedAccess(), () -> taskService, () -> metaService, () -> store, () -> nodeService,
                fixture.auditLog());
        try {
            return tool.run(ToolTestFixture.request(ProcessorTasksTool.NAME, arguments), 1);
        } catch (final WorkspaceException e) {
            // As the tool's call returns it.
            return "Error: " + e.getMessage();
        }
    }

    /// Adds a task, with its filter's id the same as its own, and its times from its status time.
    private void task(final long id,
                      final TaskStatus status,
                      final String node,
                      final long metaId,
                      final DocRef pipeline,
                      final DocRef feed,
                      final long statusTimeMs) {
        final ProcessorFilter filter = ProcessorFilter.builder()
                .id((int) id)
                .uuid("filter-" + id + "-uuid")
                .pipelineUuid(pipeline.getUuid())
                .pipelineName(pipeline.getName())
                .build();
        final boolean ended = status == TaskStatus.COMPLETE || status == TaskStatus.FAILED;
        tasks.add(new ProcessorTask(id, 1, metaId, null, node, feed.getName(), statusTimeMs - 60_000,
                statusTimeMs, status == TaskStatus.CREATED || status == TaskStatus.QUEUED
                ? null
                : statusTimeMs - 30_000, ended
                ? statusTimeMs
                : null, status, filter));
    }

    private void meta(final long id,
                      final DocRef pipeline,
                      final DocRef feed,
                      final Long parentId,
                      final String type,
                      final long createMs) {
        metas.add(Meta.builder()
                .id(id)
                .feedName(feed.getName())
                .typeName(type)
                .pipelineUuid(pipeline.getUuid())
                .parentDataId(parentId)
                .status(Status.UNLOCKED)
                .createMs(createMs)
                .build());
    }

    /// Adds an Error stream.
    ///
    /// @param counts  Its meta values, or null if they have aged off.
    /// @param content What it holds, or null if it can't be read.
    private void errorStream(final long id,
                             final long parentId,
                             final DocRef pipeline,
                             final DocRef feed,
                             final Long taskId,
                             final long createMs,
                             final Map<String, String> counts,
                             final String content) {
        metas.add(Meta.builder()
                .id(id)
                .feedName(feed.getName())
                .typeName(StreamTypeNames.ERROR)
                .pipelineUuid(pipeline.getUuid())
                .parentDataId(parentId)
                .processorTaskId(taskId)
                .status(Status.UNLOCKED)
                .createMs(createMs)
                .build());
        if (counts != null) {
            attributes.put(id, counts);
        }
        if (content != null) {
            contents.put(id, content);
        }
    }

    private static Map<String, String> counts(final long fatal, final long error, final long warning) {
        final Map<String, String> counts = new LinkedHashMap<>();
        counts.put(MetaFields.REC_FATAL.getFldName(), String.valueOf(fatal));
        counts.put(MetaFields.REC_ERROR.getFldName(), String.valueOf(error));
        counts.put(MetaFields.REC_WARN.getFldName(), String.valueOf(warning));
        return counts;
    }

    private ResultPage<ProcessorTask> findTasks(final ExpressionCriteria criteria) {
        final List<ProcessorTask> found = tasks.stream()
                .filter(task -> matches(criteria.getExpression(), field -> taskValue(task, field)))
                .sorted(Comparator.comparingLong(ProcessorTask::getId).reversed())
                .toList();
        return new ResultPage<>(page(found, criteria.getPageRequest()));
    }

    private ResultPage<ProcessorTaskSummary> summarise(final ExpressionCriteria criteria) {
        final Map<List<Object>, Long> counts = new LinkedHashMap<>();
        final Map<List<Object>, ProcessorTask> examples = new HashMap<>();
        tasks.stream()
                .filter(task -> matches(criteria.getExpression(), field -> taskValue(task, field)))
                .forEach(task -> {
                    final List<Object> key = List.of(task.getProcessorFilter().getPipelineUuid(), task.getFeedName(),
                            task.getStatus());
                    counts.merge(key, 1L, Long::sum);
                    examples.putIfAbsent(key, task);
                });
        final List<ProcessorTaskSummary> summaries = new ArrayList<>();
        counts.forEach((key, count) -> {
            final ProcessorTask task = examples.get(key);
            summaries.add(new ProcessorTaskSummary(ProcessorFilterAccess.pipelineOf(task.getProcessorFilter()),
                    task.getFeedName(), 10, task.getStatus(), count));
        });
        return new ResultPage<>(page(summaries, criteria.getPageRequest()));
    }

    /// @return The streams the search finds, of feeds the user can view, as MetaService finds them.
    private List<Meta> findMetas(final FindMetaCriteria criteria) {
        final List<Meta> found = metas.stream()
                .filter(meta -> fixture.visible.contains(feedUuid(meta.getFeedName())))
                .filter(meta -> matches(criteria.getExpression(), field -> metaValue(meta, field)))
                .sorted(Comparator.comparingLong(Meta::getId).reversed())
                .toList();
        return page(found, criteria.getPageRequest());
    }

    private static String feedUuid(final String name) {
        return List.of(FEED, OTHER_FEED, SECRET_FEED).stream()
                .filter(feed -> feed.getName().equals(name))
                .map(DocRef::getUuid)
                .findFirst()
                .orElse("");
    }

    private static <T> List<T> page(final List<T> list, final PageRequest pageRequest) {
        if (pageRequest == null) {
            return list;
        }
        final int offset = Objects.requireNonNullElse(pageRequest.getOffset(), 0);
        final int length = Objects.requireNonNullElse(pageRequest.getLength(), list.size());
        return list.subList(Math.min(offset, list.size()), Math.min(list.size(), offset + length));
    }

    private static String taskValue(final ProcessorTask task, final String field) {
        if (field.equals(ProcessorTaskFields.STATUS.getFldName())) {
            return task.getStatus().name();
        } else if (field.equals(ProcessorTaskFields.NODE_NAME.getFldName())) {
            return task.getNodeName();
        } else if (field.equals(ProcessorTaskFields.FEED.getFldName())) {
            return task.getFeedName();
        } else if (field.equals(ProcessorTaskFields.PIPELINE.getFldName())) {
            return task.getProcessorFilter().getPipelineUuid();
        } else if (field.equals(ProcessorTaskFields.PROCESSOR_FILTER_ID.getFldName())) {
            return String.valueOf(task.getProcessorFilter().getId());
        } else if (field.equals(ProcessorTaskFields.META_ID.getFldName())) {
            return String.valueOf(task.getMetaId());
        } else if (field.equals(ProcessorTaskFields.TASK_ID.getFldName())) {
            return String.valueOf(task.getId());
        } else if (field.equals(ProcessorTaskFields.STATUS_TIME_MS.getFldName())) {
            return String.valueOf(task.getStatusTimeMs());
        }
        throw new IllegalArgumentException("Unexpected task field " + field);
    }

    private static String metaValue(final Meta meta, final String field) {
        if (field.equals(MetaFields.ID.getFldName())) {
            return String.valueOf(meta.getId());
        } else if (field.equals(MetaFields.PARENT_ID.getFldName())) {
            return String.valueOf(meta.getParentMetaId());
        } else if (field.equals(MetaFields.TYPE.getFldName())) {
            return meta.getTypeName();
        } else if (field.equals(MetaFields.STATUS.getFldName())) {
            return meta.getStatus().getDisplayValue();
        } else if (field.equals(MetaFields.FEED.getFldName())) {
            return meta.getFeedName();
        } else if (field.equals(MetaFields.PIPELINE.getFldName())) {
            return meta.getPipelineUuid();
        } else if (field.equals(MetaFields.META_PROCESSOR_FILTER_ID.getFldName())) {
            return String.valueOf(meta.getProcessorFilterId());
        } else if (field.equals(MetaFields.CREATE_TIME.getFldName())) {
            // Every stream here is created in the window.
            return null;
        }
        throw new IllegalArgumentException("Unexpected meta field " + field);
    }

    /// @return True if the values match the expression, for the terms the tool makes.
    private static boolean matches(final ExpressionItem item, final Function<String, String> values) {
        if (item instanceof final ExpressionOperator operator) {
            final List<ExpressionItem> children = Objects.requireNonNullElse(operator.getChildren(), List.of());
            final Predicate<ExpressionItem> match = child -> matches(child, values);
            return operator.getOp() == Op.OR
                    ? children.isEmpty() || children.stream().anyMatch(match)
                    : children.stream().allMatch(match);
        }
        final ExpressionTerm term = (ExpressionTerm) item;
        final String value = values.apply(term.getField());
        return switch (term.getCondition()) {
            case EQUALS -> term.getValue().equalsIgnoreCase(value);
            case IN -> Arrays.asList(term.getValue().split(",")).contains(value);
            case IS_DOC_REF -> term.getDocRef().getUuid().equals(value);
            case GREATER_THAN_OR_EQUAL_TO -> value == null || Long.parseLong(value) >= Long.parseLong(term.getValue());
            case LESS_THAN -> value == null || Long.parseLong(value) < Long.parseLong(term.getValue());
            default -> throw new IllegalArgumentException("Unexpected condition " + term);
        };
    }

    private static List<ExpressionTerm> terms(final ExpressionItem item) {
        if (item instanceof final ExpressionOperator operator) {
            return Objects.requireNonNullElse(operator.getChildren(), List.<ExpressionItem>of()).stream()
                    .flatMap(child -> terms(child).stream())
                    .toList();
        }
        return List.of((ExpressionTerm) item);
    }

    private Source source(final long id) throws IOException {
        final String content = contents.get(id);
        if (content == null) {
            throw new DataException("Unable to find meta data for id=" + id);
        }
        final InputStreamProvider provider = Mockito.mock(InputStreamProvider.class);
        Mockito.when(provider.get()).thenAnswer(invocation ->
                new FakeSegmentInputStream(content.getBytes(StandardCharsets.UTF_8)));
        final Source source = Mockito.mock(Source.class);
        Mockito.when(source.count()).thenReturn(1L);
        Mockito.when(source.get(0)).thenReturn(provider);
        return source;
    }


    // --------------------------------------------------------------------------------


    /// A stream of one segment, held in memory.
    private static final class FakeSegmentInputStream extends SegmentInputStream {

        private final ByteArrayInputStream in;
        private final long size;

        private FakeSegmentInputStream(final byte[] bytes) {
            this.in = new ByteArrayInputStream(bytes);
            this.size = bytes.length;
        }

        @Override
        public long count() {
            return 1;
        }

        @Override
        public void include(final long segment) {
            // One segment only.
        }

        @Override
        public void includeAll() {
            // One segment only.
        }

        @Override
        public void exclude(final long segment) {
            // One segment only.
        }

        @Override
        public void excludeAll() {
            // One segment only.
        }

        @Override
        public long size() {
            return size;
        }

        @Override
        public int read() {
            return in.read();
        }

        @Override
        public int read(final byte[] bytes, final int offset, final int length) {
            return in.read(bytes, offset, length);
        }
    }
}
