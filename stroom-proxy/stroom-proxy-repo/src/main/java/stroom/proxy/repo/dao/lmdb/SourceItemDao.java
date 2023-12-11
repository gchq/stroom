package stroom.proxy.repo.dao.lmdb;

import stroom.proxy.repo.RepoSource;
import stroom.proxy.repo.RepoSourceItem;
import stroom.proxy.repo.dao.lmdb.serde.LongSerde;
import stroom.proxy.repo.dao.lmdb.serde.PooledByteBuffer;
import stroom.proxy.repo.dao.lmdb.serde.RepoSourceItemSerde;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.lmdbjava.Dbi;

import java.nio.ByteBuffer;


@Singleton
public class SourceItemDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractDaoLmdb.class);

    private final LmdbEnv env;
    private final Dbi<ByteBuffer> dbi;

    private RowKey<Long> rowKey;

    private final SourceDao sourceDao;
    private final LongSerde keySerde = new LongSerde();
    private final RepoSourceItemSerde valueSerde = new RepoSourceItemSerde();

    @Inject
    public SourceItemDao(final LmdbEnv env,
                         final SourceDao sourceDao) {
        try {
            this.env = env;
            this.dbi = env.openDbi("source-item");
            rowKey = new LongRowKey(env, dbi);
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }

        this.sourceDao = sourceDao;
    }

    public void clear() {
        env.clear(dbi);
        rowKey = new LongRowKey(env, dbi);
    }

    public int countItems() {
        return (int) env.count(dbi);
    }

    public void addItem(final RepoSource source, final RepoSourceItem item) {
        final RepoSourceItemPart value = new RepoSourceItemPart(
                source.fileStoreId(),
                source.feedId(),
                item.name(),
                item.aggregateId(),
                item.totalByteSize(),
                item.extensions());
        final PooledByteBuffer valueByteBuffer = valueSerde.serialise(value);
        final Long newId = rowKey.next();
        final PooledByteBuffer idByteBuffer = keySerde.serialise(newId);
        env.write(txn -> {
            dbi.put(txn, idByteBuffer.get(), valueByteBuffer.get());
            idByteBuffer.release();
            valueByteBuffer.release();
        });
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
}
