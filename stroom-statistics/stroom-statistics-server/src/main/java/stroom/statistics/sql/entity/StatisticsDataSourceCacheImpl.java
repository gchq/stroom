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

package stroom.statistics.sql.entity;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docstore.shared.DocRefUtil;
import stroom.entity.event.EntityEvent;
import stroom.entity.event.EntityEventHandler;
import stroom.entity.shared.Clearable;
import stroom.entity.shared.EntityAction;
import stroom.docref.DocRef;
import stroom.statistics.shared.StatisticStoreDoc;
import stroom.util.cache.CacheManager;
import stroom.util.cache.CacheUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
@EntityEventHandler(
        type = StatisticStoreDoc.DOCUMENT_TYPE,
        action = {
                EntityAction.CREATE,
                EntityAction.UPDATE,
                EntityAction.DELETE})
class StatisticsDataSourceCacheImpl implements StatisticStoreCache, EntityEvent.Handler, Clearable {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsDataSourceCacheImpl.class);

    private static final String STATISTICS_DATA_SOURCE_CACHE_NAME_BY_ID = "StatisticDataSourceCacheById";
    private static final String STATISTICS_DATA_SOURCE_CACHE_NAME_BY_NAME = "StatisticDataSourceCacheByName";

    private final StatisticStoreStore statisticStoreStore;
    private final CacheManager cacheManager;

    private volatile LoadingCache<String, Optional<StatisticStoreDoc>> cacheByName;
    private volatile LoadingCache<DocRef, Optional<StatisticStoreDoc>> cacheByRef;

    @Inject
    StatisticsDataSourceCacheImpl(final StatisticStoreStore statisticStoreStore,
                                  final CacheManager cacheManager) {
        this.statisticStoreStore = statisticStoreStore;
        this.cacheManager = cacheManager;
    }

    private LoadingCache<String, Optional<StatisticStoreDoc>> getCacheByName() {
        if (cacheByName == null) {
            synchronized (this) {
                if (cacheByName == null) {
                    final CacheLoader<String, Optional<StatisticStoreDoc>> cacheLoader = CacheLoader.from(k -> {
                        if (k == null) {
                            return Optional.empty();
                        }

                        // Id and key not found in cache so try pulling it from the DB
                        final List<DocRef> results = statisticStoreStore.list().stream().filter(docRef -> k.equals(docRef.getName())).collect(Collectors.toList());
                        if (results.size() > 1) {
                            throw new RuntimeException(String.format(
                                    "Found multiple StatisticDataSource entities with name %s. This should not happen",
                                    k));
                        } else if (results.size() == 1) {
                            return Optional.ofNullable(statisticStoreStore.readDocument(results.get(0)));
                        }

                        return Optional.empty();
                    });
                    cacheByName = createCache(STATISTICS_DATA_SOURCE_CACHE_NAME_BY_NAME, cacheLoader);
                }
            }
        }
        return cacheByName;
    }

    private LoadingCache<DocRef, Optional<StatisticStoreDoc>> getCacheByRef() {
        if (cacheByRef == null) {
            synchronized (this) {
                if (cacheByRef == null) {
                    final CacheLoader<DocRef, Optional<StatisticStoreDoc>> cacheLoader = CacheLoader.from(k -> Optional.ofNullable(statisticStoreStore.readDocument(k)));
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
    public StatisticStoreDoc getStatisticsDataSource(final DocRef docRef) {
        return getCacheByRef().getUnchecked(docRef).orElse(null);
    }

    @Override
    public StatisticStoreDoc getStatisticsDataSource(final String statisticName) {
        return getCacheByName().getUnchecked(statisticName).orElse(null);
    }

    @Override
    public void onChange(final EntityEvent event) {
        try {
            final Cache<String, Optional<StatisticStoreDoc>> cacheByEngineName = getCacheByName();
            final Cache<DocRef, Optional<StatisticStoreDoc>> cacheByRef = getCacheByRef();

            final EntityAction entityAction = event.getAction();

            if (EntityAction.UPDATE.equals(entityAction) ||
                    EntityAction.DELETE.equals(entityAction) ||
                    EntityAction.CREATE.equals(entityAction)) {
                final Optional<StatisticStoreDoc> optional = cacheByRef.getIfPresent(event.getDocRef());

                if (optional != null && optional.isPresent()) {
                    final StatisticStoreDoc statisticStoreEntity = optional.get();

                    // found it in one cache so remove from both
                    cacheByRef.invalidate(event.getDocRef());
                    cacheByEngineName.invalidate(statisticStoreEntity.getName());
                } else {
                    // fall back option, as it couldn't be found in the ref cache so
                    // try again in the nameEngine cache

                    // not very efficient but we shouldn't have that many entities
                    // in the cache and deletes will not happen
                    // very
                    // often.
                    cacheByEngineName.asMap().forEach((k, v) -> {
                        try {
                            if (v.isPresent()) {
                                final StatisticStoreDoc value = v.get();
                                final DocRef docRef = DocRefUtil.create(value);

                                if (docRef.equals(event.getDocRef())) {
                                    cacheByRef.invalidate(docRef);
                                    cacheByEngineName.invalidate(k);
                                }
                            }
                        } catch (final RuntimeException e) {
                            LOGGER.error(e.getMessage(), e);
                        }
                    });
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Override
    public void clear() {
        if (cacheByName != null) {
            CacheUtil.clear(cacheByName);
        }
        if (cacheByRef != null) {
            CacheUtil.clear(cacheByRef);
        }
    }
}
