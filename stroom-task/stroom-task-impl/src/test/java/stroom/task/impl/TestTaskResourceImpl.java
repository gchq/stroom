package stroom.task.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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

    @BeforeEach
    void setUp() {
        taskManagerMap.clear();
        documentEventLogMap.clear();
    }

    @Test
    void list_sameNode() {

        initNodes();

        String subPath = ResourcePaths.buildPath(TaskResource.LIST_PATH_PART, "node1");

        TaskProgressResponse expectedResponse = buildTaskProgressResponse("node1");

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

    @Test
    void list_otherNode() {

        initNodes();

        String subPath = ResourcePaths.buildPath(TaskResource.LIST_PATH_PART, "node2");

        TaskProgressResponse expectedResponse = buildTaskProgressResponse("node2");

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
    void find_sameNode() {

        initNodes();

        String subPath = ResourcePaths.buildPath(TaskResource.FIND_PATH_PART, "node1");

        TaskProgressResponse expectedResponse = buildTaskProgressResponse("node1");

        FindTaskProgressRequest findTaskProgressRequest = new FindTaskProgressRequest(new FindTaskProgressCriteria());

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

    @Test
    void find_otherNode() {

        initNodes();

        String subPath = ResourcePaths.buildPath(TaskResource.FIND_PATH_PART, "node2");

        TaskProgressResponse expectedResponse = buildTaskProgressResponse("node2");

        FindTaskProgressRequest findTaskProgressRequest = new FindTaskProgressRequest(new FindTaskProgressCriteria());

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

    @Test
    void userTasks_sameNode() {

        initNodes();

        String subPath = ResourcePaths.buildPath(TaskResource.USER_PATH_PART, "node1");

        TaskProgressResponse expectedResponse = buildTaskProgressResponse("node1");

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

    @Test
    void userTasks_otherNode() {

        initNodes();

        String subPath = ResourcePaths.buildPath(TaskResource.USER_PATH_PART, "node2");

        TaskProgressResponse expectedResponse = buildTaskProgressResponse("node2");

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

        String subPath = ResourcePaths.buildPath(TaskResource.TERMINATE_PATH_PART, "node1");

        TerminateTaskProgressRequest terminateTaskProgressRequest = new TerminateTaskProgressRequest(
                new FindTaskCriteria(),
                true);

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

    @Test
    void terminate_otherNode() {

        initNodes();

        String subPath = ResourcePaths.buildPath(TaskResource.TERMINATE_PATH_PART, "node2");

        TerminateTaskProgressRequest terminateTaskProgressRequest = new TerminateTaskProgressRequest(
                new FindTaskCriteria(),
                true);

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
                taskManager,
                sessionIdProvider,
                nodeService,
                nodeInfo,
                webTargetFactory(),
                documentEventLog);
    }

    private TaskProgress buildTaskProgress(final String taskId, final String nodeName) {
        TaskProgress taskProgress = new TaskProgress();
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