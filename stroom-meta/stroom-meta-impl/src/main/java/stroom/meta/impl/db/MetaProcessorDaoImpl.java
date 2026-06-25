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

package stroom.meta.impl.db;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.db.util.JooqUtil;
import stroom.meta.impl.MetaProcessorDao;
import stroom.meta.impl.MetaServiceConfig;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Objects;
import java.util.Optional;

import static stroom.meta.impl.db.jooq.tables.MetaProcessor.META_PROCESSOR;

@Singleton
class MetaProcessorDaoImpl implements MetaProcessorDao, Clearable {

    private static final String CACHE_NAME = "Meta Processor Cache";

    private final LoadingStroomCache<Key, Integer> cache;
    private final MetaDbConnProvider metaDbConnProvider;

    @Inject
    MetaProcessorDaoImpl(final MetaDbConnProvider metaDbConnProvider,
                         final CacheManager cacheManager,
                         final Provider<MetaServiceConfig> metaServiceConfigProvider) {
        this.metaDbConnProvider = metaDbConnProvider;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> metaServiceConfigProvider.get().getMetaProcessorCache(),
                this::load);
    }

    private int load(final Key key) {
        // Try and get the existing id from the DB.
        return get(key.getProcessorUuid())
                .or(() -> {
                    // The id isn't in the DB so create it.
                    return create(key.getProcessorUuid(), key.getPipelineUuid())
                            .or(() -> {
                                // If the id is still null then this may be because the create method failed
                                // due to the name having been inserted into the DB by another thread prior
                                // to us calling create and the DB preventing duplicate names.
                                // Assuming this is the case, try and get the id from the DB one last time.
                                return get(key.getProcessorUuid());
                            });
                })
                .orElseThrow();
    }

    @Override
    public Integer getOrCreate(final String processorUuid, final String pipelineUuid) {
        if (processorUuid == null || pipelineUuid == null) {
            return null;
        }

        final Key key = new Key(processorUuid, pipelineUuid);
        return cache.get(key);
    }

    private Optional<Integer> get(final String processorUuid) {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .select(META_PROCESSOR.ID)
                .from(META_PROCESSOR)
                .where(META_PROCESSOR.PROCESSOR_UUID.eq(processorUuid))
                .fetchOptional(META_PROCESSOR.ID));
    }

    private Optional<Integer> create(final String processorUuid, final String pipelineUuid) {
        return JooqUtil.onDuplicateKeyIgnore(() ->
                JooqUtil.contextResult(metaDbConnProvider, context -> context
                        .insertInto(META_PROCESSOR, META_PROCESSOR.PROCESSOR_UUID, META_PROCESSOR.PIPELINE_UUID)
                        .values(processorUuid, pipelineUuid)
                        .returning(META_PROCESSOR.ID)
                        .fetchOptional(META_PROCESSOR.ID)));
    }

    @Override
    public void clear() {
        cache.clear();
    }

    private static class Key {

        private final String processorUuid;
        private final String pipelineUuid;

        private Key(final String processorUuid, final String pipelineUuid) {
            this.processorUuid = processorUuid;
            this.pipelineUuid = pipelineUuid;
        }

        public String getProcessorUuid() {
            return processorUuid;
        }

        public String getPipelineUuid() {
            return pipelineUuid;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Key key = (Key) o;
            return Objects.equals(processorUuid, key.processorUuid) &&
                   Objects.equals(pipelineUuid, key.pipelineUuid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(processorUuid, pipelineUuid);
        }

        @Override
        public String toString() {
            return "Key{" +
                   "processorUuid='" + processorUuid + '\'' +
                   ", pipelineUuid='" + pipelineUuid + '\'' +
                   '}';
        }
    }
}
