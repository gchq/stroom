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
import stroom.db.util.JooqUtil.BooleanOperator;
import stroom.meta.impl.MetaFeedDao;
import stroom.meta.impl.MetaServiceConfig;
import stroom.meta.impl.db.jooq.tables.records.MetaFeedRecord;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.NullSafe;
import stroom.util.string.PatternUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static stroom.meta.impl.db.jooq.tables.MetaFeed.META_FEED;

@Singleton
class MetaFeedDaoImpl implements MetaFeedDao, Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MetaFeedDaoImpl.class);

    private static final String CACHE_NAME = "Meta Feed Cache";

    private final LoadingStroomCache<String, Integer> cache;
    private final MetaDbConnProvider metaDbConnProvider;

    @Inject
    MetaFeedDaoImpl(final MetaDbConnProvider metaDbConnProvider,
                    final CacheManager cacheManager,
                    final Provider<MetaServiceConfig> metaServiceConfigProvider) {
        this.metaDbConnProvider = metaDbConnProvider;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> metaServiceConfigProvider.get().getMetaFeedCache(),
                this::load);
    }

    private int load(final String name) {
        // Try and get the existing id from the DB.
        return fetchFromDb(name)
                .or(() -> {
                    // The id isn't in the DB so create it.
                    return tryCreate(name)
                            .or(() -> {
                                // If the id is still null then this may be because the create method failed
                                // due to the name having been inserted into the DB by another thread prior
                                // to us calling create and the DB preventing duplicate names.
                                // Assuming this is the case, try and get the id from the DB one last time.
                                return fetchFromDb(name);
                            });
                })
                .orElseThrow();
    }

    @Override
    public Integer getOrCreate(final String name) {
        return cache.get(name);
    }

    @Override
    public Optional<Integer> get(final String name) {
        if (NullSafe.isBlankString(name)) {
            return Optional.empty();
        } else {
            final Optional<Integer> optId = cache.getIfPresent(name);
            if (optId.isPresent()) {
                return optId;
            } else {
                return fetchFromDb(name);
            }
        }
    }

    private Optional<Integer> fetchFromDb(final String name) {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .select(META_FEED.ID)
                .from(META_FEED)
                .where(META_FEED.NAME.eq(name))
                .fetchOptional(META_FEED.ID));
    }

    Optional<Integer> find(final String wildCardedFeedName) {
        if (NullSafe.isBlankString(wildCardedFeedName)) {
            return Optional.empty();
        } else {
            return Optional.ofNullable(find(List.of(wildCardedFeedName)).get(wildCardedFeedName));
        }
    }

    /**
     * For a list of wild-carded names, return 0-* IDs for each one.
     * If no wildCardedFeedNames are provided, returns an empty map.
     *
     * @param wildCardedFeedNames e.g. 'TEST_*' or 'TEST_FEED'
     */
    Map<String, Integer> find(final List<String> wildCardedFeedNames) {
        return WildCardHelper.find(
                wildCardedFeedNames,
                cache,
                this::fetchWithWildCards);
    }

    private Map<String, Integer> fetchWithWildCards(final List<String> wildCardedFeedNames) {

        final Condition condition = JooqUtil.createWildCardedStringsCondition(
                META_FEED.NAME, wildCardedFeedNames, true, BooleanOperator.OR);

        final Map<String, Integer> feedToIdMap = JooqUtil.contextResult(metaDbConnProvider, context -> context
                .select(META_FEED.NAME, META_FEED.ID)
                .from(META_FEED)
                .where(condition)
                .fetchMap(META_FEED.NAME, META_FEED.ID));

        // Manually put any non wild-carded ones in the cache
        feedToIdMap.entrySet()
                .stream()
                .filter(entry ->
                        !PatternUtil.containsWildCards(entry.getKey()))
                .forEach(entry ->
                        cache.put(entry.getKey(), entry.getValue()));

        LOGGER.debug(() -> LogUtil.message("fetchWithWildCards called for wildCardedFeedNames: '{}', returning: '{}'",
                wildCardedFeedNames, feedToIdMap.entrySet()
                        .stream()
                        .map(entry -> entry.getKey() + ":" + entry.getValue())
                        .collect(Collectors.joining(", "))));

        return feedToIdMap;
    }

    Optional<Integer> tryCreate(final String name) {

        final MetaFeedRecord rec = new MetaFeedRecord(null, name);
        final MetaFeedRecord dbRec = JooqUtil.tryCreate(metaDbConnProvider, rec, META_FEED.NAME, createdRec -> {
            LOGGER.debug(() -> LogUtil.message("Created new {} record with ID: {}, name: {}",
                    META_FEED.getName(),
                    NullSafe.get(createdRec, MetaFeedRecord::getId),
                    NullSafe.get(createdRec, MetaFeedRecord::getName)));
        });

        return Optional.ofNullable(dbRec.getId());
    }

    @Override
    public List<String> list() {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .select(META_FEED.NAME)
                .from(META_FEED)
                .fetch(META_FEED.NAME));
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
