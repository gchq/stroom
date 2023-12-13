package stroom.proxy.repo.dao.lmdb;

import stroom.proxy.repo.dao.lmdb.serde.IntegerSerde;

import org.lmdbjava.Dbi;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class IntegerRowKey implements RowKey<Integer> {

    private final AtomicInteger rowId = new AtomicInteger();

    public IntegerRowKey(final LmdbEnv env,
                         final Dbi<ByteBuffer> dbi,
                         final IntegerSerde integerSerde) {
        final Optional<Integer> maxId = env.getMaxKey(dbi, integerSerde);
        rowId.set(maxId.orElse(0));
    }

    @Override
    public Integer next() {
        return rowId.incrementAndGet();
    }
}
