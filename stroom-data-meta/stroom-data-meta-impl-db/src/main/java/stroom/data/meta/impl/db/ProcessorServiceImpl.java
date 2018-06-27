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

package stroom.data.meta.impl.db;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.data.meta.impl.db.stroom.tables.records.DataProcessorRecord;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static stroom.data.meta.impl.db.stroom.tables.DataProcessor.DATA_PROCESSOR;

@Singleton
class ProcessorServiceImpl implements ProcessorService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorServiceImpl.class);

    private final Map<Integer, DataProcessorRecord> cache = new ConcurrentHashMap<>();

    private final DataSource dataSource;

    @Inject
    ProcessorServiceImpl(final DataMetaDataSource dataSource) {
        this.dataSource = dataSource;
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

    private Integer get(final Integer processorId, final String pipelineUuid) {
        DataProcessorRecord record = cache.get(processorId);
        if (record != null) {
            return record.getId();
        }

        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            record = create
                    .selectFrom(DATA_PROCESSOR)
                    .where(DATA_PROCESSOR.PROCESSOR_ID.eq(processorId))
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

    private Integer create(final int processorId, final String pipelineUuid) {
        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            final DataProcessorRecord record = create
                    .insertInto(DATA_PROCESSOR, DATA_PROCESSOR.PROCESSOR_ID, DATA_PROCESSOR.PIPELINE_UUID)
                    .values(processorId, pipelineUuid)
                    .returning(DATA_PROCESSOR.ID)
                    .fetchOne();
            cache.put(processorId, record);
            return record.getId();

        } catch (final SQLException | RuntimeException e) {
            // Expect errors in the case of duplicate names.
            LOGGER.debug(e.getMessage(), e);
        }

        return null;
    }

    void clear() {
        deleteAll();
        cache.clear();
    }

    int deleteAll() {
        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            return create
                    .delete(DATA_PROCESSOR)
                    .execute();
        } catch (final SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
