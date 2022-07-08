package stroom.cluster.mock;

import stroom.cluster.api.ClusterRole;
import stroom.cluster.api.ClusterService;
import stroom.cluster.api.NodeInfo;

import java.util.Set;
import javax.inject.Inject;

public class MockClusterService implements ClusterService {

    private final NodeInfo nodeInfo;

    @Inject
    public MockClusterService(final NodeInfo nodeInfo) {
        this.nodeInfo = nodeInfo;
    }

    @Override
    public boolean isLeader() {
        return true;
    }

    @Override
    public boolean isLeaderForRole(final ClusterRole role) {
        return true;
    }

    @Override
    public void lock(final String lockName, final Runnable runnable) {
        runnable.run();
    }

    @Override
    public void tryLock(final String lockName, final Runnable runnable) {
        runnable.run();
    }

    @Override
    public String getLeader() {
        return nodeInfo.getThisNodeName();
    }

    @Override
    public String getLocal() {
        return nodeInfo.getThisNodeName();
    }

    @Override
    public Set<String> getMembers() {
        return Set.of(nodeInfo.getThisNodeName());
    }
}
