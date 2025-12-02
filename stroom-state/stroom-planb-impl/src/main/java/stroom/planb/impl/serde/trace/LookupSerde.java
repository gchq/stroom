package stroom.planb.impl.serde.trace;

import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UsedLookupsRecorder;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

public interface LookupSerde {

    int getStorageLength(byte[] key);

    byte[] read(Txn<ByteBuffer> txn, ByteBuffer nameSlice);

    void write(Txn<ByteBuffer> txn, byte[] key, ByteBuffer byteBuffer);

    boolean usesLookup(ByteBuffer byteBuffer);

    UsedLookupsRecorder getUsedLookupsRecorder(PlanBEnv env);
}
