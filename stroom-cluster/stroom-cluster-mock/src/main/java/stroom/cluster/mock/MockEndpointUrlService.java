package stroom.cluster.mock;

import stroom.cluster.api.EndpointUrlService;

import java.util.Set;

public class MockEndpointUrlService implements EndpointUrlService {

    @Override
    public Set<String> getNodeNames() {
        return null;
    }

    @Override
    public String getBaseEndpointUrl(final String nodeName) {
        return null;
    }

    @Override
    public String getRemoteEndpointUrl(final String nodeName) {
        return null;
    }

    @Override
    public boolean shouldExecuteLocally(final String nodeName) {
        return true;
    }
}
