package stroom.planb.impl.db;

import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

public class UsedLookupsDb {

    private static final ByteBuffer VALUE = ByteBuffer.allocateDirect(0);
    private final Dbi<ByteBuffer> dbi;

    public UsedLookupsDb(final PlanBEnv env,
                         final String name) {
        dbi = env.openDbi(name + "-used", DbiFlags.MDB_CREATE);
    }

    public void record(final LmdbWriter writer, final ByteBuffer key) {
        dbi.put(writer.getWriteTxn(), key, VALUE);
    }

    public boolean isUnused(final Txn<ByteBuffer> readTxn, final ByteBuffer key) {
        return dbi.get(readTxn, key) == null;
    }

    public void drop(final LmdbWriter writer) {
        dbi.drop(writer.getWriteTxn());
    }
}
