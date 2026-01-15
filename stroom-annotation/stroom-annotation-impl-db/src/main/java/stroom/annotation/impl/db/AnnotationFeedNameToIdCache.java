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

package stroom.annotation.impl.db;

import stroom.annotation.impl.AnnotationConfig;
import stroom.cache.api.CacheManager;
import stroom.cache.api.StroomCache;
import stroom.db.util.JooqUtil;
import stroom.db.util.JooqUtil.BooleanOperator;
import stroom.util.shared.Clearable;
import stroom.util.shared.NullSafe;
import stroom.util.string.PatternUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static stroom.annotation.impl.db.jooq.tables.AnnotationFeed.ANNOTATION_FEED;

@Singleton
class AnnotationFeedNameToIdCache implements Clearable {

    private static final String CACHE_NAME = "Annotation Feed Name To Id Cache";

    private final StroomCache<String, Optional<Integer>> cache;
    private final AnnotationDbConnProvider connectionProvider;

    @Inject
    AnnotationFeedNameToIdCache(final AnnotationDbConnProvider connectionProvider,
                                final CacheManager cacheManager,
                                final Provider<AnnotationConfig> annotationConfigProvider) {
        this.connectionProvider = connectionProvider;
        cache = cacheManager.create(
                CACHE_NAME,
                () -> annotationConfigProvider.get().getAnnotationFeedCache());
    }

    public Integer getOrCreateId(final String name) {
        if (name != null) {
            return cache.get(name, k -> Optional.of(load(k))).orElse(null);
        }
        return null;
    }

    public List<Integer> getIds(final List<String> wildCardedTypeNames) {
        if (NullSafe.isEmptyCollection(wildCardedTypeNames)) {
            return Collections.emptyList();
        }
        return find(wildCardedTypeNames);
    }

    private List<Integer> find(final List<String> wildCardedNames) {
        if (NullSafe.isEmptyCollection(wildCardedNames)) {
            return Collections.emptyList();
        }

        final Set<Integer> ids = new HashSet<>(wildCardedNames.size());
        final List<String> namesNotInCache = new ArrayList<>(wildCardedNames.size());
        for (final String name : wildCardedNames) {
            if (!NullSafe.isBlankString(name)) {
                // We can't cache wildcard names as we don't know what they will match in the DB.
                if (PatternUtil.containsWildCards(name)) {
                    namesNotInCache.add(name);
                } else {
                    final Optional<Integer> optional = getId(name);
                    optional.ifPresent(ids::add);
                }
            }
        }

        ids.addAll(fetchWithWildCards(namesNotInCache));
        return ids.stream().toList();
    }

    private Set<Integer> fetchWithWildCards(final List<String> wildCardedTypeNames) {
        final Condition condition = JooqUtil.createWildCardedStringsCondition(
                ANNOTATION_FEED.NAME, wildCardedTypeNames, true, BooleanOperator.OR);

        return JooqUtil.contextResult(connectionProvider, context -> context
                .select(ANNOTATION_FEED.NAME, ANNOTATION_FEED.ID)
                .from(ANNOTATION_FEED)
                .where(condition)
                .fetchSet(ANNOTATION_FEED.ID));
    }

    public Optional<Integer> getId(final String name) {
        if (name != null) {
            return cache.get(name, this::fetch);
        }
        return Optional.empty();
    }

    private Integer load(final String name) {
        // Try and get the existing id from the DB.
        return fetch(name)
                .or(() -> {
                    // The id isn't in the DB so create it.
                    return create(name)
                            .or(() -> {
                                // If the id is still null then this may be because the create method failed
                                // due to the name having been inserted into the DB by another thread prior
                                // to us calling create and the DB preventing duplicate names.
                                // Assuming this is the case, try and get the id from the DB one last time.
                                return fetch(name);
                            });
                })
                .orElseThrow();
    }

    private Optional<Integer> fetch(final String name) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .select(ANNOTATION_FEED.ID)
                .from(ANNOTATION_FEED)
                .where(ANNOTATION_FEED.NAME.eq(name))
                .fetchOptional(ANNOTATION_FEED.ID));
    }

    private Optional<Integer> create(final String name) {
        return JooqUtil.onDuplicateKeyIgnore(() ->
                JooqUtil.contextResult(connectionProvider, context -> context
                        .insertInto(ANNOTATION_FEED, ANNOTATION_FEED.NAME)
                        .values(name)
                        .returning(ANNOTATION_FEED.ID)
                        .fetchOptional(ANNOTATION_FEED.ID)));
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
