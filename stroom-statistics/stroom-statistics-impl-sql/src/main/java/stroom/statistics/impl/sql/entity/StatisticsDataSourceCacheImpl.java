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

package stroom.statistics.impl.sql.entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.util.entity.EntityAction;
import stroom.util.entity.EntityEvent;
import stroom.util.entity.EntityEventHandler;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.PermissionException;
import stroom.statistics.impl.sql.SQLStatisticsConfig;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.util.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
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
    private final SecurityContext securityContext;
    private final SQLStatisticsConfig sqlStatisticsConfig;

    private volatile ICache<String, Optional<StatisticStoreDoc>> cacheByName;
    private volatile ICache<DocRef, Optional<StatisticStoreDoc>> cacheByRef;

    @Inject
    StatisticsDataSourceCacheImpl(final StatisticStoreStore statisticStoreStore,
                                  final CacheManager cacheManager,
                                  final SecurityContext securityContext,
                                  final SQLStatisticsConfig sqlStatisticsConfig) {
        this.statisticStoreStore = statisticStoreStore;
        this.cacheManager = cacheManager;
        this.securityContext = securityContext;
        this.sqlStatisticsConfig = sqlStatisticsConfig;
    }

    private ICache<String, Optional<StatisticStoreDoc>> getCacheByName() {
        if (cacheByName == null) {
            synchronized (this) {
                if (cacheByName == null) {
                    final Function<String, Optional<StatisticStoreDoc>> cacheLoader = k -> {
                        if (k == null) {
                            return Optional.empty();
                        }

                        // Id and key not found in cache so try pulling it from the DB
                        return securityContext.asProcessingUserResult(() -> {
                            final List<DocRef> results = statisticStoreStore.list().stream().filter(docRef -> k.equals(docRef.getName())).collect(Collectors.toList());
                            if (results.size() > 1) {
                                throw new RuntimeException(String.format(
                                        "Found multiple StatisticDataSource entities with name %s. This should not happen", k));
                            } else if (results.size() == 1) {
                                return Optional.ofNullable(statisticStoreStore.readDocument(results.get(0)));
                            }

                            return Optional.empty();
                        });
                    };
                    cacheByName = createCache(STATISTICS_DATA_SOURCE_CACHE_NAME_BY_NAME, cacheLoader);
                }
            }
        }
        return cacheByName;
    }

    private ICache<DocRef, Optional<StatisticStoreDoc>> getCacheByRef() {
        if (cacheByRef == null) {
            synchronized (this) {
                if (cacheByRef == null) {
                    final Function<DocRef, Optional<StatisticStoreDoc>> cacheLoader = k ->
                            securityContext.asProcessingUserResult(() ->
                                    Optional.ofNullable(statisticStoreStore.readDocument(k)));
                    cacheByRef = createCache(STATISTICS_DATA_SOURCE_CACHE_NAME_BY_ID, cacheLoader);
                }
            }
        }
        return cacheByRef;
    }

    private <K, V> ICache<K, V> createCache(final String name, final Function<K, V> cacheLoader) {
        return cacheManager.create(name, sqlStatisticsConfig::getDataSourceCache, cacheLoader);
    }

    private boolean permissionFilter(final StatisticStoreDoc entity) {
        if (!securityContext.hasDocumentPermission(entity.getUuid(), DocumentPermissionNames.READ)) {
            throw new PermissionException(securityContext.getUserId(), "Not permitted to read " + entity.getName());
        }
        return true;
    }

    @Override
    public StatisticStoreDoc getStatisticsDataSource(final DocRef docRef) {
        return getCacheByRef()
                .get(docRef)
                .filter(this::permissionFilter)
                .orElse(null);
    }

    @Override
    public StatisticStoreDoc getStatisticsDataSource(final String statisticName) {
        return getCacheByName()
                .get(statisticName)
                .filter(this::permissionFilter)
                .orElse(null);
    }

    @Override
    public void onChange(final EntityEvent event) {
        try {
            final ICache<String, Optional<StatisticStoreDoc>> cacheByEngineName = getCacheByName();
            final ICache<DocRef, Optional<StatisticStoreDoc>> cacheByRef = getCacheByRef();

            final EntityAction entityAction = event.getAction();

            if (EntityAction.UPDATE.equals(entityAction) ||
                    EntityAction.DELETE.equals(entityAction) ||
                    EntityAction.CREATE.equals(entityAction)) {
                final Optional<Optional<StatisticStoreDoc>> optional = cacheByRef.getOptional(event.getDocRef());

                if (optional.isPresent() && optional.get().isPresent()) {
                    final StatisticStoreDoc statisticStoreEntity = optional.get().get();

                    // found it in one cache so remove from both
                    cacheByRef.invalidate(event.getDocRef());
                    cacheByEngineName.invalidate(statisticStoreEntity.getName());
                } else {
                    // fall back option, as it couldn't be found in the ref cache so
                    // try again in the nameEngine cache

                    // not very efficient but we shouldn't have that many entities
                    // in the cache and deletes will not happen very often.
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
            cacheByName.clear();
        }
        if (cacheByRef != null) {
            cacheByRef.clear();
        }
    }
}
