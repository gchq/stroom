package stroom.security.impl.session;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.security.impl.AuthenticationEventLog;
import stroom.security.impl.SessionResource;
import stroom.security.impl.SessionResourceImpl;
import stroom.task.api.TaskContext;
import stroom.test.common.util.test.AbstractMultiNodeResourceTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class TestSessionListListener extends AbstractMultiNodeResourceTest<SessionResource> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSessionListListener.class);

    @Mock
    private AuthenticationEventLog authenticationEventLog;

    private Map<String, SessionListService> sessionListServiceMap = new HashMap<>();

    @BeforeEach
    void beforeEach() {
        sessionListServiceMap.clear();
    }

    @Test
    void listSessions() throws InterruptedException {

        initNodes();

        SessionListService sessionListService1 = sessionListServiceMap.get("node1");

        SessionListResponse sessionListResponse = sessionListService1.listSessions();

        Thread.sleep(50);

        // We call method on node1, so no requests
        Assertions.assertThat(getRequestEvents("node1"))
            .hasSize(0);
        // Node is remote so one call
        Assertions.assertThat(getRequestEvents("node2"))
            .hasSize(1);
        // Node is disabled, so no requests
        Assertions.assertThat(getRequestEvents("node3"))
            .hasSize(0);
    }

    @Test
    void testListSessions_oneNode() throws InterruptedException {
        initNodes();

        SessionListService sessionListService1 = sessionListServiceMap.get("node1");

        SessionListResponse sessionListResponse = sessionListService1.listSessions("node2");

        Thread.sleep(50);

        // We call method on node1, so no requests
        Assertions.assertThat(getRequestEvents("node1"))
            .hasSize(0);
        // Node is remote so one call
        Assertions.assertThat(getRequestEvents("node2"))
            .hasSize(1);
        // Node is disabled, so no requests
        Assertions.assertThat(getRequestEvents("node3"))
            .hasSize(0);
    }

    /**
     * Create a {@link TaskContext} that wraps the runnable/supplier with no
     * extra functionality
     */
    static TaskContext getTaskContext() {

        final TaskContext taskContext = Mockito.mock(TaskContext.class);

        // Set up TaskContext to just return the passed runnable/supplier
        when(taskContext.sub(Mockito.any(Runnable.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(taskContext.sub(Mockito.any(Supplier.class)))
                .thenAnswer(i -> i.getArgument(0));

        return taskContext;
    }

    @Override
    public String getResourceBasePath() {
        return SessionResource.BASE_PATH;
    }

    @Override
    public SessionResource getRestResource(final TestNode node,
                                           final List<TestNode> allNodes,
                                           final Map<String, String> baseEndPointUrls) {
        // Set up the NodeService mock
        final NodeService nodeService = Mockito.mock(NodeService.class,
            NodeService.class.getName() + "_" + node.getNodeName());

        when(nodeService.isEnabled(Mockito.anyString()))
            .thenAnswer(invocation ->
                allNodes.stream()
                    .filter(testNode -> testNode.getNodeName().equals(invocation.getArgument(0)))
                    .anyMatch(TestNode::isEnabled));

        when(nodeService.getBaseEndpointUrl(Mockito.anyString()))
            .thenAnswer(invocation -> baseEndPointUrls.get((String) invocation.getArgument(0)));

        when(nodeService.findNodeNames(Mockito.any(FindNodeCriteria.class)))
            .thenReturn(List.of("node1", "node2"));

        // Set up the NodeInfo mock

        final NodeInfo nodeInfo = Mockito.mock(NodeInfo.class,
            NodeInfo.class.getName() + "_" + node.getNodeName());

        when(nodeInfo.getThisNodeName())
            .thenReturn(node.getNodeName());

        final SessionListService sessionListService = new SessionListListener(
            nodeInfo,
            nodeService,
            TestSessionListListener::getTaskContext,
            webTargetFactory());

        sessionListServiceMap.put(node.getNodeName(), sessionListService);

        return new SessionResourceImpl(authenticationEventLog, sessionListService);
    }
}