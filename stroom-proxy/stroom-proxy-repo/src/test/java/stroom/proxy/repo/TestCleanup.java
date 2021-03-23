package stroom.proxy.repo;

import stroom.db.util.JooqHelper;

import name.falgout.jeffrey.testing.junit.guice.GuiceExtension;
import name.falgout.jeffrey.testing.junit.guice.IncludeModule;
import org.apache.commons.lang3.StringUtils;
import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.proxy.repo.db.jooq.tables.Aggregate.AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.AggregateItem.AGGREGATE_ITEM;
import static stroom.proxy.repo.db.jooq.tables.ForwardAggregate.FORWARD_AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.ForwardUrl.FORWARD_URL;
import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;
import static stroom.proxy.repo.db.jooq.tables.SourceEntry.SOURCE_ENTRY;
import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;

@ExtendWith(GuiceExtension.class)
@IncludeModule(ProxyRepoTestModule.class)
public class TestCleanup {

    @Inject
    private ProxyRepoSources proxyRepoSources;
    @Inject
    private ProxyRepoSourceEntries proxyRepoSourceEntries;
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
        final JooqHelper jooq = new JooqHelper(connProvider, SQLDialect.SQLITE);
        jooq.context(context -> {
            long sourceId = 0;
            long sourceItemId = 0;
            long sourceEntryId = 0;
            long aggregateId = 0;
            long aggregateItemId = 0;

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

                    // Add source items.
                    for (int k = 1; k <= 4; k++) {
                        context
                                .insertInto(
                                        SOURCE_ITEM,
                                        SOURCE_ITEM.ID,
                                        SOURCE_ITEM.NAME,
                                        SOURCE_ITEM.FEED_NAME,
                                        SOURCE_ITEM.TYPE_NAME,
                                        SOURCE_ITEM.FK_SOURCE_ID,
                                        SOURCE_ITEM.AGGREGATED)
                                .values(
                                        ++sourceItemId,
                                        String.valueOf(k),
                                        feedName,
                                        null,
                                        sourceId,
                                        true)
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

                    // Add aggregates
                    for (int k = 1; k <= 2; k++) {
                        context
                                .insertInto(
                                        AGGREGATE,
                                        AGGREGATE.ID,
                                        AGGREGATE.CREATE_TIME_MS,
                                        AGGREGATE.FEED_NAME,
                                        AGGREGATE.TYPE_NAME,
                                        AGGREGATE.BYTE_SIZE,
                                        AGGREGATE.ITEMS,
                                        AGGREGATE.COMPLETE,
                                        AGGREGATE.FORWARD_ERROR)
                                .values(
                                        ++aggregateId,
                                        System.currentTimeMillis(),
                                        feedName,
                                        null,
                                        170L,
                                        2,
                                        true,
                                        false)
                                .execute();

                        for (int l = 1; l <= 2; l++) {
                            // Add aggregate items.
                            context
                                    .insertInto(
                                            AGGREGATE_ITEM,
                                            AGGREGATE_ITEM.ID,
                                            AGGREGATE_ITEM.FK_AGGREGATE_ID,
                                            AGGREGATE_ITEM.FK_SOURCE_ITEM_ID)
                                    .values(
                                            ++aggregateItemId,
                                            aggregateId,
                                            aggregateItemId)
                                    .execute();
                        }
                    }
                }
            }
        });

        // Make sure we can't delete any sources.
        printAllTables();
        assertThat(cleanup.deleteUnusedSourceEntries()).isEqualTo(0);
        assertThat(cleanup.deleteUnusedSources()).isEqualTo(0);

        // Now pretend we forwarded the first aggregate by deleting it.
        printAllTables();
        jooq.context(context -> {
            context
                    .deleteFrom(AGGREGATE_ITEM).where(AGGREGATE_ITEM.FK_AGGREGATE_ID.eq(1L)).execute();
            context
                    .deleteFrom(AGGREGATE).where(AGGREGATE.ID.eq(1L)).execute();
        });

        // Make sure we can delete source entries and items but not data.
        printAllTables();
        assertThat(cleanup.deleteUnusedSourceEntries()).isEqualTo(6);
        assertThat(cleanup.deleteUnusedSources()).isEqualTo(0);

        // Now forward some more.
        printAllTables();
        jooq.context(context -> {
            context
                    .deleteFrom(AGGREGATE_ITEM).where(AGGREGATE_ITEM.FK_AGGREGATE_ID.eq(2L)).execute();
            context
                    .deleteFrom(AGGREGATE).where(AGGREGATE.ID.eq(2L)).execute();
        });

        // Check we can now delete the first source.
        printAllTables();
        assertThat(cleanup.deleteUnusedSourceEntries()).isEqualTo(6);
        assertThat(cleanup.deleteUnusedSources()).isEqualTo(1);

        // Forward remaining.
        printAllTables();
        jooq.context(context -> {
            context
                    .deleteFrom(AGGREGATE_ITEM).execute();
            context
                    .deleteFrom(AGGREGATE).execute();
        });

        // Check everything is deleted.
        printAllTables();
        assertThat(cleanup.deleteUnusedSourceEntries()).isEqualTo(132);
        assertThat(cleanup.deleteUnusedSources()).isEqualTo(11);

        // Check we have no source left
        assertThat(jooq.count(SOURCE_ENTRY)).isZero();
        assertThat(jooq.count(SOURCE_ITEM)).isZero();
        assertThat(jooq.count(SOURCE)).isZero();
    }

    void printAllTables() {
        printTable(SOURCE, null, "SOURCE");
        printTable(SOURCE_ITEM, null, "SOURCE_ITEM");
        printTable(SOURCE_ENTRY, null, "SOURCE_ENTRY");
        printTable(AGGREGATE, null, "AGGREGATE");
        printTable(AGGREGATE_ITEM, null, "AGGREGATE_ITEM");
        printTable(FORWARD_URL, null, "FORWARD_URL");
        printTable(FORWARD_AGGREGATE, null, "FORWARD_AGGREGATE");
    }

    <R extends Record, T extends Table<R>> void printTable(final T table,
                                                           final Condition condition,
                                                           final String message) {
        final JooqHelper jooq = new JooqHelper(connProvider, SQLDialect.SQLITE);
        jooq.context(context -> {
            final List<R> records = context.selectFrom(table).where(condition).fetch();
            printRecords(records, message);
        });
    }

    <R extends Record> void printRecords(final List<R> records, final String message) {
        System.out.println(message + ": \n" + records);
    }
}
