package stroom.cluster.api;

import java.util.Set;

public interface EndpointUrlService {

    Set<String> getNodeNames();

    String getBaseEndpointUrl(String nodeName);

    String getRemoteEndpointUrl(String nodeName);

    /**
     * @return True if the work should be executed on the local node.
     * I.e. if nodeName equals the name of the local node
     */
    boolean shouldExecuteLocally(String nodeName);
}
