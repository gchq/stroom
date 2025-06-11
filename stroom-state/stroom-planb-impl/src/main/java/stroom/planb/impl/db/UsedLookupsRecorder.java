package stroom.planb.impl.db;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

public interface UsedLookupsRecorder {

    void recordUsed(LmdbWriter writer, ByteBuffer byteBuffer);

    void deleteUnused(Txn<ByteBuffer> readTxn, LmdbWriter writer);
}
