package stroom.pipeline.refdata.store.offheapstore.databases;

import stroom.lmdb.PutOutcome;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * This is a common interface for the different DBs containing the reference data entries
 */
public interface EntryStoreDb<K> {

    void serializeKey(final ByteBuffer keyBuffer, K key);

    PutOutcome put(final Txn<ByteBuffer> writeTxn,
                   final ByteBuffer keyBuffer,
                   final ByteBuffer valueBuffer,
                   final boolean overwriteExisting);

    boolean delete(final Txn<ByteBuffer> writeTxn, final ByteBuffer keyBuffer);

    Optional<ByteBuffer> getAsBytes(Txn<ByteBuffer> txn, final ByteBuffer keyBuffer);
}
