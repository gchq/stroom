package stroom.planb.impl.serde;

import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UsedLookupsRecorder;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public interface Serde<T> {

    UsedLookupsRecorder USED_LOOKUPS_RECORDER = new UsedLookupsRecorder() {
        @Override
        public void recordUsed(final LmdbWriter writer, final ByteBuffer byteBuffer) {

        }

        @Override
        public void deleteUnused(final Txn<ByteBuffer> readTxn, final LmdbWriter writer) {

        }
    };

    void write(Txn<ByteBuffer> txn, T value, Consumer<ByteBuffer> consumer);

    T read(Txn<ByteBuffer> txn, ByteBuffer byteBuffer);

    default boolean usesLookup(final ByteBuffer byteBuffer) {
        return false;
    }

    default UsedLookupsRecorder getUsedLookupsRecorder(final PlanBEnv env) {
        return USED_LOOKUPS_RECORDER;
    }
}
