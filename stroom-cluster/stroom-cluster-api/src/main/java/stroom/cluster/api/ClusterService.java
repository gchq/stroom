package stroom.cluster.api;

import java.util.Optional;
import java.util.Set;

public interface ClusterService {

    boolean isLeader();

    boolean isLeaderForRole(ClusterRole role);

    void lock(String lockName, Runnable runnable);

    void tryLock(String lockName, Runnable runnable);

    ClusterMember getLeader();

    ClusterMember getLocal();

    Set<ClusterMember> getMembers();

    ClusterMember getMemberForOldNodeName(String oldNodeName);

    Optional<ClusterMember> getOptionalMemberForOldNodeName(String oldNodeName);
}
