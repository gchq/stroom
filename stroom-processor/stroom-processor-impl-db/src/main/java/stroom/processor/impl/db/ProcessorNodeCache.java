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

package stroom.processor.impl.db;

import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.db.util.JooqUtil;
import stroom.processor.impl.ProcessorConfig;
import stroom.processor.impl.db.jooq.tables.records.ProcessorNodeRecord;
import stroom.util.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

import static stroom.processor.impl.db.jooq.tables.ProcessorNode.PROCESSOR_NODE;

@Singleton
class ProcessorNodeCache implements Clearable {
    private static final String CACHE_NAME = "Processor Node Cache";

    private final ICache<String, Integer> cache;
    private final ProcessorDbConnProvider processorDbConnProvider;

    @Inject
    ProcessorNodeCache(final ProcessorDbConnProvider processorDbConnProvider,
                       final CacheManager cacheManager,
                       final ProcessorConfig processorConfig) {
        this.processorDbConnProvider = processorDbConnProvider;
        cache = cacheManager.create(CACHE_NAME, processorConfig::getProcessorNodeCache, this::load);
    }

    private int load(final String name) {
        // Try and get the existing id from the DB.
        return get(name)
                .or(() -> {
                    // The id isn't in the DB so create it.
                    return create(name)
                            .or(() -> {
                                // If the id is still null then this may be because the create method failed
                                // due to the name having been inserted into the DB by another thread prior
                                // to us calling create and the DB preventing duplicate names.
                                // Assuming this is the case, try and get the id from the DB one last time.
                                return get(name);
                            });
                })
                .orElseThrow();
    }

    public Integer getOrCreate(final String name) {
        return cache.get(name);
    }

//    @Override
//    public List<String> list() {
//        return JooqUtil.contextResult(connectionProvider, context -> context
//                .select(PROCESSOR_NODE.NAME)
//                .from(PROCESSOR_NODE)
//                .fetch(PROCESSOR_NODE.NAME));
//    }

    private Optional<Integer> get(final String name) {
        return JooqUtil.contextResult(processorDbConnProvider, context -> context
                .select(PROCESSOR_NODE.ID)
                .from(PROCESSOR_NODE)
                .where(PROCESSOR_NODE.NAME.eq(name))
                .fetchOptional(PROCESSOR_NODE.ID));
    }

    private Optional<Integer> create(final String name) {
        return JooqUtil.contextResult(processorDbConnProvider, context -> context
                .insertInto(PROCESSOR_NODE, PROCESSOR_NODE.NAME)
                .values(name)
                .onDuplicateKeyIgnore()
                .returning(PROCESSOR_NODE.ID)
                .fetchOptional()
                .map(ProcessorNodeRecord::getId));
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
