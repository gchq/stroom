package stroom.pipeline.refdata.store.offheapstore.databases;

import stroom.lmdb.PutOutcome;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;

public interface StagingDb<K> {

    void serializeKey(final ByteBuffer keyBuffer, K key);

    PutOutcome put(final Txn<ByteBuffer> writeTxn,
                   final ByteBuffer keyBuffer,
                   final ByteBuffer valueBuffer,
                   final boolean overwriteExisting,
                   final boolean arePutsInKeyOrder);

    String getDbName();
}
