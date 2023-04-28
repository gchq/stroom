package stroom.pipeline.refdata.store.offheapstore.databases;

import stroom.bytebuffer.PooledByteBuffer;
import stroom.lmdb.PutOutcome;
import stroom.pipeline.refdata.store.offheapstore.UID;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * This is a common interface for the different DBs containing the reference data entries
 */
public interface EntryStoreDb<K> {

    void serializeKey(final ByteBuffer keyBuffer, K key);

    /**
     * @param writeTxn
     * @param keyBuffer
     * @param valueBuffer
     * @param overwriteExisting
     * @param isAppending Only set to true if you are sure that the key will be at the end of the db
     * @return
     */
    PutOutcome put(final Txn<ByteBuffer> writeTxn,
                   final ByteBuffer keyBuffer,
                   final ByteBuffer valueBuffer,
                   final boolean overwriteExisting,
                   final boolean isAppending);

    boolean delete(final Txn<ByteBuffer> writeTxn, final ByteBuffer keyBuffer);

    Optional<ByteBuffer> getAsBytes(Txn<ByteBuffer> txn, final ByteBuffer keyBuffer);

    Optional<UID> getMaxUid(final Txn<ByteBuffer> txn, PooledByteBuffer pooledByteBuffer);
}
