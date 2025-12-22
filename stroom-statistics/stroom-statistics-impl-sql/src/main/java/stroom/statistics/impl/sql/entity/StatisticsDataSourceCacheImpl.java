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

package stroom.statistics.impl.sql.entity;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.statistics.impl.sql.SQLStatisticsConfig;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
@EntityEventHandler(
        type = StatisticStoreDoc.TYPE,
        action = {
                EntityAction.CREATE,
                EntityAction.UPDATE,
                EntityAction.DELETE})
class StatisticsDataSourceCacheImpl implements StatisticStoreCache, EntityEvent.Handler, Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StatisticsDataSourceCacheImpl.class);

    private static final String STATISTICS_DATA_SOURCE_CACHE_NAME_BY_ID = "StatisticDataSourceCacheById";
    private static final String STATISTICS_DATA_SOURCE_CACHE_NAME_BY_NAME = "StatisticDataSourceCacheByName";

    private final StatisticStoreStore statisticStoreStore;
    private final CacheManager cacheManager;
    private final SecurityContext securityContext;
    private final Provider<SQLStatisticsConfig> sqlStatisticsConfigProvider;

    // The values are held as optionals so that if there is no doc for a name then
    // the cache loader won't keep hitting the db for each get on that name.
    private volatile LoadingStroomCache<String, Optional<StatisticStoreDoc>> cacheByName;
    private volatile LoadingStroomCache<DocRef, Optional<StatisticStoreDoc>> cacheByRef;

    @Inject
    StatisticsDataSourceCacheImpl(final StatisticStoreStore statisticStoreStore,
                                  final CacheManager cacheManager,
                                  final SecurityContext securityContext,
                                  final Provider<SQLStatisticsConfig> sqlStatisticsConfigProvider) {
        this.statisticStoreStore = statisticStoreStore;
        this.cacheManager = cacheManager;
        this.securityContext = securityContext;
        this.sqlStatisticsConfigProvider = sqlStatisticsConfigProvider;
    }

    private LoadingStroomCache<String, Optional<StatisticStoreDoc>> getCacheByName() {
        if (cacheByName == null) {
            synchronized (this) {
                if (cacheByName == null) {
                    final Function<String, Optional<StatisticStoreDoc>> cacheLoader = k -> {
                        if (k == null) {
                            return Optional.empty();
                        }

                        final Optional<StatisticStoreDoc> result;
                        // Id and key not found in cache so try pulling it from the DB
                        result = securityContext.asProcessingUserResult(() -> {
                            final List<DocRef> results = statisticStoreStore.list()
                                    .stream()
                                    .filter(docRef -> k.equals(docRef.getName()))
                                    .collect(Collectors.toList());
                            if (results.size() > 1) {
                                throw new RuntimeException(String.format("Found multiple StatisticDataSource " +
                                        "entities with name %s. This should not happen", k));
                            } else if (results.size() == 1) {
                                return Optional.ofNullable(statisticStoreStore.readDocument(results.get(0)));
                            } else {
                                return Optional.empty();
                            }
                        });

                        LOGGER.debug(() -> LogUtil.message("Adding key {} to name cache, value present: {}",
                                k,
                                result.map(v -> "Y").orElse("N")));

                        return result;
                    };
                    cacheByName = createCache(STATISTICS_DATA_SOURCE_CACHE_NAME_BY_NAME, cacheLoader);
                }
            }
        }
        return cacheByName;
    }

    private LoadingStroomCache<DocRef, Optional<StatisticStoreDoc>> getCacheByRef() {
        if (cacheByRef == null) {
            synchronized (this) {
                if (cacheByRef == null) {
                    final Function<DocRef, Optional<StatisticStoreDoc>> cacheLoader = k -> {
                        final Optional<StatisticStoreDoc> result;

                        result = securityContext.asProcessingUserResult(() ->
                                Optional.ofNullable(statisticStoreStore.readDocument(k)));

                        LOGGER.debug(() -> LogUtil.message("Adding key {} to docRef cache, value present: {}",
                                k,
                                result.map(v -> "Y").orElse("N")));

                        return result;
                    };
                    cacheByRef = createCache(STATISTICS_DATA_SOURCE_CACHE_NAME_BY_ID, cacheLoader);
                }
            }
        }
        return cacheByRef;
    }

    private <K, V> LoadingStroomCache<K, V> createCache(final String name, final Function<K, V> cacheLoader) {
        return cacheManager.createLoadingCache(
                name,
                () -> sqlStatisticsConfigProvider.get().getDataSourceCache(),
                cacheLoader);
    }

    private boolean permissionFilter(final StatisticStoreDoc entity) {
        if (!securityContext.hasDocumentPermission(entity.asDocRef(), DocumentPermission.VIEW)) {
            throw new PermissionException(
                    securityContext.getUserRef(), "Not permitted to read " + entity.getName());
        }
        return true;
    }

    @Override
    public StatisticStoreDoc getStatisticsDataSource(final DocRef docRef) {
        LOGGER.doIfDebugEnabled(() -> {
            LOGGER.debug("get by docRef {}", docRef);
            dumpEntries();
        });

        return getCacheByRef()
                .get(docRef)
                .filter(this::permissionFilter)
                .orElse(null);
    }

    @Override
    public StatisticStoreDoc getStatisticsDataSource(final String statisticName) {
        LOGGER.doIfDebugEnabled(() -> {
            LOGGER.debug("get by name {}", statisticName);
            dumpEntries();
        });

        return getCacheByName()
                .get(statisticName)
                .filter(this::permissionFilter)
                .orElse(null);
    }

    @Override
    public void onChange(final EntityEvent event) {
        LOGGER.debug("onChange called for {}", event);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Entries before onChange run");
            dumpEntries();
        }
        try {
            final LoadingStroomCache<String, Optional<StatisticStoreDoc>> cacheByName = getCacheByName();
            final LoadingStroomCache<DocRef, Optional<StatisticStoreDoc>> cacheByDocRef = getCacheByRef();

            final EntityAction entityAction = event.getAction();

            if (EntityAction.UPDATE.equals(entityAction) ||
                    EntityAction.DELETE.equals(entityAction) ||
                    EntityAction.CREATE.equals(entityAction)) {

                final DocRef eventDocRef = event.getDocRef();
                final String eventDocName = eventDocRef != null
                        ? eventDocRef.getName()
                        : null;
                final AtomicBoolean haveInvalidatedName = new AtomicBoolean(false);

//                final Optional<Optional<StatisticStoreDoc>> optional = cacheByDocRef.getOptional(
//                        event.getDocRef());

                if (getCacheByRef().containsKey(event.getDocRef())) {

                    // found it in one cache so remove from both
                    LOGGER.debug("Invalidating docRef {}", eventDocRef);
                    cacheByDocRef.invalidate(eventDocRef);

                    final String name;
                    final Optional<StatisticStoreDoc> optStatStoreDoc = getCacheByRef().get(event.getDocRef());
                    if (optStatStoreDoc.isPresent()) {
                        final StatisticStoreDoc statisticStoreEntity = optStatStoreDoc.get();
                        name = statisticStoreEntity.getName();
                        haveInvalidatedName.set(true);
                    } else {
                        name = eventDocName;
                    }

                    if (name != null) {
                        LOGGER.debug("Invalidating name {}", name);
                        cacheByName.invalidate(name);
                        haveInvalidatedName.set(true);
                    }
                } else {
                    // fall back option, as it couldn't be found in the ref cache so
                    // iterate over the name cache to find the docref

                    // not very efficient but we shouldn't have that many entities
                    // in the cache and deletes will not happen very often.
                    cacheByName.forEach((name, optDoc) -> {
                        try {
                            if (optDoc.isPresent()) {
                                final StatisticStoreDoc value = optDoc.get();
                                final DocRef docDocRef = DocRefUtil.create(value);

                                if (eventDocName != null
                                        && Objects.equals(eventDocName, docDocRef.getName())) {
                                    LOGGER.debug("Invalidating name {}", name);
                                    cacheByName.invalidate(name);
                                    haveInvalidatedName.set(true);
                                }

                                if (docDocRef.equals(event.getDocRef())) {
                                    LOGGER.debug("Invalidating docRef {}", docDocRef);
                                    cacheByDocRef.invalidate(docDocRef);
                                }
                            }
                            // Just to be on the safe side
                            if (name.equals(event.getDocRef().getName())) {
                                LOGGER.debug("Invalidating name {}", name);
                                cacheByName.invalidate(name);
                                haveInvalidatedName.set(true);
                            }
                        } catch (final RuntimeException e) {
                            LOGGER.error(e.getMessage(), e);
                        }
                    });
                }
                if (!haveInvalidatedName.get()) {
                    // some change events just give us a uuid and if we only have keys with empty optionals
                    // then we have no way of invalidating the entry in a targeted way. Thus
                    // we have to take the nuclear option and clear the whole cache.
                    LOGGER.debug("Clearing name cache");
                    cacheByName.clear();
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Entries after onChange run");
            dumpEntries();
        }
    }

    private void dumpEntries() {
        LOGGER.debug("byName: {}", cacheMapToString(getCacheByName(), Function.identity()));
        LOGGER.debug("byRef: {}", cacheMapToString(getCacheByRef(), docRef ->
                docRef.getName() + ":" + docRef.getUuid()));
    }

    private <K> Set<String> cacheMapToString(final LoadingStroomCache<K, Optional<StatisticStoreDoc>> cache,
                                             final Function<K, String> keyConverter) {
        final Set<String> strings = new HashSet<>();
        cache.forEach((k, v) -> {
            strings.add(keyConverter.apply(k)
                    + " - "
                    + v.map(val -> "Y").orElse("N"));
        });
        return strings;
    }

    @Override
    public void clear() {
        // The UI cache clearing clears the cache via the cache manager, not through this method
        if (cacheByName != null) {
            LOGGER.debug("Clearing by name cache");
            cacheByName.clear();
        }
        if (cacheByRef != null) {
            LOGGER.debug("Clearing by docRef cache");
            cacheByRef.clear();
        }
    }
}
