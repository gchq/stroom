/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.streamstore.meta.db;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.Clearable;
import stroom.streamstore.meta.db.stroom.tables.records.StrmProcessorRecord;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static stroom.streamstore.meta.db.stroom.tables.StrmFeed.STRM_FEED;
import static stroom.streamstore.meta.db.stroom.tables.StrmProcessor.STRM_PROCESSOR;

@Singleton
class ProcessorServiceImpl implements ProcessorService, Clearable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorServiceImpl.class);

    private final Map<Integer, StrmProcessorRecord> cache = new ConcurrentHashMap<>();

    private final DataSource dataSource;

    @Inject
    ProcessorServiceImpl(final StreamMetaDataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * @return the feed by it's ID or null
     */
    @SuppressWarnings("unchecked")
//    @Override
    private Integer get(final Integer processorId, final String pipelineUuid) {
        StrmProcessorRecord record = cache.get(processorId);
        if (record != null) {
            return record.getId();
        }

        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);

            record = create
                    .selectFrom(STRM_PROCESSOR)
                    .where(STRM_PROCESSOR.PROCESSOR_ID.eq(processorId))
                    .fetchOne();

        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        if (record != null) {
            cache.put(processorId, record);
            return record.getId();
        }

        return null;
    }

    @Override
    public Integer getOrCreate(final Integer processorId, final String pipelineUuid) {
        if (processorId == null || pipelineUuid == null) {
            return null;
        }

        Integer id = get(processorId, pipelineUuid);
        if (id == null) {
            // Try and create.
            id = create(processorId, pipelineUuid);
            if (id == null) {
                // Get again.
                id = get(processorId, pipelineUuid);
            }
        }

        return id;
    }

//    @Override
//    public List<String> list() {
//        try (final Connection connection = dataSource.getConnection()) {
//            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
//
//            return create
//                    .select(FD.NAME)
//                    .from(FD)
//                    .fetch(FD.NAME);
//
//        } catch (final SQLException e) {
//            LOGGER.error(e.getMessage(), e);
//            throw new RuntimeException(e.getMessage(), e);
//        }
//    }

    private Integer create(final int processorId, final String pipelineUuid) {
        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);

            final StrmProcessorRecord record = create
                    .insertInto(STRM_PROCESSOR, STRM_PROCESSOR.PROCESSOR_ID, STRM_PROCESSOR.PIPE_UUID)
                    .values(processorId, pipelineUuid)
                    .returning(STRM_FEED.ID)
                    .fetchOne();
            cache.put(processorId, record);
            return record.getId();

        } catch (final SQLException e) {
            // Expect errors in the case of duplicate names.
            LOGGER.debug(e.getMessage(), e);
        }

        return null;
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
