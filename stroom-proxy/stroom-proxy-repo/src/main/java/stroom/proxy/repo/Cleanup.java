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

import stroom.db.util.JooqUtil;

import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import javax.inject.Inject;

import static stroom.proxy.repo.db.jooq.tables.AggregateItem.AGGREGATE_ITEM;
import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;
import static stroom.proxy.repo.db.jooq.tables.SourceEntry.SOURCE_ENTRY;
import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;

public class Cleanup {

    private static final Logger LOGGER = LoggerFactory.getLogger(Cleanup.class);

    private final ProxyRepoDbConnProvider connProvider;
    private final ProxyRepo proxyRepo;
    private final CleanupConfig cleanupConfig;

    @Inject
    Cleanup(final ProxyRepoDbConnProvider connProvider,
            final ProxyRepo proxyRepo,
            final CleanupConfig cleanupConfig) {
        this.connProvider = connProvider;
        this.proxyRepo = proxyRepo;
        this.cleanupConfig = cleanupConfig;
    }

    public void cleanup() {
        // Find source items that have been aggregated but no longer have an associated aggregate.
        final SelectConditionStep<Record1<Integer>> select = DSL
                .select(SOURCE_ITEM.ID)
                .from(SOURCE_ITEM)
                .leftOuterJoin(AGGREGATE_ITEM)
                .on(AGGREGATE_ITEM.FK_SOURCE_ITEM_ID.eq(SOURCE_ITEM.ID))
                .where(SOURCE_ITEM.AGGREGATED.isTrue())
                .and(AGGREGATE_ITEM.ID.isNull());

        // Start a transaction for all of the database changes.
        JooqUtil.transaction(connProvider, context -> {
            context
                    .deleteFrom(SOURCE_ENTRY)
                    .where(SOURCE_ENTRY.FK_SOURCE_ITEM_ID.in(select))
                    .execute();

            context
                    .deleteFrom(SOURCE_ITEM)
                    .where(SOURCE_ITEM.ID.in(select))
                    .execute();
        });

        deleteSource();
    }

    private void deleteSource() {
        JooqUtil.context(connProvider, context -> {
            try (final Stream<Record2<Integer, String>> stream = context
                    .selectDistinct(SOURCE.ID, SOURCE.PATH)
                    .from(SOURCE)
                    .leftOuterJoin(SOURCE_ITEM)
                    .on(SOURCE_ITEM.FK_SOURCE_ID.eq(SOURCE.ID))
                    .where(SOURCE.EXAMINED.isTrue())
                    .and(SOURCE_ITEM.ID.isNull())
                    .stream()) {

                stream.forEach(record -> {
                    final int sourceId = record.get(SOURCE.ID);
                    final String sourcePath = record.get(SOURCE.PATH);

                    try {
                        proxyRepo.deleteRepoFile(sourcePath);
                        context
                                .deleteFrom(SOURCE)
                                .where(SOURCE.ID.eq(sourceId))
                                .execute();

                    } catch (final IOException e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                });
            }
        });
    }
}