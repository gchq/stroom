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
import stroom.cache.api.CacheManager;
import stroom.cache.api.StroomCache;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Uses a {@link String} to {@link ValString} cache to ensure that for two equal strings,
 * we only have one {@link ValString} instance.
 * This is to reduce memory use in other caches.
 * <p>
 * ONLY intended for low cardinality string values, e.g. status values.
 * </p>
 */
@Singleton
class AnnotationValStringCache implements Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnnotationValStringCache.class);

    private static final String CACHE_NAME = "Annotation String Cache";

    private final StroomCache<String, Val> strToValCache;

    @Inject
    AnnotationValStringCache(final CacheManager cacheManager,
                             final Provider<AnnotationConfig> annotationConfigProvider) {
        this.strToValCache = cacheManager.create(
                CACHE_NAME,
                () -> annotationConfigProvider.get().getAnnotationValStringCache());
    }

    /**
     * Returns a {@link Val} instance for the supplied {@link String}.
     * It may be a {@link ValNull} or {@link ValString}.
     * Assuming the underlying cache is not full, calling this method for
     * two {@link String}s that are equal will return the same {@link Val} instance.
     */
    public Val getAndIntern(final String str) {
        if (str == null) {
            // No need to hit cache for this static
            return ValNull.INSTANCE;
        } else if (str.isEmpty()) {
            // No need to hit cache for this static
            return ValString.EMPTY;
        } else {
            return strToValCache.get(str, this::createAndIntern);
        }
    }

    private Val createAndIntern(final String str) {
        LOGGER.debug("createAndIntern() - str: '{}'", str);
        return ValString.create(str);
    }

    @Override
    public void clear() {
        LOGGER.debug(() -> LogUtil.message("clear() - size: {}", strToValCache.size()));
        strToValCache.clear();
    }
}
