package stroom.planb.impl.db;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.function.Consumer;

public class UsedLookupsDb {

    private static final ByteBuffer VALUE = ByteBuffer.allocateDirect(0);
    private final Dbi<ByteBuffer> dbi;

    public UsedLookupsDb(final PlanBEnv env,
                         final String name) {
        dbi = env.openDbi(name, DbiFlags.MDB_CREATE);
    }

    public void record(final LmdbWriter writer, final ByteBuffer key) {
        dbi.put(writer.getWriteTxn(), key, VALUE);
    }

    public boolean isUnused(final Txn<ByteBuffer> readTxn, final ByteBuffer key) {
        return dbi.get(readTxn, key) == null;
    }

    public void iterate(final Txn<ByteBuffer> readTxn, final Consumer<ByteBuffer> keyConsumer) {
        try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn)) {
            final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
            while (iterator.hasNext()
                   && !Thread.currentThread().isInterrupted()) {
                final KeyVal<ByteBuffer> kv = iterator.next();
                keyConsumer.accept(kv.key());
            }
        }
    }

    public void drop(final LmdbWriter writer) {
        dbi.drop(writer.getWriteTxn());
    }
}
