/*
 * Copyright 2019 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.proxy.repo;

import stroom.util.io.FileUtil;

import org.jooq.Condition;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.Result;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.proxy.repo.db.jooq.tables.AggregateItem.AGGREGATE_ITEM;
import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;
import static stroom.proxy.repo.db.jooq.tables.SourceEntry.SOURCE_ENTRY;
import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;

@Singleton
public class Cleanup {

    private static final Logger LOGGER = LoggerFactory.getLogger(Cleanup.class);
    private static final int BATCH_SIZE = 1000000;

    // Find source items that have been aggregated but no longer have an associated aggregate.
    private static final SelectConditionStep<Record1<Long>> SELECT_SOURCE_ITEM_ID = DSL
            .select(SOURCE_ITEM.ID)
            .from(SOURCE_ITEM)
            .where(SOURCE_ITEM.AGGREGATED.isTrue())
            .andNotExists(DSL
                    .select(AGGREGATE_ITEM.ID)
                    .from(AGGREGATE_ITEM)
                    .where(AGGREGATE_ITEM.FK_SOURCE_ITEM_ID.eq(SOURCE_ITEM.ID)));
    private static final Condition DELETE_SOURCE_ENTRY_CONDITION =
            SOURCE_ENTRY.FK_SOURCE_ITEM_ID.in(SELECT_SOURCE_ITEM_ID);
    private static final Condition DELETE_SOURCE_ITEM_CONDITION =
            SOURCE_ITEM.ID.in(SELECT_SOURCE_ITEM_ID);
    private static final Condition DELETE_SOURCE_CONDITION =
            SOURCE.EXAMINED.isTrue()
                    .andNotExists(DSL
                            .select(SOURCE_ITEM.ID)
                            .from(SOURCE_ITEM)
                            .where(SOURCE_ITEM.FK_SOURCE_ID.eq(SOURCE.ID)));

    private final SqliteJooqHelper jooq;
    private final Path repoDir;

    @Inject
    Cleanup(final ProxyRepoDbConnProvider connProvider,
            final RepoDirProvider repoDirProvider) {
        this.jooq = new SqliteJooqHelper(connProvider);
        this.repoDir = repoDirProvider.get();
    }

    public synchronized int cleanup() {
        int total = 0;
        total += deleteSourceEntries();
        total += deleteSource();
        return total;
    }

    int deleteSourceEntries() {
        // Start a transaction for all of the database changes.
        return jooq.transactionResult(context -> {
            int count = context
                    .deleteFrom(SOURCE_ENTRY)
                    .where(DELETE_SOURCE_ENTRY_CONDITION)
                    .execute();

            count += context
                    .deleteFrom(SOURCE_ITEM)
                    .where(DELETE_SOURCE_ITEM_CONDITION)
                    .execute();

            return count;
        });
    }

    int deleteSource() {
        final AtomicInteger total = new AtomicInteger();

        boolean run = true;
        while (run) {
            final Result<Record2<Long, String>> result = getDeletableSources(BATCH_SIZE);

            result.forEach(record -> {
                final long sourceId = record.get(SOURCE.ID);
                final String sourcePath = record.get(SOURCE.PATH);

                try {
                    // Source path is the zip.
                    final Path sourceFile = repoDir.resolve(ProxyRepoFileNames.getZip(sourcePath));
                    LOGGER.debug("Deleting: " + FileUtil.getCanonicalPath(sourceFile));
                    Files.deleteIfExists(sourceFile);

                    final Path metaFile = repoDir.resolve(ProxyRepoFileNames.getMeta(sourcePath));
                    LOGGER.debug("Deleting: " + FileUtil.getCanonicalPath(metaFile));
                    Files.deleteIfExists(metaFile);

                    final int count = jooq.contextResult(context -> context
                            .deleteFrom(SOURCE)
                            .where(SOURCE.ID.eq(sourceId))
                            .execute());
                    total.addAndGet(count);

                } catch (final IOException e) {
                    LOGGER.error(e.getMessage(), e);
                }
            });

            // Stop deleting if the last query did not return a result as big as the batch size.
            if (result.size() < BATCH_SIZE || Thread.currentThread().isInterrupted()) {
                run = false;
            }
        }

        return total.get();
    }

    Result<Record1<Long>> getDeletableSourceItems() {
        return jooq.contextResult(context -> context
                .selectDistinct(SOURCE_ITEM.ID)
                .from(SOURCE_ITEM)
                .where(DELETE_SOURCE_ITEM_CONDITION)
                .fetch());
    }

    Result<Record1<Long>> getDeletableSourceEntries() {
        return jooq.contextResult(context -> context
                .selectDistinct(SOURCE_ENTRY.ID)
                .from(SOURCE_ENTRY)
                .where(DELETE_SOURCE_ENTRY_CONDITION)
                .fetch());
    }

    Result<Record2<Long, String>> getDeletableSources(final int limit) {
        return jooq.contextResult(context -> context
                .selectDistinct(SOURCE.ID, SOURCE.PATH)
                .from(SOURCE)
                .where(DELETE_SOURCE_CONDITION)
                .limit(limit)
                .fetch());
    }
}
