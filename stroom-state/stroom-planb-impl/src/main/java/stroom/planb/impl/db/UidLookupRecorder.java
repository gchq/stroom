package stroom.planb.impl.db;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

public class UidLookupRecorder implements UsedLookupsRecorder {

    private final UsedLookupsDb usedLookupsDb;
    private final UidLookupDb uidLookupDb;

    public UidLookupRecorder(final PlanBEnv env,
                             final UidLookupDb uidLookupDb) {
        this.usedLookupsDb = new UsedLookupsDb(env, uidLookupDb.getName() + "-UidLookupDb");
        this.uidLookupDb = uidLookupDb;
    }

    @Override
    public void recordUsed(final LmdbWriter writer, final ByteBuffer byteBuffer) {
        usedLookupsDb.record(writer, byteBuffer);
    }

    public void recordUsed(final LmdbWriter writer, final long uid) {
        uidLookupDb.uidToByteBuffer(uid, byteBuffer -> {
            usedLookupsDb.record(writer, byteBuffer);
            return null;
        });
    }

    @Override
    public void deleteUnused(final Txn<ByteBuffer> readTxn, final LmdbWriter writer) {
        uidLookupDb.forEachUid(readTxn, uid -> {
            if (usedLookupsDb.isUnused(writer.getWriteTxn(), uid)) {
                uidLookupDb.deleteByUid(writer.getWriteTxn(), uid);
                writer.tryCommit();
            }
        });
        usedLookupsDb.drop(writer);
    }
}
