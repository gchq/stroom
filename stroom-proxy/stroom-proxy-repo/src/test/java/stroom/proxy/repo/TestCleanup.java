package stroom.proxy.repo;

import stroom.db.util.JooqUtil;
import stroom.proxy.repo.dao.FeedDao;
import stroom.proxy.repo.dao.SqliteJooqHelper;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.proxy.repo.db.jooq.tables.Aggregate.AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.ForwardAggregate.FORWARD_AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;
import static stroom.proxy.repo.db.jooq.tables.SourceEntry.SOURCE_ENTRY;
import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestCleanup {

    @Inject
    private FeedDao feedDao;
    @Inject
    private RepoSources proxyRepoSources;
    @Inject
    private RepoSourceItems proxyRepoSourceEntries;
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
        proxyRepoSourceEntries.clear();
        proxyRepoSources.clear();
        mockForwardDestinations.clear();
    }

    @Test
    void testCleanup() {
        jooq.transaction(context -> {
            long sourceId = 0;
            long sourceFileStoreId = 0;
            long sourceItemId = 0;
            long sourceEntryId = 0;
            long aggregateId = 0;
            long forwardAggregateId = 0;

            for (int i = 1; i <= 2; i++) {
                final long feedId = feedDao.getId(new FeedKey("TEST_FEED_" + i, null));
                for (int j = 0; j < 6; j++) {
                    // Add sources.
                    context
                            .insertInto(
                                    SOURCE,
                                    SOURCE.ID,
                                    SOURCE.FILE_STORE_ID,
                                    SOURCE.EXAMINED)
                            .values(
                                    ++sourceId,
                                    ++sourceFileStoreId,
                                    true)
                            .execute();

                    // Add an aggregate.
                    for (int k = 0; k < 2; k++) {
                        aggregateId++;

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
                                        aggregateId,
                                        System.currentTimeMillis(),
                                        feedId,
                                        170L,
                                        2,
                                        true)
                                .execute();

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
                                        FORWARD_AGGREGATE.FK_FORWARD_URL_ID)
                                .values(
                                        ++forwardAggregateId,
                                        System.currentTimeMillis(),
                                        aggregateId,
                                        false,
                                        null,
                                        null,
                                        1)
                                .execute();

                        // Add source items.
                        for (int l = 1; l <= 4; l++) {
                            context
                                    .insertInto(
                                            SOURCE_ITEM,
                                            SOURCE_ITEM.ID,
                                            SOURCE_ITEM.NAME,
                                            SOURCE_ITEM.FK_FEED_ID,
                                            SOURCE_ITEM.FK_SOURCE_ID,
                                            SOURCE_ITEM.FK_AGGREGATE_ID)
                                    .values(
                                            ++sourceItemId,
                                            i + "_" + j + "_" + k + "_" + l,
                                            feedId,
                                            sourceId,
                                            aggregateId)
                                    .execute();

                            // Add source entry.
                            context
                                    .insertInto(
                                            SOURCE_ENTRY,
                                            SOURCE_ENTRY.ID,
                                            SOURCE_ENTRY.EXTENSION,
                                            SOURCE_ENTRY.EXTENSION_TYPE,
                                            SOURCE_ENTRY.BYTE_SIZE,
                                            SOURCE_ENTRY.FK_SOURCE_ITEM_ID)
                                    .values(
                                            ++sourceEntryId,
                                            ".hdr",
                                            2,
                                            84L,
                                            sourceItemId)
                                    .execute();
                            context
                                    .insertInto(
                                            SOURCE_ENTRY,
                                            SOURCE_ENTRY.ID,
                                            SOURCE_ENTRY.EXTENSION,
                                            SOURCE_ENTRY.EXTENSION_TYPE,
                                            SOURCE_ENTRY.BYTE_SIZE,
                                            SOURCE_ENTRY.FK_SOURCE_ITEM_ID)
                                    .values(
                                            ++sourceEntryId,
                                            ".dat",
                                            4,
                                            1L,
                                            sourceItemId)
                                    .execute();
                        }
                    }
                }
            }
        });

        // Make sure we can't delete any sources.
        jooq.printAllTables();
        assertThat(proxyRepoSources.getDeletableSources(1000).size()).isZero();

        // Now pretend we forwarded the first aggregates.
        jooq.printAllTables();
        forward(1);

        // Make sure we can delete source entries and items but not data.
        jooq.printAllTables();
        assertThat(proxyRepoSources.getDeletableSources(1000).size()).isZero();

        // Now forward some more.
        jooq.printAllTables();
        forward(2);

        // Check we can now delete the first source.
        jooq.printAllTables();
        assertThat(proxyRepoSources.getDeletableSources(1000).size()).isOne();

        // Forward remaining.
        jooq.printAllTables();

        final long minId = jooq.readOnlyTransactionResult(context ->
                JooqUtil.getMinId(context, FORWARD_AGGREGATE, FORWARD_AGGREGATE.ID)
                        .orElse(0L));
        final long maxId = jooq.readOnlyTransactionResult(context ->
                JooqUtil.getMaxId(context, FORWARD_AGGREGATE, FORWARD_AGGREGATE.ID)
                        .orElse(0L));
        for (long i = minId; i <= maxId; i++) {
            forward(i);
        }

        // Check everything is deleted.
        jooq.printAllTables();
        assertThat(proxyRepoSources.getDeletableSources(1000).size()).isEqualTo(12);

        cleanup.cleanupSources();

        // Check we have no source left
        jooq.readOnlyTransaction(context -> {
            assertThat(JooqUtil.count(context, SOURCE_ENTRY)).isZero();
            assertThat(JooqUtil.count(context, SOURCE_ITEM)).isZero();
            assertThat(JooqUtil.count(context, SOURCE)).isZero();
        });
    }

    private void forward(long aggregateId) {
        final Aggregate aggregate = new Aggregate(aggregateId, 0L);
        final ForwardUrl forwardUrl = new ForwardUrl(1, "test");
        final ForwardAggregate forwardAggregate = ForwardAggregate
                .builder()
                .id(aggregateId)
                .aggregate(aggregate)
                .forwardUrl(forwardUrl)
                .build();
        aggregateForwarder.forward(forwardAggregate);
    }
}
