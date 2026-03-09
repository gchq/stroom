/*
 * Copyright 2016-2026 Crown Copyright
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
import stroom.annotation.shared.AnnotationTag;
import stroom.cache.api.CacheManager;
import stroom.cache.api.StroomCache;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Optional;

// Make this a singleton so we don't keep recreating the mappers.
@Singleton
class AnnotationTagCache implements Clearable {

    private static final String CACHE_NAME = "Annotation Tag Cache";

    private final AnnotationTagDaoImpl annotationTagDao;
    private final StroomCache<Integer, Optional<AnnotationTag>> cache;

    @Inject
    AnnotationTagCache(final AnnotationTagDaoImpl annotationTagDao,
                       final CacheManager cacheManager,
                       final Provider<AnnotationConfig> annotationConfigProvider) {
        this.annotationTagDao = annotationTagDao;
        // Can't use a loading cache cos the caller of get needs to provide the load function
        // with their DSLContext
        cache = cacheManager.create(
                CACHE_NAME,
                () -> annotationConfigProvider.get().getAnnotationTagCache());
    }

    public AnnotationTag get(final int id) {
        return cache.get(id, annotationTagDao::load).orElse(null);
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
