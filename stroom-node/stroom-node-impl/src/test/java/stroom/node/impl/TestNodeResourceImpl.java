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
import stroom.util.shared.ResultPage;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class TestNodeResourceImpl extends AbstractMultiNodeResourceTest<NodeResource> {

    private final Map<String, ClusterNodeInfo> expectedClusterNodeInfoMap = new HashMap<>();

    @Test
    void list() {

        initNodes();

        final String subPath = "";

//        List<CacheInfo> cacheInfoList = List.of(
//            new CacheInfo("cache1", Collections.emptyMap(), "node1"));
//
//        final CacheInfoResponse expectedResponse = new CacheInfoResponse(cacheInfoList);

//        when(cacheManagerService.find(Mockito.any()))
//            .thenReturn(cacheInfoList);

        FetchNodeStatusResponse expectedResponse = getTestNodes().stream()
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
    void info() {
    }

    @Test
    void ping() {
    }

    @Test
    void setPriority() {
    }

    @Test
    void setEnabled() {
    }

    @Test
    void getHealth() {
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