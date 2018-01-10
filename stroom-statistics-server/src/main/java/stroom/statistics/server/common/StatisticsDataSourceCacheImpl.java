/*
 * Copyright 2016 Crown Copyright
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

package stroom.statistics.server.common;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.entity.server.event.EntityEvent;
import stroom.entity.server.event.EntityEventHandler;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.EntityAction;
import stroom.statistics.common.FindStatisticsEntityCriteria;
import stroom.statistics.common.StatisticStoreCache;
import stroom.statistics.common.StatisticStoreEntityService;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.util.cache.CacheManager;

import javax.inject.Inject;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component("statisticsDataSourceCache")
@EntityEventHandler(
        type = StatisticStoreEntity.ENTITY_TYPE,
        action = {
                EntityAction.CREATE,
                EntityAction.UPDATE,
                EntityAction.DELETE})
class StatisticsDataSourceCacheImpl implements StatisticStoreCache, EntityEvent.Handler {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsDataSourceCacheImpl.class);

    private static final String STATISTICS_DATA_SOURCE_CACHE_NAME_BY_ID = "StatisticDataSourceCacheById";
    private static final String STATISTICS_DATA_SOURCE_CACHE_NAME_BY_NAME_ENGINE = "StatisticDataSourceCacheByNameEngine";

    private final StatisticStoreEntityService statisticsDataSourceService;
    private final CacheManager cacheManager;

    private volatile LoadingCache<NameEngineCacheKey, Optional<StatisticStoreEntity>> cacheByNameEngine;
    private volatile LoadingCache<DocRef, Optional<StatisticStoreEntity>> cacheByRef;

    @Inject
    StatisticsDataSourceCacheImpl(final StatisticStoreEntityService statisticsDataSourceService,
                                  final CacheManager cacheManager) {
        this.statisticsDataSourceService = statisticsDataSourceService;
        this.cacheManager = cacheManager;
    }

    private LoadingCache<NameEngineCacheKey, Optional<StatisticStoreEntity>> getCacheByEngineName() {
        if (cacheByNameEngine == null) {
            synchronized (this) {
                if (cacheByNameEngine == null) {
                    final CacheLoader<NameEngineCacheKey, Optional<StatisticStoreEntity>> cacheLoader = CacheLoader.from(k -> {
                        // Id and key not found in cache so try pulling it from the DB

                        final BaseResultList<StatisticStoreEntity> results = statisticsDataSourceService
                                .find(FindStatisticsEntityCriteria.instanceByNameAndEngineName(k.statisticName, k.engineName));

                        if (results.size() > 1) {
                            throw new RuntimeException(String.format(
                                    "Found multiple StatisticDataSource entities with name %s and engine %s.  This should not happen",
                                    k.statisticName, k.engineName));
                        } else if (results.size() == 1) {
                            return Optional.ofNullable(results.iterator().next());
                        }

                        return Optional.empty();
                    });
                    cacheByNameEngine = createCache(STATISTICS_DATA_SOURCE_CACHE_NAME_BY_NAME_ENGINE, cacheLoader);
                }
            }
        }
        return cacheByNameEngine;
    }

    private LoadingCache<DocRef, Optional<StatisticStoreEntity>> getCacheByRef() {
        if (cacheByRef == null) {
            synchronized (this) {
                if (cacheByRef == null) {
                    final CacheLoader<DocRef, Optional<StatisticStoreEntity>> cacheLoader = CacheLoader.from(k -> Optional.ofNullable(statisticsDataSourceService.loadByUuid(k.getUuid())));
                    cacheByRef = createCache(STATISTICS_DATA_SOURCE_CACHE_NAME_BY_ID, cacheLoader);
                }
            }
        }
        return cacheByRef;
    }

    @SuppressWarnings("unchecked")
    private <K, V> LoadingCache<K, V> createCache(final String name, final CacheLoader<K, V> cacheLoader) {
        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(100)
                .expireAfterAccess(10, TimeUnit.MINUTES);
        final LoadingCache<K, V> cache = cacheBuilder.build(cacheLoader);
        cacheManager.registerCache(name, cacheBuilder, cache);
        return cache;
    }

    @Override
    public StatisticStoreEntity getStatisticsDataSource(final DocRef docRef) {
        return getCacheByRef().getUnchecked(docRef).orElse(null);
    }

    @Override
    public StatisticStoreEntity getStatisticsDataSource(final String statisticName, final String engineName) {
        final NameEngineCacheKey key = new NameEngineCacheKey(statisticName, engineName);
        return getCacheByEngineName().getUnchecked(key).orElse(null);
    }

    private NameEngineCacheKey buildNameEngineKey(final StatisticStoreEntity statisticsDataSource) {
        return new NameEngineCacheKey(statisticsDataSource.getName(), statisticsDataSource.getEngineName());
    }

    @Override
    public void onChange(final EntityEvent event) {
        try {
            final Cache<NameEngineCacheKey, Optional<StatisticStoreEntity>> cacheByEngineName = getCacheByEngineName();
            final Cache<DocRef, Optional<StatisticStoreEntity>> cacheByRef = getCacheByRef();

            DocRef docRef = event.getDocRef();
            EntityAction entityAction = event.getAction();

            if (EntityAction.UPDATE.equals(entityAction) ||
                    EntityAction.DELETE.equals(entityAction) ||
                    EntityAction.CREATE.equals(entityAction)) {

                //Handling a create handles the case where we have content that has an orphaned doc ref
                //and then the entity for that docref is imported

                final Optional<StatisticStoreEntity> optional = cacheByRef.getIfPresent(docRef);

                LOGGER.debug("Removing docRef {} from the caches because of entity event {}", docRef, entityAction);
                //remove the entity from the docRef cache whether it exists or not
                cacheByRef.invalidate(event.getDocRef());

                if (optional != null && optional.isPresent()) {

                    //the docRef mapped to an entity in the docRef cache so determine the
                    //engine/name to remove from the other cache
                    final NameEngineCacheKey nameEngineKey = buildNameEngineKey(optional.get());
                    cacheByEngineName.invalidate(nameEngineKey);
                } else {
                    LOGGER.debug("Couldn't find docRef {} in the docRef cache", docRef);
                    // fall back option, as it couldn't be found in the ref cache so
                    // try again in the nameEngine cache.
                    // Not very efficient but we shouldn't have that many entities
                    // in the cache and deletes will not happen very often.
                    cacheByEngineName.asMap().forEach((k, v) -> {
                        try {
                            if (v.isPresent()) {
                                final StatisticStoreEntity value = v.get();
                                if (DocRef.create(value).equals(event.getDocRef())) {
                                    cacheByRef.invalidate(DocRef.create(value));
                                    cacheByEngineName.invalidate(k);
                                }
                            }
                        } catch (final Exception e) {
                            LOGGER.error(e.getMessage(), e);
                        }
                    });
                }
            }
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    /**
     * Class to act as the key object for the cache
     */
    private static class NameEngineCacheKey {
        private final String statisticName;
        private final String engineName;

        NameEngineCacheKey(final String statisticName, final String engineName) {
            this.statisticName = statisticName;
            this.engineName = engineName;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((engineName == null) ? 0 : engineName.hashCode());
            result = prime * result + ((statisticName == null) ? 0 : statisticName.hashCode());
            return result;
        }

        @Override
        public String toString() {
            return "CacheKey [statisticName=" + statisticName + ", engineName=" + engineName + "]";
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            final NameEngineCacheKey other = (NameEngineCacheKey) obj;
            if (engineName == null) {
                if (other.engineName != null)
                    return false;
            } else if (!engineName.equals(other.engineName))
                return false;
            if (statisticName == null) {
                if (other.statisticName != null)
                    return false;
            } else if (!statisticName.equals(other.statisticName))
                return false;
            return true;
        }
    }
}
