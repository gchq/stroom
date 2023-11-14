package stroom.lmdb.topic;

import stroom.bytebuffer.ByteBufferPool;
import stroom.lmdb.AbstractLmdbDb;
import stroom.lmdb.BasicLmdbDb;
import stroom.lmdb.LmdbEnv;
import stroom.lmdb.LmdbEnv.WriteTxn;
import stroom.lmdb.serde.Serde;

import org.lmdbjava.DbiFlags;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TransferQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public abstract class AbstractLmdbTopic<K, V> {

    private final AbstractLmdbDb<TopicKey<K>, V> topicDb;
    private final LmdbEnv lmdbEnvironment;
    private final ByteBufferPool byteBufferPool;
    private final TopicKeySerde<K> topicKeySerde;
    private final Serde<V> valueSerde;
    private final AtomicLong lastPublishOffset = new AtomicLong();
    private final AtomicLong lastConsumerOffset = new AtomicLong();
    private final AtomicLong lastCommittedConsumerOffset = new AtomicLong();

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
    public long publish(final Txn<ByteBuffer> writeTxn,
                        final K key,
                        final V value) {

        // TODO consider an async version of this method if we need
        final long offset = lastPublishOffset.incrementAndGet();

        // TODO consider combining the publish key+value as the topicDb value
        //  to avoid the LMDB key size limitation
        topicDb.put(
                writeTxn,
                new TopicKey<>(offset, key),
                value,
                false,
                true);

//        if (commit) {
//            lastPublishCommitOffset.set(offset);
//        } else {
//            throw new UnsupportedOperationException("Not sure if we need this");
//        }
        return offset;
    }

    /**
     * Commit the consumer offset position
     * @param writeTxn
     */
    public void commit(final Txn<ByteBuffer> writeTxn) {
        final long offset = lastConsumerOffset.get();
        if (offset > lastCommittedConsumerOffset.get()) {
            consumerOffsetDb.put(
                    writeTxn,
                    offset,
                    null,
                    false,
                    true);
            lastCommittedConsumerOffset.set(offset);
        }
    }

//    public synchronized Entry<K, V> consume(final Txn<ByteBuffer> writeTxn,
//                                            final boolean commit) {
//
////        lastConsumerOffset.ac
//
//    }


    // --------------------------------------------------------------------------------


    private static class TopicKey<K> {

        private final long offset;
        private final K key;

        public TopicKey(final long offset, final K key) {
            this.offset = offset;
            this.key = key;
        }

        public long getOffset() {
            return offset;
        }

        public K getKey() {
            return key;
        }
    }


    // --------------------------------------------------------------------------------


    private static class TopicKeySerde<K> implements Serde<TopicKey<K>> {

        private static final int OFFSET_BYTES = Long.BYTES;
        private final Serde<K> keySerde;

        private TopicKeySerde(final Serde<K> keySerde) {
            this.keySerde = keySerde;
        }

        @Override
        public TopicKey<K> deserialize(final ByteBuffer byteBuffer) {
            final long offset = byteBuffer.getLong();
            final K key = keySerde != null
                    ? keySerde.deserialize(byteBuffer.slice())
                    : null;
            byteBuffer.rewind();
            return new TopicKey<>(offset, key);
        }

        @Override
        public void serialize(final ByteBuffer byteBuffer, final TopicKey<K> topicKey) {
            byteBuffer.putLong(topicKey.getOffset());
            if (keySerde != null) {
                keySerde.serialize(byteBuffer, topicKey.getKey());
            }
            byteBuffer.flip();
        }
    }


    // --------------------------------------------------------------------------------


    private static class VoidSerde implements Serde<Void> {

        private VoidSerde() {
            super();
        }

        @Override
        public Void deserialize(final ByteBuffer byteBuffer) {
            byteBuffer.rewind();
            return null;
        }

        @Override
        public void serialize(final ByteBuffer byteBuffer, final Void object) {
            // Nothing to serialise
            byteBuffer.flip();
        }
    }


    // --------------------------------------------------------------------------------


    private static class SignedLongSerde implements Serde<Long> {

        @Override
        public Long deserialize(final ByteBuffer byteBuffer) {
            final long val = byteBuffer.getLong();
            byteBuffer.rewind();
            return val;
        }

        @Override
        public void serialize(final ByteBuffer byteBuffer, final Long val) {
            byteBuffer.putLong(val);
            byteBuffer.flip();
        }
    }


    // --------------------------------------------------------------------------------


    private static class TxnAction {

        private final Consumer<Txn<ByteBuffer>> action;
        private final boolean commit;

        private TxnAction(final Consumer<Txn<ByteBuffer>> action, final boolean commit) {
            this.action = Objects.requireNonNull(action);
            this.commit = commit;
        }

        public void run(final Txn<ByteBuffer> txn) {
            action.accept(txn);
        }

        public boolean shouldCommit() {
            return commit;
        }
    }


    private static class DbWriter {

        private final LmdbEnv lmdbEnv;
        private final TransferQueue<TxnAction> actionTransferQueue;
        private final ExecutorService executorService;
        private final WriteTxn writeTxn;
        private Instant lastCommitTime = Instant.now();
        private int actionsSinceLastCommit = 0;

        public DbWriter(final LmdbEnv lmdbEnv) {
            this.lmdbEnv = lmdbEnv;
            this.actionTransferQueue = new LinkedTransferQueue<>();
            this.executorService = Executors.newSingleThreadExecutor();
            // We are the only writer so can hold the txn open indefinitely
            this.writeTxn = lmdbEnv.openWriteTxn();

            executorService.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    final TxnAction txnAction = actionTransferQueue.poll(2, TimeUnit);
                    if (txnAction != null) {
                        try {
                            txnAction.run(writeTxn.getTxn());
                        } catch (Exception e) {
                            // TODO Can't throw, need to put ex in a future or similar
                        }
                    }
                }
            });
        }

        public void putSync(final Consumer<Txn<ByteBuffer>> action, final boolean commit) {
            if (action != null) {
                try {
                    actionTransferQueue.transfer(new TxnAction(action, commit));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted");
                }
            }
        }

        // TODO may want to return a Future or accept a callback
        public void putAsync(final Consumer<Txn<ByteBuffer>> action, final boolean commit) {
            if (action != null) {
                try {
                    actionTransferQueue.put(new TxnAction(action, commit));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted");
                }
            }
        }
    }
}
