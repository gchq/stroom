package stroom.proxy.repo.dao.lmdb;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class IntegerRowKey implements RowKey<Integer> {

    private final AtomicInteger rowId = new AtomicInteger();

    public IntegerRowKey(final Db<Integer, ?> db) {
        final Optional<Integer> maxId = db.getMaxKey();
        rowId.set(maxId.orElse(0));
    }

    @Override
    public Integer next() {
        return rowId.incrementAndGet();
    }
}
