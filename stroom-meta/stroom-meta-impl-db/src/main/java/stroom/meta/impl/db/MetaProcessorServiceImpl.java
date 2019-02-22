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
import stroom.meta.impl.db.jooq.tables.records.MetaProcessorRecord;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static stroom.meta.impl.db.jooq.tables.MetaProcessor.META_PROCESSOR;

@Singleton
class MetaProcessorServiceImpl implements MetaProcessorService {
    // TODO : @66 Replace with a proper cache.
    private final Map<String, MetaProcessorRecord> cache = new ConcurrentHashMap<>();

    private final ConnectionProvider connectionProvider;

    @Inject
    MetaProcessorServiceImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public Integer getOrCreate(final String processorUuid, final String processorFilterUuid, final String pipelineUuid) {
        if (processorUuid == null || pipelineUuid == null) {
            return null;
        }

        Integer id = get(processorUuid, processorFilterUuid, pipelineUuid);
        if (id == null) {
            // Create.
            id = create(processorUuid, processorFilterUuid, pipelineUuid);
        }

        return id;
    }

    private Integer get(final String processorUuid, final String processorFilterUuid, final String pipelineUuid) {
        MetaProcessorRecord record = cache.get(processorUuid);
        if (record != null) {
            return record.getId();
        }

        return JooqUtil.contextResult(connectionProvider, context -> context
                .selectFrom(META_PROCESSOR)
                .where(META_PROCESSOR.PROCESSOR_UUID.eq(processorUuid)
                        .and(META_PROCESSOR.PROCESSOR_FILTER_UUID.eq(processorFilterUuid))
                        .and(META_PROCESSOR.PIPELINE_UUID.eq(pipelineUuid)))
                .fetchOptional()
                .map(r -> {
                    cache.put(processorUuid, r);
                    return r.getId();
                })
                .orElse(null));
    }

    private Integer create(final String processorUuid, final String processorFilterUuid, final String pipelineUuid) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .insertInto(META_PROCESSOR, META_PROCESSOR.PROCESSOR_UUID, META_PROCESSOR.PROCESSOR_FILTER_UUID, META_PROCESSOR.PIPELINE_UUID)
                .values(processorUuid, processorFilterUuid, pipelineUuid)
                .onDuplicateKeyIgnore()
                .returning(META_PROCESSOR.ID)
                .fetchOptional()
                .map(record -> {
                    cache.put(processorUuid, record);
                    return record.getId();
                })
                .orElseGet(() -> get(processorUuid, processorFilterUuid, pipelineUuid))
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
