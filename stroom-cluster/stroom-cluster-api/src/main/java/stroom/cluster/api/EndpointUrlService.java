package stroom.cluster.api;

public interface EndpointUrlService {

    String getBaseEndpointUrl(ClusterMember member);

    String getRemoteEndpointUrl(ClusterMember member);

    /**
     * @return True if the work should be executed on the local member.
     * I.e. if member equals the local member
     */
    boolean shouldExecuteLocally(ClusterMember member);
}
