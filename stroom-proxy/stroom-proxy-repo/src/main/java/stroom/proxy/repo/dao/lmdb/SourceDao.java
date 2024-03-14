package stroom.proxy.repo.dao.lmdb;

import stroom.proxy.repo.FeedAndType;
import stroom.proxy.repo.RepoSource;
import stroom.proxy.repo.dao.lmdb.serde.LongSerde;
import stroom.proxy.repo.dao.lmdb.serde.RepoSourceValueSerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;
import stroom.util.shared.Flushable;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Singleton
public class SourceDao implements Clearable, Flushable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SourceDao.class);

    //    private final LmdbEnv env;
    private final Db<Long, RepoSourceValue> db;


    private final FeedDao feedDao;
//    private final LongSerde keySerde;
//    private final RepoSourceValueSerde valueSerde;

    private final LmdbQueue<Long> newSourceQueue;
    private final LmdbQueue<Long> examinedSourceQueue;
    private final LmdbQueue<Long> deletableSourceQueue;


//

//
//    public void getAll(final BiConsumer<K, V> consumer) {
//        try (final Txn<ByteBuffer> txn = env.txnRead()) {
//            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(txn)) {
//                for (final KeyVal<ByteBuffer> kv : cursor) {
//                    final K key = getKeySerde().deserialise(kv.key());
//                    final V value = getValueSerde().deserialise(kv.val());
//                    consumer.accept(key, value);
//                }
//            }
//        }
//    }
//
//    public Optional<K> getOptionalId(final V value) {
//        final ByteBuffer valueByteBuffer = getValueSerde().serialise(value);
//        final long hash = ByteBufferUtils.xxHash(valueByteBuffer);
//        final ByteBuffer hashByteBuffer = hashSerde.serialise(hash);
//        return fetchId(dbi, indexDbi, hashByteBuffer, valueByteBuffer);
//    }
//
//    public K getOrCreateId(final V value) {
//        final ByteBuffer valueByteBuffer = getValueSerde().serialise(value);
//        final long hash = ByteBufferUtils.xxHash(valueByteBuffer);
//        final ByteBuffer hashByteBuffer = hashSerde.serialise(hash);
//        Optional<K> id = fetchId(dbi, indexDbi, hashByteBuffer, valueByteBuffer);
//        if (id.isPresent()) {
//            return id.get();
//        }
//
//        writeLock.lock();
//        try {
//            // Try fetch under lock.
//            id = fetchId(dbi, indexDbi, hashByteBuffer, valueByteBuffer);
//            if (id.isPresent()) {
//                return id.get();
//            }
//
//            // Couldn't fetch, try put.
//            final K newId = rowKey.next();
//            final ByteBuffer idByteBuffer = getKeySerde().serialise(newId);
//            put(idByteBuffer, hashByteBuffer, valueByteBuffer);
//            writeQueue.sync();
//
//            return newId;
//
//        } finally {
//            writeLock.unlock();
//        }
//    }
//
//    public void sync() {
//        writeQueue.sync();
//    }
//
//    // TODO : NOT FLUSHED OR SYNCHRONISED
//    public K put(final V value) {
//        final ByteBuffer valueByteBuffer = getValueSerde().serialise(value);
//        final long hash = ByteBufferUtils.xxHash(valueByteBuffer);
//        final ByteBuffer hashByteBuffer = hashSerde.serialise(hash);
//        final K newId = rowKey.next();
//        final ByteBuffer idByteBuffer = getKeySerde().serialise(newId);
//        put(idByteBuffer, hashByteBuffer, valueByteBuffer);
//        return newId;
//    }
//
//    private void put(final ByteBuffer idBuffer,
//                     final ByteBuffer hashByteBuffer,
//                     final ByteBuffer value) {
//        writeQueue.write(txn -> {
//            final ByteBuffer existingIndexValue = indexDbi.get(txn, hashByteBuffer);
//            if (existingIndexValue != null) {
//                final ByteBuffer appended;
//                final int bufferSize = existingIndexValue.remaining() + getKeyLength();
//                try (final MyByteBufferOutput output = new MyByteBufferOutput(bufferSize, bufferSize)) {
//                    output.writeByteBuffer(existingIndexValue);
//                    output.writeByteBuffer(idBuffer);
//                    output.flush();
//                    appended = output.getByteBuffer().flip();
//                }
//                indexDbi.put(txn, hashByteBuffer, appended);
//            } else {
//                indexDbi.put(txn, hashByteBuffer, idBuffer);
//            }
//
//            dbi.put(txn, idBuffer, value);
//        });
//    }
//
//    private Optional<K> fetchId(final Dbi<ByteBuffer> dbi,
//                                final Dbi<ByteBuffer> indexDbi,
//                                final ByteBuffer hashByteBuffer,
//                                final ByteBuffer valueByteBuffer) {
//        try (final Txn<ByteBuffer> txn = env.txnRead()) {
//            final ByteBuffer value = indexDbi.get(txn, hashByteBuffer);
//            if (value != null) {
//                while (value.hasRemaining()) {
//                    final K id = getKeySerde().deserialise(value);
//                    final ByteBuffer index = getKeySerde().serialise(id);
//                    final ByteBuffer storedValue = dbi.get(txn, index);
//                    if (storedValue.equals(valueByteBuffer)) {
//                        return Optional.of(id);
//                    }
//                }
//            }
//            return Optional.empty();
//        }
//    }
//
//    public long count() {
//        try (final Txn<ByteBuffer> txn = env.txnRead()) {
//            return LmdbUtil.count(txn, dbi);
//        }
//    }
//
//    public void clear() {
//        writeQueue.write(txn -> LmdbUtil.deleteAll(txn, dbi));
//        writeQueue.write(txn -> LmdbUtil.deleteAll(txn, indexDbi));
//        writeQueue.commit();
//        writeQueue.sync();
//
//        rowKey = createRowKey(env, dbi);
//    }


    @Inject
    public SourceDao(final LmdbEnv env,
                     final FeedDao feedDao) {
        try {
//            this.env = env;
            db = env.openDb("source", new LongSerde(), new RepoSourceValueSerde());
            newSourceQueue = new LmdbQueue<>(env, "new-source", new LongSerde());
            examinedSourceQueue = new LmdbQueue<>(env, "examined-source", new LongSerde());
            deletableSourceQueue = new LmdbQueue<>(env, "deletable-source", new LongSerde());
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }

        this.feedDao = feedDao;
    }

