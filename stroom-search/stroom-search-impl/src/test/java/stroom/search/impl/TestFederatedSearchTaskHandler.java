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

package stroom.search.impl;

import stroom.cluster.task.api.ClusterTaskTerminator;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.node.api.NodeInfo;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.common.v2.ResultStore;
import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleTaskContext;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.shared.ThreadPool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/// Tests what [FederatedSearchTaskHandler] does when a search can't be started on a node.
class TestFederatedSearchTaskHandler {

    private static final String NODE_1 = "node1";
    private static final String NODE_2 = "node2";
    private static final String NODE_3 = "node3";

    private final NodeSearchTask node2Task = nodeSearchTask(1L, 2L);
    private final NodeSearchTask node3Task = nodeSearchTask(3L);

    private ResultStore resultStore;
    private FederatedSearchTask task;
    private NodeTaskCreator nodeTaskCreator;
    private LocalNodeSearch localNodeSearch;
    private RemoteNodeSearch remoteNodeSearch;
    private FederatedSearchTaskHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        resultStore = Mockito.mock(ResultStore.class);
        task = new FederatedSearchTask(
                null,
                new QueryKey("search"),
                "Test search",
                Query.builder().expression(ExpressionOperator.builder().build()).build(),
                List.of(),
                null);
        task.setResultStore(resultStore);

        nodeTaskCreator = Mockito.mock(NodeTaskCreator.class);
        Mockito.when(nodeTaskCreator.createNodeSearchTasks(any(), any(), any()))
                .thenReturn(Map.of(NODE_2, node2Task, NODE_3, node3Task));

        localNodeSearch = Mockito.mock(LocalNodeSearch.class);
        remoteNodeSearch = Mockito.mock(RemoteNodeSearch.class);

        final TargetNodeSetFactory targetNodeSetFactory = Mockito.mock(TargetNodeSetFactory.class);
        Mockito.when(targetNodeSetFactory.getSourceNode()).thenReturn(NODE_1);
        Mockito.when(targetNodeSetFactory.getEnabledTargetNodeSet()).thenReturn(Set.of(NODE_1, NODE_2, NODE_3));

        final NodeInfo nodeInfo = Mockito.mock(NodeInfo.class);
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn(NODE_1);

        // Runs whatever it is given, as whoever.
        final SecurityContext securityContext = Mockito.mock(SecurityContext.class, invocation -> {
            if (invocation.getArguments().length == 1 && invocation.getArgument(0) instanceof final Runnable runnable) {
                runnable.run();
            }
            return null;
        });

        // Runs everything on the calling thread so that the test is in step with the search.
        final Executor directExecutor = Runnable::run;
        final ExecutorProvider executorProvider = Mockito.mock(ExecutorProvider.class);
        Mockito.when(executorProvider.get()).thenReturn(directExecutor);
        Mockito.when(executorProvider.get(any(ThreadPool.class))).thenReturn(directExecutor);

