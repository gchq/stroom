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
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.docref.DocRef;
import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContextFactory;
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.FindTaskProgressRequest;
import stroom.task.shared.TaskId;
import stroom.task.shared.TaskProgress;
import stroom.task.shared.TaskProgressResponse;
import stroom.task.shared.TaskResource;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Supplier;

/// `server_tasks` - the tasks running on the cluster right now, as Monitoring > Server Tasks shows them, for
/// "what is running?", "how many searches are users running?", "who is running the longest searches?", "what is
/// node X busy with?" and "is anything stuck?".
///
/// - `summary` (the default) counts the running tasks by task name, user and node, top-level tasks apart from
///   sub-tasks, and gives the oldest. With `kind` `search` it counts users' searches, each once by its top-level
///   task, with the sub-tasks each has on each node.
/// - `list` lists the tasks, oldest (or newest) first, capped, or all of them to a file with `out`.
/// - `tree` shows one task with its sub-tasks, as the screen's tree does.
///
/// The tasks are read, never terminated, through [TaskResource], as the screen reads them: every enabled node is
/// asked for its tasks, and a node that doesn't answer is said to be missing. The screen needs the Manage Tasks
/// permission, and so does this tool: each node's `TaskManagerImpl.find` checks it again, and gives every user's
/// tasks. Without it Stroom only shows a user the tasks of their own browser session (User Tasks), which the
/// agent isn't in, so the tool says so and gives nothing. Every check fails closed. Each call is logged as a
/// Search of the Task Manager.
class ServerTasksTool extends AbstractWorkspaceTool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ServerTasksTool.class);

    static final String NAME = "server_tasks";
    static final String MODE_ARG = "mode";
    static final String NAME_ARG = "name";
    static final String USER_ARG = "user";
    static final String NODE_ARG = "node";
    static final String MIN_AGE_ARG = "min_age";
    static final String TOP_LEVEL_ONLY_ARG = "top_level_only";
    static final String KIND_ARG = "kind";
    static final String ORDER_ARG = "order";
    static final String LIMIT_ARG = "limit";
    static final String ID_ARG = "id";

    static final String SUMMARY = "summary";
    static final String LIST = "list";
    static final String TREE = "tree";
    static final List<String> MODES = List.of(SUMMARY, LIST, TREE);

    static final String ANY = "any";
    static final String SEARCH = "search";
    static final List<String> KINDS = List.of(ANY, SEARCH);

    static final String OLDEST = "oldest";
    static final String NEWEST = "newest";
    static final List<String> ORDERS = List.of(OLDEST, NEWEST);

    static final int DEFAULT_LIST_LIMIT = 50;
    static final int MAX_LIST_LIMIT = 500;
    /// The most lines in each group of counts in a summary.
    static final int MAX_GROUP_LINES = 15;
    /// The most tasks (or searches) given as the oldest in a summary.
    static final int MAX_OLDEST = 10;
    /// The most lines of a tree.
    static final int MAX_TREE_LINES = 200;
    /// The most characters of a task's info shown in a result.
    static final int MAX_INFO_CHARS = 200;
    /// The most characters of a task's info written to a file.
    static final int MAX_FILE_INFO_CHARS = 1_000;
    static final int PREVIEW_ROWS = 10;
    /// Tasks running for longer than this are counted as long-running in a summary.
    static final Duration LONG_RUNNING = Duration.ofHours(1);
    /// The longest the tool waits for the nodes to answer.
    static final Duration NODE_WAIT = Duration.ofSeconds(15);
    static final String DEFAULT_OUT = "/outputs/server_tasks.csv";

    static final String NEEDS_MANAGE_TASKS = "Monitoring > Server Tasks needs the Manage Tasks permission, which "
                                             + "you don't have, so this tool can't show the tasks running on the "
                                             + "cluster. Without it Stroom shows you only the tasks of your own "
                                             + "browser session (User Tasks), which the agent can't see.";
    static final List<String> TASK_COLUMNS = List.of("Id", "Parent Id", "Name", "User", "Node", "Thread",
            "Submit Time", "Age Ms", "Age", "Sub-tasks", "Info");

    /// The Task Manager, as the searchable data source of server tasks names it, for the audit log.
    static final DocRef TASK_MANAGER = new DocRef("TaskManager", "TaskManager", "Task Manager");

    /// The names of the top-level tasks that run one search each: Lucene index, analytic store and event searches
    /// (`FederatedSearchExecutor`), Elasticsearch, Solr and SQL Statistics searches. Searches of Plan B state
    /// stores, floor maps and Stroom's own data sources (`SearchableSearchProvider`) are named
    /// `<data source> Search` or `Search '<query key>'`.
    static final Set<String> SEARCH_TASK_NAMES = Set.of(
            "FederatedSearchTask",
            "Elasticsearch Cluster Search",
            "SolrSearchTask",
            "Sql Statistic Search");
    /// Top-level tasks named like searches that aren't one: a poll for a search's results.
    static final Set<String> NOT_SEARCH_TASK_NAMES = Set.of("Dashboard Search");
    /// The names (or starts of names) of the sub-tasks searches run on each node, for searches whose top-level
    /// task wasn't seen.
    static final List<String> SEARCH_SUB_TASK_NAMES = List.of(
            "Search node: ",
            "Cluster Search",
            "Analytics Node Search",
            "Search Index Shard",
            "Index Searcher",
            "Search Index",
            "Search Elasticsearch Index",
            "Elasticsearch Search Scroll Slice",
            "Extraction Task");
    /// The info of a search that has finished and is held for the UI to read its results.
    static final String STAYING_ALIVE = "staying alive for UI requests";
    /// The start of the name of the tasks this tool runs to read each node's tasks, left out of its results.
    static final String READ_TASK_NAME = "Ask Stroom AI: reading the tasks of node ";

    private final SecurityContext securityContext;
    private final Provider<TaskResource> taskResourceProvider;
    private final Provider<NodeService> nodeServiceProvider;
    private final TaskContextFactory taskContextFactory;
    private final ExecutorProvider executorProvider;
    private final AiAuditLog auditLog;

    @Inject
    ServerTasksTool(final ChatWorkspace workspace,
                    final SecurityContext securityContext,
                    final Provider<TaskResource> taskResourceProvider,
                    final Provider<NodeService> nodeServiceProvider,
                    final TaskContextFactory taskContextFactory,
                    final ExecutorProvider executorProvider,
                    final AiAuditLog auditLog) {
        super(workspace);
        this.securityContext = securityContext;
        this.taskResourceProvider = taskResourceProvider;
        this.nodeServiceProvider = nodeServiceProvider;
        this.taskContextFactory = taskContextFactory;
        this.executorProvider = executorProvider;
        this.auditLog = auditLog;
    }

    @Override
    public ToolSpecification specification() {
        return ToolSpecification.builder()
                .name(NAME)
                .description("The live view of Monitoring > Server Tasks: the tasks running on every node of the "
                             + "cluster right now, for 'what is running?', 'how many searches are users "
                             + "running?', 'who is running the longest searches?', 'what is node X busy with?' "
                             + "or 'is anything stuck?'. Not for stream processing (use processor_tasks). mode "
                             + "'summary' (the default) counts the tasks by name, user and node (top-level "
                             + "tasks apart from sub-tasks) and gives the oldest; with kind 'search' it counts "
                             + "users' searches, each once (not each node's sub-tasks), by user, type and node, "
                             + "and gives the longest running. mode 'list' lists tasks (id, name, info, user, "
                             + "node, thread, age, parent, sub-tasks), oldest first; give out for a file. mode "
                             + "'tree' shows one task (id) with its sub-tasks. All modes take the same filters. "
                             + "Each result is a snapshot, and says which nodes didn't answer. Read only: it "
                             + "never stops a task. Needs the Manage Tasks permission, as the screen does.")
                .parameters(JsonObjectSchema.builder()
                        .addEnumProperty(MODE_ARG, MODES, "summary (default), list or tree.")
                        .addEnumProperty(KIND_ARG, KINDS, "search for users' searches only, each counted once "
                                                          + "by its top-level task; any (default) for all "
                                                          + "tasks.")
                        .addStringProperty(NAME_ARG, "Tasks whose name contains this, ignoring case.")
                        .addStringProperty(USER_ARG, "Tasks run by users whose name contains this, ignoring "
                                                     + "case.")
                        .addStringProperty(NODE_ARG, "Tasks on this node. For kind search, searches with a task "
                                                     + "on it.")
                        .addStringProperty(MIN_AGE_ARG, "Tasks running for at least this long, e.g. 10m, 2h, "
                                                        + "PT30M.")
                        .addBooleanProperty(TOP_LEVEL_ONLY_ARG, "True for top-level tasks only, not sub-tasks.")
                        .addEnumProperty(ORDER_ARG, ORDERS, "For list: oldest first (default) or newest first.")
                        .addIntegerProperty(LIMIT_ARG, "For list: the most tasks (default " + DEFAULT_LIST_LIMIT
                                                       + ", at most " + MAX_LIST_LIMIT + ").")
                        .addStringProperty(OUT_ARG, "For list: a file in /outputs/ to write all the matching "
                                                    + "tasks to, .csv for a table (e.g. " + DEFAULT_OUT
                                                    + ") or .txt.")
                        .addStringProperty(ID_ARG, "For tree: the task's id, as list gives it.")
                        .build())
                .build();
    }

    @Override
    String run(final ToolExecutionRequest request, final int chatId) {
        if (!canManageTasks()) {
            return NEEDS_MANAGE_TASKS;
        }
        final String mode = choice(request, MODE_ARG, MODES, SUMMARY);
        final Filters filters = filters(request);
        final String id = stringArg(request, ID_ARG);
        if (TREE.equals(mode) && id == null) {
            throw new WorkspaceException("Give the id of the task to show as " + ID_ARG + ".");
        }
        final boolean newest = NEWEST.equals(choice(request, ORDER_ARG, ORDERS, OLDEST));
        final WorkspacePath out = LIST.equals(mode)
                ? outArg(request)
                : null;
        final Snapshot snapshot = snapshot();
        final StringBuilder sb = new StringBuilder();
        appendHeader(sb, snapshot, filters);
        final int shown = switch (mode) {
            case LIST -> list(sb, request, chatId, out, newest, snapshot, filters);
            case TREE -> tree(sb, snapshot, filters, id);
            default -> summary(sb, snapshot, filters);
        };
        logSearch(chatId, mode, filters, id, shown);
        return sb.toString().stripTrailing();
    }

    /// @return True if the user has the Manage Tasks permission; false if not, or if it can't be checked.
    private boolean canManageTasks() {
        try {
            return securityContext.hasAppPermission(AppPermission.MANAGE_TASKS_PERMISSION);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to check the Manage Tasks permission: {}", e.getMessage()),
                    e);
            return false;
        }
    }

    private static String choice(final ToolExecutionRequest request,
                                 final String name,
                                 final List<String> choices,
                                 final String other) {
        final Object value = argsMap(request).get(name);
        if (value == null || String.valueOf(value).isBlank()) {
            return other;
        }
        final String choice = String.valueOf(value).strip().toLowerCase(Locale.ROOT);
        if (!choices.contains(choice)) {
            throw new WorkspaceException("The " + name + " must be one of: " + String.join(", ", choices) + ".");
        }
        return choice;
    }

    /// @return The tasks asked about, from the arguments.
    /// @throws WorkspaceException for an argument that can't be used.
    private Filters filters(final ToolExecutionRequest request) {
        final List<String> description = new ArrayList<>();
        final String kind = choice(request, KIND_ARG, KINDS, ANY);
        final boolean searches = SEARCH.equals(kind);
        if (searches) {
            description.add("users' searches");
        }
        final String name = stringArg(request, NAME_ARG);
        if (name != null) {
            description.add("name contains '" + name + "'");
        }
        final String user = stringArg(request, USER_ARG);
        if (user != null) {
            description.add("user contains '" + user + "'");
        }
        final String node = stringArg(request, NODE_ARG);
        if (node != null) {
            description.add((searches
                    ? "with a task on node "
                    : "on node ") + node);
        }
        final String minAgeArg = stringArg(request, MIN_AGE_ARG);
        Duration minAge = null;
        if (minAgeArg != null) {
            try {
                minAge = ProcessorTasksTool.duration(minAgeArg);
            } catch (final WorkspaceException e) {
                throw new WorkspaceException("'" + minAgeArg + "' isn't a time " + MIN_AGE_ARG + " can be: give "
                                             + "an ISO 8601 duration (e.g. PT30M) or a number of seconds, "
                                             + "minutes, hours or days (e.g. 90s, 30m, 2h, 1d).");
            }
            description.add("running for at least " + duration(minAge.toMillis()));
        }
        final boolean topLevelOnly = booleanArg(request, TOP_LEVEL_ONLY_ARG, false);
        if (topLevelOnly && !searches) {
            description.add("top-level tasks only");
        }
        return new Filters(searches, name, user, node, minAge, topLevelOnly, String.join(", ", description));
    }

    // --------------------------------------------------------------------------------
    // The snapshot.
    // --------------------------------------------------------------------------------

    /// Asks every enabled node for its tasks, as the screen does, waiting at most [#NODE_WAIT] for them.
    ///
    /// @return What the nodes that answered are running.
    private Snapshot snapshot() {
        final List<String> enabled;
        final List<String> disabled = new ArrayList<>();
        try {
            final NodeService nodeService = nodeServiceProvider.get();
            enabled = NullSafe.list(nodeService.findNodeNames(FindNodeCriteria.allEnabled())).stream()
                    .sorted()
                    .toList();
            NullSafe.list(nodeService.findNodeNames(new FindNodeCriteria())).stream()
                    .filter(nodeName -> !enabled.contains(nodeName))
                    .sorted()
                    .forEach(disabled::add);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to find the nodes: {}", e.getMessage()), e);
            throw new WorkspaceException("The cluster's nodes couldn't be found: " + e.getMessage());
        }
        final long timeMs = System.currentTimeMillis();
        final Set<String> ownTaskIds = ConcurrentHashMap.newKeySet();
        final Executor executor = executorProvider.get();
        final Map<String, CompletableFuture<TaskProgressResponse>> futures = new LinkedHashMap<>();
        for (final String nodeName : enabled) {
            // The task is created here, so it runs as the user.
            final Supplier<TaskProgressResponse> supplier = taskContextFactory.contextResult(
                    READ_TASK_NAME + "'" + nodeName + "'",
                    taskContext -> {
                        NullSafe.consume(taskContext.getTaskId(), TaskId::getId, ownTaskIds::add);
                        return taskResourceProvider.get().find(nodeName,
                                new FindTaskProgressRequest(new FindTaskProgressCriteria()));
                    });
            futures.put(nodeName, CompletableFuture.supplyAsync(supplier, executor));
        }

        final long deadlineNanos = System.nanoTime() + nodeWait().toNanos();
        final Map<String, TaskProgress> tasks = new LinkedHashMap<>();
        final List<String> answered = new ArrayList<>();
        final Map<String, String> notAnswered = new LinkedHashMap<>();
        futures.forEach((nodeName, future) -> {
            final TaskProgressResponse response = await(nodeName, future, deadlineNanos, notAnswered);
            if (response == null) {
                return;
            }
            final List<String> errors = NullSafe.list(response.getErrors()).stream()
                    .filter(Objects::nonNull)
                    .toList();
            if (!errors.isEmpty()) {
                notAnswered.put(nodeName, String.join("; ", errors));
                return;
            }
            answered.add(nodeName);
            for (final TaskProgress task : NullSafe.list(response.getValues())) {
                if (task != null && task.getId() != null && !ownTaskIds.contains(task.getId().getId())) {
                    if (task.getNodeName() == null) {
                        task.setNodeName(nodeName);
                    }
                    tasks.putIfAbsent(task.getId().getId(), task);
                }
            }
        });
        return new Snapshot(timeMs, new TaskTree(tasks), answered, notAnswered, disabled);
    }

    private Duration nodeWait() {
        final Duration toolLimit = timeLimit();
        final Duration twoThirds = toolLimit.multipliedBy(2).dividedBy(3);
        return twoThirds.compareTo(NODE_WAIT) < 0
                ? twoThirds
                : NODE_WAIT;
    }

    /// @return The node's answer, or null if it didn't give one in time (said in notAnswered).
    private static TaskProgressResponse await(final String nodeName,
                                              final CompletableFuture<TaskProgressResponse> future,
                                              final long deadlineNanos,
                                              final Map<String, String> notAnswered) {
        try {
            final long remaining = Math.max(0, deadlineNanos - System.nanoTime());
            final TaskProgressResponse response = future.get(remaining, TimeUnit.NANOSECONDS);
            if (response == null) {
                notAnswered.put(nodeName, "no answer");
            }
            return response;
        } catch (final TimeoutException e) {
            future.cancel(true);
            notAnswered.put(nodeName, "no answer in time");
        } catch (final ExecutionException e) {
            final Throwable cause = Objects.requireNonNullElse(e.getCause(), e);
            LOGGER.debug(() -> LogUtil.message("Unable to read the tasks of node {}: {}", nodeName,
                    cause.getMessage()), cause);
            notAnswered.put(nodeName, NullSafe.nonBlankStringElse(cause.getMessage(),
                    cause.getClass().getSimpleName()));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            notAnswered.put(nodeName, "interrupted");
        }
        return null;
    }

    private static void appendHeader(final StringBuilder sb, final Snapshot snapshot, final Filters filters) {
        sb.append("Server tasks (Monitoring > Server Tasks), a snapshot as of ")
                .append(DateUtil.createNormalDateTimeString(snapshot.timeMs()))
                .append(". Tasks start and finish all the time, so call again for a fresh one.\n");
        sb.append("Nodes that answered: ").append(snapshot.answered().isEmpty()
                ? "none"
                : String.join(", ", snapshot.answered())).append('.');
        if (!snapshot.notAnswered().isEmpty()) {
            final List<String> parts = new ArrayList<>();
            snapshot.notAnswered().forEach((nodeName, reason) -> parts.add(nodeName + " ("
                                                                           + shorten(reason, MAX_INFO_CHARS)
                                                                           + ")"));
            sb.append(" Didn't answer: ").append(String.join(", ", parts))
                    .append(", so their tasks are missing.");
        }
        if (!snapshot.disabled().isEmpty()) {
            sb.append(" Disabled nodes, not asked: ").append(String.join(", ", snapshot.disabled())).append('.');
        }
        sb.append('\n');
        if (!filters.description().isEmpty()) {
            sb.append("Filters: ").append(filters.description()).append(".\n");
        }
    }

    // --------------------------------------------------------------------------------
    // summary
    // --------------------------------------------------------------------------------

    private int summary(final StringBuilder sb, final Snapshot snapshot, final Filters filters) {
        return filters.searches()
                ? searchSummary(sb, snapshot, filters)
                : taskSummary(sb, snapshot, filters);
    }

    private int taskSummary(final StringBuilder sb, final Snapshot snapshot, final Filters filters) {
        final TaskTree tree = snapshot.tree();
        final List<TaskProgress> matched = tree.tasks().stream()
                .filter(task -> filters.matches(task, snapshot.timeMs()))
                .toList();
        final long topLevel = matched.stream().filter(ServerTasksTool::isTopLevel).count();
        sb.append('\n').append(matched.size()).append(" task(s) running: ").append(topLevel)
                .append(" top-level, ").append(matched.size() - topLevel).append(" sub-task(s).\n");
        if (matched.isEmpty()) {
            return 0;
        }
        appendGroups(sb, "By task name", matched, task -> NullSafe.nonBlankStringElse(task.getTaskName(), "?"));
        appendGroups(sb, "By user", matched, ServerTasksTool::user);
        appendGroups(sb, "By node", matched, ServerTasksTool::node);
        final long longRunning = matched.stream()
                .filter(task -> age(task, snapshot.timeMs()) >= LONG_RUNNING.toMillis())
                .count();
        sb.append("\nRunning for over ").append(duration(LONG_RUNNING.toMillis()))
                .append(": ").append(longRunning).append(" task(s).\n");
        sb.append("\nOldest:\n");
        matched.stream()
                .sorted(oldestFirst(snapshot.timeMs()))
                .limit(MAX_OLDEST)
                .forEach(task -> sb.append("- ").append(describe(task, tree, snapshot.timeMs())).append('\n'));
        return matched.size();
    }

    private static void appendGroups(final StringBuilder sb,
                                     final String heading,
                                     final List<TaskProgress> tasks,
                                     final Function<TaskProgress, String> key) {
        final Map<String, long[]> counts = new HashMap<>();
        for (final TaskProgress task : tasks) {
            final long[] count = counts.computeIfAbsent(key.apply(task), k -> new long[2]);
            count[0]++;
            if (isTopLevel(task)) {
                count[1]++;
            }
        }
        sb.append('\n').append(heading).append(" (tasks, of which top-level):\n");
        final List<Map.Entry<String, long[]>> sorted = counts.entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, long[]> entry) -> entry.getValue()[0])
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .toList();
        sorted.stream()
                .limit(MAX_GROUP_LINES)
                .forEach(entry -> sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()[0])
                        .append(" (").append(entry.getValue()[1]).append(" top-level)\n"));
        if (sorted.size() > MAX_GROUP_LINES) {
            sb.append("(").append(sorted.size() - MAX_GROUP_LINES).append(" more not shown; filter to see "
                                                                          + "them.)\n");
        }
    }

    private int searchSummary(final StringBuilder sb, final Snapshot snapshot, final Filters filters) {
        final TaskTree tree = snapshot.tree();
        final List<TaskProgress> searches = searches(snapshot, filters);
        final long subTasks = searches.stream().mapToLong(search -> tree.descendants(search).size()).sum();
        sb.append('\n').append(searches.size()).append(" search(es) running, each counted once by its top-level "
                                                       + "task; their ").append(subTasks)
                .append(" sub-task(s) on the nodes aren't counted as searches.\n");
        appendOrphanedSearches(sb, snapshot, filters);
        if (searches.isEmpty()) {
            return 0;
        }
        final long finished = searches.stream().filter(ServerTasksTool::isFinishedSearch).count();
        if (finished > 0) {
            sb.append("Of these, ").append(finished).append(" have finished searching and are holding their "
                                                            + "results for the UI to read (info says '")
                    .append(STAYING_ALIVE).append("'); ").append(searches.size() - finished)
                    .append(" are still searching.\n");
        }
        appendGroups(sb, "By user", searches, ServerTasksTool::user);
        appendGroups(sb, "By type", searches, ServerTasksTool::searchType);
        appendGroups(sb, "By the node running the top-level task", searches, ServerTasksTool::node);
        final Map<String, Long> subTasksByNode = new LinkedHashMap<>();
        searches.forEach(search -> tree.descendants(search).forEach(task ->
                subTasksByNode.merge(node(task), 1L, Long::sum)));
        if (!subTasksByNode.isEmpty()) {
            sb.append("\nTheir sub-tasks by node: ").append(counts(subTasksByNode)).append(".\n");
        }
        sb.append("\nLongest running:\n");
        searches.stream()
                .sorted(oldestFirst(snapshot.timeMs()))
                .limit(MAX_OLDEST)
                .forEach(search -> sb.append("- ").append(describeSearch(search, tree, snapshot.timeMs()))
                        .append('\n'));
        return searches.size();
    }

    /// Notes the searches seen only through their sub-tasks, e.g. because the node running their top-level task
    /// didn't answer.
    private static void appendOrphanedSearches(final StringBuilder sb,
                                               final Snapshot snapshot,
                                               final Filters filters) {
        final Map<String, List<TaskProgress>> orphans = orphanedSearches(snapshot, filters);
        if (orphans.isEmpty()) {
            return;
        }
        final Map<String, Long> byNode = new LinkedHashMap<>();
        orphans.values().forEach(tasks -> tasks.forEach(task -> byNode.merge(node(task), 1L, Long::sum)));
        sb.append("Another ").append(orphans.size()).append(" search(es) were seen only through their "
                                                            + "sub-tasks (").append(counts(byNode))
                .append("), as their top-level task wasn't in the snapshot (its node didn't answer, or it has "
                        + "just finished).\n");
    }

    // --------------------------------------------------------------------------------
    // list
    // --------------------------------------------------------------------------------

    private int list(final StringBuilder sb,
                     final ToolExecutionRequest request,
                     final int chatId,
                     final WorkspacePath out,
                     final boolean newest,
                     final Snapshot snapshot,
                     final Filters filters) {
        final TaskTree tree = snapshot.tree();
        final Integer limitArg = intArg(request, LIMIT_ARG);
        final int limit = limitArg == null
                ? DEFAULT_LIST_LIMIT
                : Math.max(1, Math.min(limitArg, MAX_LIST_LIMIT));
        final Comparator<TaskProgress> order = newest
                ? oldestFirst(snapshot.timeMs()).reversed()
                : oldestFirst(snapshot.timeMs());
        final List<TaskProgress> matched = (filters.searches()
                ? searches(snapshot, filters).stream()
                : tree.tasks().stream().filter(task -> filters.matches(task, snapshot.timeMs())))
                .sorted(order)
                .toList();
        sb.append('\n').append(filters.searches()
                        ? "Searches (each by its top-level task)"
                        : "Tasks").append(", ").append(newest
                        ? "newest"
                        : "oldest").append(" first: ").append(matched.size()).append('\n');
        if (filters.searches()) {
            appendOrphanedSearches(sb, snapshot, filters);
        }
        if (matched.isEmpty()) {
            return 0;
        }
        if (out != null) {
            if (out.path().toLowerCase(Locale.ROOT).endsWith(".csv")) {
                final List<List<String>> rows = matched.stream()
                        .map(task -> row(task, tree, snapshot.timeMs()))
                        .toList();
                ToolTables.writeCsv(workspace, chatId, out, TASK_COLUMNS, rows, null);
                sb.append("Wrote ").append(rows.size()).append(" row(s) to ").append(out)
                        .append("; use the table tool on it to filter, group or sort. The first rows:\n\n")
                        .append(ToolTables.preview(TASK_COLUMNS, rows, PREVIEW_ROWS));
            } else {
                final StringBuilder text = new StringBuilder();
                matched.forEach(task -> text.append("- ").append(describe(task, tree, snapshot.timeMs(),
                        MAX_FILE_INFO_CHARS)).append('\n'));
                workspace.write(chatId, out, text.toString(), null);
                sb.append("Wrote ").append(matched.size()).append(" task(s) to ").append(out).append(".\n");
            }
            return matched.size();
        }
        final List<TaskProgress> shown = matched.subList(0, Math.min(limit, matched.size()));
        shown.forEach(task -> sb.append("- ").append(filters.searches()
                ? describeSearch(task, tree, snapshot.timeMs())
                : describe(task, tree, snapshot.timeMs())).append('\n'));
        if (matched.size() > shown.size()) {
            sb.append("(").append(matched.size() - shown.size()).append(" more not shown; give a larger limit, "
                                                                        + "narrow the filters, or give out to "
                                                                        + "write them all to a file.)\n");
        }
        return shown.size();
    }

    private static List<String> row(final TaskProgress task, final TaskTree tree, final long nowMs) {
        final long age = age(task, nowMs);
        return List.of(
                task.getId().getId(),
                NullSafe.getOrElse(task.getId().getParentId(), TaskId::getId, ""),
                NullSafe.string(task.getTaskName()),
                user(task),
                node(task),
                NullSafe.string(task.getThreadName()),
                task.getSubmitTimeMs() > 0
                        ? DateUtil.createNormalDateTimeString(task.getSubmitTimeMs())
                        : "",
                String.valueOf(age),
                duration(age),
                String.valueOf(tree.children(task).size()),
                shorten(NullSafe.string(task.getTaskInfo()), MAX_FILE_INFO_CHARS));
    }

    // --------------------------------------------------------------------------------
    // tree
    // --------------------------------------------------------------------------------

    private int tree(final StringBuilder sb, final Snapshot snapshot, final Filters filters, final String id) {
        final TaskTree tree = snapshot.tree();
        final TaskProgress task = tree.get(id);
        if (task == null) {
            sb.append("\nNo task with the id ").append(id).append(" is in the snapshot: it may have finished");
            if (!snapshot.notAnswered().isEmpty()) {
                sb.append(", or be on a node that didn't answer");
            }
            sb.append(". Use list to find running tasks' ids.");
            return 0;
        }
        final List<String> ancestors = new ArrayList<>();
        TaskId parentId = task.getId().getParentId();
        while (parentId != null) {
            final TaskProgress parent = tree.get(parentId.getId());
            ancestors.addFirst(parent == null
                    ? "<<dead>> " + parentId.getId() + " (not in the snapshot)"
                    : NullSafe.string(parent.getTaskName()) + " " + parentId.getId() + " on " + node(parent));
            parentId = parentId.getParentId();
        }
        sb.append('\n').append(ancestors.isEmpty()
                ? "A top-level task."
                : "Under: " + String.join(" > ", ancestors) + ".").append('\n');
        if (!filters.description().isEmpty()) {
            sb.append("(The filters aren't used for a tree.)\n");
        }
        final List<String> lines = new ArrayList<>();
        appendTree(lines, task, tree, 0, snapshot.timeMs());
        lines.stream().limit(MAX_TREE_LINES).forEach(line -> sb.append(line).append('\n'));
        if (lines.size() > MAX_TREE_LINES) {
            sb.append("(").append(lines.size() - MAX_TREE_LINES).append(" more sub-tasks not shown; give a "
                                                                        + "sub-task's id to see its part of "
                                                                        + "the tree.)\n");
        }
        return Math.min(lines.size(), MAX_TREE_LINES);
    }

    private static void appendTree(final List<String> lines,
                                   final TaskProgress task,
                                   final TaskTree tree,
                                   final int depth,
                                   final long nowMs) {
        lines.add("  ".repeat(depth) + "- " + describe(task, tree, nowMs));
        tree.children(task).stream()
                .sorted(oldestFirst(nowMs))
                .forEach(child -> appendTree(lines, child, tree, depth + 1, nowMs));
    }

    // --------------------------------------------------------------------------------
    // Searches.
    // --------------------------------------------------------------------------------

    /// @return True if the task is the top-level task of one search.
    static boolean isSearch(final TaskProgress task) {
        if (!isTopLevel(task)) {
            return false;
        }
        final String name = NullSafe.string(task.getTaskName());
        if (SEARCH_TASK_NAMES.contains(name)) {
            return true;
        }
        if (NOT_SEARCH_TASK_NAMES.contains(name)) {
            return false;
        }
        return name.endsWith(" Search") || name.startsWith("Search '");
    }

    /// @return True if the task is one a search runs on a node.
    static boolean isSearchSubTask(final TaskProgress task) {
        final String name = NullSafe.string(task.getTaskName());
        return !isTopLevel(task) && SEARCH_SUB_TASK_NAMES.stream().anyMatch(name::startsWith);
    }

    private static boolean isFinishedSearch(final TaskProgress task) {
        return NullSafe.string(task.getTaskInfo()).endsWith(STAYING_ALIVE);
    }

    /// @return The searches that match the filters, by their top-level tasks.
    private static List<TaskProgress> searches(final Snapshot snapshot, final Filters filters) {
        final TaskTree tree = snapshot.tree();
        return tree.tasks().stream()
                .filter(ServerTasksTool::isSearch)
                .filter(search -> filters.matchesSearch(search, tree.descendants(search), snapshot.timeMs()))
                .toList();
    }

    /// @return The sub-tasks of searches whose top-level task isn't in the snapshot, by the id of their top task.
    private static Map<String, List<TaskProgress>> orphanedSearches(final Snapshot snapshot, final Filters filters) {
        final TaskTree tree = snapshot.tree();
        final Map<String, List<TaskProgress>> orphans = new LinkedHashMap<>();
        for (final TaskProgress task : tree.tasks()) {
            if (isSearchSubTask(task)) {
                final TaskId topId = topId(task.getId());
                if (tree.get(topId.getId()) == null) {
                    orphans.computeIfAbsent(topId.getId(), k -> new ArrayList<>()).add(task);
                }
            }
        }
        // Only the filters that don't need the top-level task can be used.
        orphans.values().removeIf(tasks -> filters.node() != null && tasks.stream()
                .noneMatch(task -> filters.node().equalsIgnoreCase(node(task))));
        orphans.values().removeIf(tasks -> filters.user() != null && tasks.stream()
                .noneMatch(task -> contains(user(task), filters.user())));
        return orphans;
    }

    private static TaskId topId(final TaskId taskId) {
        TaskId top = taskId;
        while (top.getParentId() != null) {
            top = top.getParentId();
        }
        return top;
    }

    private static String searchType(final TaskProgress task) {
        final String name = NullSafe.string(task.getTaskName());
        return switch (name) {
            case "FederatedSearchTask" -> "Lucene index, analytic store or event search (FederatedSearchTask)";
            case "Elasticsearch Cluster Search" -> "Elasticsearch (Elasticsearch Cluster Search)";
            case "SolrSearchTask" -> "Solr (SolrSearchTask)";
            case "Sql Statistic Search" -> "SQL Statistics (Sql Statistic Search)";
            default -> name;
        };
    }

    private static String describeSearch(final TaskProgress search, final TaskTree tree, final long nowMs) {
        final Map<String, Long> byNode = new LinkedHashMap<>();
        tree.descendants(search).forEach(task -> byNode.merge(node(task), 1L, Long::sum));
        return "Search " + search.getId().getId() + " (" + search.getTaskName() + ") by " + user(search) + " on "
               + node(search) + ", running " + duration(age(search, nowMs))
               + (byNode.isEmpty()
                ? ", no sub-tasks"
                : ", sub-tasks: " + counts(byNode))
               + (isFinishedSearch(search)
                ? " (finished, holding results for the UI)"
                : "")
               + info(search, MAX_INFO_CHARS);
    }

    // --------------------------------------------------------------------------------
    // Helpers.
    // --------------------------------------------------------------------------------

    /// @return A task in words, e.g. `123_4 'FederatedSearchTask' by admin on node1 (thread stroom-1), running
    /// 2.5m, 3 sub-task(s); info: …`.
    private static String describe(final TaskProgress task, final TaskTree tree, final long nowMs) {
        return describe(task, tree, nowMs, MAX_INFO_CHARS);
    }

    private static String describe(final TaskProgress task,
                                   final TaskTree tree,
                                   final long nowMs,
                                   final int maxInfoChars) {
        final StringBuilder sb = new StringBuilder(task.getId().getId())
                .append(" '").append(NullSafe.string(task.getTaskName())).append("' by ").append(user(task))
                .append(" on ").append(node(task));
        if (!NullSafe.isBlankString(task.getThreadName())) {
            sb.append(" (thread ").append(task.getThreadName()).append(')');
        }
        sb.append(", running ").append(duration(age(task, nowMs)));
        final TaskId parentId = task.getId().getParentId();
        if (parentId != null) {
            sb.append(", parent ").append(parentId.getId());
        }
        final int children = tree.children(task).size();
        if (children > 0) {
            sb.append(", ").append(children).append(" sub-task(s)");
        }
        return sb.append(info(task, maxInfoChars)).toString();
    }

    private static String info(final TaskProgress task, final int maxChars) {
        final String info = NullSafe.trim(task.getTaskInfo());
        return info.isEmpty()
                ? ""
                : "; info: " + shorten(info.replaceAll("\\s+", " "), maxChars);
    }

    /// @return A duration for people, in its two largest units, e.g. `2d 3h`, `1h 5m`, `3m 20s` or `45s`.
    static String duration(final long ms) {
        final Duration duration = Duration.ofMillis(Math.max(0, ms));
        final List<String> parts = new ArrayList<>();
        addPart(parts, duration.toDays(), "d");
        addPart(parts, duration.toHoursPart(), "h");
        addPart(parts, duration.toMinutesPart(), "m");
        addPart(parts, duration.toSecondsPart(), "s");
        final List<String> shown = new ArrayList<>(parts.subList(0, Math.min(2, parts.size())));
        if (shown.size() > 1 && shown.getLast().startsWith("0")) {
            shown.removeLast();
        }
        return shown.isEmpty()
                ? ms + "ms"
                : String.join(" ", shown);
    }

    private static void addPart(final List<String> parts, final long amount, final String unit) {
        // Only the units from the largest that isn't zero.
        if (amount > 0 || !parts.isEmpty()) {
            parts.add(amount + unit);
        }
    }

    static String shorten(final String text, final int maxChars) {
        return text.length() > maxChars
                ? text.substring(0, maxChars) + "… (" + (text.length() - maxChars) + " more characters)"
                : text;
    }

    private static String counts(final Map<String, Long> counts) {
        final List<String> parts = new ArrayList<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .forEach(entry -> parts.add(entry.getKey() + " " + entry.getValue()));
        return String.join(", ", parts);
    }

    private static boolean isTopLevel(final TaskProgress task) {
        return task.getId().getParentId() == null;
    }

    /// @return How long the task has been running: as its node worked it out, as the screen shows it, or from
    /// the snapshot's time if the node didn't say.
    private static long age(final TaskProgress task, final long nowMs) {
        if (task.getTimeNowMs() > 0 && task.getSubmitTimeMs() > 0) {
            return Math.max(0, task.getAgeMs());
        }
        return task.getSubmitTimeMs() > 0
                ? Math.max(0, nowMs - task.getSubmitTimeMs())
                : 0;
    }

    private static Comparator<TaskProgress> oldestFirst(final long nowMs) {
        return Comparator.comparingLong((TaskProgress task) -> age(task, nowMs)).reversed()
                .thenComparing(task -> task.getId().getId());
    }

    private static String user(final TaskProgress task) {
        return NullSafe.getOrElse(task.getUserRef(), UserRef::toDisplayString, "?");
    }

    private static String node(final TaskProgress task) {
        return NullSafe.nonBlankStringElse(task.getNodeName(), "?");
    }

    private static boolean contains(final String value, final String part) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(part.toLowerCase(Locale.ROOT));
    }

    private static boolean userMatches(final TaskProgress task, final String user) {
        final UserRef userRef = task.getUserRef();
        return userRef != null && (contains(userRef.getDisplayName(), user)
                                   || contains(userRef.getSubjectId(), user)
                                   || contains(userRef.getFullName(), user));
    }

    private void logSearch(final int chatId,
                           final String mode,
                           final Filters filters,
                           final String id,
                           final int shown) {
        final String query = "server tasks" + (filters.description().isEmpty()
                ? ""
                : " where " + filters.description()) + (TREE.equals(mode)
                ? ", tree of task " + id
                : "");
        auditLog.searched(chatId, TASK_MANAGER, query, (long) shown,
                "Server tasks read by the " + NAME + " tool (" + mode + ")");
    }

    @Override
    public boolean readsUntrustedContent() {
        // Task info can quote users' queries and data.
        return true;
    }


    // --------------------------------------------------------------------------------


    /// The tasks asked about.
    ///
    /// @param searches     True for users' searches only.
    /// @param name         Part of the task name, or null.
    /// @param user         Part of the user's name, or null.
    /// @param node         The node, or null.
    /// @param minAge       How long the tasks must have been running, or null.
    /// @param topLevelOnly True for top-level tasks only.
    /// @param description  The filters, in words, for the result and the audit log.
    private record Filters(boolean searches,
                           String name,
                           String user,
                           String node,
                           Duration minAge,
                           boolean topLevelOnly,
                           String description) {

        private boolean matches(final TaskProgress task, final long nowMs) {
            return (name == null || contains(task.getTaskName(), name))
                   && (user == null || userMatches(task, user))
                   && (node == null || node.equalsIgnoreCase(task.getNodeName()))
                   && (minAge == null || age(task, nowMs) >= minAge.toMillis())
                   && (!topLevelOnly || isTopLevel(task));
        }

        /// @return True if the search, with its sub-tasks, matches: by its top-level task, except for the node,
        /// which may be that of any of its tasks.
        private boolean matchesSearch(final TaskProgress search,
                                      final List<TaskProgress> subTasks,
                                      final long nowMs) {
            return (name == null || contains(search.getTaskName(), name))
                   && (user == null || userMatches(search, user))
                   && (node == null || node.equalsIgnoreCase(search.getNodeName())
                       || subTasks.stream().anyMatch(task -> node.equalsIgnoreCase(task.getNodeName())))
                   && (minAge == null || age(search, nowMs) >= minAge.toMillis());
        }
    }


    // --------------------------------------------------------------------------------


    /// What the cluster was running.
    ///
    /// @param timeMs      When it was taken.
    /// @param tree        The tasks of the nodes that answered.
    /// @param answered    The nodes that answered.
    /// @param notAnswered The nodes that didn't, with why.
    /// @param disabled    The disabled nodes, which weren't asked.
    private record Snapshot(long timeMs,
                            TaskTree tree,
                            List<String> answered,
                            Map<String, String> notAnswered,
                            List<String> disabled) {

    }


    // --------------------------------------------------------------------------------


    /// The tasks, by id, with each one's sub-tasks, across the nodes, as the screen's tree links them.
    private static final class TaskTree {

        private final Map<String, TaskProgress> tasks;
        private final Map<String, List<TaskProgress>> children = new HashMap<>();
        private final Map<String, List<TaskProgress>> descendants = new HashMap<>();

        private TaskTree(final Map<String, TaskProgress> tasks) {
            this.tasks = tasks;
            tasks.values().forEach(task -> NullSafe.consume(task.getId().getParentId(), TaskId::getId,
                    parentId -> children.computeIfAbsent(parentId, k -> new ArrayList<>()).add(task)));
        }

        private List<TaskProgress> tasks() {
            return List.copyOf(tasks.values());
        }

        private TaskProgress get(final String id) {
            return tasks.get(id);
        }

        private List<TaskProgress> children(final TaskProgress task) {
            return children.getOrDefault(task.getId().getId(), List.of());
        }

        /// @return All the task's sub-tasks, at any depth, found by their ids' chains of parents, so those whose
        /// own parent has gone (or is on a node that didn't answer) are still found.
        private List<TaskProgress> descendants(final TaskProgress task) {
            return descendants.computeIfAbsent(task.getId().getId(), id -> tasks.values().stream()
                    .filter(other -> other != task && other.getId().isOrHasAncestor(task.getId()))
                    .toList());
        }
    }
}
