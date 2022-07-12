package stroom.cluster.mock;

import stroom.cluster.api.ClusterMember;
import stroom.cluster.api.EndpointUrlService;

public class MockEndpointUrlService implements EndpointUrlService {

    @Override
    public String getBaseEndpointUrl(final ClusterMember member) {
        return null;
    }

    @Override
    public String getRemoteEndpointUrl(final ClusterMember member) {
        return null;
    }

    @Override
    public boolean shouldExecuteLocally(final ClusterMember member) {
        return true;
    }
}
