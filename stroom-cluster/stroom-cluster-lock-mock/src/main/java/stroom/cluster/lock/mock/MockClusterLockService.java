package stroom.cluster.lock.mock;

import stroom.cluster.lock.api.ClusterLockService;

import java.util.function.Supplier;

public class MockClusterLockService implements ClusterLockService {

    @Override
    public void tryLock(final String lockName, final Runnable runnable) {
        runnable.run();
    }

    @Override
    public void lock(final String lockName, final Runnable runnable) {
        runnable.run();
    }

    @Override
    public <T> T lockResult(final String lockName, final Supplier<T> supplier) {
        return supplier.get();
    }
}
