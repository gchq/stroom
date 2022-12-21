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
import stroom.cache.api.LoadingStroomCache;
import stroom.db.util.JooqUtil;
import stroom.db.util.JooqUtil.BooleanOperator;
import stroom.meta.impl.MetaServiceConfig;
import stroom.meta.impl.MetaTypeDao;
import stroom.meta.impl.db.jooq.tables.records.MetaTypeRecord;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;

import org.jooq.Condition;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import static stroom.meta.impl.db.jooq.tables.MetaType.META_TYPE;

@Singleton
class MetaTypeDaoImpl implements MetaTypeDao, Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MetaTypeDaoImpl.class);

    private static final String CACHE_NAME = "Meta Type Cache";

    private final LoadingStroomCache<String, Integer> cache;
    private final MetaDbConnProvider metaDbConnProvider;

    @Inject
    MetaTypeDaoImpl(final MetaDbConnProvider metaDbConnProvider,
                    final CacheManager cacheManager,
                    final Provider<MetaServiceConfig> metaServiceConfigProvider) {

        LOGGER.debug("Initialising MetaTypeDaoImpl");

        this.metaDbConnProvider = metaDbConnProvider;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> metaServiceConfigProvider.get().getMetaTypeCache(),
                this::load);

        // Ensure some types are preloaded.
        final Set<String> metaTypes = metaServiceConfigProvider.get().getMetaTypes();
        LOGGER.debug("metaTypes: {}", metaTypes);
        if (metaTypes != null) {
            metaTypes.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .forEach(this::load);
        }
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

    Map<String, List<Integer>> find(final List<String> names) {
        final Condition condition = JooqUtil.createWildCardedStringsCondition(
                META_TYPE.NAME, names, true, BooleanOperator.OR);

        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                .select(META_TYPE.NAME, META_TYPE.ID)
                .from(META_TYPE)
                .where(condition)
                .fetchGroups(META_TYPE.NAME, META_TYPE.ID));
    }

    private Optional<Integer> create(final String name) {
        return JooqUtil.contextResult(metaDbConnProvider, context -> context
                        .insertInto(META_TYPE, META_TYPE.NAME)
                        .values(name)
                        .onDuplicateKeyIgnore()
                        .returning(META_TYPE.ID)
                        .fetchOptional())
                .map(MetaTypeRecord::getId);
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
        cache.clear();
    }
}
