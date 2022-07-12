package stroom.security.impl;

import stroom.cluster.api.ClusterService;
import stroom.cluster.api.EndpointUrlService;
import stroom.security.shared.SessionListResponse;
import stroom.security.shared.SessionResource;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.test.common.util.test.AbstractMultiNodeResourceTest;
import stroom.test.common.util.test.MockClusterService;
import stroom.test.common.util.test.MockEndpointUrlService;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@MockitoSettings(strictness = Strictness.LENIENT)
class TestSessionListListener extends AbstractMultiNodeResourceTest<SessionResource> {

    private final Map<String, SessionListService> sessionListServiceMap = new HashMap<>();

    private static final int BASE_PORT = 7030;

    public TestSessionListListener() {
        super(createNodeList(BASE_PORT));
    }

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

    @Override
    public String getResourceBasePath() {
        return SessionResource.BASE_PATH;
    }

    @Override
    public SessionResource getRestResource(final TestMember local,
                                           final List<TestMember> members) {
        final ClusterService clusterService = new MockClusterService(local, members);

        // Set up the NodeService mock
        final EndpointUrlService endpointUrlService = new MockEndpointUrlService(local, members);

//        when(nodeService.isEnabled(Mockito.anyString()))
//                .thenAnswer(invocation ->
//                        allNodes.stream()
//                                .filter(testNode -> testNode.getNodeName().equals(invocation.getArgument(0)))
//                                .anyMatch(TestNode::isEnabled));

//        when(endpointUrlService.getBaseEndpointUrl(Mockito.anyString()))
//                .thenAnswer(invocation -> baseEndPointUrls.get((String) invocation.getArgument(0)));
//
//        when(clusterService.findNodeNames(Mockito.any(FindNodeCriteria.class)))
//                .thenReturn(List.of("node1", "node2"));

        // Set up the NodeInfo mock

//        final NodeInfo nodeInfo = Mockito.mock(NodeInfo.class,
//                NodeInfo.class.getName() + "_" + node.getNodeName());
//
//        when(nodeInfo.getThisNodeName())
//                .thenReturn(node.getNodeName());

        final SessionListService sessionListService = new SessionListListener(
                clusterService,
                endpointUrlService,
                new SimpleTaskContextFactory(),
                webTargetFactory());

        sessionListServiceMap.put(local.getUuid(), sessionListService);

        return new SessionResourceImpl(() -> sessionListService);
    }
}
