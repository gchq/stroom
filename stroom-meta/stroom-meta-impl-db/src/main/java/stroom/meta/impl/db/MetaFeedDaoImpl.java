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

package stroom.meta.impl.db;

import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.db.util.JooqUtil;
import stroom.meta.impl.MetaFeedDao;
import stroom.meta.impl.db.jooq.tables.records.MetaFeedRecord;

import org.jooq.Condition;
import org.jooq.Field;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

import static stroom.meta.impl.db.jooq.tables.MetaFeed.META_FEED;

@Singleton
class MetaFeedDaoImpl implements MetaFeedDao {
    private static final String CACHE_NAME = "Meta Feed Cache";

    private final ICache<String, Integer> cache;
    private final MetaDbConnProvider metaDbConnProvider;

    @Inject
    MetaFeedDaoImpl(final MetaDbConnProvider metaDbConnProvider,
                    final CacheManager cacheManager,
                    final MetaServiceConfig metaServiceConfig) {
        this.metaDbConnProvider = metaDbConnProvider;
        cache = cacheManager.create(CACHE_NAME, metaServiceConfig::getMetaFeedCache, this::load);
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

    @Override
    public Integer getOrCreate(final String name) {
        return cache.get(name);
    }

    Optional<Integer> get(final String name) {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .select(META_FEED.ID)
                .from(META_FEED)
                .where(META_FEED.NAME.eq(name))
                .fetchOptional(META_FEED.ID));
    }

    List<Integer> find(final String name) {
        final Condition condition = createCondition(META_FEED.NAME, name);
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .select(META_FEED.ID)
                .from(META_FEED)
                .where(condition)
                .fetch(META_FEED.ID));
    }

    private Condition createCondition(final Field<String> field, final String name) {
        if (name != null) {
            if (name.contains("*")) {
                return field.like(name.replaceAll("\\*", "%"));
            }
            return field.eq(name);
        }
        return null;
    }

    Optional<Integer> create(final String name) {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .insertInto(META_FEED, META_FEED.NAME)
                .values(name)
                .onDuplicateKeyIgnore()
                .returning(META_FEED.ID)
                .fetchOptional()
                .map(MetaFeedRecord::getId));
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
        deleteAll();
        cache.clear();
    }

    private void deleteAll() {
        JooqUtil.truncateTable(metaDbConnProvider, META_FEED);
//        return JooqUtil.contextResult(metaDbConnProvider, context -> context
//                .truncate(META_FEED)
//                .execute());
    }
}
