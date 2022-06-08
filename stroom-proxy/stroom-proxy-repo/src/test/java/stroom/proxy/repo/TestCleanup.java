package stroom.proxy.repo;

import stroom.db.util.JooqUtil;
import stroom.proxy.repo.dao.FeedDao;
import stroom.proxy.repo.dao.SourceDao;
import stroom.proxy.repo.dao.SourceItemDao;
import stroom.proxy.repo.dao.SqliteJooqHelper;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.proxy.repo.db.jooq.tables.Aggregate.AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.ForwardAggregate.FORWARD_AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;
import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestCleanup {

    @Inject
    private FeedDao feedDao;
    @Inject
    private RepoSources repoSources;
    @Inject
    private SourceDao sourceDao;
    @Inject
    private SourceItemDao sourceItemDao;
    @Inject
    private Aggregator aggregator;
    @Inject
    private AggregateForwarder aggregateForwarder;
    @Inject
    private Cleanup cleanup;
    @Inject
    private MockForwardDestinations mockForwardDestinations;
    @Inject
    private SqliteJooqHelper jooq;

    @BeforeEach
    void beforeEach() {
        aggregateForwarder.clear();
        aggregator.clear();
        sourceItemDao.clear();
        repoSources.clear();
        mockForwardDestinations.clear();
    }

    @Test
    void testCleanup() {
        final AtomicLong sourceId = new AtomicLong();
        final AtomicLong sourceFileStoreId = new AtomicLong();
        final AtomicLong sourceItemId = new AtomicLong();
        final AtomicLong sourceEntryId = new AtomicLong();
        final AtomicLong aggregateId = new AtomicLong();
        final AtomicLong forwardAggregateId = new AtomicLong();

        for (int i = 1; i <= 2; i++) {
            final long feedId = feedDao.getId(new FeedKey("TEST_FEED_" + i, null));

            // Add an aggregate.
            jooq.transaction(context -> {
                aggregateId.incrementAndGet();

                // Add aggregate.
                context
                        .insertInto(
                                AGGREGATE,
                                AGGREGATE.ID,
                                AGGREGATE.CREATE_TIME_MS,
                                AGGREGATE.FK_FEED_ID,
                                AGGREGATE.BYTE_SIZE,
                                AGGREGATE.ITEMS,
                                AGGREGATE.COMPLETE)
                        .values(
                                aggregateId.get(),
                                System.currentTimeMillis(),
                                feedId,
                                170L,
                                2,
                                true)
                        .execute();

                for (int j = 0; j < 2; j++) {
                    // Add aggregate forward.
                    context
                            .insertInto(
                                    FORWARD_AGGREGATE,
                                    FORWARD_AGGREGATE.ID,
                                    FORWARD_AGGREGATE.UPDATE_TIME_MS,
                                    FORWARD_AGGREGATE.FK_AGGREGATE_ID,
                                    FORWARD_AGGREGATE.SUCCESS,
                                    FORWARD_AGGREGATE.ERROR,
                                    FORWARD_AGGREGATE.TRIES,
                                    FORWARD_AGGREGATE.FK_FORWARD_DEST_ID)
                            .values(
                                    forwardAggregateId.incrementAndGet(),
                                    System.currentTimeMillis(),
                                    aggregateId.get(),
                                    false,
                                    null,
                                    null,
                                    1)
                            .execute();
                }
            });
        }

        for (int i = 1; i <= 2; i++) {
            final long feedId = feedDao.getId(new FeedKey("TEST_FEED_" + i, null));

            jooq.transaction(context -> {
                for (int j = 0; j < 6; j++) {
                    final long localSourceId = sourceId.incrementAndGet();
                    final long localFileStoreId = sourceFileStoreId.incrementAndGet();

                    // Add sources.
                    context
                            .insertInto(
                                    SOURCE,
                                    SOURCE.ID,
                                    SOURCE.FILE_STORE_ID,
                                    SOURCE.FK_FEED_ID,
                                    SOURCE.EXAMINED,
                                    SOURCE.ITEM_COUNT)
                            .values(
                                    localSourceId,
                                    localFileStoreId,
                                    feedId,
                                    true,
                                    4)
                            .execute();

                    // Add source items.
                    for (int k = 1; k <= 4; k++) {
                        context
                                .insertInto(
                                        SOURCE_ITEM,
                                        SOURCE_ITEM.ID,
                                        SOURCE_ITEM.NAME,
                                        SOURCE_ITEM.FK_FEED_ID,
                                        SOURCE_ITEM.FK_SOURCE_ID,
                                        SOURCE_ITEM.FILE_STORE_ID,
                                        SOURCE_ITEM.FK_AGGREGATE_ID,
                                        SOURCE_ITEM.EXTENSIONS)
                                .values(
                                        sourceItemId.incrementAndGet(),
                                        feedId + "_" + k,
                                        feedId,
                                        localSourceId,
                                        localFileStoreId,
                                        k % 2 == 0
                                                ? 1L
                                                : 2L,
                                        ".hdr,.dat")
                                .execute();
                    }
                }
            });
        }

        // Make sure we can't delete any sources.
        check(12, 48, 96, 0);

        // Now pretend we forwarded the first aggregates.
        forward(1, 1);
        check(12, 48, 96, 0);

        // Now forward some more.
        forward(1, 2);
        check(12, 24, 48, 0);

        forward(2, 3);
        check(12, 24, 48, 0);

        forward(2, 4);
        check(12, 0, 0, 12);

        cleanup.cleanupSources();

        // Check we have no source left
        jooq.readOnlyTransaction(context -> {
            assertThat(JooqUtil.count(context, SOURCE_ITEM)).isZero();
            assertThat(JooqUtil.count(context, SOURCE)).isZero();
        });
    }

    private void check(final int sources,
                       final int items,
                       final int entries,
                       final int deletableSources) {
//        jooq.printAllTables();
        assertThat(sourceDao.countSources()).isEqualTo(sources);
        assertThat(sourceItemDao.countItems()).isEqualTo(items);
        assertThat(sourceDao.countDeletableSources()).isEqualTo(deletableSources);
    }

    private void forward(final long aggregateId,
                         final long forwardAggregateId) {
        final Aggregate aggregate = new Aggregate(aggregateId, 1L);
        final ForwardDest forwardDest = new ForwardDest(1, "test");
        final ForwardAggregate forwardAggregate = ForwardAggregate
                .builder()
                .id(forwardAggregateId)
                .aggregate(aggregate)
                .forwardDest(forwardDest)
                .build();
        aggregateForwarder.forward(forwardAggregate);
    }
}
