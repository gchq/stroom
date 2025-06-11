package stroom.planb.impl.db;

import stroom.planb.impl.serde.val.VariableValType;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

public class VariableUsedLookupsRecorder implements UsedLookupsRecorder {

    private final UidLookupRecorder uidLookupRecorder;
    private final HashLookupRecorder hashLookupRecorder;

    public VariableUsedLookupsRecorder(final PlanBEnv env,
                                       final UidLookupDb uidLookupDb,
                                       final HashLookupDb hashLookupDb) {
        uidLookupRecorder = new UidLookupRecorder(env, uidLookupDb);
        hashLookupRecorder = new HashLookupRecorder(env, hashLookupDb);
    }

    @Override
    public void recordUsed(final LmdbWriter writer, final ByteBuffer byteBuffer) {
        // Read the variable type.
        final VariableValType valType = VariableValType.PRIMITIVE_VALUE_CONVERTER
                .fromPrimitiveValue(byteBuffer.get());
        switch (valType) {
            case UID_LOOKUP -> uidLookupRecorder.recordUsed(writer, byteBuffer);
            case HASH_LOOKUP -> hashLookupRecorder.recordUsed(writer, byteBuffer);
            default -> {
                // Do nothing.
            }
        }
    }

    @Override
    public void deleteUnused(final Txn<ByteBuffer> readTxn, final LmdbWriter writer) {
        uidLookupRecorder.deleteUnused(readTxn, writer);
        hashLookupRecorder.deleteUnused(readTxn, writer);
    }
}
