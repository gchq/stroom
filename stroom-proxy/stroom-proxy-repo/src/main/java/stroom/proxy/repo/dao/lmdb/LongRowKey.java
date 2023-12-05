package stroom.proxy.repo.dao.lmdb;

import org.lmdbjava.Dbi;
import org.lmdbjava.Env;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class LongRowKey implements RowKey<Long> {

    private final AtomicLong rowId = new AtomicLong();

    public LongRowKey(final Env<ByteBuffer> env, final Dbi<ByteBuffer> dbi) {
        final Optional<Long> maxId = LmdbUtil.getMaxLongId(env, dbi);
        rowId.set(maxId.orElse(0L));
    }

    @Override
    public Long next() {
        return rowId.incrementAndGet();
    }
}
