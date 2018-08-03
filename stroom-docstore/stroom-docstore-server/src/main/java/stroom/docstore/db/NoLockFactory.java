package stroom.docstore.db;

import stroom.docstore.RWLockFactory;

import java.util.function.Supplier;

class NoLockFactory implements RWLockFactory {
    @Override
    public void lock(final String uuid, final Runnable runnable) {
        runnable.run();
    }

    @Override
    public <T> T lockResult(final String uuid, final Supplier<T> supplier) {
        return supplier.get();
    }
}