package stroom.proxy.repo.dao.lmdb;

import stroom.bytebuffer.PooledByteBuffer;
import stroom.proxy.repo.RepoSource;
import stroom.proxy.repo.RepoSourceItem;
import stroom.proxy.repo.RepoSourceItemRef;
import stroom.proxy.repo.dao.lmdb.serde.LongSerde;
import stroom.proxy.repo.dao.lmdb.serde.RepoSourceItemSerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;
import stroom.util.shared.Flushable;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.lmdbjava.Dbi;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.concurrent.TimeUnit;


@Singleton
public class SourceItemDao implements Clearable, Flushable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractDaoLmdb.class);

//    private final LmdbEnv env;
    private final Db<Long, RepoSourceItemPart> db;

    private RowKey<Long> rowKey;

    private final SourceDao sourceDao;
//    private final LongSerde keySerde;
//    private final RepoSourceItemSerde valueSerde;
    private final LmdbQueue<Long> newSourceItemQueue;

    @Inject
    public SourceItemDao(final LmdbEnv env,
                         final SourceDao sourceDao,
                         final LongSerde keySerde,
                         final RepoSourceItemSerde valueSerde) {
        try {
//            this.env = env;
            this.db = env.openDb("source-item", new LongSerde(), new RepoSourceItemSerde());
//            this.keySerde = keySerde;
//            this.valueSerde = valueSerde;
            rowKey = new LongRowKey(db);

            newSourceItemQueue = new LmdbQueue<>(env, "new-source-item", new LongSerde());
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }

        this.sourceDao = sourceDao;
    }

    public int countItems() {
        return (int) db.count();
    }

    public void addItem(final RepoSource source, final RepoSourceItem item) {
        final RepoSourceItemPart value = new RepoSourceItemPart(
                source.fileStoreId(),
                source.feedId(),
                item.name(),
                item.aggregateId(),
                item.totalByteSize(),
                item.extensions());
        final long newId = rowKey.next();
        db.put(newId, value);

//        final PooledByteBuffer valueByteBuffer = valueSerde.serialize(value);
//
//        final PooledByteBuffer idByteBuffer = keySerde.serialize(newId);
//        env.writeAsync(txn -> {
//            dbi.put(txn, idByteBuffer.getByteBuffer(), valueByteBuffer.getByteBuffer());
//            idByteBuffer.release();
//            valueByteBuffer.release();
//        });
    }

    public void setAggregate(final long itemId, final long aggregateId) {
//        final PooledByteBuffer keyByteBuffer = keySerde.serialize(itemId);
//        RepoSourceItemPart repoSourceItemPart = env.readResult(txn -> valueSerde
//                .deserialize(dbi.get(txn, keyByteBuffer.getByteBuffer())));
//        repoSourceItemPart = new RepoSourceItemPart(
//                repoSourceItemPart.fileStoreId(),
//                repoSourceItemPart.feedId(),
//                repoSourceItemPart.name(),
//                aggregateId,
//                repoSourceItemPart.totalByteSize(),
//                repoSourceItemPart.extensions());
//        final PooledByteBuffer valueByteBuffer = valueSerde.serialize(repoSourceItemPart);
//        env.writeAsync(txn -> {
//            dbi.put(txn, keyByteBuffer.getByteBuffer(), valueByteBuffer.getByteBuffer());
//            keyByteBuffer.release();
//            valueByteBuffer.release();
//        });
    }

    public RepoSourceItemRef getNextSourceItem() {
        final long itemId = newSourceItemQueue.take();
        return getSourceItem(itemId);
    }

    public Optional<RepoSourceItemRef> getNextSourceItem(final long time, final TimeUnit timeUnit) {
        final Optional<Long> optional = newSourceItemQueue.take(time, timeUnit);
        return optional.map(this::getSourceItem);
    }

    private RepoSourceItemRef getSourceItem(final long itemId) {
        final RepoSourceItemPart repoSourceItemPart = db.get(itemId);
        return new RepoSourceItemRef(itemId, repoSourceItemPart.feedId(), repoSourceItemPart.totalByteSize());

//        try (final PooledByteBuffer keyBuffer = keySerde.serialize(itemId)) {
//            return env.readResult(txn -> {
//                final ByteBuffer valueBuffer = dbi.get(txn, keyBuffer.getByteBuffer());
//                final RepoSourceItemPart repoSourceItemPart = valueSerde.deserialize(valueBuffer);
//                return new RepoSourceItemRef(itemId, repoSourceItemPart.feedId(), repoSourceItemPart.totalByteSize());
//            });
//        }
    }
//
//
//    @Override
//    public Batch<RepoSourceItemRef> getNewSourceItems() {
//        return null;
//    }
//
//    @Override
//    public Batch<RepoSourceItemRef> getNewSourceItems(final long timeout, final TimeUnit timeUnit) {
//        return null;
//    }

//    public void deleteBySourceId(final long sourceId) {
//        try (final Txn<ByteBuffer> txn = env.txnRead()) {
//            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(txn)) {
//                for (final KeyVal<ByteBuffer> keyVal : cursor) {
//                    final RepoSourceItemPart value = valueSerde.deserialise(keyVal.val());
//
//                    if (value.sourceId() == sourceId) { // TODO : Add index but only used by tests so maybe no issue.
//                        env.write(writeTxn -> dbi.delete(writeTxn, keyVal.key()));
//                    }
//                }
//            }
//        }
//    }

//    public List<SourceItems> fetchSourceItemsByAggregateId(final long aggregateId) {
//        final Map<Source, List<Item>> resultMap = new HashMap<>();
//        try (final Txn<ByteBuffer> txn = env.txnRead()) {
//            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(txn)) {
//                for (final KeyVal<ByteBuffer> keyVal : cursor) {
//                    final RepoSourceItemPart value = valueSerde.deserialise(keyVal.val());
//
//                    if (value.aggregateId() == aggregateId) { // TODO : Add index.
//                        final SourceItems.Source source = new SourceItems.Source(
//                                value.sourceId(),
//                                value.fileStoreId());
//
//                        final SourceItems.Item item = new SourceItems.Item(
//                                keySerde.deserialise(keyVal.key()),
//                                value.name(),
//                                value.feedId(),
//                                value.aggregateId(),
//                                value.totalByteSize(),
//                                value.extensions());
//
//                        resultMap.computeIfAbsent(source, s -> new ArrayList<>()).add(item);
//                    }
//                }
//            }
//        }
//
//        // Sort the sources and items.
//        return resultMap
//                .entrySet()
//                .stream()
//                .map(entry -> new SourceItems(entry.getKey(), entry.getValue()))
//                .peek(sourceItems -> sourceItems.list().sort(Comparator.comparing(SourceItems.Item::id)))
//                .sorted(Comparator.comparing(sourceItems -> sourceItems.source().id()))
//                .toList();
//    }

    @Override
    public void clear() {
        db.clear();
        rowKey = new LongRowKey(db);
        newSourceItemQueue.clear();
    }

    @Override
    public void flush() {
        newSourceItemQueue.flush();
    }
}
