package stroom.test.common.util.test;

import stroom.cluster.api.EndpointUrlService;
import stroom.test.common.util.test.AbstractMultiNodeResourceTest.TestNode;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MockEndpointUrlService implements EndpointUrlService {

    private final TestNode node;
    private final List<TestNode> allNodes;
    private final Map<String, String> baseEndPointUrl;

    public MockEndpointUrlService(final TestNode node,
                                  final List<TestNode> allNodes,
                                  final Map<String, String> baseEndPointUrl) {
        this.node = node;
        this.allNodes = allNodes;
        this.baseEndPointUrl = baseEndPointUrl;
    }

    @Override
    public Set<String> getNodeNames() {
        return allNodes.stream().map(TestNode::getNodeName).collect(Collectors.toSet());
    }

    @Override
    public String getBaseEndpointUrl(final String nodeName) {
        return baseEndPointUrl.get(nodeName);
    }

    @Override
    public String getRemoteEndpointUrl(final String nodeName) {
        return baseEndPointUrl.get(nodeName);
    }

    @Override
    public boolean shouldExecuteLocally(final String nodeName) {
        return node.getNodeName().equals(nodeName);
    }
}
