package stroom.cluster.mock;

import stroom.cluster.api.ClusterMember;
import stroom.cluster.api.ClusterRole;
import stroom.cluster.api.ClusterService;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.inject.Inject;

public class MockClusterService implements ClusterService {

    private final ClusterMember clusterMember;

    @Inject
    public MockClusterService() {
        this.clusterMember = new ClusterMember(UUID.randomUUID().toString());
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
    public ClusterMember getLeader() {
        return clusterMember;
    }

    @Override
    public ClusterMember getLocal() {
        return clusterMember;
    }

    @Override
    public Set<ClusterMember> getMembers() {
        return Collections.singleton(clusterMember);
    }

    @Override
    public ClusterMember getMemberForOldNodeName(final String oldNodeName) {
        return clusterMember;
    }

    @Override
    public Optional<ClusterMember> getOptionalMemberForOldNodeName(final String oldNodeName) {
        return Optional.of(clusterMember);
    }
}
