package stroom.proxy.repo.dao.lmdb;

import stroom.proxy.repo.dao.lmdb.serde.IntegerSerde;
import stroom.proxy.repo.dao.lmdb.serde.LongSerde;
import stroom.proxy.repo.dao.lmdb.serde.Serde;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;

public class LmdbUtil {

    public static Optional<Integer> getMaxIntegerId(final Env<ByteBuffer> env, final Dbi<ByteBuffer> dbi) {
        return getMaxId(env, dbi, new IntegerSerde(), Integer.MAX_VALUE);
    }

    public static Optional<Long> getMaxLongId(final Env<ByteBuffer> env, final Dbi<ByteBuffer> dbi) {
        return getMaxId(env, dbi, new LongSerde(), Long.MAX_VALUE);
    }

    public static <T> Optional<T> getMaxId(final Env<ByteBuffer> env,
                                           final Dbi<ByteBuffer> dbi,
                                           final Serde<T> serde,
                                           final T maxValue) {
        final ByteBuffer byteBuffer = serde.serialise(maxValue);
        KeyRange<ByteBuffer> keyRange = KeyRange.atMostBackward(byteBuffer);
        try (final Txn<ByteBuffer> txn = env.txnRead()) {
            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(txn, keyRange)) {
                if (cursor.iterator().hasNext()) {
                    final KeyVal<ByteBuffer> value = cursor.iterator().next();
                    return Optional.of(serde.deserialise(value.key()));
                }
            }
            return Optional.empty();
        }
    }

    public static void deleteAll(final Txn<ByteBuffer> txn, final Dbi<ByteBuffer> dbi) {
        try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(txn, KeyRange.all())) {
            for (final KeyVal<ByteBuffer> kv : cursorIterable) {
                dbi.delete(kv.key());
            }
        }
    }

    public static long count(final Txn<ByteBuffer> txn, final Dbi<ByteBuffer> dbi) {
        long count = 0;
        try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(txn, KeyRange.all())) {
            for (final KeyVal<ByteBuffer> kv : cursorIterable) {
                count++;
            }
        }
        return count;
    }
}