        handler = new FederatedSearchTaskHandler(
                targetNodeSetFactory,
                Mockito.mock(TaskManager.class),
                Mockito.mock(ClusterTaskTerminator.class),
                securityContext,
                executorProvider,
                new SimpleTaskContextFactory(),
                nodeInfo,
                () -> localNodeSearch,
                () -> remoteNodeSearch);
    }

    @Test
    void testEveryNodeStarts() {
        exec();

        verifySearched(remoteNodeSearch, NODE_2, node2Task);
        verifySearched(remoteNodeSearch, NODE_3, node3Task);
        Mockito.verify(nodeTaskCreator, Mockito.never()).reassign(any(), any());
        Mockito.verify(resultStore, Mockito.never()).onFailure(any(), any());
        Mockito.verify(resultStore).signalComplete();
    }

    @Test
    void testNodeThatCannotBeStarted_shardsSearchedByAnotherNode() {
        final NodeSearchTask reassignedTask = nodeSearchTask(1L, 2L);
        failsToStart(remoteNodeSearch, NODE_2, node2Task);
        Mockito.when(nodeTaskCreator.reassign(node2Task, Set.of(NODE_2)))
                .thenReturn(new NodeSearchReassignment(Map.of(NODE_3, reassignedTask), false));

        exec();

        // The other node runs its own task and the one it has taken over.
        verifySearched(remoteNodeSearch, NODE_3, node3Task);
        verifySearched(remoteNodeSearch, NODE_3, reassignedTask);
        // Nothing is missing from the results, so the search reports no failure.
        Mockito.verify(resultStore, Mockito.never()).onFailure(any(), any());
        Mockito.verify(resultStore).signalComplete();
    }

    @Test
    void testNodeThatCannotBeStarted_workThatCannotMoveIsReported() {
        final NodeSearchTask reassignedTask = nodeSearchTask(2L);
        final NodeSearchStartException startException = failsToStart(remoteNodeSearch, NODE_2, node2Task);
        // One of the node's shards is on a local volume, so only the other can be given away.
        Mockito.when(nodeTaskCreator.reassign(node2Task, Set.of(NODE_2)))
                .thenReturn(new NodeSearchReassignment(Map.of(NODE_3, reassignedTask), true));

        exec();

        verifySearched(remoteNodeSearch, NODE_3, node3Task);
        verifySearched(remoteNodeSearch, NODE_3, reassignedTask);
        Mockito.verify(resultStore).onFailure(NODE_2, startException);
        Mockito.verify(resultStore).signalComplete();
    }

    @Test
    void testNodeThatCannotBeStarted_searchTypeThatCannotBeReassigned() {
        final NodeSearchStartException startException = failsToStart(remoteNodeSearch, NODE_2, node2Task);
        Mockito.when(nodeTaskCreator.reassign(any(), any())).thenReturn(NodeSearchReassignment.NOT_POSSIBLE);

        exec();

        // The failure of one node does not stop the others being searched.
        verifySearched(remoteNodeSearch, NODE_3, node3Task);
        Mockito.verify(resultStore).onFailure(NODE_2, startException);
        Mockito.verify(resultStore).signalComplete();
    }

    @Test
    void testSecondNodeAlsoCannotBeStarted_shardsMoveAgain() {
        final NodeSearchTask firstReassignedTask = nodeSearchTask(1L, 2L);
        final NodeSearchTask secondReassignedTask = nodeSearchTask(1L, 2L);
        failsToStart(remoteNodeSearch, NODE_2, node2Task);
        failsToStart(remoteNodeSearch, NODE_3, firstReassignedTask);
        Mockito.when(nodeTaskCreator.reassign(node2Task, Set.of(NODE_2)))
                .thenReturn(new NodeSearchReassignment(Map.of(NODE_3, firstReassignedTask), false));
        // The second time round both nodes that failed are left out.
        Mockito.when(nodeTaskCreator.reassign(firstReassignedTask, Set.of(NODE_2, NODE_3)))
                .thenReturn(new NodeSearchReassignment(Map.of(NODE_1, secondReassignedTask), false));

        exec();

        // This node ends up searching them itself.
        verifySearched(localNodeSearch, NODE_1, secondReassignedTask);
        Mockito.verify(resultStore, Mockito.never()).onFailure(any(), any());
        Mockito.verify(resultStore).signalComplete();
    }

    @Test
    void testNodeThatCannotBeStarted_searchAlreadyStopped_nothingIsReassigned() {
        // Stopping a search interrupts the calls that start it on each node, which then look like nodes
        // that could not be started.
        failsToStart(remoteNodeSearch, NODE_2, node2Task);
        final SimpleTaskContext stoppedContext = new SimpleTaskContext() {
            @Override
            public boolean isTerminated() {
                // Not stopped when the search begins, but stopped by the time the node fails.
                return Mockito.mockingDetails(remoteNodeSearch).getInvocations().size() > 0;
            }
        };

        handler.exec(stoppedContext, task, nodeTaskCreator);

        Mockito.verify(nodeTaskCreator, Mockito.never()).reassign(any(), any());
        Mockito.verify(resultStore, Mockito.never()).onFailure(any(), any());
    }

    @Test
    void testReassignmentThatFails_isReportedAsLostWork() {
        final NodeSearchStartException startException = failsToStart(remoteNodeSearch, NODE_2, node2Task);
        Mockito.when(nodeTaskCreator.reassign(any(), any())).thenThrow(new RuntimeException("database down"));

        exec();

        verifySearched(remoteNodeSearch, NODE_3, node3Task);
        Mockito.verify(resultStore).onFailure(NODE_2, startException);
        Mockito.verify(resultStore).signalComplete();
    }

    private void exec() {
        handler.exec(new SimpleTaskContext(), task, nodeTaskCreator);
    }

    private NodeSearchStartException failsToStart(final NodeSearch nodeSearch,
                                                  final String nodeName,
                                                  final NodeSearchTask nodeSearchTask) {
        final NodeSearchStartException exception = new NodeSearchStartException("Unable to connect to " + nodeName);
        Mockito.doThrow(exception)
                .when(nodeSearch)
                .searchNode(eq(NODE_1), eq(nodeName), eq(task), Mockito.same(nodeSearchTask), any());
        return exception;
    }

    private void verifySearched(final NodeSearch nodeSearch,
                                final String nodeName,
                                final NodeSearchTask nodeSearchTask) {
        Mockito.verify(nodeSearch)
                .searchNode(eq(NODE_1), eq(nodeName), eq(task), Mockito.same(nodeSearchTask), any());
    }

    private static NodeSearchTask nodeSearchTask(final Long... shards) {
        return new NodeSearchTask(
                NodeSearchTaskType.LUCENE,
                null,
                "Cluster Search",
                null,
                new QueryKey("search"),
                null,
                List.of(),
                null,
                List.of(shards));
    }
}
