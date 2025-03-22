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

package stroom.annotation.impl.db;

import stroom.annotation.impl.AnnotationConfig;
import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.db.util.JooqUtil;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Optional;

import static stroom.annotation.impl.db.jooq.tables.AnnotationFeed.ANNOTATION_FEED;

@Singleton
class AnnotationFeedCache implements Clearable {

    private static final String CACHE_NAME = "Annotation Feed Cache";

    private final LoadingStroomCache<Object, Object> cache;
    private final AnnotationDbConnProvider connectionProvider;

    @Inject
    AnnotationFeedCache(final AnnotationDbConnProvider connectionProvider,
                        final CacheManager cacheManager,
                        final Provider<AnnotationConfig> annotationConfigProvider) {
        this.connectionProvider = connectionProvider;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> annotationConfigProvider.get().getAnnotationFeedCache(),
                this::load);
    }

    private Object load(final Object o) {
        if (o instanceof final Integer id) {
            return JooqUtil.contextResult(connectionProvider, context -> context
                    .select(ANNOTATION_FEED.NAME)
                    .from(ANNOTATION_FEED)
                    .where(ANNOTATION_FEED.ID.eq(id))
                    .fetchOptional(ANNOTATION_FEED.NAME));

        } else if (o instanceof final String name) {
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

        return Optional.empty();
    }

    public Integer getOrCreateId(final String name) {
        if (name != null) {
            final Object obj = cache.get(name);
            if (obj instanceof final Integer id) {
                return id;
            }
        }
        return null;
    }

    public Optional<Integer> getId(final String name) {
        if (name != null) {
            Optional<Object> optional = cache.getIfPresent(name);
            if (optional.isPresent()) {
                final Object o = optional.get();
                if (o instanceof final Integer id) {
                    return Optional.of(id);
                }
            }
        }

        return Optional.empty();
    }

    public Optional<String> getName(final Integer id) {
        if (id != null) {
            final Object obj = cache.get(id);
            if (obj instanceof final Optional<?> optional) {
                final Object o = optional.orElse(null);
                if (o instanceof final String name) {
                    return Optional.of(name);
                }
            }
        }
        return Optional.empty();
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
