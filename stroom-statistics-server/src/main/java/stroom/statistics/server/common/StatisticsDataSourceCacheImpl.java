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

import org.ehcache.Cache;
import org.ehcache.config.CacheConfiguration;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.expiry.Duration;
import org.ehcache.expiry.Expirations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.cache.Loader;
import stroom.entity.server.event.EntityEvent;
import stroom.entity.server.event.EntityEventHandler;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.EntityAction;
import stroom.statistics.common.FindStatisticsEntityCriteria;
import stroom.statistics.common.StatisticStoreCache;
import stroom.statistics.common.StatisticStoreEntityService;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.util.cache.CentralCacheManager;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

@Component("statisticsDataSourceCache")
@EntityEventHandler(type = StatisticStoreEntity.ENTITY_TYPE, action = {EntityAction.UPDATE, EntityAction.DELETE})
class StatisticsDataSourceCacheImpl implements StatisticStoreCache, EntityEvent.Handler {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsDataSourceCacheImpl.class);

    private static final String STATISTICS_DATA_SOURCE_CACHE_NAME_BY_ID = "StatisticDataSourceCacheById";
    private static final String STATISTICS_DATA_SOURCE_CACHE_NAME_BY_NAME_ENGINE = "StatisticDataSourceCacheByNameEngine";

    private final StatisticStoreEntityService statisticsDataSourceService;
    private final CentralCacheManager cacheManager;

    private volatile Cache<NameEngineCacheKey, StatisticStoreEntity> cacheByNameEngine;
    private volatile Cache<DocRef, StatisticStoreEntity> cacheByRef;

    @Inject
    StatisticsDataSourceCacheImpl(final StatisticStoreEntityService statisticsDataSourceService,
                                  final CentralCacheManager cacheManager) {
        this.statisticsDataSourceService = statisticsDataSourceService;
        this.cacheManager = cacheManager;
    }

    private Cache<NameEngineCacheKey, StatisticStoreEntity> getCacheByEngineName() {
        if (cacheByNameEngine == null) {
            synchronized (this) {
                if (cacheByNameEngine == null) {
                    final Loader<NameEngineCacheKey, StatisticStoreEntity> loader = new Loader<NameEngineCacheKey, StatisticStoreEntity>() {
                        @Override
                        public StatisticStoreEntity load(final NameEngineCacheKey key) throws Exception {
                            // Id and key not found in cache so try pulling it from the DB

                            final BaseResultList<StatisticStoreEntity> results = statisticsDataSourceService
                                    .find(FindStatisticsEntityCriteria.instanceByNameAndEngineName(key.statisticName, key.engineName));

                            if (results.size() > 1) {
                                throw new RuntimeException(String.format(
                                        "Found multiple StatisticDataSource entities with name %s and engine %s.  This should not happen",
                                        key.statisticName, key.engineName));
                            } else if (results.size() == 1) {
                                return results.iterator().next();
                            }

                            return null;
                        }
                    };

                    final CacheConfiguration<NameEngineCacheKey, StatisticStoreEntity> cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder(NameEngineCacheKey.class, StatisticStoreEntity.class,
                            ResourcePoolsBuilder.heap(100))
                            .withExpiry(Expirations.timeToIdleExpiration(Duration.of(10, TimeUnit.MINUTES)))
                            .withLoaderWriter(loader)
                            .build();

                    cacheByNameEngine = cacheManager.createCache(STATISTICS_DATA_SOURCE_CACHE_NAME_BY_NAME_ENGINE, cacheConfiguration);
                }
            }
        }
        return cacheByNameEngine;
    }

    private Cache<DocRef, StatisticStoreEntity> getCacheByRef() {
        if (cacheByRef == null) {
            synchronized (this) {
                if (cacheByRef == null) {
                    final Loader<DocRef, StatisticStoreEntity> loader = new Loader<DocRef, StatisticStoreEntity>() {
                        @Override
                        public StatisticStoreEntity load(final DocRef docRef) throws Exception {
                            return statisticsDataSourceService.loadByUuid(docRef.getUuid());
                        }
                    };

                    final CacheConfiguration<DocRef, StatisticStoreEntity> cacheConfiguration = CacheConfigurationBuilder.newCacheConfigurationBuilder(DocRef.class, StatisticStoreEntity.class,
                            ResourcePoolsBuilder.heap(100))
                            .withExpiry(Expirations.timeToIdleExpiration(Duration.of(10, TimeUnit.MINUTES)))
                            .withLoaderWriter(loader)
                            .build();

                    cacheByRef = cacheManager.createCache(STATISTICS_DATA_SOURCE_CACHE_NAME_BY_ID, cacheConfiguration);
                }
            }
        }
        return cacheByRef;
    }

    @Override
    public StatisticStoreEntity getStatisticsDataSource(final DocRef docRef) {
        return getCacheByRef().get(docRef);
    }

    @Override
    public StatisticStoreEntity getStatisticsDataSource(final String statisticName, final String engineName) {
        final NameEngineCacheKey key = new NameEngineCacheKey(statisticName, engineName);
        return getCacheByEngineName().get(key);
    }

    private NameEngineCacheKey buildNameEngineKey(final StatisticStoreEntity statisticsDataSource) {
        return new NameEngineCacheKey(statisticsDataSource.getName(), statisticsDataSource.getEngineName());
    }

    @Override
    public void onChange(final EntityEvent event) {
        try {
            final Cache<NameEngineCacheKey, StatisticStoreEntity> cacheByEngineName = getCacheByEngineName();
            final Cache<DocRef, StatisticStoreEntity> cacheByRef = getCacheByRef();

            if (EntityAction.UPDATE.equals(event.getAction()) || EntityAction.DELETE.equals(event.getAction())) {
                final StatisticStoreEntity statisticsDataSource = cacheByRef.get(event.getDocRef());

                if (statisticsDataSource != null) {
                    // found it in one cache so remove from both
                    final NameEngineCacheKey nameEngineKey = buildNameEngineKey(statisticsDataSource);

                    cacheByRef.remove(event.getDocRef());
                    cacheByEngineName.remove(nameEngineKey);

                } else {
                    // fall back option, as it couldn't be found in the ref cache so
                    // try again in the nameEngine cache

                    // not very efficient but we shouldn't have that many entities
                    // in the cache and deletes will not happen
                    // very
                    // often.
                    cacheByEngineName.forEach(entry -> {
                        try {
                            final StatisticStoreEntity value = entry.getValue();
                            if (value != null && DocRef.create(value).equals(event.getDocRef())) {
                                cacheByRef.remove(DocRef.create(value));
                                cacheByEngineName.remove(entry.getKey());
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
