package stroom.proxy.repo.dao;

import stroom.proxy.repo.RepoSourceEntry;
import stroom.proxy.repo.RepoSourceItem;
import stroom.proxy.repo.RepoSourceItemRef;
import stroom.proxy.repo.WorkQueue;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.jooq.BatchBindStep;
import org.jooq.DSLContext;
import org.jooq.Field;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;
import static stroom.proxy.repo.db.jooq.tables.SourceEntry.SOURCE_ENTRY;
import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;

@Singleton
public class SourceItemDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SourceItemDao.class);

    private static final Field<?>[] SOURCE_ITEM_COLUMNS = new Field<?>[]{
            SOURCE_ITEM.ID,
            SOURCE_ITEM.NAME,
            SOURCE_ITEM.FEED_NAME,
            SOURCE_ITEM.TYPE_NAME,
            SOURCE_ITEM.BYTE_SIZE,
            SOURCE_ITEM.SOURCE_ID,
            SOURCE_ITEM.AGGREGATE_ID,
            SOURCE_ITEM.NEW_POSITION};
    private static final Object[] SOURCE_ITEM_VALUES = new Object[SOURCE_ITEM_COLUMNS.length];

    private static final Field<?>[] SOURCE_ENTRY_COLUMNS = new Field<?>[]{
            SOURCE_ENTRY.ID,
            SOURCE_ENTRY.EXTENSION,
            SOURCE_ENTRY.EXTENSION_TYPE,
            SOURCE_ENTRY.BYTE_SIZE,
            SOURCE_ENTRY.FK_SOURCE_ITEM_ID};
    private static final Object[] SOURCE_ENTRY_VALUES = new Object[SOURCE_ENTRY_COLUMNS.length];

    private final SqliteJooqHelper jooq;

    private final AtomicLong sourceItemRecordId = new AtomicLong();
    private final AtomicLong sourceEntryRecordId = new AtomicLong();
    private WorkQueue newQueue;

    @Inject
    SourceItemDao(final SqliteJooqHelper jooq) {
        this.jooq = jooq;
        init();
    }

    private void init() {
        newQueue = WorkQueue.createWithJooq(jooq, SOURCE_ITEM, SOURCE_ITEM.NEW_POSITION);

        final long maxSourceItemRecordId = jooq.getMaxId(SOURCE_ITEM, SOURCE_ITEM.ID).orElse(0L);
        sourceItemRecordId.set(maxSourceItemRecordId);

        final long maxSourceEntryRecordId = jooq.getMaxId(SOURCE_ENTRY, SOURCE_ENTRY.ID).orElse(0L);
        sourceEntryRecordId.set(maxSourceEntryRecordId);
    }

    public void clear() {
        jooq.deleteAll(SOURCE_ENTRY);
        jooq.deleteAll(SOURCE_ITEM);
        jooq.checkEmpty(SOURCE_ENTRY);
        jooq.checkEmpty(SOURCE_ITEM);
        init();
    }

    public int countItems() {
        return jooq.count(SOURCE_ITEM);
    }

    public int countEntries() {
        return jooq.count(SOURCE_ENTRY);
    }

    public void addItems(final Path fullPath,
                         final long sourceId,
                         final Collection<RepoSourceItem> items) {
        jooq.underLock(() -> {
            newQueue.put(writePos -> {
                final List<Object[]> sourceItems = new ArrayList<>(items.size());
                final List<Object[]> sourceEntries = new ArrayList<>();
                for (final RepoSourceItem sourceItemRecord : items) {
                    if (sourceItemRecord.getFeedName() == null) {
                        LOGGER.error(() ->
                                "Source item has no feed name: " +
                                        fullPath +
                                        " - " +
                                        sourceItemRecord.getName());
                    } else {
                        final long itemRecordId = sourceItemRecordId.incrementAndGet();

                        final Object[] sourceItem = new Object[SOURCE_ITEM_COLUMNS.length];
                        sourceItem[0] = itemRecordId;
                        sourceItem[1] = sourceItemRecord.getName();
                        sourceItem[2] = sourceItemRecord.getFeedName();
                        sourceItem[3] = sourceItemRecord.getTypeName();
                        sourceItem[4] = sourceItemRecord.getTotalByteSize();
                        sourceItem[5] = sourceItemRecord.getSource().getId();
                        sourceItem[6] = sourceItemRecord.getAggregateId();
                        sourceItem[7] = writePos.incrementAndGet();
                        sourceItems.add(sourceItem);

                        final List<RepoSourceEntry> entries = sourceItemRecord.getEntries();
                        for (final RepoSourceEntry entry : entries) {
                            final long entryRecordId = sourceEntryRecordId.incrementAndGet();

                            final Object[] sourceEntry = new Object[SOURCE_ENTRY_COLUMNS.length];
                            sourceEntry[0] = entryRecordId;
                            sourceEntry[1] = entry.getExtension();
                            sourceEntry[2] = entry.getType().getId();
                            sourceEntry[3] = entry.getByteSize();
                            sourceEntry[4] = itemRecordId;
                            sourceEntries.add(sourceEntry);
                        }
                    }
                }

                jooq.transaction(context -> {
                    insertItems(context, sourceItems);
                    insertEntries(context, sourceEntries);

                    // Mark the source as having been examined.
                    setSourceExamined(context, sourceId);
                });
            });
            return null;
        });
    }

    private void insertItems(final DSLContext context, final List<Object[]> sourceItems) {
        if (sourceItems.size() > 0) {
            final BatchBindStep batchBindStep = context.batch(context
                    .insertInto(SOURCE_ITEM)
                    .columns(SOURCE_ITEM_COLUMNS)
                    .values(SOURCE_ITEM_VALUES));
            for (final Object[] sourceItem : sourceItems) {
                batchBindStep.bind(sourceItem);
            }
            batchBindStep.execute();
        }
    }

    private void insertEntries(final DSLContext context, final List<Object[]> sourceEntries) {
        if (sourceEntries.size() > 0) {
            final BatchBindStep batchBindStep = context.batch(context
                    .insertInto(SOURCE_ENTRY)
                    .columns(SOURCE_ENTRY_COLUMNS)
                    .values(SOURCE_ENTRY_VALUES));
            for (final Object[] sourceEntry : sourceEntries) {
                batchBindStep.bind(sourceEntry);
            }
            batchBindStep.execute();
        }
    }

    private void setSourceExamined(final DSLContext context, final long sourceId) {
        context
                .update(SOURCE)
                .set(SOURCE.EXAMINED, true)
                .setNull(SOURCE.NEW_POSITION)
                .where(SOURCE.ID.eq(sourceId))
                .execute();
    }

    public Optional<RepoSourceItemRef> getNewSourceItem() {
        return newQueue.get(this::getSourceItemAtQueuePosition);
    }

    public Optional<RepoSourceItemRef> getNewSourceItem(final long timeout,
                                                        final TimeUnit timeUnit) {
        return newQueue.get(this::getSourceItemAtQueuePosition, timeout, timeUnit);
    }

    public Optional<RepoSourceItemRef> getSourceItemAtQueuePosition(final long position) {
        return jooq.contextResult(context -> context
                        .select(SOURCE_ITEM.ID,
                                SOURCE_ITEM.FEED_NAME,
                                SOURCE_ITEM.TYPE_NAME,
                                SOURCE_ITEM.BYTE_SIZE)
                        .from(SOURCE_ITEM)
                        .where(SOURCE_ITEM.NEW_POSITION.eq(position))
                        .fetchOptional())
                .map(r -> new RepoSourceItemRef(
                        r.get(SOURCE_ITEM.ID),
                        r.get(SOURCE_ITEM.FEED_NAME),
                        r.get(SOURCE_ITEM.TYPE_NAME),
                        r.get(SOURCE_ITEM.BYTE_SIZE))
                );
    }
}
