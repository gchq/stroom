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

import org.jooq.Condition;
import org.jooq.Field;
import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.db.util.JooqUtil;
import stroom.meta.impl.MetaTypeDao;
import stroom.meta.impl.db.jooq.tables.records.MetaTypeRecord;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;

import static stroom.meta.impl.db.jooq.tables.MetaType.META_TYPE;

@Singleton
class MetaTypeDaoImpl implements MetaTypeDao {
    private static final String CACHE_NAME = "Meta Type Cache";

    private final ICache<String, Integer> cache;
    private final MetaDbConnProvider metaDbConnProvider;

    @Inject
    MetaTypeDaoImpl(final MetaDbConnProvider metaDbConnProvider,
                    final CacheManager cacheManager,
                    final MetaServiceConfig metaServiceConfig) {
        this.metaDbConnProvider = metaDbConnProvider;
        cache = cacheManager.create(CACHE_NAME, metaServiceConfig::getMetaTypeCache, this::load);
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

    private Optional<Integer> get(final String name) {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .select(META_TYPE.ID)
                .from(META_TYPE)
                .where(META_TYPE.NAME.eq(name))
                .fetchOptional(META_TYPE.ID));
    }

    List<Integer> find(final String name) {
        final Condition condition = createCondition(META_TYPE.NAME, name);
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .select(META_TYPE.ID)
                .from(META_TYPE)
                .where(condition)
                .fetch(META_TYPE.ID));
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

    private Optional<Integer> create(final String name) {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .insertInto(META_TYPE, META_TYPE.NAME)
                .values(name)
                .onDuplicateKeyIgnore()
                .returning(META_TYPE.ID)
                .fetchOptional()
                .map(MetaTypeRecord::getId));
    }

    @Override
    public List<String> list() {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .select(META_TYPE.NAME)
                .from(META_TYPE)
                .fetch(META_TYPE.NAME));
    }

    @Override
    public void clear() {
        deleteAll();
        cache.clear();
    }

    private int deleteAll() {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .delete(META_TYPE)
                .execute());
    }
}
