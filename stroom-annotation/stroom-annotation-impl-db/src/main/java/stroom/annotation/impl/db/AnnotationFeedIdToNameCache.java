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
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static stroom.annotation.impl.db.jooq.tables.AnnotationFeed.ANNOTATION_FEED;

@Singleton
class AnnotationFeedIdToNameCache implements Clearable {

    private static final String CACHE_NAME = "Annotation Feed Id To Name Cache";

    private final StroomCache<Integer, Optional<String>> cache;
    private final AnnotationDbConnProvider connectionProvider;

    @Inject
    AnnotationFeedIdToNameCache(final AnnotationDbConnProvider connectionProvider,
                                final CacheManager cacheManager,
                                final Provider<AnnotationConfig> annotationConfigProvider) {
        this.connectionProvider = connectionProvider;
        cache = cacheManager.create(
                CACHE_NAME,
                () -> annotationConfigProvider.get().getAnnotationFeedCache());
    }

    private Optional<String> load(final Integer id) {
        try {
            return CompletableFuture.supplyAsync(() -> JooqUtil.contextResult(connectionProvider, context -> context
                    .select(ANNOTATION_FEED.NAME)
                    .from(ANNOTATION_FEED)
                    .where(ANNOTATION_FEED.ID.eq(id))
                    .fetchOptional(ANNOTATION_FEED.NAME))).get();
        } catch (final InterruptedException e) {
            throw new UncheckedInterruptedException(e);
        } catch (final ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<String> getName(final Integer id) {
        if (id != null) {
            return cache.get(id, this::load);
        }
        return Optional.empty();
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
