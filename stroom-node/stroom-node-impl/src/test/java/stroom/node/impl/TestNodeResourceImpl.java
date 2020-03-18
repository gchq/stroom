package stroom.node.impl;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import stroom.cluster.api.ClusterNodeManager;
import stroom.event.logging.api.DocumentEventLog;
import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeInfo;
import stroom.node.shared.ClusterNodeInfo;
import stroom.node.shared.FetchNodeStatusResponse;
import stroom.node.shared.Node;
import stroom.node.shared.NodeResource;
import stroom.node.shared.NodeStatusResult;
import stroom.test.common.util.test.AbstractMultiNodeResourceTest;
import stroom.util.date.DateUtil;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.ResultPage;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class TestNodeResourceImpl extends AbstractMultiNodeResourceTest<NodeResource> {

    private final Map<String, ClusterNodeInfo> expectedClusterNodeInfoMap = new HashMap<>();
    private final Map<String, NodeServiceImpl> nodeServiceMap = new HashMap<>();

    @Test
    void list() {

        initNodes();

        final String subPath = "";

        final FetchNodeStatusResponse expectedResponse = getTestNodes().stream()
            .map(testNode -> {
                Node node2 = new Node();
                node2.setEnabled(testNode.isEnabled());
                node2.setName(testNode.getNodeName());
                return new NodeStatusResult(node2, testNode.getNodeName().equals("node1"));
            })
            .collect(FetchNodeStatusResponse.collector(FetchNodeStatusResponse::new));

        doGetTest(
            subPath,
            FetchNodeStatusResponse.class,
            expectedResponse);
    }

    @Test
    void info_sameNode() {

        initNodes();

        final String subPath = "/node1";

        final ClusterNodeInfo expectedResponse = expectedClusterNodeInfoMap.get("node1");

        doGetTest(
            subPath,
            ClusterNodeInfo.class,
            expectedResponse);

        assertThat(getRequestEvents("node1"))
            .hasSize(1);
        assertThat(getRequestEvents("node2"))
            .hasSize(0);
        assertThat(getRequestEvents("node3"))
            .hasSize(0);
    }

    @Test
    void info_otherNode() {

        initNodes();

        final String subPath = "/node2";

        final ClusterNodeInfo expectedResponse = expectedClusterNodeInfoMap.get("node2");

        final ClusterNodeInfo actualResponse = doGetTest(
            subPath,
            ClusterNodeInfo.class,
            null);

        assertThat(actualResponse.getPing())
            .isNotNull();
        // The resource sets the ping time which will be different to our expected one so just set them the same
        // so we can still equals the objects.
        actualResponse.setPing(expectedResponse.getPing());

        assertThat(actualResponse)
            .isEqualTo(expectedResponse);

        assertThat(getRequestEvents("node1"))
            .hasSize(1);
        assertThat(getRequestEvents("node2"))
            .hasSize(1);
        assertThat(getRequestEvents("node3"))
            .hasSize(0);
    }

    @Test
    void info_otherNode_disabled() {

        initNodes();

        final String subPath = "/node3";

        final ClusterNodeInfo expectedResponse = expectedClusterNodeInfoMap.get("node3");

        final ClusterNodeInfo actualResponse = doGetTest(
            subPath,
            ClusterNodeInfo.class,
            null);

        assertThat(actualResponse.getPing())
            .isNull();
        assertThat(actualResponse.getError())
            .isNotNull();
        // The resource sets the ping time which will be different to our expected one so just set them the same
        // so we can still equals the objects.
        actualResponse.setPing(expectedResponse.getPing());

        assertThat(getRequestEvents("node1"))
            .hasSize(1);
        assertThat(getRequestEvents("node2"))
            .hasSize(0);
        assertThat(getRequestEvents("node3"))
            .hasSize(0); // node down
    }

    @Test
    void ping_sameNode() {

        initNodes();

        final String subPath = ResourcePaths.buildPath("node1", NodeResource.PING_PATH_PART);

        final Long actualResponse = doGetTest(
            subPath,
            Long.class,
            null);

        assertThat(actualResponse)
            .isNotNull();

        assertThat(actualResponse)
            .isGreaterThan(0);

        assertThat(getRequestEvents("node1"))
            .hasSize(1);
        assertThat(getRequestEvents("node2"))
            .hasSize(0);
        assertThat(getRequestEvents("node3"))
            .hasSize(0);
    }

    @Test
    void ping_otherNode() {

        initNodes();

        final String subPath = ResourcePaths.buildPath("node2", NodeResource.PING_PATH_PART);

        final Long actualResponse = doGetTest(
            subPath,
            Long.class,
            null);

        assertThat(actualResponse)
            .isNotNull();

        assertThat(actualResponse)
            .isGreaterThan(0);

        assertThat(getRequestEvents("node1"))
            .hasSize(1);
        assertThat(getRequestEvents("node2"))
            .hasSize(1);
        assertThat(getRequestEvents("node3"))
            .hasSize(0);
    }

    @Test
    void ping_badRequest() {

        initNodes();

        final String subPath = ResourcePaths.buildPath("node2", NodeResource.PING_PATH_PART);

        final Long actualResponse = doGetTest(
            subPath,
            Long.class,
            null);

        assertThat(actualResponse)
            .isNotNull();

        assertThat(actualResponse)
            .isGreaterThan(0);

        assertThat(getRequestEvents("node1"))
            .hasSize(1);
        assertThat(getRequestEvents("node2"))
            .hasSize(1);
        assertThat(getRequestEvents("node3"))
            .hasSize(0);
    }

    @Test
    void setPriority() {
        initNodes();

        final String subPath = ResourcePaths.buildPath("node2", NodeResource.PRIORITY_PATH_PART);

        doPutTest(
            subPath,
            10L);

        Node newNode = new Node();
        newNode.setName("node2");
        newNode.setPriority(10);

        // We are hitting node1 but setting node2
        verify(nodeServiceMap.get("node1"), Mockito.times(1))
            .update(Mockito.eq(newNode));
    }

    @Test
    void setEnabled() {
        initNodes();

        final String subPath = ResourcePaths.buildPath("node2", NodeResource.ENABLED_PATH_PART);

        doPutTest(
            subPath,
            Boolean.FALSE);

        Node newNode = new Node();
        newNode.setName("node2");
        newNode.setEnabled(false);

        // We are hitting node1 but setting node2
        verify(nodeServiceMap.get("node1"), Mockito.times(1))
            .update(Mockito.eq(newNode));
    }

    @Override
    public String getResourceBasePath() {
        return NodeResource.BASE_PATH;
    }

    @Override
    public NodeResource getRestResource(final TestNode node,
                                        final List<TestNode> allNodes,
                                        final Map<String, String> baseEndPointUrls) {
        // Set up the NodeService mock
        final NodeServiceImpl nodeService = createNamedMock(NodeServiceImpl.class, node);

        when(nodeService.isEnabled(Mockito.anyString()))
            .thenAnswer(invocation ->
                allNodes.stream()
                    .filter(testNode -> testNode.getNodeName().equals(invocation.getArgument(0)))
                    .anyMatch(TestNode::isEnabled));

        when(nodeService.getBaseEndpointUrl(Mockito.anyString()))
            .thenAnswer(invocation -> baseEndPointUrls.get((String) invocation.getArgument(0)));

        when(nodeService.find(Mockito.any(FindNodeCriteria.class)))
            .thenReturn(allNodes.stream()
                .map(testNode -> {
                    Node node2 = new Node();
                    node2.setEnabled(testNode.isEnabled());
                    node2.setName(testNode.getNodeName());
                    return node2;
//                    return new NodeStatusResult(node2, node.getNodeName().equals("node1"));
                })
                .collect(ResultPage.collector(ResultPage::new)));

        when(nodeService.getNode(Mockito.anyString()))
            .thenAnswer(invocation -> {
                String nodeName = invocation.getArgument(0);
                Node node2 = new Node();
                node2.setName(nodeName);
                return node2;
            });

        nodeServiceMap.put(node.getNodeName(), nodeService);

        // Set up the NodeInfo mock

        final NodeInfo nodeInfo = createNamedMock(NodeInfo.class, node);

        when(nodeInfo.getThisNodeName())
            .thenReturn(node.getNodeName());

        // Set up the ClusterNodeManager mock

        final ClusterNodeManager clusterNodeManager = createNamedMock(ClusterNodeManager.class, node);

        ClusterNodeInfo clusterNodeInfo = new ClusterNodeInfo(
            DateUtil.createNormalDateTimeString(Instant.now().toEpochMilli()),
            new BuildInfo(
                DateUtil.createNormalDateTimeString(Instant.now().toEpochMilli()),
                "v1.1",
                DateUtil.createNormalDateTimeString(Instant.now().toEpochMilli())),
            node.getNodeName(),
            getBaseEndPointUrl(node));

        expectedClusterNodeInfoMap.put(node.getNodeName(), clusterNodeInfo);

        when(clusterNodeManager.getClusterNodeInfo())
            .thenReturn(clusterNodeInfo);

        final DocumentEventLog documentEventLog = createNamedMock(DocumentEventLog.class, node);

        return new NodeResourceImpl(
            nodeService,
            nodeInfo,
            clusterNodeManager,
            webTargetFactory(),
            documentEventLog);
    }
}