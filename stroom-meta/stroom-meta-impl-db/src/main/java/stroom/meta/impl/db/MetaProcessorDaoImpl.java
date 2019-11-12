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
import stroom.meta.impl.MetaProcessorDao;
import stroom.meta.impl.db.jooq.tables.records.MetaProcessorRecord;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static stroom.meta.impl.db.jooq.tables.MetaProcessor.META_PROCESSOR;

@Singleton
class MetaProcessorDaoImpl implements MetaProcessorDao {
    // TODO : @66 Replace with a proper cache.
    private final Map<String, Integer> cache = new ConcurrentHashMap<>();

    private final MetaDbConnProvider metaDbConnProvider;

    @Inject
    MetaProcessorDaoImpl(final MetaDbConnProvider metaDbConnProvider) {
        this.metaDbConnProvider = metaDbConnProvider;
    }

    @Override
    public Integer getOrCreate(final String processorUuid, final String pipelineUuid) {
        if (processorUuid == null || pipelineUuid == null) {
            return null;
        }

        // Try and get the id from the cache.
        return Optional.ofNullable(cache.get(processorUuid))
                .or(() -> {
                    // Try and get the existing id from the DB.
                    return get(processorUuid)
                            .or(() -> {
                                // The id isn't in the DB so create it.
                                return create(processorUuid, pipelineUuid)
                                        .or(() -> {
                                            // If the id is still null then this may be because the create method failed
                                            // due to the name having been inserted into the DB by another thread prior
                                            // to us calling create and the DB preventing duplicate names.
                                            // Assuming this is the case, try and get the id from the DB one last time.
                                            return get(processorUuid);
                                        });
                            })
                            .map(i -> {
                                // Cache for next time.
                                cache.put(processorUuid, i);
                                return i;
                            });
                }).orElseThrow();
    }

    private Optional<Integer> get(final String processorUuid) {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .select(META_PROCESSOR.ID)
                .from(META_PROCESSOR)
                .where(META_PROCESSOR.PROCESSOR_UUID.eq(processorUuid))
                .fetchOptional(META_PROCESSOR.ID));
    }

    private Optional<Integer> create(final String processorUuid, final String pipelineUuid) {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .insertInto(META_PROCESSOR, META_PROCESSOR.PROCESSOR_UUID, META_PROCESSOR.PIPELINE_UUID)
                .values(processorUuid, pipelineUuid)
                .onDuplicateKeyIgnore()
                .returning(META_PROCESSOR.ID)
                .fetchOptional()
                .map(MetaProcessorRecord::getId));
    }

    @Override
    public void clear() {
        deleteAll();
        cache.clear();
    }

    private int deleteAll() {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .delete(META_PROCESSOR)
                .execute());
    }
}
