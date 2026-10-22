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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.ToolDeadline;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.data.shared.StreamTypeNames;
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
import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeService;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.common.v2.DateExpressionParser;
import stroom.security.shared.AppPermission;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.StoredError;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// `processor_tasks` - the processing tasks across the cluster, for "what's failing in processing, and why?",
/// backlog and throughput questions, and to look at individual tasks.
///
/// - `failures` (the default) answers "what is failing and why" in one call: tasks in a time window by status, by
///   pipeline and by node, with failure rates; the FAILED tasks grouped by pipeline, feed and node with their
///   first and last times (Stroom doesn't record why a task failed, and this tool doesn't look for it: the user
///   looks in that node's application log in their own log system); tasks that look stuck; and the errors inside
///   processing that completed, from the Error streams the pipelines wrote, read and grouped into failure modes.
/// - `list` lists individual tasks, newest first.
/// - `summary` counts tasks grouped by pipeline, feed, node, status and/or processor filter.
///
/// Read as the user. Processing tasks need the Manage Processors permission, as the Active Tasks tab does; without
/// it nothing else is given. Of the tasks, only those whose pipeline (or analytic rule) and feed the user can
/// view are counted or shown, and nothing says how many others there are. Error streams are found through
/// [MetaService], which only finds those of feeds the user may view, and only those whose pipeline the user can
/// view are used. Their content is read only with the View Data permission, as the data viewer needs, and the
/// stream store checks the user may view each stream's feed again when it opens it. Each Error stream read is
/// logged as a View. Every check fails closed.
class ProcessorTasksTool extends AbstractWorkspaceTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorTasksTool.class);

    static final String NAME = "processor_tasks";
    static final String MODE_ARG = "mode";
    static final String PIPELINES_ARG = "pipelines";
    static final String FILTERS_ARG = "filters";
    static final String FEEDS_ARG = "feeds";
    static final String STREAMS_ARG = "streams";
    static final String NODES_ARG = "nodes";
    static final String STATUSES_ARG = "statuses";
    static final String SINCE_ARG = "since";
    static final String UNTIL_ARG = "until";
    static final String GROUP_BY_ARG = "group_by";
    static final String STUCK_AFTER_ARG = "stuck_after";
    static final String LIMIT_ARG = "limit";
    static final String SAMPLE_ARG = "sample";

    static final String FAILURES = "failures";
    static final String LIST = "list";
    static final String SUMMARY = "summary";
    static final List<String> MODES = List.of(FAILURES, LIST, SUMMARY);

    static final String PIPELINE = "pipeline";
    static final String FEED = "feed";
    static final String NODE = "node";
    static final String STATUS = "status";
    static final String FILTER = "filter";
    static final List<String> GROUPS = List.of(PIPELINE, FEED, NODE, STATUS, FILTER);
    static final List<String> DEFAULT_GROUP_BY = List.of(PIPELINE, STATUS);

    /// The window `failures` looks at when no `since` is given.
    static final Duration DEFAULT_WINDOW = Duration.ofHours(24);
    /// How long a running task's status may go unrenewed before `failures` calls it possibly stuck.
    static final Duration DEFAULT_STUCK_AFTER = Duration.ofHours(1);
    static final int DEFAULT_LIST_LIMIT = 50;
    static final int MAX_LIST_LIMIT = 500;
    /// The most tasks listed in the result itself; more go to the `out` file.
    static final int MAX_INLINE_TASKS = 100;
    /// The most rows of counts read from one summary query.
    static final int MAX_SUMMARY_ROWS = 2_000;
    /// The most task rows read when counting by processor filter, or finding FAILED tasks.
    static final int MAX_SCAN_ROWS = 10_000;
    /// The most task rows read at a time.
    static final int PAGE_SIZE = ProcessorTaskFinder.PAGE_SIZE;
    /// The most stuck-looking tasks read.
    static final int MAX_STUCK_ROWS = 1_000;
    /// The most pipelines whose Error streams are looked for, each with its own query.
    static final int MAX_PIPELINES_QUERIED = 20;
    /// The most Error streams found for each pipeline.
    static final int MAX_ERROR_STREAMS_PER_PIPELINE = 200;
    static final int DEFAULT_SAMPLE = 30;
    static final int MAX_SAMPLE = 100;
    /// The most bytes of one Error stream read.
    static final int MAX_BYTES_PER_STREAM = 256 * 1024;
    /// The most tasks looked up at once, for the node that wrote each Error stream.
    static final int TASK_LOOKUP_CHUNK = 200;
    /// The most group lines in each part of a result.
    static final int MAX_GROUP_LINES = 20;
    /// The most failure modes shown.
    static final int MAX_MODES = 15;
    /// The most example tasks shown for each group of FAILED or stuck tasks.
    static final int MAX_EXAMPLE_TASKS = 3;
    static final int PREVIEW_ROWS = 10;
    static final String DEFAULT_OUT = "/outputs/processor_tasks.csv";
    /// What a node with this share of the FAILED tasks (or more) is called out for.
    static final double NODE_SIGNAL_SHARE = 0.5;

    static final String NEEDS_MANAGE_PROCESSORS = "You need the Manage Processors permission to see processing "
                                                  + "tasks, as the Active Tasks tab does.";
    static final String FAILED_REASON_NOTE = "Stroom doesn't record why a task failed; look in that node's "
                                             + "application log (in your log system) around these times.";
    static final List<String> TASK_COLUMNS = List.of("Task Id", "Status", "Node", "Stream Id", "Feed", "Pipeline",
            "Pipeline UUID", "Filter Id", "Create Time", "Start Time", "End Time", "Status Time", "Duration Ms",
            "Error Stream Ids", "Fatal Errors", "Errors", "Warnings");

    private static final Set<TaskStatus> RUNNING = Set.of(TaskStatus.ASSIGNED, TaskStatus.PROCESSING);
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern SIMPLE_DURATION = Pattern.compile("(\\d+)\\s*([smhd])", Pattern.CASE_INSENSITIVE);
    private static final Pattern RELATIVE_TIME = Pattern.compile("-\\s*\\d+\\s*[a-zA-Z]+.*");

    private final ProcessorFilterAccess processorFilterAccess;
    private final DocumentAccess documentAccess;
    private final FeedAccess feedAccess;
    private final Provider<ProcessorTaskService> processorTaskServiceProvider;
    private final Provider<MetaService> metaServiceProvider;
    private final Provider<Store> storeProvider;
    private final Provider<NodeService> nodeServiceProvider;
    private final AiAuditLog auditLog;

    @Inject
    ProcessorTasksTool(final ChatWorkspace workspace,
                       final ProcessorFilterAccess processorFilterAccess,
                       final DocumentAccess documentAccess,
                       final FeedAccess feedAccess,
                       final Provider<ProcessorTaskService> processorTaskServiceProvider,
                       final Provider<MetaService> metaServiceProvider,
                       final Provider<Store> storeProvider,
                       final Provider<NodeService> nodeServiceProvider,
                       final AiAuditLog auditLog) {
        super(workspace);
        this.processorFilterAccess = processorFilterAccess;
        this.documentAccess = documentAccess;
        this.feedAccess = feedAccess;
        this.processorTaskServiceProvider = processorTaskServiceProvider;
        this.metaServiceProvider = metaServiceProvider;
        this.storeProvider = storeProvider;
        this.nodeServiceProvider = nodeServiceProvider;
        this.auditLog = auditLog;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .requires(AppPermission.MANAGE_PROCESSORS_PERMISSION)
                .unlockedBy(AppPermission.VIEW_DATA_PERMISSION)
                .build();
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.PROCESSING;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("The tool for processing across the cluster: what is failing and why, backlogs and "
                                 + "throughput, and individual processing tasks. mode 'failures' (the default) is "
                                 + "the one-call answer to 'what's failing in processing (today), and why?': tasks "
                                 + "in a window (default the last 24 hours) by status, pipeline and node with "
                                 + "failure rates; FAILED tasks grouped by pipeline, feed and node, with first and "
                                 + "last times (Stroom keeps no reason for these: look in that node's application "
                                 + "log in the user's log system); tasks that look stuck; and the errors inside "
                                 + "processing that completed, read from the Error streams the pipelines wrote and "
                                 + "grouped into failure modes (pattern, severity, element, counts, pipelines, "
                                 + "feeds, nodes, examples with stream links), with what was sampled. mode 'list' "
                                 + "lists tasks (id, status, node, stream, feed, pipeline, "
                                 + "filter, times), newest first")
                        .chatOnly("; give out for a CSV")
                        .general(". mode 'summary' counts tasks grouped by any of pipeline, feed, node, status, "
                                 + "filter, e.g. 'how many tasks are queued per node?'. All modes take the same "
                                 + "filters. Needs the Manage Processors permission.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addEnumProperty(MODE_ARG, MODES, "failures (default), list or summary.")
                        .addProperty(PIPELINES_ARG, stringArraySchema("Pipeline (or analytic rule) UUIDs."))
                        .addProperty(FEEDS_ARG, stringArraySchema("Feed names or UUIDs."))
                        .addProperty(NODES_ARG, stringArraySchema("Node names."))
                        .addProperty(FILTERS_ARG, stringArraySchema("Processor filter ids or UUIDs."))
                        .addProperty(STREAMS_ARG, stringArraySchema("Stream (meta) ids the tasks process."))
                        .addProperty(STATUSES_ARG, stringArraySchema("For list and summary: task statuses, any "
                                                                     + "of Created, Queued, Assigned, "
                                                                     + "Processing, Complete, Failed, "
                                                                     + "Deleted."))
                        .addStringProperty(SINCE_ARG, "Tasks whose status changed at or after this time: an ISO "
                                                      + "8601 date or date and time (UTC), a period back from "
                                                      + "now (P7D, PT6H, -7d, -6h) or a Stroom date expression "
                                                      + "(now()-1d, day()). failures defaults to the last 24 "
                                                      + "hours; list and summary to any time.")
                        .addStringProperty(UNTIL_ARG, "Tasks whose status changed before this time, in the same "
                                                      + "forms.")
                        .addProperty(GROUP_BY_ARG, stringArraySchema("For summary: any of "
                                                                     + String.join(", ", GROUPS)
                                                                     + " (default pipeline, status)."))
                        .addStringProperty(STUCK_AFTER_ARG, "For failures: running tasks whose status hasn't "
                                                            + "changed for this long are called possibly "
                                                            + "stuck, e.g. PT30M, 2h (default 1h).")
                        .addIntegerProperty(SAMPLE_ARG, "For failures: the most Error streams to read for the "
                                                        + "failure modes (default " + DEFAULT_SAMPLE + ", at "
                                                        + "most " + MAX_SAMPLE + ").")
                        .addIntegerProperty(LIMIT_ARG, "For list: the most tasks (default " + DEFAULT_LIST_LIMIT
                                                       + ", at most " + MAX_LIST_LIMIT + ").")
                        .addStringProperty(OUT_ARG, ToolDescription.builder()
                                .chatOnly("For list: a .csv file in /outputs/ to write the tasks to, e.g. "
                                          + DEFAULT_OUT + ".")
                                .build())
                        .build())
                .build();
    }

    @Override
    protected Duration timeLimit(final ToolCallContext context) {
        // Reading Error streams can take longer than a workspace step.
        return limits(context).getQueryTimeLimit().getDuration();
    }

    @Override
    protected ToolResult run(final ToolCall request, final ToolCallContext context) {
        if (!processorFilterAccess.canManageProcessors()) {
            return ToolResult.text(NEEDS_MANAGE_PROCESSORS);
        }
        final String mode = mode(request);
        final Scope scope = scope(request, mode, context.links());
        final Visibility visibility = new Visibility();
        final String result = switch (mode) {
            case LIST -> list(request, context, scope, visibility);
            case SUMMARY -> summary(request, scope, visibility);
            default -> failures(request, context, scope, visibility);
        };
        logSearch(context, mode, scope, visibility);
        return ToolResult.text(result);
    }

    private static String mode(final ToolCall request) {
        final Object value = argsMap(request).get(MODE_ARG);
        if (value == null || String.valueOf(value).isBlank()) {
            return FAILURES;
        }
        final String mode = String.valueOf(value).strip().toLowerCase(Locale.ROOT);
        if (!MODES.contains(mode)) {
            throw new WorkspaceException("The mode must be one of: " + String.join(", ", MODES) + ".");
        }
        return mode;
    }

    // --------------------------------------------------------------------------------
    // The filters.
    // --------------------------------------------------------------------------------

    /// @param links How to link to Stroom objects for the caller.
    /// @return The tasks asked about, from the arguments.
    /// @throws WorkspaceException for an argument that can't be used.
    private Scope scope(final ToolCall request, final String mode, final StroomLinks links) {
        final List<String> description = new ArrayList<>();
        final List<DocRef> pipelines = new ArrayList<>();
        for (final String uuid : stringListArg(request, PIPELINES_ARG)) {
            final DocRef pipeline = documentAccess.findDocRef(uuid)
                    .filter(docRef -> PipelineDoc.TYPE.equals(docRef.getType())
                                      || AnalyticRuleDoc.TYPE.equals(docRef.getType()))
                    .orElseThrow(() -> new WorkspaceException("No pipeline or analytic rule was found with the UUID "
                                                              + uuid + ", or you don't have permission to view "
                                                              + "it."));
            pipelines.add(pipeline);
        }
        if (!pipelines.isEmpty()) {
            description.add("pipeline is " + String.join(" or ", pipelines.stream()
                    .map(docRef -> "'" + docRef.getName() + "'")
                    .toList()));
        }
        final List<String> feeds = new ArrayList<>();
        for (final String feed : stringListArg(request, FEEDS_ARG)) {
            feeds.add(feedName(feed));
        }
        if (!feeds.isEmpty()) {
            description.add("feed is " + String.join(" or ", feeds));
        }
        final List<String> nodes = stringListArg(request, NODES_ARG);
        if (!nodes.isEmpty()) {
            description.add("node is " + String.join(" or ", nodes));
        }
        final List<Integer> filters = new ArrayList<>();
        for (final String filter : stringListArg(request, FILTERS_ARG)) {
            filters.add(filterId(filter));
        }
        if (!filters.isEmpty()) {
            description.add("processor filter is " + String.join(" or ", filters.stream()
                    .map(String::valueOf)
                    .toList()));
        }
        final List<Long> streams = new ArrayList<>();
        for (final String stream : stringListArg(request, STREAMS_ARG)) {
            streams.add(positiveLong(stream, "stream id"));
        }
        if (!streams.isEmpty()) {
            description.add("stream is " + String.join(" or ", streams.stream().map(String::valueOf).toList()));
        }
        final List<TaskStatus> statuses = new ArrayList<>();
        for (final String status : stringListArg(request, STATUSES_ARG)) {
            final TaskStatus taskStatus = status(status);
            if (!statuses.contains(taskStatus)) {
                statuses.add(taskStatus);
            }
        }
        if (!statuses.isEmpty()) {
            description.add("status is " + String.join(" or ", statuses.stream()
                    .map(TaskStatus::getDisplayValue)
                    .toList()));
        }
        final long nowMs = System.currentTimeMillis();
        final String since = stringArg(request, SINCE_ARG);
        final String until = stringArg(request, UNTIL_ARG);
        Long sinceMs = since == null
                ? null
                : time(since, SINCE_ARG);
        final Long untilMs = until == null
                ? null
                : time(until, UNTIL_ARG);
        if (sinceMs == null && FAILURES.equals(mode)) {
            sinceMs = (untilMs == null
                    ? nowMs
                    : untilMs) - DEFAULT_WINDOW.toMillis();
        }
        if (sinceMs != null && untilMs != null && sinceMs >= untilMs) {
            throw new WorkspaceException("The time given for " + SINCE_ARG + " must be before the time given for "
                                         + UNTIL_ARG + ".");
        }
        if (sinceMs != null) {
            description.add("status changed from " + DateUtil.createNormalDateTimeString(sinceMs));
        }
        if (untilMs != null) {
            description.add("status changed before " + DateUtil.createNormalDateTimeString(untilMs));
        }
        return new Scope(pipelines, feeds, nodes, filters, streams, statuses, sinceMs, untilMs, nowMs,
                String.join(", ", description), links);
    }

    /// @return The name of a feed given by name or UUID, if the user can view it.
    private String feedName(final String feed) {
        if (UUID_PATTERN.matcher(feed).matches()) {
            return documentAccess.findDocRef(feed)
                    .filter(docRef -> FeedDoc.TYPE.equals(docRef.getType()))
                    .map(DocRef::getName)
                    .orElseThrow(() -> new WorkspaceException("No feed was found with the UUID " + feed + ", or you "
                                                              + "don't have permission to view it."));
        }
        if (!feedAccess.canView(feed)) {
            throw new WorkspaceException("No feed named '" + feed + "' was found, or you don't have permission to "
                                         + "view it.");
        }
        return feed;
    }

    /// @return The id of a processor filter given by id or UUID.
    private int filterId(final String filter) {
        if (UUID_PATTERN.matcher(filter).matches()) {
            return processorFilterAccess.find(filter)
                    .map(ProcessorFilter::getId)
                    .orElseThrow(() -> new WorkspaceException("No processor filter was found with the UUID " + filter
                                                              + ", or you don't have permission to view it."));
        }
        final long id = positiveLong(filter, "processor filter id or UUID");
        if (id > Integer.MAX_VALUE) {
            throw new WorkspaceException("'" + filter + "' isn't a processor filter id or UUID.");
        }
        return (int) id;
    }

    private static long positiveLong(final String value, final String what) {
        try {
            final long id = Long.parseLong(value.strip().replace(",", ""));
            if (id > 0) {
                return id;
            }
        } catch (final NumberFormatException e) {
            // Not a number, so not an id.
        }
        throw new WorkspaceException("'" + value + "' isn't a " + what + ".");
    }

    private static TaskStatus status(final String value) {
        for (final TaskStatus status : TaskStatus.values()) {
            if (status.name().equalsIgnoreCase(value.strip())
                || status.getDisplayValue().equalsIgnoreCase(value.strip())) {
                return status;
            }
        }
        throw new WorkspaceException("'" + value + "' isn't a task status. Give any of: " + String.join(", ",
                Arrays.stream(TaskStatus.values()).map(TaskStatus::getDisplayValue).toList()) + ".");
    }

    /// @param value An ISO 8601 date, date and time or period back from now, a relative time such as `-7d`, or a
    ///              Stroom date expression such as `now()-1d`.
    /// @param name  The argument it was given for.
    /// @return The time, in milliseconds since the epoch.
    /// @throws WorkspaceException if it isn't a time in any of those forms.
    static long time(final String value, final String name) {
        final String trimmed = value.strip();
        if (RELATIVE_TIME.matcher(trimmed).matches() || trimmed.contains("(")) {
            final String expression = trimmed.startsWith("-")
                    ? "now()" + trimmed
                    : trimmed;
            try {
                final Optional<ZonedDateTime> time = DateExpressionParser.parse(expression);
                if (time.isPresent()) {
                    return time.get().toInstant().toEpochMilli();
                }
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "Unable to parse the date expression " + expression, e);
            }
            throw new WorkspaceException("'" + value + "' isn't a time " + name + " can be: give an ISO 8601 date "
                                         + "(e.g. 2026-10-01), date and time (e.g. 2026-10-01T12:00:00Z), period "
                                         + "back from now (e.g. P7D, PT6H, -7d or -6h) or a date expression (e.g. "
                                         + "now()-1d).");
        }
        return StreamMetaTool.time(trimmed, name);
    }

    /// @param value An ISO 8601 duration, e.g. `PT30M`, or a number of seconds, minutes, hours or days, e.g. `2h`.
    /// @return The duration.
    /// @throws WorkspaceException if it isn't a positive duration in either form.
    static Duration duration(final String value) {
        final String trimmed = value.strip();
        Duration duration = null;
        final Matcher matcher = SIMPLE_DURATION.matcher(trimmed);
        if (matcher.matches()) {
            final long amount = Long.parseLong(matcher.group(1));
            duration = switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                case "s" -> Duration.ofSeconds(amount);
                case "m" -> Duration.ofMinutes(amount);
                case "h" -> Duration.ofHours(amount);
                default -> Duration.ofDays(amount);
            };
        } else {
            try {
                duration = Duration.parse(trimmed.toUpperCase(Locale.ROOT));
            } catch (final DateTimeParseException e) {
                // Reported below.
            }
        }
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new WorkspaceException("'" + value + "' isn't a time " + STUCK_AFTER_ARG + " can be: give an ISO "
                                         + "8601 duration (e.g. PT30M) or a number of minutes, hours or days (e.g. "
                                         + "30m, 2h, 1d).");
        }
        return duration;
    }

    /// @param scope    The tasks asked about.
    /// @param statuses The statuses to find, or null for those of the scope.
    /// @param window   True to keep to the scope's time window.
    /// @return The search for the tasks.
    private static ExpressionOperator expression(final Scope scope,
                                                 final List<TaskStatus> statuses,
                                                 final boolean window) {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.AND);
        if (!scope.pipelines().isEmpty()) {
            final ExpressionOperator.Builder or = ExpressionOperator.builder().op(Op.OR);
            scope.pipelines().forEach(pipeline ->
                    or.addDocRefTerm(ProcessorTaskFields.PIPELINE, Condition.IS_DOC_REF, pipeline));
            builder.addOperator(or.build());
        }
        if (!scope.feeds().isEmpty()) {
            final ExpressionOperator.Builder or = ExpressionOperator.builder().op(Op.OR);
            scope.feeds().forEach(feed -> or.addTextTerm(ProcessorTaskFields.FEED, Condition.EQUALS, feed));
            builder.addOperator(or.build());
        }
        if (!scope.nodes().isEmpty()) {
            final ExpressionOperator.Builder or = ExpressionOperator.builder().op(Op.OR);
            scope.nodes().forEach(node -> or.addTextTerm(ProcessorTaskFields.NODE_NAME, Condition.EQUALS, node));
            builder.addOperator(or.build());
        }
        if (!scope.filters().isEmpty()) {
            final ExpressionOperator.Builder or = ExpressionOperator.builder().op(Op.OR);
            scope.filters().forEach(filter ->
                    or.addIdTerm(ProcessorTaskFields.PROCESSOR_FILTER_ID, Condition.EQUALS, filter));
            builder.addOperator(or.build());
        }
        if (!scope.streams().isEmpty()) {
            final ExpressionOperator.Builder or = ExpressionOperator.builder().op(Op.OR);
            scope.streams().forEach(stream -> or.addIdTerm(ProcessorTaskFields.META_ID, Condition.EQUALS, stream));
            builder.addOperator(or.build());
        }
        final List<TaskStatus> wanted = statuses == null
                ? scope.statuses()
                : statuses;
        if (!wanted.isEmpty()) {
            final ExpressionOperator.Builder or = ExpressionOperator.builder().op(Op.OR);
            wanted.forEach(status -> or.addTextTerm(ProcessorTaskFields.STATUS, Condition.EQUALS, status.name()));
            builder.addOperator(or.build());
        }
        if (window && scope.sinceMs() != null) {
            builder.addLongTerm(ProcessorTaskFields.STATUS_TIME_MS, Condition.GREATER_THAN_OR_EQUAL_TO,
                    scope.sinceMs());
        }
        if (window && scope.untilMs() != null) {
            builder.addLongTerm(ProcessorTaskFields.STATUS_TIME_MS, Condition.LESS_THAN, scope.untilMs());
        }
        return builder.build();
    }

    // --------------------------------------------------------------------------------
    // Reading tasks.
    // --------------------------------------------------------------------------------

    /// Counts the tasks by pipeline, feed and status in the database, and by node too if asked, with a query per
    /// node and the rest counted as on no node yet.
    private Counts countSummaries(final ExpressionOperator expression,
                                  final boolean byNode,
                                  final Scope scope,
                                  final Visibility visibility) {
        final Counts counts = new Counts();
        final List<ProcessorTaskSummary> all = summaries(expression, counts);
        if (!byNode) {
            all.forEach(summary -> counts.add(visibility, summary.getPipeline(), summary.getFeed(), null, null,
                    summary.getStatus(), summary.getCount()));
            return counts;
        }
        final List<String> nodes = nodeNames(scope, counts);
        if (nodes == null) {
            all.forEach(summary -> counts.add(visibility, summary.getPipeline(), summary.getFeed(), null, null,
                    summary.getStatus(), summary.getCount()));
            return counts;
        }
        // What is left of each count once each node's share is taken off is on no node yet.
        final Map<List<Object>, Long> remaining = new LinkedHashMap<>();
        final Map<List<Object>, ProcessorTaskSummary> byKey = new HashMap<>();
        for (final ProcessorTaskSummary summary : all) {
            final List<Object> key = summaryKey(summary);
            remaining.merge(key, summary.getCount(), Long::sum);
            byKey.putIfAbsent(key, summary);
        }
        for (final String node : nodes) {
            ToolDeadline.check();
            final ExpressionOperator nodeExpression = ExpressionOperator.builder().op(Op.AND)
                    .addOperator(expression)
                    .addTextTerm(ProcessorTaskFields.NODE_NAME, Condition.EQUALS, node)
                    .build();
            for (final ProcessorTaskSummary summary : summaries(nodeExpression, counts)) {
                counts.add(visibility, summary.getPipeline(), summary.getFeed(), node, null, summary.getStatus(),
                        summary.getCount());
                remaining.merge(summaryKey(summary), -summary.getCount(), Long::sum);
            }
        }
        if (!counts.capped) {
            remaining.forEach((key, count) -> {
                if (count > 0) {
                    final ProcessorTaskSummary summary = byKey.get(key);
                    counts.add(visibility, summary.getPipeline(), summary.getFeed(), null, null,
                            summary.getStatus(), count);
                }
            });
        }
        return counts;
    }

    private static List<Object> summaryKey(final ProcessorTaskSummary summary) {
        return List.of(NullSafe.getOrElse(summary.getPipeline(), DocRef::getUuid, ""),
                NullSafe.string(summary.getFeed()),
                NullSafe.getOrElse(summary.getStatus(), TaskStatus::name, ""));
    }

    private List<ProcessorTaskSummary> summaries(final ExpressionOperator expression, final Counts counts) {
        final ExpressionCriteria criteria = new ExpressionCriteria(expression);
        // One more than the most, to tell if there are more.
        criteria.setPageRequest(new PageRequest(0, MAX_SUMMARY_ROWS + 1));
        final List<ProcessorTaskSummary> found = NullSafe.list(NullSafe.get(
                processorTaskServiceProvider.get().findSummary(criteria), ResultPage::getValues));
        if (found.size() > MAX_SUMMARY_ROWS) {
            counts.capped = true;
            return found.subList(0, MAX_SUMMARY_ROWS);
        }
        return found;
    }

    /// @return The nodes to count tasks on: those asked for, else all Stroom's nodes; or null if they can't be
    /// found, which the counts note.
    private List<String> nodeNames(final Scope scope, final Counts counts) {
        if (!scope.nodes().isEmpty()) {
            return scope.nodes();
        }
        try {
            return NullSafe.list(nodeServiceProvider.get().findNodeNames(new FindNodeCriteria())).stream()
                    .filter(NullSafe::isNonBlankString)
                    .sorted()
                    .toList();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find the nodes", e);
            counts.nodesUnknown = true;
            return null;
        }
    }

    /// Counts the tasks from their rows, which can say which processor filter and node each is of, up to
    /// [#MAX_SCAN_ROWS] rows.
    private Counts countRows(final ExpressionOperator expression, final Visibility visibility) {
        final Counts counts = new Counts();
        final Tasks tasks = tasks(expression, MAX_SCAN_ROWS, Integer.MAX_VALUE, visibility);
        for (final ProcessorTask task : tasks.visible()) {
            counts.add(visibility, ProcessorFilterAccess.pipelineOf(task.getProcessorFilter()), task.getFeedName(),
                    task.getNodeName(), task.getProcessorFilter().getId(), task.getStatus(), 1);
        }
        counts.capped = tasks.capped();
        return counts;
    }

    /// Reads tasks, newest first, keeping those the user may see.
    ///
    /// @param expression The search.
    /// @param maxRows    The most rows to read.
    /// @param maxVisible The most tasks to keep.
    private Tasks tasks(final ExpressionOperator expression,
                        final int maxRows,
                        final int maxVisible,
                        final Visibility visibility) {
        final ProcessorTaskFinder.Tasks found = ProcessorTaskFinder.find(processorTaskServiceProvider.get(),
                expression, maxRows, maxVisible, visibility::canSee);
        return new Tasks(found.visible(), found.capped(), found.more());
    }

    // --------------------------------------------------------------------------------
    // summary
    // --------------------------------------------------------------------------------

    private String summary(final ToolCall request, final Scope scope, final Visibility visibility) {
        final List<String> groupBy = groupBy(request);
        final ExpressionOperator expression = expression(scope, null, true);
        final Counts counts = groupBy.contains(FILTER)
                ? countRows(expression, visibility)
                : countSummaries(expression, groupBy.contains(NODE), scope, visibility);
        final StringBuilder sb = new StringBuilder();
        sb.append("Processing tasks").append(describe(scope)).append(", counted by ")
                .append(String.join(", ", groupBy)).append(":\n");
        if (counts.counts.isEmpty()) {
            sb.append("None you can see.\n");
            appendCountNotes(sb, counts, groupBy.contains(FILTER));
            return sb.toString().stripTrailing();
        }
        final List<String> dimensions = groupBy.stream().filter(group -> !STATUS.equals(group)).toList();
        final boolean byStatus = groupBy.contains(STATUS);
        final Map<List<String>, Map<TaskStatus, Long>> groups = new LinkedHashMap<>();
        for (final Count count : counts.counts) {
            final List<String> key = dimensions.stream().map(dimension -> label(dimension, count, scope.links()))
                    .toList();
            groups.computeIfAbsent(key, k -> new EnumMap<>(TaskStatus.class))
                    .merge(count.status(), count.count(), Long::sum);
        }
        final List<Map.Entry<List<String>, Map<TaskStatus, Long>>> sorted = new ArrayList<>(groups.entrySet());
        sorted.sort(Comparator.comparingLong((Map.Entry<List<String>, Map<TaskStatus, Long>> entry) ->
                        total(entry.getValue())).reversed()
                .thenComparing(entry -> String.join(", ", entry.getKey())));
        final int shown = Math.min(sorted.size(), MAX_GROUP_LINES * 2);
        for (final Map.Entry<List<String>, Map<TaskStatus, Long>> entry : sorted.subList(0, shown)) {
            sb.append("- ").append(entry.getKey().isEmpty()
                    ? "All"
                    : String.join(", ", entry.getKey())).append(": ");
            if (byStatus) {
                sb.append(byStatus(entry.getValue())).append(" (").append(total(entry.getValue()))
                        .append(" in all)");
            } else {
                sb.append(total(entry.getValue())).append(" task(s)");
            }
            sb.append('\n');
        }
        if (sorted.size() > shown) {
            sb.append("(").append(sorted.size() - shown).append(" more group(s), with fewer tasks; narrow the "
                                                                 + "filters to see them.)\n");
        }
        appendCountNotes(sb, counts, groupBy.contains(FILTER));
        return sb.toString().stripTrailing();
    }

    private static List<String> groupBy(final ToolCall request) {
        final List<String> groupBy = new ArrayList<>();
        for (final String group : stringListArg(request, GROUP_BY_ARG)) {
            final String lower = group.toLowerCase(Locale.ROOT);
            if (!GROUPS.contains(lower)) {
                throw new WorkspaceException("Can't group by '" + group + "'. Group by any of: "
                                             + String.join(", ", GROUPS) + ".");
            }
            if (!groupBy.contains(lower)) {
                groupBy.add(lower);
            }
        }
        return groupBy.isEmpty()
                ? DEFAULT_GROUP_BY
                : groupBy;
    }

    private static String label(final String dimension, final Count count, final StroomLinks links) {
        return switch (dimension) {
            case PIPELINE -> count.pipeline().getType() + " " + links.document(count.pipeline());
            case FEED -> "feed " + NullSafe.nonBlankStringElse(count.feed(), "?");
            case NODE -> count.node() == null
                    ? "no node yet"
                    : "node " + count.node();
            case FILTER -> "filter " + NullSafe.getOrElse(count.filterId(), String::valueOf, "?");
            default -> "";
        };
    }

    private static void appendCountNotes(final StringBuilder sb, final Counts counts, final boolean fromRows) {
        if (counts.capped) {
            sb.append(fromRows
                    ? "Only the newest " + MAX_SCAN_ROWS + " tasks were read to count by filter, so there are more; "
                      + "narrow the filters, e.g. by time.\n"
                    : "Only the first " + MAX_SUMMARY_ROWS + " rows of counts were read, so there are more; narrow "
                      + "the filters.\n");
        }
        if (counts.nodesUnknown) {
            sb.append("Stroom's nodes couldn't be found, so the tasks aren't counted by node.\n");
        }
    }

    // --------------------------------------------------------------------------------
    // list
    // --------------------------------------------------------------------------------

    private String list(final ToolCall request,
                        final ToolCallContext context,
                        final Scope scope,
                        final Visibility visibility) {
        final SessionWorkspace workspace = sessionWorkspace(context);
        final WorkspacePath out = outArg(request);
        final Integer limitArg = intArg(request, LIMIT_ARG);
        final int limit = limitArg == null
                ? DEFAULT_LIST_LIMIT
                : Math.max(1, Math.min(limitArg, MAX_LIST_LIMIT));
        final Tasks tasks = tasks(expression(scope, null, true), MAX_SCAN_ROWS, limit, visibility);
        tasks.visible().forEach(task -> visibility.streams.add(task.getMetaId()));
        final StringBuilder sb = new StringBuilder();
        sb.append("Processing tasks").append(describe(scope)).append(", newest first: ");
        if (tasks.visible().isEmpty()) {
            sb.append("none you can see.");
            if (tasks.capped()) {
                sb.append(" The search stopped after ").append(MAX_SCAN_ROWS).append(" tasks; narrow the filters.");
            }
            return sb.toString();
        }
        sb.append(tasks.visible().size());
        if (tasks.more()) {
            sb.append(" (there are more; give a larger limit or narrow the filters)");
        } else if (tasks.capped()) {
            sb.append(" (the search stopped after ").append(MAX_SCAN_ROWS).append(" tasks, so there may be more; "
                                                                                  + "narrow the filters)");
        }
        sb.append('\n');
        final TaskErrorStreams errorStreams = TaskErrorStreams.find(metaServiceProvider.get(),
                tasks.visible());
        errorStreams.ids().forEach(visibility.errorStreams::add);
        if (errorStreams.lookupFailed()) {
            sb.append("(The tasks' Error streams couldn't be found.)\n");
        }
        if (out != null) {
            final List<List<String>> rows = tasks.visible().stream()
                    .map(task -> row(task, errorStreams.of(task)))
                    .toList();
            ToolTables.writeCsv(workspace, out, TASK_COLUMNS, rows, scope.pipelines().size() == 1
                    ? scope.pipelines().getFirst().getUuid()
                    : null);
            sb.append("Wrote ").append(rows.size()).append(" row(s) to ").append(out)
                    .append("; use the table tool on it to filter, group or sort. The first rows:\n\n")
                    .append(ToolTables.preview(TASK_COLUMNS, rows, PREVIEW_ROWS));
            return sb.toString();
        }
        final List<ProcessorTask> shown = tasks.visible().subList(0, Math.min(tasks.visible().size(),
                MAX_INLINE_TASKS));
        shown.forEach(task -> sb.append("- ").append(describe(task, scope.links())).append("; ")
                .append(errorStreams.describe(task, scope.links()))
                .append('\n'));
        if (tasks.visible().size() > shown.size()) {
            sb.append("(").append(tasks.visible().size() - shown.size()).append(" more not shown; give out to write "
                                                                                 + "them all to a file.)\n");
        }
        return sb.toString().stripTrailing();
    }

    /// @return A task in words, e.g. `Task 12: Failed on node node1, stream 34 of feed F, Pipeline P, filter 5;
    /// created …, started …, ended … (took 2.5s)`.
    private static String describe(final ProcessorTask task, final StroomLinks links) {
        final ProcessorFilter filter = task.getProcessorFilter();
        final StringBuilder sb = new StringBuilder("Task ").append(task.getId()).append(": ")
                .append(NullSafe.getOrElse(task.getStatus(), TaskStatus::getDisplayValue, "Unknown"));
        if (NullSafe.isNonBlankString(task.getNodeName())) {
            sb.append(" on node ").append(task.getNodeName());
        }
        sb.append(", ").append(links.stream(task.getMetaId())).append(" of feed ")
                .append(NullSafe.nonBlankStringElse(task.getFeedName(), "?"));
        final DocRef pipeline = ProcessorFilterAccess.pipelineOf(filter);
        sb.append(", ").append(pipeline.getType()).append(' ').append(links.document(pipeline))
                .append(", filter ").append(filter.getId());
        sb.append("; created ").append(time(task.getCreateTimeMs()));
        if (task.getStartTimeMs() != null) {
            sb.append(", started ").append(time(task.getStartTimeMs()));
        }
        if (task.getEndTimeMs() != null) {
            sb.append(", ended ").append(time(task.getEndTimeMs()));
        }
        final Long duration = duration(task);
        if (duration != null) {
            sb.append(" (took ").append(Duration.ofMillis(duration).toString().substring(2).toLowerCase(Locale.ROOT))
                    .append(')');
        }
        if (task.getStatusTimeMs() != null && !RUNNING.contains(task.getStatus())) {
            sb.append(", status set ").append(time(task.getStatusTimeMs()));
        } else if (task.getStatusTimeMs() != null) {
            sb.append(", status renewed ").append(time(task.getStatusTimeMs()));
        }
        return sb.toString();
    }

    private static List<String> row(final ProcessorTask task, final List<MetaRow> errorStreams) {
        final DocRef pipeline = ProcessorFilterAccess.pipelineOf(task.getProcessorFilter());
        final MetaCounts counts = MetaCounts.of(errorStreams);
        return List.of(String.valueOf(task.getId()),
                NullSafe.getOrElse(task.getStatus(), TaskStatus::getDisplayValue, ""),
                NullSafe.string(task.getNodeName()),
                NullSafe.getOrElse(task.getMetaId(), String::valueOf, ""),
                NullSafe.string(task.getFeedName()),
                NullSafe.string(pipeline.getName()),
                NullSafe.string(pipeline.getUuid()),
                NullSafe.getOrElse(task.getProcessorFilter().getId(), String::valueOf, ""),
                timeOrEmpty(task.getCreateTimeMs()),
                timeOrEmpty(task.getStartTimeMs()),
                timeOrEmpty(task.getEndTimeMs()),
                timeOrEmpty(task.getStatusTimeMs()),
                NullSafe.getOrElse(duration(task), String::valueOf, ""),
                String.join(" ", errorStreams.stream().map(row -> String.valueOf(row.getMeta().getId())).toList()),
                counts.known() ? String.valueOf(counts.fatal()) : "",
                counts.known() ? String.valueOf(counts.error()) : "",
                counts.known() ? String.valueOf(counts.warning()) : "");
    }

    /// @return The ids, separated by commas, for an IN term.
    static String ids(final List<Long> ids) {
        return String.join(",", ids.stream().map(String::valueOf).toList());
    }

    private static Long duration(final ProcessorTask task) {
        return task.getStartTimeMs() != null && task.getEndTimeMs() != null
                ? Math.max(0, task.getEndTimeMs() - task.getStartTimeMs())
                : null;
    }

    // --------------------------------------------------------------------------------
    // failures
    // --------------------------------------------------------------------------------

    private String failures(final ToolCall request,
                            final ToolCallContext context,
                            final Scope scope,
                            final Visibility visibility) {
        final long startNanos = System.nanoTime();
        final String stuckAfterArg = stringArg(request, STUCK_AFTER_ARG);
        final Duration stuckAfter = stuckAfterArg == null
                ? DEFAULT_STUCK_AFTER
                : duration(stuckAfterArg);
        final Integer sampleArg = intArg(request, SAMPLE_ARG);
        final int sample = sampleArg == null
                ? DEFAULT_SAMPLE
                : Math.max(0, Math.min(sampleArg, MAX_SAMPLE));

        final StringBuilder sb = new StringBuilder();
        sb.append("Processing").append(scope.nodes().isEmpty()
                ? " across the cluster"
                : "").append(describe(scope)).append(".\n");
        final Counts overview = appendOverview(sb, scope, visibility);
        appendFailedTasks(sb, scope, visibility);
        appendStuckTasks(sb, scope, stuckAfter, visibility);
        appendErrorStreams(sb, context, scope, overview, sample, startNanos, visibility);
        return sb.toString().stripTrailing();
    }

    // Tasks in the window by status, pipeline, feed and node, with failure rates.
    private Counts appendOverview(final StringBuilder sb, final Scope scope, final Visibility visibility) {
        final Counts counts = countSummaries(expression(scope, List.of(), true), true, scope, visibility);
        sb.append("\n## Tasks whose status changed in the window\n");
        if (counts.counts.isEmpty()) {
            sb.append("None you can see.\n");
            appendCountNotes(sb, counts, false);
            return counts;
        }
        final Map<TaskStatus, Long> byStatus = new EnumMap<>(TaskStatus.class);
        counts.counts.forEach(count -> byStatus.merge(count.status(), count.count(), Long::sum));
        final long failed = byStatus.getOrDefault(TaskStatus.FAILED, 0L);
        sb.append(total(byStatus)).append(" task(s): ").append(byStatus(byStatus)).append(".\n");

        sb.append("\nBy pipeline (most failures first):\n");
        appendRates(sb, rates(counts, count -> count.pipeline().getType() + " "
                                               + scope.links().document(count.pipeline())), failed, false);
        final Map<String, long[]> feedRates = rates(counts, count -> NullSafe.nonBlankStringElse(count.feed(), "?"));
        if (failed > 0) {
            sb.append("\nBy feed (most failures first):\n");
            appendRates(sb, feedRates, failed, false);
        }
        if (!counts.nodesUnknown) {
            sb.append("\nBy node:\n");
            final Map<String, long[]> nodeRates = rates(counts, count -> count.node() == null
                    ? "no node yet"
                    : "node " + count.node());
            appendRates(sb, nodeRates, failed, true);
            final long nodesWithTasks = nodeRates.keySet().stream()
                    .filter(node -> !"no node yet".equals(node))
                    .count();
            nodeRates.entrySet().stream()
                    .filter(entry -> nodesWithTasks > 1 && failed > 0
                                     && entry.getValue()[1] >= failed * NODE_SIGNAL_SHARE)
                    .findFirst()
                    .ifPresent(entry -> sb.append("Most of the failed tasks (").append(percent(entry.getValue()[1],
                            failed)).append(") were on ").append(entry.getKey()).append(": a strong sign the "
                                                                                     + "problem is with that node "
                                                                                     + "rather than the data, so "
                                                                                     + "look at its application "
                                                                                     + "log first.\n"));
        }
        appendCountNotes(sb, counts, false);
        return counts;
    }

    /// @return For each group, the count of tasks and of FAILED tasks.
    private static Map<String, long[]> rates(final Counts counts,
                                             final Function<Count, String> group) {
        final Map<String, long[]> rates = new LinkedHashMap<>();
        for (final Count count : counts.counts) {
            final long[] rate = rates.computeIfAbsent(group.apply(count), k -> new long[2]);
            rate[0] += count.count();
            if (count.status() == TaskStatus.FAILED) {
                rate[1] += count.count();
            }
        }
        return rates;
    }

    private static void appendRates(final StringBuilder sb,
                                    final Map<String, long[]> rates,
                                    final long failed,
                                    final boolean share) {
        final List<Map.Entry<String, long[]>> sorted = new ArrayList<>(rates.entrySet());
        sorted.sort(Comparator.comparingLong((Map.Entry<String, long[]> entry) -> entry.getValue()[1]).reversed()
                .thenComparing(Comparator.comparingLong((Map.Entry<String, long[]> entry) ->
                        entry.getValue()[0]).reversed())
                .thenComparing(Map.Entry::getKey));
        final int shown = Math.min(sorted.size(), MAX_GROUP_LINES);
        for (final Map.Entry<String, long[]> entry : sorted.subList(0, shown)) {
            final long tasks = entry.getValue()[0];
            final long failures = entry.getValue()[1];
            sb.append("- ").append(entry.getKey()).append(": ").append(tasks).append(" task(s), ").append(failures)
                    .append(" Failed (").append(percent(failures, tasks)).append(')');
            if (share && failures > 0) {
                sb.append(", ").append(percent(failures, failed)).append(" of all the failed tasks");
            }
            sb.append('\n');
        }
        if (sorted.size() > shown) {
            sb.append("(").append(sorted.size() - shown).append(" more, with fewer failures.)\n");
        }
    }

    // The FAILED tasks in the window, grouped by pipeline, feed and node, with examples.
    private void appendFailedTasks(final StringBuilder sb, final Scope scope, final Visibility visibility) {
        sb.append("\n## Failed tasks\n");
        final Tasks tasks = tasks(expression(scope, List.of(TaskStatus.FAILED), true), MAX_SCAN_ROWS,
                Integer.MAX_VALUE, visibility);
        if (tasks.visible().isEmpty()) {
            sb.append("None you can see in the window.\n");
            return;
        }
        final Map<String, List<ProcessorTask>> groups = new LinkedHashMap<>();
        for (final ProcessorTask task : tasks.visible()) {
            final DocRef pipeline = ProcessorFilterAccess.pipelineOf(task.getProcessorFilter());
            final String key = pipeline.getType() + " " + scope.links().document(pipeline) + ", feed "
                               + NullSafe.nonBlankStringElse(task.getFeedName(), "?") + ", node "
                               + NullSafe.nonBlankStringElse(task.getNodeName(), "?");
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(task);
            visibility.streams.add(task.getMetaId());
        }
        sb.append(tasks.visible().size()).append(" failed task(s)");
        if (tasks.capped()) {
            sb.append(" (only the newest ").append(MAX_SCAN_ROWS).append(" were read, so there are more)");
        }
        sb.append(". ").append(FAILED_REASON_NOTE).append('\n');
        final List<Map.Entry<String, List<ProcessorTask>>> sorted = new ArrayList<>(groups.entrySet());
        sorted.sort(Comparator.comparingInt((Map.Entry<String, List<ProcessorTask>> entry) ->
                entry.getValue().size()).reversed().thenComparing(Map.Entry::getKey));
        final int shown = Math.min(sorted.size(), MAX_GROUP_LINES);
        final List<ProcessorTask> exampleTasks = new ArrayList<>();
        sorted.subList(0, shown).forEach(entry -> exampleTasks.addAll(entry.getValue().subList(0,
                Math.min(entry.getValue().size(), MAX_EXAMPLE_TASKS))));
        final TaskErrorStreams errorStreams = TaskErrorStreams.find(metaServiceProvider.get(), exampleTasks);
        errorStreams.ids().forEach(visibility.errorStreams::add);
        for (final Map.Entry<String, List<ProcessorTask>> entry : sorted.subList(0, shown)) {
            final List<ProcessorTask> group = entry.getValue();
            final long first = group.stream().mapToLong(ProcessorTasksTool::when).min().orElse(0);
            final long last = group.stream().mapToLong(ProcessorTasksTool::when).max().orElse(0);
            sb.append("- ").append(entry.getKey()).append(": ").append(group.size()).append(" failed, first ")
                    .append(time(first)).append(", last ").append(time(last)).append(". E.g. ");
            final List<String> examples = new ArrayList<>();
            for (final ProcessorTask task : group.subList(0, Math.min(group.size(), MAX_EXAMPLE_TASKS))) {
                final StringBuilder example = new StringBuilder("task ").append(task.getId()).append(" (")
                        .append(scope.links().stream(task.getMetaId()));
                if (task.getStartTimeMs() != null) {
                    example.append(", started ").append(time(task.getStartTimeMs()));
                }
                example.append(", ended ").append(time(when(task))).append("; ")
                        .append(errorStreams.describe(task, scope.links())).append(')');
                examples.add(example.toString());
            }
            sb.append(String.join("; ", examples)).append(".\n");
        }
        if (sorted.size() > shown) {
            sb.append("(").append(sorted.size() - shown).append(" more group(s), with fewer failures; use mode list "
                                                                 + "with statuses ['Failed'] to see them.)\n");
        }
    }

    /// @return When a task ended, or else when its status was last set.
    private static long when(final ProcessorTask task) {
        return Objects.requireNonNullElseGet(task.getEndTimeMs(),
                () -> Objects.requireNonNullElse(task.getStatusTimeMs(), 0L));
    }

    // Tasks that have been Assigned or Processing for longer than they should, by node.
    private void appendStuckTasks(final StringBuilder sb,
                                  final Scope scope,
                                  final Duration stuckAfter,
                                  final Visibility visibility) {
        final String after = stuckAfter.toString().substring(2).toLowerCase(Locale.ROOT);
        sb.append("\n## Possibly stuck tasks\n");
        sb.append("Tasks still Assigned or Processing whose status hasn't changed for over ").append(after)
                .append(" (a node renews a running task's status time as it goes, so these are often on a node "
                        + "that has stopped, or lost the database): ");
        final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                .addOperator(expression(scope, List.copyOf(RUNNING), false))
                .addLongTerm(ProcessorTaskFields.STATUS_TIME_MS, Condition.LESS_THAN,
                        scope.nowMs() - stuckAfter.toMillis())
                .build();
        final Tasks tasks = tasks(expression, MAX_STUCK_ROWS, Integer.MAX_VALUE, visibility);
        if (tasks.visible().isEmpty()) {
            sb.append("none you can see.\n");
            return;
        }
        sb.append(tasks.visible().size()).append(tasks.capped()
                ? " (only the newest " + MAX_STUCK_ROWS + " were read, so there are more)"
                : "").append('\n');
        final Map<String, List<ProcessorTask>> byNode = new LinkedHashMap<>();
        for (final ProcessorTask task : tasks.visible()) {
            byNode.computeIfAbsent(NullSafe.nonBlankStringElse(task.getNodeName(), "?"), k -> new ArrayList<>())
                    .add(task);
            visibility.streams.add(task.getMetaId());
        }
        byNode.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, List<ProcessorTask>> entry) ->
                        entry.getValue().size()).reversed().thenComparing(Map.Entry::getKey))
                .limit(MAX_GROUP_LINES)
                .forEach(entry -> {
                    final List<ProcessorTask> group = entry.getValue();
                    final long oldest = group.stream()
                            .mapToLong(task -> Objects.requireNonNullElse(task.getStatusTimeMs(), 0L))
                            .min()
                            .orElse(0);
                    sb.append("- node ").append(entry.getKey()).append(": ").append(group.size())
                            .append(" task(s), oldest status time ").append(time(oldest)).append(". E.g. ");
                    sb.append(String.join("; ", group.stream()
                            .limit(MAX_EXAMPLE_TASKS)
                            .map(task -> {
                                final DocRef pipeline = ProcessorFilterAccess.pipelineOf(task.getProcessorFilter());
                                return "task " + task.getId() + " (" + scope.links().stream(task.getMetaId())
                                       + ", " + pipeline.getType() + " " + scope.links().document(pipeline) + ", "
                                       + NullSafe.getOrElse(task.getStatus(), TaskStatus::getDisplayValue, "?")
                                       + ")";
                            })
                            .toList())).append('\n');
                });
    }

    // The errors inside processing: the Error streams written in the window by the pipelines asked about, or else
    // by those that had tasks in the window, and the failure modes read from a sample of them.
    private void appendErrorStreams(final StringBuilder sb,
                                    final ToolCallContext context,
                                    final Scope scope,
                                    final Counts overview,
                                    final int sample,
                                    final long startNanos,
                                    final Visibility visibility) {
        sb.append("\n## Errors inside processing that completed\n");
        sb.append("Most problems don't fail a task: the pipeline carries on and writes the errors to an Error "
                  + "stream, a child of the stream it was processing. Error streams created in the window: ");
        final List<DocRef> candidates = errorStreamPipelines(scope, overview);
        final List<DocRef> pipelines = candidates.subList(0, Math.min(candidates.size(), MAX_PIPELINES_QUERIED));
        final ErrorStreams found = findErrorStreams(scope, pipelines);
        final Map<String, String> scopeNodes = scope.nodes().isEmpty()
                ? Map.of()
                : nodesOf(found.streams());
        final List<ErrorStream> streams = found.streams().stream()
                .filter(stream -> scope.nodes().isEmpty()
                                  || scope.nodes().contains(scopeNodes.getOrDefault(taskKey(stream), "")))
                .toList();
        final ErrorCoverage coverage = new ErrorCoverage(pipelines.size(), candidates.size() - pipelines.size(),
                found.cappedPipelines(), found.failedQueries());
        if (streams.isEmpty()) {
            sb.append(pipelines.isEmpty()
                    ? "none looked for, as no pipeline you can see had tasks in the window."
                    : "none you can see.").append('\n');
            appendSearchCoverage(sb, coverage);
            return;
        }
        streams.forEach(stream -> visibility.errorStreams.add(stream.meta().getId()));
        // By pipeline and feed, with the most streams first.
        final Map<String, ErrorGroup> groups = new LinkedHashMap<>();
        for (final ErrorStream stream : streams) {
            groups.computeIfAbsent(stream.pipeline().getUuid() + "/" + stream.meta().getFeedName(),
                    k -> new ErrorGroup(stream.pipeline(), stream.meta().getFeedName())).add(stream);
        }
        final List<ErrorGroup> sorted = new ArrayList<>(groups.values());
        sorted.sort(Comparator.comparingInt((ErrorGroup group) -> group.streams.size()).reversed()
                .thenComparing(group -> NullSafe.string(group.pipeline.getName()) + "/" + group.feed));
        final List<ErrorStream> sampled = spread(sorted, sample);
        // Only the streams sampled have their counts read, as meta values age off and are costly to read.
        final Map<Long, Map<String, String>> attributes = attributesOf(sampled);
        final Map<String, String> nodes = scope.nodes().isEmpty()
                ? nodesOf(sampled)
                : scopeNodes;

        sb.append(streams.size()).append(found.cappedPipelines() > 0
                ? "+"
                : "").append(", by pipeline and feed (newest first; the counts are from the meta of the streams "
                             + "sampled):\n");
        for (final ErrorGroup group : sorted.subList(0, Math.min(sorted.size(), MAX_GROUP_LINES))) {
            sb.append("- ").append(group.pipeline.getType()).append(' ')
                    .append(scope.links().document(group.pipeline))
                    .append(", feed ").append(group.feed).append(": ").append(group.streams.size())
                    .append(" stream(s)");
            final List<Map<String, String>> groupCounts = group.streams.stream()
                    .filter(sampled::contains)
                    .map(stream -> attributes.getOrDefault(stream.meta().getId(), Map.of()))
                    .toList();
            if (!groupCounts.isEmpty()) {
                final MetaCounts counts = MetaCounts.ofAttributes(groupCounts);
                sb.append("; of the ").append(groupCounts.size()).append(" sampled: ");
                if (counts.known()) {
                    sb.append(counts.fatal()).append(" fatal, ").append(counts.error()).append(" error(s), ")
                            .append(counts.warning()).append(" warning(s)");
                }
                if (counts.unknown() > 0) {
                    sb.append(counts.known()
                            ? "; "
                            : "").append("counts unavailable for ").append(counts.unknown())
                            .append(" (their meta values have aged off; their content still holds the markers)");
                }
            }
            sb.append('\n');
        }
        if (sorted.size() > MAX_GROUP_LINES) {
            sb.append("(").append(sorted.size() - MAX_GROUP_LINES).append(" more, with fewer streams.)\n");
        }
        appendFailureModes(sb, context, sampled, streams.size(), sample, nodes, startNanos, visibility);
        appendSearchCoverage(sb, coverage);
    }

    /// @return The pipelines to look for Error streams of: those asked for, else those the user can see that had
    /// tasks in the window, most failures then most tasks first.
    private static List<DocRef> errorStreamPipelines(final Scope scope, final Counts overview) {
        if (!scope.pipelines().isEmpty()) {
            return scope.pipelines();
        }
        final Map<String, DocRef> pipelines = new LinkedHashMap<>();
        final Map<String, long[]> rates = new HashMap<>();
        for (final Count count : overview.counts) {
            pipelines.putIfAbsent(count.pipeline().getUuid(), count.pipeline());
            final long[] rate = rates.computeIfAbsent(count.pipeline().getUuid(), k -> new long[2]);
            rate[0] += count.count();
            if (count.status() == TaskStatus.FAILED) {
                rate[1] += count.count();
            }
        }
        return pipelines.values().stream()
                .sorted(Comparator.comparingLong((DocRef pipeline) -> rates.get(pipeline.getUuid())[1]).reversed()
                        .thenComparing(Comparator.comparingLong((DocRef pipeline) ->
                                rates.get(pipeline.getUuid())[0]).reversed())
                        .thenComparing(pipeline -> NullSafe.string(pipeline.getName())))
                .toList();
    }

    private static void appendSearchCoverage(final StringBuilder sb, final ErrorCoverage coverage) {
        if (coverage.pipelinesLeftOut() > 0) {
            sb.append("Error streams were looked for of the ").append(coverage.pipelinesQueried())
                    .append(" pipeline(s) with the most failures and tasks; ").append(coverage.pipelinesLeftOut())
                    .append(" more with tasks in the window weren't looked at: give pipelines to look at them.\n");
        }
        if (coverage.cappedPipelines() > 0) {
            sb.append("For ").append(coverage.cappedPipelines()).append(" pipeline(s) only the newest ")
                    .append(MAX_ERROR_STREAMS_PER_PIPELINE).append(" Error streams were found, so there are more; "
                                                                   + "narrow the window to see older ones.\n");
        }
        if (coverage.failedQueries() > 0) {
            sb.append("The Error streams of ").append(coverage.failedQueries()).append(" pipeline(s) couldn't be "
                                                                                       + "found.\n");
        }
    }

    /// Finds the Error streams created in the window of each pipeline, with a query per pipeline, which the
    /// database answers from its index of each processor's streams by create time, rather than looking through
    /// every stream created in the window. Only streams of feeds the user may view are found.
    private ErrorStreams findErrorStreams(final Scope scope, final List<DocRef> pipelines) {
        final List<ErrorStream> streams = new ArrayList<>();
        int capped = 0;
        int failed = 0;
        for (final DocRef pipeline : pipelines) {
            ToolDeadline.check();
            final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.AND)
                    .addDocRefTerm(MetaFields.PIPELINE, Condition.IS_DOC_REF, pipeline)
                    .addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.ERROR)
                    .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue());
            if (!scope.feeds().isEmpty()) {
                final ExpressionOperator.Builder or = ExpressionOperator.builder().op(Op.OR);
                scope.feeds().forEach(feed -> or.addTextTerm(MetaFields.FEED, Condition.EQUALS, feed));
                builder.addOperator(or.build());
            }
            if (!scope.filters().isEmpty()) {
                builder.addTerm(MetaFields.META_PROCESSOR_FILTER_ID.getFldName(), Condition.IN,
                        String.join(",", scope.filters().stream().map(String::valueOf).toList()));
            }
            if (!scope.streams().isEmpty()) {
                builder.addTerm(MetaFields.PARENT_ID.getFldName(), Condition.IN, ids(scope.streams()));
            }
            if (scope.sinceMs() != null) {
                builder.addDateTerm(MetaFields.CREATE_TIME, Condition.GREATER_THAN_OR_EQUAL_TO,
                        DateUtil.createNormalDateTimeString(scope.sinceMs()));
            }
            if (scope.untilMs() != null) {
                builder.addDateTerm(MetaFields.CREATE_TIME, Condition.LESS_THAN,
                        DateUtil.createNormalDateTimeString(scope.untilMs()));
            }
            final FindMetaCriteria criteria = new FindMetaCriteria(builder.build());
            // One more than the most, to tell if there are more.
            criteria.setPageRequest(new PageRequest(0, MAX_ERROR_STREAMS_PER_PIPELINE + 1));
            criteria.setSort(MetaFields.ID.getFldName(), true, false);
            try {
                final List<Meta> found = NullSafe.list(NullSafe.get(metaServiceProvider.get().find(criteria),
                        ResultPage::getValues));
                if (found.size() > MAX_ERROR_STREAMS_PER_PIPELINE) {
                    capped++;
                }
                found.stream()
                        .limit(MAX_ERROR_STREAMS_PER_PIPELINE)
                        .filter(Objects::nonNull)
                        .forEach(meta -> streams.add(new ErrorStream(meta, pipeline)));
            } catch (final ToolTimeoutException e) {
                throw e;
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "Unable to find the Error streams of " + pipeline, e);
                failed++;
            }
        }
        return new ErrorStreams(streams, capped, failed);
    }

    private static String taskKey(final ErrorStream stream) {
        return String.valueOf(stream.meta().getProcessorTaskId());
    }

    /// @param streams Error streams.
    /// @return The node that ran the task that wrote each Error stream, by task id, where it can be found, with a
    /// query per [#TASK_LOOKUP_CHUNK] streams.
    private Map<String, String> nodesOf(final List<ErrorStream> streams) {
        final List<Long> taskIds = streams.stream()
                .map(stream -> stream.meta().getProcessorTaskId())
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        final Map<String, String> nodes = new HashMap<>();
        for (int start = 0; start < taskIds.size(); start += TASK_LOOKUP_CHUNK) {
            ToolDeadline.check();
            final List<Long> chunk = taskIds.subList(start, Math.min(taskIds.size(), start + TASK_LOOKUP_CHUNK));
            final ExpressionCriteria criteria = new ExpressionCriteria(ExpressionOperator.builder()
                    .addTerm(ProcessorTaskFields.TASK_ID.getFldName(), Condition.IN, ids(chunk))
                    .build());
            criteria.setPageRequest(new PageRequest(0, chunk.size()));
            try {
                NullSafe.list(NullSafe.get(processorTaskServiceProvider.get().find(criteria), ResultPage::getValues))
                        .stream()
                        .filter(task -> NullSafe.isNonBlankString(task.getNodeName()))
                        .forEach(task -> nodes.put(String.valueOf(task.getId()), task.getNodeName()));
            } catch (final ToolTimeoutException e) {
                throw e;
            } catch (final RuntimeException e) {
                LOGGER.debug(() -> "Unable to find the tasks of the Error streams", e);
            }
        }
        return nodes;
    }

    /// @return The attributes (e.g. the error counts) of the streams, by id, read in one query.
    private Map<Long, Map<String, String>> attributesOf(final List<ErrorStream> streams) {
        final Map<Long, Map<String, String>> attributes = new HashMap<>();
        if (streams.isEmpty()) {
            return attributes;
        }
        final FindMetaCriteria criteria = new FindMetaCriteria(ExpressionOperator.builder()
                .addTerm(MetaFields.ID.getFldName(), Condition.IN,
                        ids(streams.stream().map(stream -> stream.meta().getId()).toList()))
                .build());
        criteria.setPageRequest(new PageRequest(0, streams.size()));
        try {
            NullSafe.list(NullSafe.get(metaServiceProvider.get().findRows(criteria), ResultPage::getValues))
                    .stream()
                    .filter(row -> row.getMeta() != null)
                    .forEach(row -> attributes.put(row.getMeta().getId(), NullSafe.map(row.getAttributes())));
        } catch (final ToolTimeoutException e) {
            throw e;
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read the attributes of the Error streams", e);
        }
        return attributes;
    }

    // Reads the sampled Error streams and groups their markers into failure modes.
    private void appendFailureModes(final StringBuilder sb,
                                    final ToolCallContext context,
                                    final List<ErrorStream> sampled,
                                    final int found,
                                    final int sample,
                                    final Map<String, String> nodes,
                                    final long startNanos,
                                    final Visibility visibility) {
        sb.append("\nFailure modes, from the markers in the Error streams read (most severe first, then the most "
                  + "often met):\n");
        if (!documentAccess.canViewData()) {
            sb.append("Reading Error streams needs the View Data permission, as the data viewer does, so only the "
                      + "counts above are given.\n");
            return;
        }
        final Map<Long, Long> sources = visibleParents(sampled);
        final FailureModes modes = new FailureModes();
        final Coverage coverage = new Coverage();
        final long budgetNanos = timeLimit(context).toNanos() / 2;
        for (final ErrorStream stream : sampled) {
            if (System.nanoTime() - startNanos > budgetNanos) {
                coverage.outOfTime = true;
                break;
            }
            ToolDeadline.check();
            final Meta meta = stream.meta();
            final ReadText text = readErrorStream(meta.getId());
            if (text == null) {
                coverage.unreadable++;
                continue;
            }
            coverage.read++;
            auditLog.streamsViewed(context.session(), List.of(meta.getId()),
                    "Read the markers of Error stream " + meta.getId()
                                                                  + " with the " + NAME + " tool");
            visibility.read.add(meta.getId());
            if (text.truncated()) {
                coverage.truncated++;
            }
            final ErrorStreamMarkers.Parsed parsed = ErrorStreamMarkers.parse(text.text(), text.truncated());
            coverage.unparsed += parsed.unreadable();
            final FailureModes.Source source = new FailureModes.Source(meta.getId(), sources.get(meta.getId()),
                    stream.pipeline(), meta.getFeedName(), nodes.get(taskKey(stream)), meta.getCreateMs());
            for (final StoredError marker : parsed.markers()) {
                modes.add(marker, source);
            }
        }
        final List<FailureModes.Mode> ranked = modes.ranked();
        if (ranked.isEmpty()) {
            sb.append("None found in the streams read.\n");
        }
        int number = 0;
        for (final FailureModes.Mode mode : ranked.subList(0, Math.min(ranked.size(), MAX_MODES))) {
            number++;
            appendMode(sb, number, mode, context.links());
        }
        if (ranked.size() > MAX_MODES) {
            sb.append("(").append(ranked.size() - MAX_MODES).append(" more mode(s), less severe or met less often.)\n");
        }
        sb.append("\nCoverage: read ").append(coverage.read).append(" of the ").append(found)
                .append(" Error stream(s) found (a sample of up to ").append(sample)
                .append(", spread across the pipelines and feeds, newest first, up to ")
                .append(size(MAX_BYTES_PER_STREAM)).append(" of each), and parsed ").append(modes.markers())
                .append(" marker(s) into ").append(ranked.size()).append(" mode(s).");
        if (coverage.truncated > 0) {
            sb.append(' ').append(coverage.truncated).append(" stream(s) were larger than that, so only their "
                                                             + "start was read.");
        }
        if (coverage.unreadable > 0) {
            sb.append(' ').append(coverage.unreadable).append(" stream(s) couldn't be read (they may have been "
                                                              + "deleted).");
        }
        if (coverage.unparsed > 0) {
            sb.append(' ').append(coverage.unparsed).append(" line(s) couldn't be read as markers.");
        }
        if (coverage.outOfTime) {
            sb.append(" Reading stopped early as the call was running out of time.");
        }
        sb.append(" The counts are of the markers read, not of all the errors. Each stream's own markers are in "
                  + "the data viewer: open its Error stream.\n");
    }

    private static void appendMode(final StringBuilder sb,
                                   final int number,
                                   final FailureModes.Mode mode,
                                   final StroomLinks links) {
        sb.append(number).append(". ").append(mode.key().severity().getDisplayValue());
        if (!mode.key().element().isEmpty()) {
            sb.append(" in element '").append(mode.key().element()).append('\'');
        }
        sb.append(": `").append(mode.key().pattern().replace("`", "'")).append("` — ").append(mode.count())
                .append(" marker(s) in ").append(mode.streams().size()).append(" stream(s); ")
                .append(String.join(", ", mode.pipelines().stream()
                        .map(pipeline -> pipeline.getType() + " " + links.document(pipeline))
                        .toList()))
                .append("; feed(s) ").append(String.join(", ", mode.feeds()));
        if (!mode.nodes().isEmpty()) {
            sb.append("; node(s) ").append(String.join(", ", mode.nodes()));
        }
        sb.append("; first ").append(time(mode.firstMs())).append(", last ").append(time(mode.lastMs()))
                .append('\n');
        for (final FailureModes.Example example : mode.examples()) {
            sb.append("   e.g. \"").append(shortenLine(example.message(), 500)).append("\" (")
                    .append(links.stream(example.source().errorStreamId(), "Error stream"));
            if (example.source().sourceStreamId() != null) {
                sb.append(", in ").append(links.stream(example.source().sourceStreamId()));
            }
            if (!example.location().isEmpty()) {
                sb.append(" at ").append(example.location());
            }
            sb.append(")\n");
        }
    }

    /// @return Up to the sample size of the streams, taken from each group in turn (newest first within each), so
    /// one noisy pipeline doesn't take the whole sample.
    static List<ErrorStream> spread(final List<ErrorGroup> groups, final int sample) {
        final List<ErrorStream> spread = new ArrayList<>();
        int index = 0;
        boolean added = true;
        while (spread.size() < sample && added) {
            added = false;
            for (final ErrorGroup group : groups) {
                if (spread.size() >= sample) {
                    break;
                }
                if (index < group.streams.size()) {
                    spread.add(group.streams.get(index));
                    added = true;
                }
            }
            index++;
        }
        return spread;
    }

    /// @return The parent (source) stream of each Error stream, by the Error stream's id, where the user may see
    /// the parent, read in one query.
    private Map<Long, Long> visibleParents(final List<ErrorStream> streams) {
        final Map<Long, Long> parents = new HashMap<>();
        final Set<Long> parentIds = new LinkedHashSet<>();
        streams.forEach(stream -> NullSafe.consume(stream.meta().getParentMetaId(), parentIds::add));
        if (parentIds.isEmpty()) {
            return parents;
        }
        try {
            final FindMetaCriteria criteria = new FindMetaCriteria(ExpressionOperator.builder()
                    .addTerm(MetaFields.ID.getFldName(), Condition.IN, ids(List.copyOf(parentIds)))
                    .build());
            criteria.setPageRequest(new PageRequest(0, parentIds.size()));
            final Set<Long> visible = new HashSet<>();
            NullSafe.list(NullSafe.get(metaServiceProvider.get().find(criteria), ResultPage::getValues))
                    .forEach(meta -> visible.add(meta.getId()));
            streams.forEach(stream -> {
                final Long parent = stream.meta().getParentMetaId();
                if (parent != null && visible.contains(parent)) {
                    parents.put(stream.meta().getId(), parent);
                }
            });
        } catch (final ToolTimeoutException e) {
            throw e;
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to find the parents of the Error streams", e);
        }
        return parents;
    }

    /// @return The start of an Error stream's text, up to [#MAX_BYTES_PER_STREAM], or null if it couldn't be
    /// read.
    private ReadText readErrorStream(final long id) {
        try {
            final StreamReader.RawText text = StreamReader.errorStreamText(storeProvider.get(), id,
                    MAX_BYTES_PER_STREAM);
            return new ReadText(text.text(), text.truncated());
        } catch (final ToolTimeoutException e) {
            throw e;
        } catch (final IOException | RuntimeException e) {
            // E.g. a stream that has been deleted since it was found.
            LOGGER.debug(() -> "Unable to read Error stream " + id + ": " + e.getMessage(), e);
            return null;
        }
    }

    // --------------------------------------------------------------------------------
    // Helpers.
    // --------------------------------------------------------------------------------

    private void logSearch(final ToolCallContext context, final String mode, final Scope scope,
            final Visibility visibility) {
        final Map<String, String> data = new LinkedHashMap<>();
        data.put("Mode", mode);
        data.put("Tasks", String.valueOf(visibility.tasksSeen));
        auditLog.documentsSearched(context.session(), "processing tasks" + (scope.description().isEmpty()
                        ? ""
                        : " where " + scope.description()), List.copyOf(visibility.pipelines.values()), data,
                "Processing tasks read by the " + NAME + " tool (" + mode + ")");
        if (!visibility.streams.isEmpty() || !visibility.errorStreams.isEmpty()) {
            final List<Long> streams = new ArrayList<>(visibility.streams);
            streams.addAll(visibility.errorStreams);
            auditLog.streamsSearched(context.session(),
                    "streams of processing tasks, and Error streams written in processing",
                    streams, Map.of("ErrorStreamsRead", String.valueOf(visibility.read.size())),
                    "Streams found by the " + NAME + " tool (" + mode + ")");
        }
    }

    private static String describe(final Scope scope) {
        return scope.description().isEmpty()
                ? ""
                : " (" + scope.description() + ")";
    }

    private static long total(final Map<TaskStatus, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    private static String byStatus(final Map<TaskStatus, Long> counts) {
        final List<String> parts = new ArrayList<>();
        new EnumMap<>(counts).forEach((status, count) -> parts.add(count + " " + (status == null
                ? "Unknown"
                : status.getDisplayValue())));
        return String.join(", ", parts);
    }

    private static String percent(final long part, final long whole) {
        return whole == 0
                ? "0%"
                : String.format(Locale.ROOT, "%.1f%%", 100.0 * part / whole);
    }

    private static String time(final Long ms) {
        return ms == null || ms <= 0
                ? "?"
                : DateUtil.createNormalDateTimeString(ms);
    }

    private static String timeOrEmpty(final Long ms) {
        return ms == null || ms <= 0
                ? ""
                : DateUtil.createNormalDateTimeString(ms);
    }

    @Override
    public boolean readsUntrustedContent() {
        // Error messages quote the data, and names are given by other users.
        return true;
    }


    // --------------------------------------------------------------------------------


    /// The tasks asked about.
    ///
    /// @param sinceMs     The start of the window (status time), or null for any time.
    /// @param untilMs     The end of the window, or null for now.
    /// @param nowMs       When the call was made.
    /// @param description The filters, in words, for the result and the audit log.
    /// @param links       How to link to Stroom objects in the result, for the caller.
    private record Scope(List<DocRef> pipelines,
                         List<String> feeds,
                         List<String> nodes,
                         List<Integer> filters,
                         List<Long> streams,
                         List<TaskStatus> statuses,
                         Long sinceMs,
                         Long untilMs,
                         long nowMs,
                         String description,
                         StroomLinks links) {

    }


    // --------------------------------------------------------------------------------


    /// A count of tasks the user may see.
    private record Count(DocRef pipeline, String feed, String node, Integer filterId, TaskStatus status,
                         long count) {

    }


    // --------------------------------------------------------------------------------


    /// Tasks read, newest first.
    ///
    /// @param visible Those the user may see.
    /// @param capped  True if reading stopped at the most rows to read, with more to read.
    /// @param more    True if there were more the user may see than were kept.
    private record Tasks(List<ProcessorTask> visible, boolean capped, boolean more) {

    }


    // --------------------------------------------------------------------------------


    /// Error streams found.
    ///
    /// @param streams         Those found, newest first within each pipeline.
    /// @param cappedPipelines How many pipelines had more than [#MAX_ERROR_STREAMS_PER_PIPELINE].
    /// @param failedQueries   How many pipelines' Error streams couldn't be found.
    private record ErrorStreams(List<ErrorStream> streams, int cappedPipelines, int failedQueries) {

    }


    // --------------------------------------------------------------------------------


    /// An Error stream, with the pipeline it was looked for by (which the user can view).
    record ErrorStream(Meta meta, DocRef pipeline) {

    }


    // --------------------------------------------------------------------------------


    /// How the Error streams were looked for.
    ///
    /// @param pipelinesQueried How many pipelines were looked at.
    /// @param pipelinesLeftOut How many more had tasks in the window but weren't looked at.
    /// @param cappedPipelines  How many had more Error streams than were found.
    /// @param failedQueries    How many couldn't be looked at.
    private record ErrorCoverage(int pipelinesQueried, int pipelinesLeftOut, int cappedPipelines,
                                 int failedQueries) {

    }


    // --------------------------------------------------------------------------------


    /// The text read from an Error stream.
    ///
    /// @param text      The text.
    /// @param truncated True if there was more than was read.
    private record ReadText(String text, boolean truncated) {

    }


    // --------------------------------------------------------------------------------


    /// Counts of tasks the user may see.
    private static final class Counts {

        private final List<Count> counts = new ArrayList<>();
        private boolean capped;
        private boolean nodesUnknown;

        private void add(final Visibility visibility,
                         final DocRef pipeline,
                         final String feed,
                         final String node,
                         final Integer filterId,
                         final TaskStatus status,
                         final long count) {
            if (count > 0 && visibility.canSee(pipeline, feed)) {
                counts.add(new Count(pipeline, feed, node, filterId, status, count));
                visibility.tasksSeen += count;
            }
        }
    }


    // --------------------------------------------------------------------------------


    /// The Error streams of one pipeline and feed, newest first.
    static final class ErrorGroup {

        private final DocRef pipeline;
        private final String feed;
        private final List<ErrorStream> streams = new ArrayList<>();

        ErrorGroup(final DocRef pipeline, final String feed) {
            this.pipeline = pipeline;
            this.feed = feed;
        }

        void add(final ErrorStream stream) {
            streams.add(stream);
        }
    }


    // --------------------------------------------------------------------------------


    /// What the user may see, checked once per pipeline and feed, and what was shown, for the audit log.
    private final class Visibility {

        private final Map<String, Boolean> pipelineViews = new HashMap<>();
        private final Map<String, Boolean> feedViews = new HashMap<>();
        private final Map<String, DocRef> pipelines = new LinkedHashMap<>();
        private final Set<Long> streams = new LinkedHashSet<>();
        private final Set<Long> errorStreams = new LinkedHashSet<>();
        private final Set<Long> read = new LinkedHashSet<>();
        private long tasksSeen;

        /// @return True if the user can view the task's pipeline (or analytic rule) and feed.
        private boolean canSee(final ProcessorTask task) {
            final ProcessorFilter filter = task.getProcessorFilter();
            if (filter == null || filter.getPipelineUuid() == null) {
                return false;
            }
            if (canSee(ProcessorFilterAccess.pipelineOf(filter), task.getFeedName())) {
                tasksSeen++;
                return true;
            }
            return false;
        }

        private boolean canSee(final DocRef pipeline, final String feed) {
            if (pipeline == null || pipeline.getUuid() == null) {
                return false;
            }
            final boolean canViewPipeline = pipelineViews.computeIfAbsent(pipeline.getUuid(),
                    uuid -> documentAccess.canView(pipeline));
            final boolean canSee = canViewPipeline
                                   && feedViews.computeIfAbsent(NullSafe.string(feed), feedAccess::canView);
            if (canSee) {
                pipelines.putIfAbsent(pipeline.getUuid(), pipeline);
            }
            return canSee;
        }
    }


    // --------------------------------------------------------------------------------


    /// What was read of the Error streams sampled.
    private static final class Coverage {

        private int read;
        private int truncated;
        private int unreadable;
        private int unparsed;
        private boolean outOfTime;
    }
}
