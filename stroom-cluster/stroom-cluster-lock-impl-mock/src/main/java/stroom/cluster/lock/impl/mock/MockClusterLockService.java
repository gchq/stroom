package stroom.cluster.lock.impl.mock;

import stroom.cluster.lock.api.ClusterLockService;

public class MockClusterLockService implements ClusterLockService {
    @Override
    public boolean tryLock(final String lockName) {
        return true;
    }

    @Override
    public void releaseLock(final String lockName) {
    }

    @Override
    public void lock(final String lockName, final Runnable runnable) {
        runnable.run();
    }
}
