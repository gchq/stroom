package stroom.cluster.api;

import java.util.Optional;
import java.util.Set;

public interface ClusterService {

    boolean isLeader();

    boolean isLeaderForRole(ClusterRole role);

    void lock(String lockName, Runnable runnable);

    void tryLock(String lockName, Runnable runnable);

    String getLeader();

    String getLocal();

    Set<String> getMembers();
}
