package stroom.proxy.repo.dao.lmdb;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.Env;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;

public class LmdbUtil {

    public static ByteBuffer ofLong(final long l) {
        final ByteBuffer hashByteBuffer = ByteBuffer.allocateDirect(Long.BYTES);
        hashByteBuffer.putLong(l);
        hashByteBuffer.flip();
        return hashByteBuffer;
    }

    public static Optional<Long> getMaxId(final Env<ByteBuffer> env, final Dbi<ByteBuffer> dbi) {
        final ByteBuffer byteBuffer = ofLong(Long.MAX_VALUE);
        KeyRange<ByteBuffer> keyRange = KeyRange.atMostBackward(byteBuffer);
        try (final Txn<ByteBuffer> txn = env.txnRead()) {
            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(txn, keyRange)) {
                if (cursor.iterator().hasNext()) {
                    final KeyVal<ByteBuffer> value = cursor.iterator().next();
                    return Optional.of(value.key().getLong());
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
