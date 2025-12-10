/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.processor.impl.db;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.db.util.JooqUtil;
import stroom.processor.impl.ProcessorConfig;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Optional;

import static stroom.processor.impl.db.jooq.tables.ProcessorNode.PROCESSOR_NODE;

@Singleton
class ProcessorNodeCache implements Clearable {

    private static final String CACHE_NAME = "Processor Node Cache";

    private final LoadingStroomCache<String, Integer> cache;
    private final ProcessorDbConnProvider processorDbConnProvider;

    @Inject
    ProcessorNodeCache(final ProcessorDbConnProvider processorDbConnProvider,
                       final CacheManager cacheManager,
                       final Provider<ProcessorConfig> processorConfigProvider) {
        this.processorDbConnProvider = processorDbConnProvider;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> processorConfigProvider.get().getProcessorNodeCache(),
                this::load);
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
        if (name == null) {
            return null;
        }
        return cache.get(name);
    }

    private Optional<Integer> get(final String name) {
        return JooqUtil.contextResult(processorDbConnProvider, context -> context
                .select(PROCESSOR_NODE.ID)
                .from(PROCESSOR_NODE)
                .where(PROCESSOR_NODE.NAME.eq(name))
                .fetchOptional(PROCESSOR_NODE.ID));
    }

    private Optional<Integer> create(final String name) {
        return JooqUtil.onDuplicateKeyIgnore(() ->
                JooqUtil.contextResult(processorDbConnProvider, context -> context
                        .insertInto(PROCESSOR_NODE, PROCESSOR_NODE.NAME)
                        .values(name)
                        .returning(PROCESSOR_NODE.ID)
                        .fetchOptional(PROCESSOR_NODE.ID)));
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
