package stroom.planb.impl.db;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

public class HashLookupRecorder implements UsedLookupsRecorder {

    private final UsedLookupsDb usedLookupsDb;
    private final HashLookupDb hashLookupDb;

    public HashLookupRecorder(final PlanBEnv env,
                              final HashLookupDb hashLookupDb) {
        this.usedLookupsDb = new UsedLookupsDb(env, hashLookupDb.getName() + "-HashLookupDb");
        this.hashLookupDb = hashLookupDb;
    }

    @Override
    public void recordUsed(final LmdbWriter writer, final ByteBuffer byteBuffer) {
        usedLookupsDb.record(writer, byteBuffer);
    }

    @Override
    public void deleteUnused(final Txn<ByteBuffer> readTxn, final LmdbWriter writer) {
        hashLookupDb.forEachHash(readTxn, hash -> {
            if (usedLookupsDb.isUnused(writer.getWriteTxn(), hash)) {
                hashLookupDb.deleteByHash(writer.getWriteTxn(), hash);
                writer.tryCommit();
            }
        });
        usedLookupsDb.drop(writer);
    }
}
