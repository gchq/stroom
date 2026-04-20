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
import stroom.query.language.functions.ValString;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;

import com.github.benmanes.caffeine.cache.Interner;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Objects;

/**
 * Combines a {@link String} to {@link ValString} cache with a {@link ValString} interner
 * so we can hit the cache with a {@link String} and get back an interned {@link ValString}.
 * <p>
 * ONLY intended for low cardinality string values.
 * </p>
 */
@Singleton
class AnnotationValStringInterner implements Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnnotationValStringInterner.class);

    private static final String CACHE_NAME = "Annotation String Cache";

    private final Interner<ValString> interner;
    private final StroomCache<String, ValString> cache;

    @Inject
    AnnotationValStringInterner(final CacheManager cacheManager,
                                final Provider<AnnotationConfig> annotationConfigProvider) {
        this.interner = Interner.newWeakInterner();
        this.cache = cacheManager.create(
                CACHE_NAME,
                () -> annotationConfigProvider.get().getAnnotationValStringCache());
    }

    public ValString getAndIntern(final String str) {
        Objects.requireNonNull(str);
        if (str.isEmpty()) {
            // No need to hit cache for this static
            return ValString.EMPTY;
        } else {
            return cache.get(str, this::createAndIntern);
        }
    }

    private ValString createAndIntern(final String str) {
        LOGGER.debug("createAndIntern() - str: {}", str);
        return interner.intern(ValString.create(str));
    }

    @Override
    public void clear() {
        LOGGER.debug(() -> LogUtil.message("clear() - size: {}", cache.size()));
        cache.clear();
    }
}
