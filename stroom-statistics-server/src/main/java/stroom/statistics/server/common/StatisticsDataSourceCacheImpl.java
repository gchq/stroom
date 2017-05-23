/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.statistics.server.common;

import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.entity.server.event.EntityEvent;
import stroom.entity.server.event.EntityEventHandler;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.EntityAction;
import stroom.query.api.DocRef;
import stroom.statistics.common.FindStatisticsEntityCriteria;
import stroom.statistics.common.StatisticStoreCache;
import stroom.statistics.common.StatisticStoreEntityService;
import stroom.statistics.shared.StatisticStoreEntity;

import javax.inject.Inject;

@Component("statisticsDataSourceCache")
@EntityEventHandler(type = StatisticStoreEntity.ENTITY_TYPE, action = {EntityAction.UPDATE, EntityAction.DELETE})
public class StatisticsDataSourceCacheImpl implements StatisticStoreCache, EntityEvent.Handler {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsDataSourceCacheImpl.class);

    private static final String STATISTICS_DATA_SOURCE_CACHE_NAME_BY_ID = "StatisticDataSourceCacheById";
    private static final String STATISTICS_DATA_SOURCE_CACHE_NAME_BY_NAME = "StatisticDataSourceCacheByName";

    private final StatisticStoreEntityService statisticsDataSourceService;
    private final CacheManager cacheManager;

    private volatile Ehcache cacheByRef;
    private volatile Ehcache cacheByName;

    @Inject
    public StatisticsDataSourceCacheImpl(final StatisticStoreEntityService statisticsDataSourceService,
                                         final CacheManager cacheManager) {
        this.statisticsDataSourceService = statisticsDataSourceService;
        this.cacheManager = cacheManager;
    }

    private Ehcache getCacheByName() {
        if (cacheByName == null) {
            synchronized (this) {
                if (cacheByName == null) {
                    cacheByName = cacheManager.getEhcache(STATISTICS_DATA_SOURCE_CACHE_NAME_BY_NAME);
                }
            }
        }
        return cacheByName;
    }

    private Ehcache getCacheByRef() {
        if (cacheByRef == null) {
            synchronized (this) {
                if (cacheByRef == null) {
                    cacheByRef = cacheManager.getEhcache(STATISTICS_DATA_SOURCE_CACHE_NAME_BY_ID);
                }
            }
        }
        return cacheByRef;
    }

    @Override
    public StatisticStoreEntity getStatisticsDataSource(final DocRef docRef) {
        final Ehcache cacheByRef = getCacheByRef();

        final Element cacheResult = cacheByRef.get(docRef);

        StatisticStoreEntity statisticsDataSource = null;

        if (cacheResult == null && !cacheByRef.isKeyInCache(docRef)) {
            // dataSource and key not found in cache so pull it from the DB and
            // if found cache it
            statisticsDataSource = statisticsDataSourceService.loadByUuid(docRef.getUuid());

            if (statisticsDataSource != null) {
                // it is possible multiple threads may try and do this at the
                // same time but only the first one will
                // manage to get it into the cache.
                putToBothCaches(docRef, statisticsDataSource);
            } else {
                // not found in DB so put a null value in the cache to stop us
                // looking in the DB again.
                cacheByRef.putIfAbsent(new Element(docRef, null));
            }

        } else if (cacheResult != null) {
            statisticsDataSource = (StatisticStoreEntity) cacheResult.getObjectValue();
        }

        return statisticsDataSource;
    }

    @Override
    public StatisticStoreEntity getStatisticsDataSource(final String statisticName, final String engineName) {
        final Ehcache cacheByName = getCacheByName();

        final Element cacheResult = cacheByName.get(statisticName);

        StatisticStoreEntity statisticsDataSource = null;

        if (cacheResult == null && !cacheByName.isKeyInCache(statisticName)) {
            // Id and key not found in cache so try pulling it from the DB

            final BaseResultList<StatisticStoreEntity> results = statisticsDataSourceService
                    .find(FindStatisticsEntityCriteria.instanceByNameAndEngineName(statisticName, engineName));

            if (results.size() > 1) {
                throw new RuntimeException(String.format(
                        "Found multiple StatisticDataSource entities with name %s, engine %s and type %s.  This should not happen",
                        statisticName, engineName));
            } else if (results.size() == 1) {
                statisticsDataSource = results.iterator().next();
                // it is possible multiple threads may try and do this at the
                // same time but only the first one will
                // manage to get it into the cache.
                putToBothCaches(DocRefUtil.create(statisticsDataSource), statisticsDataSource);
            } else if (results.size() == 0) {
                // not found in DB so put a null value in the cache to stop us
                // looking in the DB again.
                cacheByName.putIfAbsent(new Element(statisticName, null));
            }
        } else if (cacheResult != null) {
            statisticsDataSource = (StatisticStoreEntity) cacheResult.getObjectValue();
        }

        return statisticsDataSource;
    }

    private void putToBothCaches(final DocRef docRef,
                                 final StatisticStoreEntity statisticsDataSource) {
        getCacheByName().putIfAbsent(new Element(statisticsDataSource.getName(), statisticsDataSource));
        getCacheByRef().putIfAbsent(new Element(docRef, statisticsDataSource));
    }

    private void putToCache(final Ehcache cache, final Element element) {
        cache.put(element);
    }


    @Override
    public void onChange(final EntityEvent event) {
        try {
            final Ehcache cacheByName = getCacheByName();
            final Ehcache cacheByRef = getCacheByRef();

//        final long entityId = event.getDocRef().getId();

            if (EntityAction.UPDATE.equals(event.getAction())) {
                final StatisticStoreEntity statisticsDataSource = statisticsDataSourceService.loadByUuid(event.getDocRef().getUuid());

                if (statisticsDataSource == null) {
                    throw new RuntimeException(
                            String.format("Unable to find a statistics data source entity %s", event.getDocRef()));
                }

                // put the updated item into both caches

                putToCache(cacheByRef, new Element(event.getDocRef(), statisticsDataSource));
                putToCache(cacheByName, new Element(statisticsDataSource.getName(), statisticsDataSource));

            } else if (EntityAction.DELETE.equals(event.getAction())) {
                final Element element = cacheByRef.get(event.getDocRef());

                if (element != null) {
                    // found it in one cache so remove from both

                    final StatisticStoreEntity statisticsDataSource = (StatisticStoreEntity) element.getObjectValue();

                    cacheByRef.remove(event.getDocRef());
                    cacheByName.remove(statisticsDataSource.getName());

                } else {
                    // fall back option, as it couldn't be found in the ref cache so
                    // try again in the nameEngine cache

                    // not very efficient but we shouldn't have that many entities
                    // in the cache and deletes will not happen
                    // very
                    // often.
                    for (final Object key : cacheByName.getKeys()) {
                        try {
                            final Element elem = cacheByName.get(key);
                            if (elem != null) {
                                final StatisticStoreEntity statisticsDataSource = (StatisticStoreEntity) elem.getObjectValue();
                                if (statisticsDataSource != null && DocRefUtil.create(statisticsDataSource).equals(event.getDocRef())) {
                                    cacheByRef.remove(DocRefUtil.create(statisticsDataSource));
                                    cacheByName.remove(key);
                                }
                            }
                        } catch (final Exception e) {
                            LOGGER.error(e.getMessage(), e);
                        }
                    }
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

        public NameEngineCacheKey(final String statisticName, final String engineName) {
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
