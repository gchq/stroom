package stroom.test.common.util.test;

import stroom.cluster.api.ClusterMember;
import stroom.cluster.api.ClusterRole;
import stroom.cluster.api.ClusterService;
import stroom.test.common.util.test.AbstractMultiNodeResourceTest.TestMember;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class MockClusterService implements ClusterService {

    private final TestMember local;
    private final List<TestMember> members;

    public MockClusterService(final TestMember local,
                              final List<TestMember> members) {
        this.local = local;
        this.members = members;
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
        return local.getMember();
    }

    @Override
    public ClusterMember getLocal() {
        return local.getMember();
    }

    @Override
    public Set<ClusterMember> getMembers() {
        return members
                .stream()
                .filter(TestMember::isEnabled)
                .map(TestMember::getMember)
                .collect(Collectors.toSet());
    }

    @Override
    public ClusterMember getMemberForOldNodeName(final String oldNodeName) {
        return getOptionalMemberForOldNodeName(oldNodeName)
                .orElseThrow(() -> new RuntimeException("Cluster member not found for '" + oldNodeName + "'"));
    }

    @Override
    public Optional<ClusterMember> getOptionalMemberForOldNodeName(final String oldNodeName) {
        return members
                .stream()
                .filter(member -> member.getUuid().equals(oldNodeName))
                .map(TestMember::getMember)
                .findFirst();
    }
}
