package stroom.proxy.repo;

import stroom.proxy.repo.dao.SqliteJooqHelper;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.apache.commons.lang3.StringUtils;
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
    private ProxyRepoDbConnProvider connProvider;
    @Inject
    private SqliteJooqHelper jooqHelper;

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
        final SqliteJooqHelper jooq = new SqliteJooqHelper(connProvider);
        jooq.context(context -> {
            long sourceId = 0;
            long sourceItemId = 0;
            long sourceEntryId = 0;
            long aggregateId = 0;
            long forwardAggregateId = 0;

            for (int i = 1; i <= 2; i++) {
                String feedName = "TEST_FEED_" + i;
                for (int j = 0; j < 6; j++) {
                    // Add sources.
                    context
                            .insertInto(
                                    SOURCE,
                                    SOURCE.ID,
                                    SOURCE.PATH,
                                    SOURCE.LAST_MODIFIED_TIME_MS,
                                    SOURCE.EXAMINED)
                            .values(
                                    ++sourceId,
                                    StringUtils.leftPad(String.valueOf(sourceId), 3, "0") + ".zip",
                                    System.currentTimeMillis(),
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
                                        AGGREGATE.FEED_NAME,
                                        AGGREGATE.TYPE_NAME,
                                        AGGREGATE.BYTE_SIZE,
                                        AGGREGATE.ITEMS,
                                        AGGREGATE.COMPLETE)
                                .values(
                                        aggregateId,
                                        System.currentTimeMillis(),
                                        feedName,
                                        null,
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
                                            SOURCE_ITEM.FEED_NAME,
                                            SOURCE_ITEM.TYPE_NAME,
                                            SOURCE_ITEM.SOURCE_ID,
                                            SOURCE_ITEM.AGGREGATE_ID)
                                    .values(
                                            ++sourceItemId,
                                            i + "_" + j + "_" + k + "_" + l,
                                            feedName,
                                            null,
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
        jooqHelper.printAllTables();
        assertThat(proxyRepoSources.getDeletableSources().size()).isZero();

        // Now pretend we forwarded the first aggregates.
        jooqHelper.printAllTables();
        forward(1);

        // Make sure we can delete source entries and items but not data.
        jooqHelper.printAllTables();
        assertThat(proxyRepoSources.getDeletableSources().size()).isZero();

        // Now forward some more.
        jooqHelper.printAllTables();
        forward(2);

        // Check we can now delete the first source.
        jooqHelper.printAllTables();
        assertThat(proxyRepoSources.getDeletableSources().size()).isOne();

        // Forward remaining.
        jooqHelper.printAllTables();

        final long minId = jooq.getMinId(FORWARD_AGGREGATE, FORWARD_AGGREGATE.ID).orElse(0L);
        final long maxId = jooq.getMaxId(FORWARD_AGGREGATE, FORWARD_AGGREGATE.ID).orElse(0L);
        for (long i = minId; i <= maxId; i++) {
            forward(i);
        }

        // Check everything is deleted.
        jooqHelper.printAllTables();
        assertThat(proxyRepoSources.getDeletableSources().size()).isEqualTo(12);

        cleanup.cleanupSources();

        // Check we have no source left
        assertThat(jooq.count(SOURCE_ENTRY)).isZero();
        assertThat(jooq.count(SOURCE_ITEM)).isZero();
        assertThat(jooq.count(SOURCE)).isZero();
    }

    private void forward(long aggregateId) {
        final Aggregate aggregate = new Aggregate(aggregateId, null, null);
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
