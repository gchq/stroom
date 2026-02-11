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
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Cache for mapping annotation feed IDs to feed names.
 * <p>
 * This cache uses async execution to avoid thread-local connection conflicts
 * when accessed from within existing database transaction contexts.
 */
@Singleton
class AnnotationFeedIdToNameCache implements Clearable {

    private static final String CACHE_NAME = "Annotation Feed Id To Name Cache";

    private final StroomCache<Integer, Optional<String>> cache;
    private final AnnotationFeedDao annotationFeedDao;

    @Inject
    AnnotationFeedIdToNameCache(final AnnotationFeedDao annotationFeedDao,
                                final CacheManager cacheManager,
                                final Provider<AnnotationConfig> annotationConfigProvider) {
        this.annotationFeedDao = annotationFeedDao;
        cache = cacheManager.create(
                CACHE_NAME,
                () -> annotationConfigProvider.get().getAnnotationFeedCache());
    }

    public Optional<String> getName(final Integer id) {
        if (id == null) {
            return Optional.empty();
        }
        return cache.get(id, this::load);
    }

    /**
     * Load feed name from database asynchronously to avoid connection conflicts.
     */
    private Optional<String> load(final Integer id) {
        return AnnotationFeedDao.async(() -> annotationFeedDao.fetchById(id));
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
