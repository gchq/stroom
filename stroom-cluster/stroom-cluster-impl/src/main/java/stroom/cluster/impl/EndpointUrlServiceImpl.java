package stroom.cluster.impl;

import stroom.cluster.api.EndpointUrlService;

import java.util.Set;
import javax.inject.Inject;

public class EndpointUrlServiceImpl implements EndpointUrlService {
    private final ClusterServiceImpl clusterService;

    @Inject
    public EndpointUrlServiceImpl(final ClusterServiceImpl clusterService) {
        this.clusterService = clusterService;
    }

    @Override
    public Set<String> getNodeNames() {
        return clusterService.getNodeNames();
    }

    /**
     * @param nodeName The name of the node to get the base endpoint for
     * @return The base endpoint url for inter-node communications, e.g. http://some-fqdn:8080
     */
    @Override
    public String getRemoteEndpointUrl(final String nodeName) {
        // A normal url is something like "http://fqdn:8080"
        final String url = clusterService.getBaseEndpointUrl(nodeName);

        if (url == null || url.isBlank()) {
            throw new RuntimeException("Remote node '" + nodeName + "' has no URL set");
        }

        final String thisNodeUrl = clusterService.getBaseEndpointUrl(clusterService.getLocalNodeName().orElseThrow());
        if (url.equals(thisNodeUrl)) {
            throw new RuntimeException("Remote node '" + nodeName + "' is using the same URL as this node");
        }

        return url;
    }

    @Override
    public String getBaseEndpointUrl(final String nodeName) {
        return clusterService.getBaseEndpointUrl(nodeName);
    }

    /**
     * @return True if the work should be executed on the local node.
     * I.e. if nodeName equals the name of the local node
     */
    @Override
    public boolean shouldExecuteLocally(final String nodeName) {
        final String thisNodeName = clusterService.getLocalNodeName().orElse(null);
        if (thisNodeName == null) {
            throw new RuntimeException("This node has no name");
        }

        // If this is the node that was contacted then just return our local info.
        return thisNodeName.equals(nodeName);
    }
}
