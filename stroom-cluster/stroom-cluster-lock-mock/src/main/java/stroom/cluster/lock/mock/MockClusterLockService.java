package stroom.cluster.lock.mock;

import stroom.cluster.lock.api.ClusterLockService;

public class MockClusterLockService implements ClusterLockService {
    @Override
    public void tryLock(final String lockName, final Runnable runnable) {
        runnable.run();
    }

    @Override
    public void lock(final String lockName, final Runnable runnable) {
        runnable.run();
    }
}
