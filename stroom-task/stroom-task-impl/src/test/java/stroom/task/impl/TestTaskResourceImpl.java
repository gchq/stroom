/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.task.impl;

import stroom.event.logging.api.DocumentEventLog;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.task.shared.FindTaskCriteria;
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.FindTaskProgressRequest;
import stroom.task.shared.TaskId;
import stroom.task.shared.TaskProgress;
import stroom.task.shared.TaskProgressResponse;
import stroom.task.shared.TaskResource;
import stroom.task.shared.TerminateTaskProgressRequest;
import stroom.test.common.util.test.AbstractMultiNodeResourceTest;
import stroom.util.servlet.SessionIdProvider;
import stroom.util.shared.ResourcePaths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class TestTaskResourceImpl extends AbstractMultiNodeResourceTest<TaskResource> {

    private final Map<String, TaskManagerImpl> taskManagerMap = new HashMap<>();
    private final Map<String, DocumentEventLog> documentEventLogMap = new HashMap<>();

    private static final int BASE_PORT = 7050;

    public TestTaskResourceImpl() {
        super(createNodeList(BASE_PORT));
    }

    @BeforeEach
    void setUp() {
        taskManagerMap.clear();
        documentEventLogMap.clear();
    }

    @Disabled // TODO @AT Need to rework this after the remote rest stuff was moved to NodeService
    @Test
    void list_sameNode() {

        initNodes();

        final String subPath = ResourcePaths.buildPath(TaskResource.LIST_PATH_PART, "node1");

        final TaskProgressResponse expectedResponse = buildTaskProgressResponse("node1");

        doGetTest(
                subPath,
                TaskProgressResponse.class,
                expectedResponse);

        assertThat(getRequestEvents("node1"))
                .hasSize(1);
        assertThat(getRequestEvents("node2"))
                .hasSize(0);
        assertThat(getRequestEvents("node3"))
                .hasSize(0);
    }

    @Disabled // TODO @AT Need to rework this after the remote rest stuff was moved to NodeService
    @Test
    void list_otherNode() {

        initNodes();

        final String subPath = ResourcePaths.buildPath(TaskResource.LIST_PATH_PART, "node2");

        final TaskProgressResponse expectedResponse = buildTaskProgressResponse("node2");

        doGetTest(
                subPath,
                TaskProgressResponse.class,
                expectedResponse);

        assertThat(getRequestEvents("node1"))
                .hasSize(1);
        assertThat(getRequestEvents("node2"))
                .hasSize(1);
        assertThat(getRequestEvents("node3"))
                .hasSize(0);
    }

    @Disabled // TODO @AT Need to rework this after the remote rest stuff was moved to NodeService
    @Test
    void find_sameNode() {

        initNodes();

        final String subPath = ResourcePaths.buildPath(TaskResource.FIND_PATH_PART, "node1");

        final TaskProgressResponse expectedResponse = buildTaskProgressResponse("node1");

        final FindTaskProgressRequest findTaskProgressRequest = new FindTaskProgressRequest(
                new FindTaskProgressCriteria());

        doPostTest(
                subPath,
                findTaskProgressRequest,
                TaskProgressResponse.class,
                expectedResponse);

        assertThat(getRequestEvents("node1"))
                .hasSize(1);
        assertThat(getRequestEvents("node2"))
                .hasSize(0);
        assertThat(getRequestEvents("node3"))
                .hasSize(0);
    }

    @Disabled // TODO @AT Need to rework this after the remote rest stuff was moved to NodeService
    @Test
    void find_otherNode() {

        initNodes();

        final String subPath = ResourcePaths.buildPath(TaskResource.FIND_PATH_PART, "node2");

        final TaskProgressResponse expectedResponse = buildTaskProgressResponse("node2");

        final FindTaskProgressRequest findTaskProgressRequest = new FindTaskProgressRequest(
                new FindTaskProgressCriteria());

        doPostTest(
                subPath,
                findTaskProgressRequest,
                TaskProgressResponse.class,
                expectedResponse);

        assertThat(getRequestEvents("node1"))
                .hasSize(1);
        assertThat(getRequestEvents("node2"))
                .hasSize(1);
        assertThat(getRequestEvents("node3"))
                .hasSize(0);
    }

    @Disabled // TODO @AT Need to rework this after the remote rest stuff was moved to NodeService
    @Test
    void userTasks_sameNode() {

        initNodes();

        final String subPath = ResourcePaths.buildPath(TaskResource.USER_PATH_PART, "node1");

        final TaskProgressResponse expectedResponse = buildTaskProgressResponse("node1");

        doGetTest(
                subPath,
                TaskProgressResponse.class,
                expectedResponse);

        assertThat(getRequestEvents("node1"))
                .hasSize(1);
        assertThat(getRequestEvents("node2"))
                .hasSize(0);
        assertThat(getRequestEvents("node3"))
                .hasSize(0);
    }

    @Disabled // TODO @AT Need to rework this after the remote rest stuff was moved to NodeService
    @Test
    void userTasks_otherNode() {

        initNodes();

        final String subPath = ResourcePaths.buildPath(TaskResource.USER_PATH_PART, "node2");

        final TaskProgressResponse expectedResponse = buildTaskProgressResponse("node2");

        doGetTest(
                subPath,
                TaskProgressResponse.class,
                expectedResponse);

        assertThat(getRequestEvents("node1"))
                .hasSize(1);
        assertThat(getRequestEvents("node2"))
                .hasSize(1);
        assertThat(getRequestEvents("node3"))
                .hasSize(0);
    }

    @Test
    void terminate_sameNode() {

        initNodes();

        final String subPath = ResourcePaths.buildPath(TaskResource.TERMINATE_PATH_PART, "node1");

        final TerminateTaskProgressRequest terminateTaskProgressRequest = new TerminateTaskProgressRequest(
                new FindTaskCriteria());

        doPostTest(
                subPath,
                terminateTaskProgressRequest,
                Boolean.class,
                Boolean.TRUE);

        assertThat(getRequestEvents("node1"))
                .hasSize(1);
        assertThat(getRequestEvents("node2"))
                .hasSize(0);
        assertThat(getRequestEvents("node3"))
                .hasSize(0);
    }

    @Disabled // TODO @AT Need to rework this after the remote rest stuff was moved to NodeService
    @Test
    void terminate_otherNode() {

        initNodes();

        final String subPath = ResourcePaths.buildPath(TaskResource.TERMINATE_PATH_PART, "node2");

        final TerminateTaskProgressRequest terminateTaskProgressRequest = new TerminateTaskProgressRequest(
                new FindTaskCriteria());

        doPostTest(
                subPath,
                terminateTaskProgressRequest,
                Boolean.class,
                Boolean.TRUE);

        assertThat(getRequestEvents("node1"))
                .hasSize(1);
        assertThat(getRequestEvents("node2"))
                .hasSize(1);
        assertThat(getRequestEvents("node3"))
                .hasSize(0);
    }

    @Override
    public String getResourceBasePath() {
        return TaskResource.BASE_PATH;
    }

    @Override
    public TaskResource getRestResource(final TestNode node,
                                        final List<TestNode> allNodes,
                                        final Map<String, String> baseEndPointUrls) {

        final TaskManagerImpl taskManager = createNamedMock(TaskManagerImpl.class, node);

        when(taskManager.find(any()))
                .thenReturn(buildTaskProgressResponse(node.getNodeName()));

        final SessionIdProvider sessionIdProvider = createNamedMock(SessionIdProvider.class, node);

        when(sessionIdProvider.get())
                .thenReturn(UUID.randomUUID().toString());

        // Set up the NodeService mock
        final NodeService nodeService = createNamedMock(NodeService.class, node);

        when(nodeService.isEnabled(Mockito.anyString()))
                .then(invocation ->
                        allNodes.stream()
                                .filter(testNode -> testNode.getNodeName().equals(invocation.getArgument(0)))
                                .anyMatch(TestNode::isEnabled));

        when(nodeService.getBaseEndpointUrl(Mockito.anyString()))
                .then(invocation -> baseEndPointUrls.get((String) invocation.getArgument(0)));

        // Set up the NodeInfo mock

        final NodeInfo nodeInfo = createNamedMock(NodeInfo.class, node);

        when(nodeInfo.getThisNodeName())
                .thenReturn(node.getNodeName());

        final DocumentEventLog documentEventLog = createNamedMock(DocumentEventLog.class, node);

        documentEventLogMap.put(node.getNodeName(), documentEventLog);

        return new TaskResourceImpl(
                () -> taskManager,
                () -> sessionIdProvider,
                () -> nodeService);
    }

    private TaskProgress buildTaskProgress(final String taskId, final String nodeName) {
        final TaskProgress taskProgress = new TaskProgress();
        taskProgress.setId(new TaskId(taskId, null));
        return taskProgress;
    }

    private TaskProgressResponse buildTaskProgressResponse(final String nodeName) {
        return new TaskProgressResponse(List.of(
                buildTaskProgress("task1", nodeName),
                buildTaskProgress("task2", nodeName),
                buildTaskProgress("task3", nodeName)));
    }
}
