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

import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeService;
import stroom.security.shared.AppPermission;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.shared.FindTaskProgressRequest;
import stroom.task.shared.TaskId;
import stroom.task.shared.TaskProgress;
import stroom.task.shared.TaskProgressResponse;
import stroom.task.shared.TaskResource;
import stroom.util.shared.UserRef;
import stroom.util.time.StroomDuration;

import event.logging.SearchEventAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class TestServerTasksTool {

    private static final long NOW = System.currentTimeMillis();
    private static final long MINUTE = Duration.ofMinutes(1).toMillis();
    private static final long HOUR = Duration.ofHours(1).toMillis();

    @TempDir
    Path tempDir;

    private ToolTestFixture fixture;
    private final TaskResource taskResource = Mockito.mock(TaskResource.class);
    private final NodeService nodeService = Mockito.mock(NodeService.class);
    private final ExecutorProvider executorProvider = Mockito.mock(ExecutorProvider.class);
    /// The enabled nodes.
    private final List<String> enabledNodes = new ArrayList<>(List.of("node1", "node2"));
    /// The disabled nodes.
    private final List<String> disabledNodes = new ArrayList<>();
    /// The tasks each node is running.
    private final Map<String, List<TaskProgress>> tasks = new HashMap<>();
    /// The errors each node that can't be reached answers with.
    private final Map<String, String> errors = new HashMap<>();
    /// The nodes whose call throws.
    private final Set<String> throwing = new HashSet<>();
    /// The nodes that never answer.
    private final Set<String> silent = new HashSet<>();

    @BeforeEach
    void setUp() {
        fixture = new ToolTestFixture(tempDir);
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.MANAGE_TASKS_PERMISSION))
                .thenReturn(true);
        Mockito.when(nodeService.findNodeNames(Mockito.any())).thenAnswer(invocation -> {
            final FindNodeCriteria criteria = invocation.getArgument(0);
            final List<String> nodes = new ArrayList<>(enabledNodes);
            if (!Boolean.TRUE.equals(criteria.isEnabled())) {
                nodes.addAll(disabledNodes);
            }
            return nodes;
        });
        Mockito.when(taskResource.find(Mockito.anyString(), Mockito.any(FindTaskProgressRequest.class)))
                .thenAnswer(invocation -> {
                    final String nodeName = invocation.getArgument(0);
                    final FindTaskProgressRequest request = invocation.getArgument(1);
                    // As the screen asks: every task, not just those of a session.
                    assertThat(request.getCriteria().getSessionId()).isNull();
                    if (silent.contains(nodeName)) {
                        Thread.sleep(2_000);
                    }
                    if (throwing.contains(nodeName)) {
                        throw new RuntimeException("Boom on " + nodeName);
                    }
                    if (errors.containsKey(nodeName)) {
                        return new TaskProgressResponse(List.of(), List.of(errors.get(nodeName)), null);
                    }
                    return new TaskProgressResponse(tasks.getOrDefault(nodeName, List.of()), List.of(), null);
                });
        // Runs each node's call on its own thread, as the nodes are asked at the same time.
        final Executor executor = runnable -> Thread.ofVirtual().start(runnable);
        Mockito.when(executorProvider.get()).thenReturn(executor);
    }

    @Test
    void refusesWithoutTheManageTasksPermission() {
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.MANAGE_TASKS_PERMISSION))
                .thenReturn(false);
        task("node1", "1", null, "Data Processor", "alice", 10 * MINUTE, "");

        assertThat(run("{}")).isEqualTo(ServerTasksTool.NEEDS_MANAGE_TASKS);
        assertThat(run("{\"mode\":\"list\"}")).isEqualTo(ServerTasksTool.NEEDS_MANAGE_TASKS);
        assertThat(ServerTasksTool.NEEDS_MANAGE_TASKS).contains("only the tasks of your own browser session");
        Mockito.verifyNoInteractions(taskResource, nodeService);
        assertThat(fixture.logged(SearchEventAction.class)).isEmpty();
    }

    @Test
    void refusesWhenThePermissionCheckFails() {
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.MANAGE_TASKS_PERMISSION))
                .thenThrow(new IllegalStateException("No user"));

        assertThat(run("{}")).isEqualTo(ServerTasksTool.NEEDS_MANAGE_TASKS);
        Mockito.verifyNoInteractions(taskResource, nodeService);
    }

    @Test
    void summaryCountsEveryUsersTasksByNameUserAndNode() {
        final TaskProgress processor = task("node1", "1", null, "Data Processor", "alice", 10 * MINUTE, "");
        task("node1", "2", processor, "Pipeline Step", "alice", 9 * MINUTE, "");
        task("node2", "3", null, "Data Processor", "bob", 5 * MINUTE, "");
        task("node2", "4", null, "Index Shard Manager", "admin", 2 * HOUR, "Flushing");

        final String result = run("{}");

        assertThat(result)
                .contains("Server tasks (Monitoring > Server Tasks), a snapshot as of ")
                .contains("Nodes that answered: node1, node2.")
                .doesNotContain("Didn't answer")
                .contains("4 task(s) running: 3 top-level, 1 sub-task(s).")
                .contains("By task name (tasks, of which top-level):\n- Data Processor: 2 (2 top-level)\n"
                          + "- Index Shard Manager: 1 (1 top-level)\n- Pipeline Step: 1 (0 top-level)")
                .contains("By user (tasks, of which top-level):\n- alice: 2 (1 top-level)\n"
                          + "- admin: 1 (1 top-level)\n- bob: 1 (1 top-level)")
                .contains("By node (tasks, of which top-level):\n- node1: 2 (1 top-level)\n"
                          + "- node2: 2 (2 top-level)")
                .contains("Running for over 1h: 1 task(s).");
        // The oldest first.
        final String oldest = result.substring(result.indexOf("Oldest:"));
        assertThat(oldest).startsWith("Oldest:\n- 4 'Index Shard Manager' by admin on node2 (thread thread-4), "
                                      + "running 2h; info: Flushing\n- 1 'Data Processor' by alice on node1");
        assertThat(oldest.indexOf("- 1 'Data")).isLessThan(oldest.indexOf("- 2 'Pipeline"));
        assertThat(oldest).contains("- 1 'Data Processor' by alice on node1 (thread thread-1), running 10m, "
                                    + "1 sub-task(s)");
        assertThat(oldest).contains("- 2 'Pipeline Step' by alice on node1 (thread thread-2), running 9m, "
                                    + "parent 1");
    }

    @Test
    void summaryFiltersByNameUserNodeAgeAndTopLevel() {
        final TaskProgress processor = task("node1", "1", null, "Data Processor", "alice", 10 * MINUTE, "");
        task("node1", "2", processor, "Pipeline Step", "alice", 9 * MINUTE, "");
        task("node2", "3", null, "Data Processor", "bob", 5 * MINUTE, "");
        task("node2", "4", null, "Index Shard Manager", "admin", 2 * HOUR, "");

        assertThat(run("{\"name\":\"processor\"}")).contains("Filters: name contains 'processor'.")
                .contains("2 task(s) running: 2 top-level, 0 sub-task(s).");
        assertThat(run("{\"user\":\"ALI\"}")).contains("2 task(s) running: 1 top-level, 1 sub-task(s).");
        assertThat(run("{\"node\":\"node2\"}")).contains("Filters: on node node2.")
                .contains("2 task(s) running: 2 top-level");
        assertThat(run("{\"min_age\":\"1h\"}")).contains("Filters: running for at least 1h.")
                .contains("1 task(s) running: 1 top-level")
                .contains("- 4 'Index Shard Manager'");
        assertThat(run("{\"min_age\":\"PT9M30S\"}")).contains("2 task(s) running");
        assertThat(run("{\"top_level_only\":true}")).contains("3 task(s) running: 3 top-level, 0 sub-task(s).");
        assertThat(run("{\"user\":\"nobody\"}")).contains("0 task(s) running").doesNotContain("Oldest");
    }

    @Test
    void searchesAreCountedOnceDespiteTheirSubTasksOnEachNode() {
        // A Lucene search by alice, run from node1, with sub-tasks on both nodes.
        final TaskProgress search = task("node1", "s1", null, "FederatedSearchTask", "alice", 3 * MINUTE,
                "Search 'abc'");
        final TaskProgress node1 = task("node1", "s1a", search, "Search node: node1", "alice", 3 * MINUTE, "");
        final TaskProgress node2 = task("node1", "s1b", search, "Search node: node2", "alice", 3 * MINUTE, "");
        task("node1", "s1c", node1, "Cluster Search", "alice", 3 * MINUTE, "");
        final TaskProgress cluster = task("node2", "s1d", node2, "Cluster Search", "alice", 3 * MINUTE, "");
        task("node2", "s1e", cluster, "Search Index Shard", "alice", MINUTE, "");
        task("node2", "s1f", cluster, "Search Index Shard", "alice", MINUTE, "");
        // An Elasticsearch search by bob, and one by alice that has finished and is held for the UI.
        task("node2", "s2", null, "Elasticsearch Cluster Search", "bob", 20 * MINUTE, "Search 'def'");
        task("node1", "s3", null, "FederatedSearchTask", "alice", HOUR + MINUTE,
                "Search 'ghi' - staying alive for UI requests");
        // Not searches: a poll for results, and processing.
        task("node1", "p1", null, "Dashboard Search", "alice", 0, "Polling for new search results");
        task("node2", "p2", null, "Data Processor", "processor", MINUTE, "");

        final String result = run("{\"kind\":\"search\"}");

        assertThat(result)
                .contains("Filters: users' searches.")
                .contains("3 search(es) running, each counted once by its top-level task; their 6 sub-task(s) "
                          + "on the nodes aren't counted as searches.")
                .contains("Of these, 1 have finished searching and are holding their results for the UI to "
                          + "read")
                .contains("By user (tasks, of which top-level):\n- alice: 2 (2 top-level)\n- bob: 1")
                .contains("- Lucene index, analytic store or event search (FederatedSearchTask): 2")
                .contains("- Elasticsearch (Elasticsearch Cluster Search): 1")
                .contains("By the node running the top-level task (tasks, of which top-level):\n"
                          + "- node1: 2 (2 top-level)\n- node2: 1 (1 top-level)")
                .contains("Their sub-tasks by node: node1 3, node2 3.")
                .doesNotContain("Dashboard Search")
                .doesNotContain("Data Processor");
        final String longest = result.substring(result.indexOf("Longest running:"));
        assertThat(longest).startsWith("Longest running:\n- Search s3 (FederatedSearchTask) by alice on node1, "
                                       + "running 1h 1m, no sub-tasks (finished, holding results for the UI)");
        assertThat(longest).contains("- Search s1 (FederatedSearchTask) by alice on node1, running 3m, "
                                     + "sub-tasks: node1 3, node2 3; info: Search 'abc'");

        // A node filter finds the searches with a task on it.
        assertThat(run("{\"kind\":\"search\",\"node\":\"node2\"}")).contains("2 search(es) running");
        assertThat(run("{\"kind\":\"search\",\"user\":\"bob\"}")).contains("1 search(es) running");
        assertThat(run("{\"kind\":\"search\",\"mode\":\"list\"}"))
                .contains("Searches (each by its top-level task), oldest first: 3")
                .contains("- Search s2 (Elasticsearch Cluster Search) by bob on node2, running 20m");
    }

    @Test
    void otherSearchTaskNamesAreRecognised() {
        task("node1", "a", null, "SolrSearchTask", "alice", MINUTE, "");
        task("node1", "b", null, "Sql Statistic Search", "alice", MINUTE, "");
        task("node1", "c", null, "My State Store Search", "alice", MINUTE, "");
        task("node1", "d", null, "Search 'key'", "alice", MINUTE, "");
        final TaskProgress parent = task("node1", "e", null, "Ask Stroom AI", "alice", MINUTE, "");
        // A sub-task named like a search isn't one: only top-level tasks are.
        task("node1", "f", parent, "Event Search", "alice", MINUTE, "");

        assertThat(run("{\"kind\":\"search\"}")).contains("4 search(es) running");
        assertThat(ServerTasksTool.isSearch(new TaskProgress(new TaskId("x", null), "Dashboard Search", null,
                null, null, "node1", 0L, 0L, null, null))).isFalse();
    }

    @Test
    void searchesSeenOnlyThroughTheirSubTasksAreNoted() {
        // node1 runs the search's top-level task, but doesn't answer.
        errors.put("node1", "Connection refused");
        final TaskId searchId = new TaskId("s1", null);
        final TaskId nodeTaskId = new TaskId("s1b", searchId);
        final TaskProgress cluster = add("node2", new TaskId("s1d", nodeTaskId), "Cluster Search", "alice",
                MINUTE, "");
        task("node2", "s1e", cluster, "Search Index Shard", "alice", MINUTE, "");

        final String result = run("{\"kind\":\"search\"}");

        assertThat(result)
                .contains("Didn't answer: node1 (Connection refused), so their tasks are missing.")
                .contains("0 search(es) running")
                .contains("Another 1 search(es) were seen only through their sub-tasks (node2 2), as their "
                          + "top-level task wasn't in the snapshot");
    }

    @Test
    void nodesThatDontAnswerAreReported() {
        enabledNodes.add("node3");
        enabledNodes.add("node4");
        disabledNodes.add("node5");
        errors.put("node2", "Connection refused");
        throwing.add("node3");
        silent.add("node4");
        fixture.setLimits(new AiWorkspaceConfig(null, null, null, null, null, StroomDuration.ofMillis(300), null,
                null, null, null, null, null, null));
        task("node1", "1", null, "Data Processor", "alice", MINUTE, "");

        final String result = run("{}");

        assertThat(result)
                .contains("Nodes that answered: node1.")
                .contains("Didn't answer: node2 (Connection refused), node3 (")
                .contains("Boom on node3")
                .contains("node4 (no answer in time), so their tasks are missing.")
                .contains("Disabled nodes, not asked: node5.")
                .contains("1 task(s) running");
    }

    @Test
    void listIsCappedAndCanBeWrittenToAFile() {
        for (int i = 1; i <= 60; i++) {
            task(i % 2 == 0
                    ? "node1"
                    : "node2", "t" + i, null, "Task " + i, "alice", i * MINUTE, "info " + i);
        }

        final String result = run("{\"mode\":\"list\"}");
        assertThat(result)
                .contains("Tasks, oldest first: 60\n- t60 'Task 60' by alice on node1")
                .contains("(10 more not shown; give a larger limit");
        assertThat(result.lines().filter(line -> line.startsWith("- t")).count()).isEqualTo(50);

        assertThat(run("{\"mode\":\"list\",\"order\":\"newest\",\"limit\":2}"))
                .contains("Tasks, newest first: 60\n- t1 'Task 1'")
                .contains("- t2 'Task 2'")
                .doesNotContain("- t3 ")
                .contains("(58 more not shown");
        assertThat(run("{\"mode\":\"list\",\"limit\":100000}").lines()
                .filter(line -> line.startsWith("- t")).count()).isEqualTo(60);

        final String csv = run("{\"mode\":\"list\",\"out\":\"/outputs/tasks.csv\"}");
        assertThat(csv).contains("Wrote 60 row(s) to /outputs/tasks.csv")
                .contains("| Id | Parent Id | Name | User | Node | Thread |");
        assertThat(fixture.written.get("/outputs/tasks.csv"))
                .startsWith("Id,Parent Id,Name,User,Node,Thread,Submit Time,Age Ms,Age,Sub-tasks,Info")
                .contains("t60,,Task 60,alice,node1,thread-t60,")
                .contains(",info 1\r\n");
        assertThat(fixture.written.get("/outputs/tasks.csv").lines().count()).isEqualTo(61);

        assertThat(run("{\"mode\":\"list\",\"out\":\"/outputs/tasks.txt\"}"))
                .contains("Wrote 60 task(s) to /outputs/tasks.txt.");
        assertThat(fixture.written.get("/outputs/tasks.txt").lines().count()).isEqualTo(60);
    }

    @Test
    void treeShowsATaskWithItsSubTasks() {
        final TaskProgress search = task("node1", "s1", null, "FederatedSearchTask", "alice", 3 * MINUTE,
                "Search 'abc'");
        final TaskProgress node2 = task("node1", "s1b", search, "Search node: node2", "alice", 3 * MINUTE, "");
        final TaskProgress cluster = task("node2", "s1d", node2, "Cluster Search", "alice", 2 * MINUTE, "");
        task("node2", "s1e", cluster, "Search Index Shard", "alice", MINUTE, "shard 7");
        task("node1", "other", null, "Data Processor", "bob", MINUTE, "");

        final String result = run("{\"mode\":\"tree\",\"id\":\"s1\"}");
        assertThat(result)
                .contains("A top-level task.\n"
                          + "- s1 'FederatedSearchTask' by alice on node1 (thread thread-s1), running 3m, "
                          + "1 sub-task(s); info: Search 'abc'\n"
                          + "  - s1b 'Search node: node2' by alice on node1 (thread thread-s1b), running 3m, "
                          + "parent s1, 1 sub-task(s)\n"
                          + "    - s1d 'Cluster Search' by alice on node2 (thread thread-s1d), running 2m, "
                          + "parent s1b, 1 sub-task(s)\n"
                          + "      - s1e 'Search Index Shard' by alice on node2 (thread thread-s1e), running "
                          + "1m, parent s1d; info: shard 7")
                .doesNotContain("Data Processor");

        assertThat(run("{\"mode\":\"tree\",\"id\":\"s1d\"}"))
                .contains("Under: FederatedSearchTask s1 on node1 > Search node: node2 s1b on node1.");
        assertThat(run("{\"mode\":\"tree\",\"id\":\"gone\"}"))
                .contains("No task with the id gone is in the snapshot: it may have finished.");
        assertThat(run("{\"mode\":\"tree\"}")).isEqualTo("Error: Give the id of the task to show as id.");
    }

    @Test
    void longTaskInfoIsShortened() {
        final String info = "select * from \"Secret Index\" where " + "x".repeat(500);
        task("node1", "1", null, "FederatedSearchTask", "alice", MINUTE, info);

        final String result = run("{\"mode\":\"list\"}");

        assertThat(result)
                .contains("info: " + info.substring(0, ServerTasksTool.MAX_INFO_CHARS) + "… ("
                          + (info.length() - ServerTasksTool.MAX_INFO_CHARS) + " more characters)")
                .doesNotContain(info);
    }

    @Test
    void eachCallIsLoggedAsASearchOfTheTaskManager() {
        task("node1", "1", null, "Data Processor", "alice", MINUTE, "");
        task("node1", "2", null, "Data Processor", "bob", MINUTE, "");

        run("{\"user\":\"alice\"}");
        run("{\"mode\":\"tree\",\"id\":\"2\"}");

        final List<SearchEventAction> searches = fixture.logged(SearchEventAction.class);
        assertThat(searches).hasSize(2);
        assertThat(searches.getFirst().getQuery().getRaw()).isEqualTo("server tasks where user contains 'alice'");
        assertThat(searches.getFirst().getTotalResults()).isEqualTo(1);
        assertThat(searches.getFirst().getDataSources().getDataSource().getFirst()).contains("Task Manager");
        assertThat(searches.get(1).getQuery().getRaw()).isEqualTo("server tasks, tree of task 2");
    }

    @Test
    void badArgumentsAreRejected() {
        assertThat(run("{\"mode\":\"kill\"}")).isEqualTo("Error: The mode must be one of: summary, list, tree.");
        assertThat(run("{\"kind\":\"index\"}")).isEqualTo("Error: The kind must be one of: any, search.");
        assertThat(run("{\"min_age\":\"soon\"}")).startsWith("Error: 'soon' isn't a time min_age can be");
        assertThat(run("{\"mode\":\"list\",\"order\":\"up\"}"))
                .isEqualTo("Error: The order must be one of: oldest, newest.");
        Mockito.verifyNoInteractions(taskResource);
    }

    @Test
    void isReadOnly() {
        assertThat(tool().isReadOnly()).isTrue();
        assertThat(tool().definition().name()).isEqualTo("server_tasks");
        assertThat(tool().definition().description()).contains("Monitoring > Server Tasks")
                .contains("processor_tasks");
    }

    private TaskProgress task(final String nodeName,
                              final String id,
                              final TaskProgress parent,
                              final String name,
                              final String user,
                              final long ageMs,
                              final String info) {
        return add(nodeName, new TaskId(id, parent == null
                ? null
                : parent.getId()), name, user, ageMs, info);
    }

    private TaskProgress add(final String nodeName,
                             final TaskId id,
                             final String name,
                             final String user,
                             final long ageMs,
                             final String info) {
        final TaskProgress task = new TaskProgress(id, name, info,
                UserRef.builder().uuid(user + "-uuid").subjectId(user).displayName(user).build(),
                "thread-" + id.getId(), nodeName, NOW - ageMs, NOW, null, null);
        tasks.computeIfAbsent(nodeName, k -> new ArrayList<>()).add(task);
        return task;
    }

    private ServerTasksTool tool() {
        return new ServerTasksTool(fixture.workspace, fixture.securityContext, () -> taskResource,
                () -> nodeService, new SimpleTaskContextFactory(), executorProvider, fixture.auditLog());
    }

    private String run(final String arguments) {
        try {
            return tool().run(ToolTestFixture.request(ServerTasksTool.NAME, arguments), ToolCalls.inChat(1,
                    fixture.workspace)).text();
        } catch (final WorkspaceException e) {
            // As the tool's call returns it.
            return "Error: " + e.getMessage();
        }
    }
}
