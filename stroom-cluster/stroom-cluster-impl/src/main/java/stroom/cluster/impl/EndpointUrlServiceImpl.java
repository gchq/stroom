package stroom.cluster.impl;

import stroom.cluster.api.ClusterMember;
import stroom.cluster.api.EndpointUrlService;

import javax.inject.Inject;

public class EndpointUrlServiceImpl implements EndpointUrlService {

    private final ClusterServiceImpl clusterService;

    @Inject
    public EndpointUrlServiceImpl(final ClusterServiceImpl clusterService) {
        this.clusterService = clusterService;
    }

    /**
     * @param nodeName The name of the node to get the base endpoint for
     * @return The base endpoint url for inter-node communications, e.g. http://some-fqdn:8080
     */
    @Override
    public String getRemoteEndpointUrl(final ClusterMember member) {
        // A normal url is something like "http://fqdn:8080"
        final String url = clusterService.getBaseEndpointUrl(member);

        if (url == null || url.isBlank()) {
            throw new RuntimeException("Remote node '" + member + "' has no URL set");
        }

        final String thisNodeUrl = clusterService.getBaseEndpointUrl(clusterService.getLocal());
        if (url.equals(thisNodeUrl)) {
            throw new RuntimeException("Remote node '" + member + "' is using the same URL as this node");
        }

        return url;
    }

    @Override
    public String getBaseEndpointUrl(final ClusterMember member) {
        return clusterService.getBaseEndpointUrl(member);
    }

    /**
     * @return True if the work should be executed on the local node.
     * I.e. if nodeName equals the name of the local node
     */
    @Override
    public boolean shouldExecuteLocally(final ClusterMember member) {
        final ClusterMember local = clusterService.getLocal();

        // If this is the node that was contacted then just return our local info.
        return local.equals(member);
    }
}
