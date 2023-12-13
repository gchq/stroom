package stroom.proxy.repo.dao.lmdb;

import stroom.proxy.repo.dao.lmdb.serde.LongSerde;

import org.lmdbjava.Dbi;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public class LongRowKey implements RowKey<Long> {

    private final AtomicLong rowId = new AtomicLong();

    public LongRowKey(final LmdbEnv env,
                      final Dbi<ByteBuffer> dbi,
                      final LongSerde longSerde) {
        final Optional<Long> maxId = env.getMaxKey(dbi, longSerde);
        rowId.set(maxId.orElse(0L));
    }

    @Override
    public Long next() {
        return rowId.incrementAndGet();
    }
}
