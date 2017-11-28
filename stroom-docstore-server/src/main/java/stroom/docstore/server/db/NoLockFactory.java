package stroom.docstore.server.db;

import stroom.docstore.server.RWLock;
import stroom.docstore.server.RWLockFactory;

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