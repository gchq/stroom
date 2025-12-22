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

package stroom.data.store.impl.fs.s3v2;


import stroom.aws.s3.impl.S3Manager;
import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.util.cache.CacheConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

public class ZstdDictionarySupplierImpl implements ZstdDictionarySupplier {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ZstdDictionarySupplierImpl.class);

    public static final String CACHE_NAME = "Zstandard Dictionary Cache";

    private final S3Manager s3Manager;
    private final LoadingStroomCache<String, Optional<ZstdDictionary>> cache;

    @Inject
    public ZstdDictionarySupplierImpl(final S3Manager s3Manager,
                                      final CacheManager cacheManager) {
        this.s3Manager = s3Manager;
        // TODO cache config needs to come from config
        // Dictionaries are immutable things, so no expiry needed
        this.cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> CacheConfig.builder()
                        .maximumSize(1_000)
                        .build(),
                this::fetchZstdDictionary);
    }

    @Override
    public Optional<ZstdDictionary> getZstdDictionary(final String dictionaryUuid) throws IOException {
        if (NullSafe.isBlankString(dictionaryUuid)) {
            throw new IllegalArgumentException("Dictionary uuid must not be null or empty");
        } else {
            final Optional<ZstdDictionary> optDictionary = cache.get(dictionaryUuid);
            LOGGER.debug("getZstdDictionary() - dictionaryUuid: {}, optDictionary: {}", dictionaryUuid, optDictionary);
            return optDictionary;
        }
    }

    private Optional<ZstdDictionary> fetchZstdDictionary(final String dictionaryUuid) {
        try {
            LOGGER.debug("fetchZstdDictionary() - dictionaryUuid: {}", dictionaryUuid);
            // TODO
            throw new IOException("TODO");
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
