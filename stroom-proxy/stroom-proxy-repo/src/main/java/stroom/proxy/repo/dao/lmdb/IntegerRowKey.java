package stroom.proxy.repo.dao.lmdb;

import org.lmdbjava.Dbi;
import org.lmdbjava.Env;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class IntegerRowKey implements RowKey<Integer> {

    private final AtomicInteger rowId = new AtomicInteger();

    public IntegerRowKey(final Env<ByteBuffer> env, final Dbi<ByteBuffer> dbi) {
        final Optional<Integer> maxId = LmdbUtil.getMaxIntegerId(env, dbi);
        rowId.set(maxId.orElse(0));
    }

    @Override
    public Integer next() {
        return rowId.incrementAndGet();
    }
}
