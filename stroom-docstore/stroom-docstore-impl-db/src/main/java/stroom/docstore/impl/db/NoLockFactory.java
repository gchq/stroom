package stroom.docstore.impl.db;


import stroom.docstore.api.RWLockFactory;

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