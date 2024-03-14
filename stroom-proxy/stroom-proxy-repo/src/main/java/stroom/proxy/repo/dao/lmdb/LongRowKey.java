package stroom.proxy.repo.dao.lmdb;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class LongRowKey implements RowKey<Long> {

    private final AtomicLong rowId = new AtomicLong();

    public LongRowKey(final Db<Long, ?> db) {
        final Optional<Long> maxId = db.getMaxKey();
        rowId.set(maxId.orElse(0L));
    }

    @Override
    public Long next() {
        return rowId.incrementAndGet();
    }
}
