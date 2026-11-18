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
import stroom.security.shared.AppPermission;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
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
import java.util.function.Supplier;

/// A fixed cluster of processing tasks, streams and Error streams for the reference output of processor_tasks, as
/// `TestProcessorTasksTool` builds its world (copied here, so that test can change freely). Every time is fixed, and
/// each call gives its window as absolute times, so the output never depends on the clock.
final class ProcessorTasksReferenceWorld {

    /// The window every reference call gives, as absolute times.
    static final String WINDOW = "\"since\":\"2026-10-01T00:00:00Z\",\"until\":\"2026-10-01T13:00:00Z\"";

    private static final DocRef PIPELINE = new DocRef(PipelineDoc.TYPE, "pipe-uuid", "Firewall pipeline");
    private static final DocRef OTHER_PIPELINE = new DocRef(PipelineDoc.TYPE, "other-pipe-uuid", "DNS pipeline");
    private static final DocRef SECRET_PIPELINE = new DocRef(PipelineDoc.TYPE, "secret-pipe-uuid", "Secret pipe");
    private static final DocRef FEED = new DocRef(FeedDoc.TYPE, "feed-uuid", "FIREWALL-EVENTS");
    private static final DocRef OTHER_FEED = new DocRef(FeedDoc.TYPE, "other-feed-uuid", "DNS-EVENTS");
    private static final DocRef SECRET_FEED = new DocRef(FeedDoc.TYPE, "secret-feed-uuid", "SECRET-EVENTS");
    private static final long NOW = Instant.parse("2026-10-01T12:00:00Z").toEpochMilli();
    private static final long HOUR = Duration.ofHours(1).toMillis();

    private final ToolTestFixture fixture;
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

    /// @param tempDir A folder for the workspace's temporary files.
    ProcessorTasksReferenceWorld(final Path tempDir) throws IOException {
        fixture = new ToolTestFixture(tempDir);
        fixture.add(PIPELINE, null, true);
        fixture.add(OTHER_PIPELINE, null, true);
        fixture.add(SECRET_PIPELINE, null, false);
        fixture.add(FEED, null, true);
        fixture.add(OTHER_FEED, null, true);
        fixture.add(SECRET_FEED, null, false);
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION))
                .thenReturn(true);
        Mockito.when(fixture.workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
        Mockito.when(nodeService.findNodeNames(Mockito.any())).thenReturn(List.of("node1", "node2", "node3"));
        Mockito.when(taskService.find(Mockito.any())).thenAnswer(invocation -> findTasks(invocation.getArgument(0)));
        Mockito.when(taskService.findSummary(Mockito.any()))
                .thenAnswer(invocation -> summarise(invocation.getArgument(0)));
        Mockito.when(metaService.find(Mockito.any())).thenAnswer(invocation ->
                new ResultPage<>(findMetas(invocation.getArgument(0))));
        Mockito.when(metaService.findRows(Mockito.any())).thenAnswer(invocation -> {
            final FindMetaCriteria criteria = invocation.getArgument(0);
            return new ResultPage<>(findMetas(criteria).stream()
                    .map(meta -> new MetaRow(meta, null, attributes.getOrDefault(meta.getId(), Map.of())))
                    .toList());
        });
        Mockito.when(store.openSource(Mockito.anyLong())).thenAnswer(invocation -> source(invocation.getArgument(0)));
        addTheCluster();
    }

    /// Runs processor_tasks as the model calls it.
    ///
    /// @param arguments The call's arguments, as JSON.
    /// @return What the model is given.
    String run(final String arguments) {
        final ProcessorTasksTool tool = new ProcessorTasksTool(fixture.workspace,
                fixture.processorFilterAccess(processorFilterService), fixture.documentAccess(),
                fixture.feedAccess(), () -> taskService, () -> metaService, () -> store, () -> nodeService,
                fixture.auditLog());
        return tool.execute(ToolTestFixture.request(ProcessorTasksTool.NAME, arguments), ToolCalls.inChat(1,
                fixture.workspace)).text();
    }

    /// @return What has been written to the workspace, by path.
    Map<String, String> written() {
        return fixture.written;
    }

    // Failed, complete, stuck and queued tasks on three nodes, some of pipelines or feeds the user can't view, and
    // Error streams with markers of two failure modes, one that can't be read and one whose counts have aged off.
    private void addTheCluster() {
        task(1, TaskStatus.FAILED, "node1", 100, PIPELINE, FEED, NOW - 3 * HOUR);
        task(2, TaskStatus.FAILED, "node1", 101, PIPELINE, FEED, NOW - 2 * HOUR);
        task(3, TaskStatus.FAILED, "node1", 102, PIPELINE, FEED, NOW - HOUR);
        task(4, TaskStatus.FAILED, "node2", 103, OTHER_PIPELINE, OTHER_FEED, NOW - HOUR);
        task(5, TaskStatus.COMPLETE, "node2", 104, OTHER_PIPELINE, OTHER_FEED, NOW - HOUR);
        task(6, TaskStatus.FAILED, "node3", 105, SECRET_PIPELINE, FEED, NOW - HOUR);
        task(7, TaskStatus.COMPLETE, "node1", 106, PIPELINE, FEED, NOW - HOUR);
        task(8, TaskStatus.COMPLETE, "node2", 107, PIPELINE, FEED, NOW - HOUR);
        task(9, TaskStatus.PROCESSING, "node3", 108, PIPELINE, FEED, NOW - 3 * HOUR);
        task(10, TaskStatus.QUEUED, null, 109, PIPELINE, FEED, NOW - HOUR);
        task(11, TaskStatus.COMPLETE, "node1", 110, PIPELINE, SECRET_FEED, NOW - HOUR);
        meta(106, PIPELINE, FEED, null, "Raw Events", NOW - 2 * HOUR);
        errorStream(900, 106, PIPELINE, FEED, 7L, NOW - HOUR, counts(0, 2, 0), """
                [2:14 xsltFilter] ERROR: Value '12' is not a number
                [3:9 xsltFilter] ERROR: Value 'abc' is not a number
                """);
        errorStream(901, 107, PIPELINE, FEED, 8L, NOW - HOUR, counts(1, 1, 0), """
                [5:1 xsltFilter] ERROR: Value 'x y' is not a number
                [1:1 splitter] FATAL: Unexpected end of file
                """);
        // Its meta values have aged off.
        errorStream(902, 104, OTHER_PIPELINE, OTHER_FEED, 5L, NOW - HOUR, null, "[1:1 xslt] WARN: Odd\n");
        // Can't be read.
        errorStream(903, 104, OTHER_PIPELINE, OTHER_FEED, 5L, NOW - HOUR, counts(0, 1, 0), null);
    }

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

    // Adds an Error stream: its counts are null if they have aged off, and its content null if it can't be read.
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