//    @Override
//    RowKey<Long> createRowKey(final Env<ByteBuffer> env, final Dbi<ByteBuffer> dbi) {
//        return new LongRowKey(env, dbi);
//    }
//
//    @Override
//    Serde<RepoSourcePart> getValueSerde() {
//        return valueSerde;
//    }
//
//    @Override
//    Serde<Long> getKeySerde() {
//        return keySerde;
//    }
//
//    @Override
//    int getKeyLength() {
//        return Long.BYTES;
//    }

//    @Override
//    public long getId(final FeedKey feedKey) {
//        return super.getOrCreateId(feedKey);
//    }
//
//    @Override
//    public FeedKey getKey(final long id) {
//        return get(id).orElse(null);
//    }
//
//    @Override
//    public int countFeeds() {
//        return (int) super.count();
//    }


    public long getMaxFileStoreId() {
        return db.getMaxKey().orElse(0L);
    }

    public void addSource(final long fileStoreId, final String feedName, final String typeName) {
        final long feedId = feedDao.getId(new FeedAndType(feedName, typeName));
        final RepoSourceValue value = new RepoSourceValue(feedId, 0);
        db.put(fileStoreId, value);

//        final PooledByteBuffer keyByteBuffer = keySerde.serialize(fileStoreId);
//        final PooledByteBuffer valueByteBuffer = valueSerde.serialize(value);
//        env.writeAsync(txn -> {
//            dbi.put(txn, keyByteBuffer.getByteBuffer(), valueByteBuffer.getByteBuffer());
//            keyByteBuffer.release();
//            valueByteBuffer.release();
//        });
        newSourceQueue.put(fileStoreId);
    }

    public RepoSource getNextSource() {
        final long fileStoreId = newSourceQueue.take();
        return getSource(fileStoreId);
    }

    public Optional<RepoSource> getNextSource(final long time, final TimeUnit timeUnit) {
        final Optional<Long> fileStoreId = newSourceQueue.take(time, timeUnit);
        return fileStoreId.map(this::getSource);
    }

    private RepoSource getSource(long fileStoreId) {
        final RepoSourceValue value = db.get(fileStoreId);

//        final PooledByteBuffer keyBuffer = keySerde.serialize(fileStoreId);
//        final RepoSourceValue value = env.readResult(txn -> {
//            final ByteBuffer valueBuffer = dbi.get(txn, keyBuffer.getByteBuffer());
//            return valueSerde.deserialize(valueBuffer);
//        });
//        keyBuffer.release();
        return new RepoSource(fileStoreId, value.feedId());
    }

    public long countSources() {
        return db.count();
    }

//    @Override
//    public void markDeletableSources() {
//        try (final Txn<ByteBuffer> txn = env.txnRead()) {
//            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(txn)) {
//                for (final KeyVal<ByteBuffer> keyVal : cursor) {
//                    final RepoSourcePart value = valueSerde.deserialise(keyVal.val());
//                    if (!value.deleted() && value.examined() && value.itemCount() == 0) {
//                        writeQueue.write(writeTxn -> {
//                            final RepoSourcePart updated = new RepoSourcePart(
//                                    value.fileStoreId(),
//                                    value.feedId(),
//                                    value.examined(),
//                                    true,
//                                    value.itemCount());
//                            final ByteBuffer newValue = valueSerde.serialise(updated);
//                            dbi.put(writeTxn, keyVal.key(), newValue);
//                        });
//                    }
//                }
//            }
//        }
//    }


    public RepoSource getDeletableSource() {
        final long fileStoreId = deletableSourceQueue.take();
        return getSource(fileStoreId);
    }

