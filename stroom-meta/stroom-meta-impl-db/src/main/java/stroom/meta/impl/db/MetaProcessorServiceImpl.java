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

package stroom.meta.impl.db;

import stroom.db.util.JooqUtil;
import stroom.meta.impl.db.tables.records.MetaProcessorRecord;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static stroom.meta.impl.db.tables.MetaProcessor.META_PROCESSOR;

@Singleton
class MetaProcessorServiceImpl implements MetaProcessorService {
    private final Map<Integer, MetaProcessorRecord> cache = new ConcurrentHashMap<>();

    private final ConnectionProvider connectionProvider;

    @Inject
    MetaProcessorServiceImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
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
//        JooqUtil.context(connectionProvider, context -> context
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
        MetaProcessorRecord record = cache.get(processorId);
        if (record != null) {
            return record.getId();
        }

        return JooqUtil.contextResult(connectionProvider, context -> context
                .selectFrom(META_PROCESSOR)
                .where(META_PROCESSOR.PROCESSOR_ID.eq(processorId))
                .fetchOptional()
                .map(r -> {
                    cache.put(processorId, r);
                    return r.getId();
                })
                .orElse(null));
    }

    private Integer create(final int processorId, final String pipelineUuid) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .insertInto(META_PROCESSOR, META_PROCESSOR.PROCESSOR_ID, META_PROCESSOR.PIPELINE_UUID)
                .values(processorId, pipelineUuid)
                .onDuplicateKeyIgnore()
                .returning(META_PROCESSOR.ID)
                .fetchOptional()
                .map(record -> {
                    cache.put(processorId, record);
                    return record.getId();
                })
                .orElseGet(() -> get(processorId, pipelineUuid))
        );
    }

    void clear() {
        deleteAll();
        cache.clear();
    }

    int deleteAll() {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .delete(META_PROCESSOR)
                .execute());
    }
}
