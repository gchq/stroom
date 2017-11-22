package stroom.docstore.server.fs;

import stroom.docstore.server.RWLock;
import stroom.docstore.server.RWLockFactory;

import java.util.concurrent.locks.Lock;

final class StripedLockFactory implements RWLockFactory {
    private final StripedLock stripedLock = new StripedLock();

    @Override
    public RWLock lock(final String uuid) {
        final Lock lock = stripedLock.getLockForKey(uuid);
        return new Impl(lock);
    }

    class Impl implements RWLock {
        private final Lock lock;

        Impl(Lock lock) {
            this.lock = lock;
            lock.lock();
        }

        @Override
        public void close() {
            lock.unlock();
        }
    }
}