//    @Override
//    public List<RepoSource> getDeletableSources(final long minSourceId, final int limit) {
//        final ByteBuffer min = keySerde.serialise(minSourceId);
//        final KeyRange<ByteBuffer> keyRange = KeyRange.atLeast(min);
//        final List<RepoSource> list = new ArrayList<>();
//        try (final Txn<ByteBuffer> txn = env.txnRead()) {
//            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(txn, keyRange)) {
//                for (final KeyVal<ByteBuffer> keyVal : cursor) {
//                    final RepoSourcePart value = valueSerde.deserialise(keyVal.val());
//                    if (value.deleted()) {
//                        list.add(new RepoSource(
//                                keySerde.deserialise(keyVal.key()),
//                                value.fileStoreId(),
//                                value.feedId()));
//                        if (list.size() >= limit) {
//                            break;
//                        }
//                    }
//                }
//            }
//        }
//        return list;
//    }

    public long countDeletableSources() {
        return deletableSourceQueue.size();
//        markDeletableSources();
//        return getDeletableSources(0, 1000).size();
    }

    public void deleteSource(final long fileStoreId) {
        db.delete(fileStoreId);

//        final PooledByteBuffer keyBuffer = keySerde.serialize(fileStoreId);
//        env.writeAsync(txn -> {
//            dbi.delete(txn, keyBuffer.getByteBuffer());
//            keyBuffer.release();
//        });
    }

//    @Override
//    public int deleteSources() {
//        int count = 0;
//        try (final Txn<ByteBuffer> txn = env.txnRead()) {
//            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(txn)) {
//                for (final KeyVal<ByteBuffer> keyVal : cursor) {
//                    final RepoSourcePart value = valueSerde.deserialise(keyVal.val());
//                    if (value.deleted()) {
//                        writeQueue.write(writeTxn -> dbi.delete(writeTxn, keyVal.key()));
//                        count++;
//                    }
//                }
//            }
//        }
//        return count;
//    }

    //    @Override
//    public void resetExamined() {
//        try (final Txn<ByteBuffer> txn = env.txnRead()) {
//            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(txn)) {
//                for (final KeyVal<ByteBuffer> keyVal : cursor) {
//                    final RepoSourcePart value = valueSerde.deserialise(keyVal.val());
//                    final RepoSourcePart updated = new RepoSourcePart(
//                            value.fileStoreId(),
//                            value.feedId(),
//                            false,
//                            value.deleted(),
//                            0);
//                    final ByteBuffer newValue = valueSerde.serialise(updated);
//                    dbi.put(keyVal.key(), newValue);
//                }
//            }
//        }
//    }
//
    public void setSourceExamined(final long fileStoreId, final int itemCount) {
        if (itemCount == 0) {
            deletableSourceQueue.put(fileStoreId);

        } else {
            final RepoSourceValue value = db.get(fileStoreId);
            final RepoSourceValue newValue = new RepoSourceValue(
                    value.feedId(),
                    itemCount);
            db.put(fileStoreId, newValue);
            examinedSourceQueue.put(fileStoreId);

//            final PooledByteBuffer keyBuffer = keySerde.serialize(fileStoreId);
//            env.writeAsync(txn -> {
//                final ByteBuffer value = dbi.get(txn, keyBuffer.getByteBuffer());
//                final RepoSourceValue repoSourcePart = valueSerde.deserialize(value);
//                final RepoSourceValue newRepoSourcePart = new RepoSourceValue(
//                        repoSourcePart.feedId(),
//                        itemCount);
//                final PooledByteBuffer newValue = valueSerde.serialize(newRepoSourcePart);
//                dbi.put(txn, keyBuffer.getByteBuffer(), newValue.getByteBuffer());
//                keyBuffer.release();
//                newValue.release();
//            });
//            examinedSourceQueue.put(fileStoreId);
        }
    }
//
//    @Override
//    public void setSourceExamined(final DSLContext context,
//                                  final long sourceId,
//                                  final boolean examined,
//                                  final int itemCount) {
//        setSourceExamined(null, sourceId, examined, itemCount);
//    }

//    public void clearQueue() {
//        newSourceQueue.clear();
//        examinedSourceQueue.clear();
//        deletableSourceQueue.clear();
//    }

    @Override
    public void clear() {
        db.clear();
        newSourceQueue.clear();
        examinedSourceQueue.clear();
        deletableSourceQueue.clear();
    }

    @Override
    public void flush() {
        newSourceQueue.flush();
        examinedSourceQueue.flush();
        deletableSourceQueue.flush();
    }

    LmdbQueue<Long> getNewSourceQueue() {
        return newSourceQueue;
    }

    LmdbQueue<Long> getDeletableSourceQueue() {
        return deletableSourceQueue;
    }

    LmdbQueue<Long> getExaminedSourceQueue() {
        return examinedSourceQueue;
    }
}
