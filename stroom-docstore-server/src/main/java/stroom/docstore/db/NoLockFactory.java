package stroom.docstore.db;

import stroom.docstore.RWLock;
import stroom.docstore.RWLockFactory;

class NoLockFactory implements RWLockFactory {
    @Override
    public RWLock lock(final String uuid) {
        return new Impl();
    }

    class Impl implements RWLock {
        Impl() {
        }

        @Override
        public void close() {
        }
    }
}