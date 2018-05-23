package stroom.docstore.fs;

import stroom.docstore.RWLockFactory;

import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

final class StripedLockFactory implements RWLockFactory {
    private final StripedLock stripedLock = new StripedLock();

    @Override
    public void lock(final String uuid, final Runnable runnable) {
        final Lock lock = stripedLock.getLockForKey(uuid);
        lock.lock();
        try {
            runnable.run();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public <T> T lockResult(final String uuid, final Supplier<T> supplier) {
        final Lock lock = stripedLock.getLockForKey(uuid);
        lock.lock();
        try {
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }
}