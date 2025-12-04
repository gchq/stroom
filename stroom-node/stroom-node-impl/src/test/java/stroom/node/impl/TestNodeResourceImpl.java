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

package stroom.node.impl;

import stroom.cluster.api.ClusterNodeManager;
import stroom.cluster.api.ClusterState;
import stroom.event.logging.api.DocumentEventLog;
import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeInfo;
import stroom.node.shared.ClusterNodeInfo;
import stroom.node.shared.FetchNodeStatusResponse;
import stroom.node.shared.FindNodeStatusCriteria;
import stroom.node.shared.Node;
import stroom.node.shared.NodeResource;
import stroom.node.shared.NodeStatusResult;
import stroom.test.common.util.test.AbstractMultiNodeResourceTest;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings(strictness = Strictness.LENIENT)
class TestNodeResourceImpl extends AbstractMultiNodeResourceTest<NodeResource> {

    private final Map<String, ClusterNodeInfo> expectedClusterNodeInfoMap = new HashMap<>();
    private final Map<String, NodeServiceImpl> nodeServiceMap = new HashMap<>();

    private static final int BASE_PORT = 7040;

    public TestNodeResourceImpl() {
        super(createNodeList(BASE_PORT));
    }

    @Test
    void list() {
        initNodes();

        final String subPath = ResourcePaths.buildPath(NodeResource.FIND_PATH_PART);
        ;

        final FetchNodeStatusResponse expectedResponse = getTestNodes().stream()
                .map(testNode -> {
                    final Node node2 = new Node();
                    node2.setEnabled(testNode.isEnabled());
                    node2.setName(testNode.getNodeName());
                    return new NodeStatusResult(node2, testNode.getNodeName().equals("node1"));
                })
                .collect(FetchNodeStatusResponse.collector(FetchNodeStatusResponse::new));

        final FindNodeStatusCriteria findNodeStatusCriteria = new FindNodeStatusCriteria();

        doPostTest(
                subPath,
                findNodeStatusCriteria,
                FetchNodeStatusResponse.class,
                expectedResponse);
    }

    @Disabled // TODO @AT Need to rework this after the remote rest stuff was moved to NodeService
    @Test
    void info_sameNode() {

        initNodes();

        final String subPath = ResourcePaths.buildPath(NodeResource.INFO_PATH_PART, "node1");

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

    @Disabled // TODO @AT Need to rework this after the remote rest stuff was moved to NodeService
    @Test
    void info_otherNode() {

        initNodes();

        final String subPath = ResourcePaths.buildPath(NodeResource.INFO_PATH_PART, "node2");

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

        final String subPath = ResourcePaths.buildPath(NodeResource.INFO_PATH_PART, "node3");

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

    @Disabled // TODO @AT Need to rework this after the remote rest stuff was moved to NodeService
    @Test
    void ping_sameNode() {

        initNodes();

        final String subPath = ResourcePaths.buildPath(NodeResource.PING_PATH_PART, "node1");

        final Long actualResponse = doGetTest(
                subPath,
                Long.class,
                null);

        assertThat(actualResponse)
                .isNotNull();

        // On the same node so can't assume the ping time will be >0.

        assertThat(getRequestEvents("node1"))
                .hasSize(1);
        assertThat(getRequestEvents("node2"))
                .hasSize(0);
        assertThat(getRequestEvents("node3"))
                .hasSize(0);
    }

    @Disabled // TODO @AT Need to rework this after the remote rest stuff was moved to NodeService
    @Test
    void ping_otherNode() {

        initNodes();

        final String subPath = ResourcePaths.buildPath(NodeResource.PING_PATH_PART, "node2");

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

    @Disabled // TODO @AT Need to rework this after the remote rest stuff was moved to NodeService
    @Test
    void ping_badRequest() {

        initNodes();

        final String subPath = ResourcePaths.buildPath(NodeResource.PING_PATH_PART, "node2");

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

        final String subPath = ResourcePaths.buildPath(NodeResource.PRIORITY_PATH_PART, "node2");

        doPutTest(
                subPath,
                10L);

        final Node newNode = new Node();
        newNode.setName("node2");
        newNode.setPriority(10);

        // We are hitting node1 but setting node2
        verify(nodeServiceMap.get("node1"), Mockito.times(1))
                .update(Mockito.eq(newNode));
    }

    @Test
    void setEnabled() {
        initNodes();

        final String subPath = ResourcePaths.buildPath(NodeResource.ENABLED_PATH_PART, "node2");

        doPutTest(
                subPath,
                Boolean.FALSE);

        final Node newNode = new Node();
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
                            final Node node2 = new Node();
                            node2.setEnabled(testNode.isEnabled());
                            node2.setName(testNode.getNodeName());
                            return node2;
                        })
                        .collect(ResultPage.collector(ResultPage::new)));

        when(nodeService.getNode(Mockito.anyString()))
                .thenAnswer(invocation -> {
                    final String nodeName = invocation.getArgument(0);
                    final Node node2 = new Node();
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

        final long now = System.currentTimeMillis();
        final ClusterNodeInfo clusterNodeInfo = new ClusterNodeInfo(
                now,
                new BuildInfo(
                        now,
                        "v1.1",
                        now),
                node.getNodeName(),
                getBaseEndPointUrl(node));

        expectedClusterNodeInfoMap.put(node.getNodeName(), clusterNodeInfo);

        when(clusterNodeManager.getClusterNodeInfo())
                .thenReturn(clusterNodeInfo);

        final ClusterState clusterState = buildClusterState(allNodes);

        when(clusterNodeManager.getClusterState())
                .thenReturn(clusterState);

        final DocumentEventLog documentEventLog = createNamedMock(DocumentEventLog.class, node);

        return new NodeResourceImpl(
                () -> nodeService,
                () -> nodeInfo,
                () -> clusterNodeManager,
                () -> documentEventLog);
    }

    private ClusterState buildClusterState(final List<TestNode> allNodes) {
        final ClusterState clusterState = new ClusterState();
        clusterState.setAllNodes(allNodes.stream()
                .map(TestNode::getNodeName)
                .collect(Collectors.toSet()));
        clusterState.setEnabledNodes(allNodes.stream()
                .filter(TestNode::isEnabled)
                .map(TestNode::getNodeName)
                .collect(Collectors.toSet()));
        clusterState.setMasterNodeName("node1");
        clusterState.setUpdateTime(Instant.now().toEpochMilli());

        return clusterState;
    }
}
