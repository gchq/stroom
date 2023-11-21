package stroom.lmdb.topic;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.bytebuffer.PooledByteBufferPair;
import stroom.lmdb.AbstractLmdbDb;
import stroom.lmdb.BasicLmdbDb;
import stroom.lmdb.LmdbEnv;
import stroom.lmdb.serde.Serde;

import org.lmdbjava.Cursor;
import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.GetOp;
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractLmdbTopic<K, V> {

    private final AbstractLmdbDb<TopicKey<K>, V> topicDb;
    private final LmdbEnv lmdbEnvironment;
    private final ByteBufferPool byteBufferPool;
    private final TopicKeySerde<K> topicKeySerde;
    private final Serde<V> valueSerde;
    private final AtomicLong lastPublishOffset = new AtomicLong();
    private final AtomicLong lastConsumerOffset = new AtomicLong();
    private final AtomicLong lastCommittedConsumerOffset = new AtomicLong();
    private final DbWriter dbWriter;

    private final BasicLmdbDb<Long, Void> consumerOffsetDb;


    public AbstractLmdbTopic(final LmdbEnv lmdbEnvironment,
                             final ByteBufferPool byteBufferPool,
                             final Serde<K> keySerde,
                             final Serde<V> valueSerde,
                             final String dbName,
                             final DbiFlags... dbiFlags) {
        this.lmdbEnvironment = lmdbEnvironment;
        this.byteBufferPool = byteBufferPool;
        this.topicKeySerde = new TopicKeySerde<>(keySerde);
        this.valueSerde = valueSerde;

        // TODO May want multiple of these two dbs so we can have different consumer offsets
        //  for different threads
        this.topicDb = new AbstractLmdbDb<>(
                lmdbEnvironment,
                byteBufferPool,
                topicKeySerde,
                valueSerde,
                dbName,
                dbiFlags) {
        };

        this.consumerOffsetDb = new BasicLmdbDb<>(
                lmdbEnvironment,
                byteBufferPool,
                new SignedLongSerde(),
                new VoidSerde(),
                dbName,
                dbiFlags);
        dbWriter = new DbWriter(lmdbEnvironment);

        lmdbEnvironment.doWithReadTxn(txn -> {
            lastPublishOffset.set(getLastOffset(txn));
            lastConsumerOffset.set(getLastConsumerOffset(txn));
        });
    }

    private long getLastConsumerOffset(final Txn<ByteBuffer> txn) {
        // Get latest offset from the db if there is one
        return consumerOffsetDb.lastEntry(txn)
                .map(Entry::getKey)
                .orElse(-1L);
    }

    private long getLastOffset(final Txn<ByteBuffer> txn) {
        // Get latest offset from the db if there is one
        return topicDb.lastEntry(txn)
                .map(Entry::getKey)
                .map(TopicKey::getOffset)
                .orElse(-1L);
    }

    /**
     * Publish a message onto the topic
     */
    public long publishSync(final K key,
                            final V value,
                            final boolean commit) {

        // TODO consider an async version of this method if we need
        final long offset = lastPublishOffset.incrementAndGet();

        // TODO consider combining the publish key+value as the topicDb value
        //  to avoid the LMDB key size limitation
        dbWriter.putSync(commit, writeTxn -> {
            topicDb.put(
                    writeTxn,
                    new TopicKey<>(offset, key),
                    value,
                    false,
                    true);
        });

//        if (commit) {
//            lastPublishCommitOffset.set(offset);
//        } else {
//            throw new UnsupportedOperationException("Not sure if we need this");
//        }
        return offset;
    }

    /**
     * Publish a message onto the topic
     */
    public long publishAsync(final K key,
                             final V value,
                             final boolean commit) {

        // TODO consider an async version of this method if we need
        final long offset = lastPublishOffset.incrementAndGet();

        // TODO consider combining the publish key+value as the topicDb value
        //  to avoid the LMDB key size limitation
        dbWriter.putAsync(commit, writeTxn -> {
            topicDb.put(
                    writeTxn,
                    new TopicKey<>(offset, key),
                    value,
                    false,
                    true);
        });

//        if (commit) {
//            lastPublishCommitOffset.set(offset);
//        } else {
//            throw new UnsupportedOperationException("Not sure if we need this");
//        }
        return offset;
    }

    /**
     * Commit the consumer offset position
     */
    public void commitOffset() {
        final long offset = lastConsumerOffset.get();
        if (offset > lastCommittedConsumerOffset.get()) {
            dbWriter.putSync(true, writeTxn -> {
                consumerOffsetDb.put(
                        writeTxn,
                        offset,
                        null,
                        false,
                        true);
            });
            lastCommittedConsumerOffset.set(offset);
        }
    }

    public synchronized Entry<K, V> consume(final boolean commit) {
        final long offset = lastConsumerOffset.incrementAndGet();

        lmdbEnvironment.getWithReadTxn(readTxn -> {
            try (PooledByteBuffer pooledStartKeyBuffer = topicDb.getPooledKeyBuffer()) {
                // Partial key with offset only
                final TopicKey startKey = TopicKey.asStartKey(offset);
                final ByteBuffer startKeyBuffer = pooledStartKeyBuffer.getByteBuffer();
                topicDb.serializeKey(startKeyBuffer, startKey);

                try (Cursor<ByteBuffer> cursor = topicDb.getLmdbDbi().openCursor(readTxn)) {
                    // Set cursor at >= start key, i.e. at the offset
                    cursor.get(startKeyBuffer, GetOp.MDB_SET_RANGE);
                    final V val = topicDb.deserializeValue(cursor.val());
                    Objects.requireNonNull(val, () -> "No value for offset " + offset);
                    return val;
                }
            }
        });
        return null;
    }
}
