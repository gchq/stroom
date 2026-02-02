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

package stroom.data.store.impl.fs.s3v2;


import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.cache.api.StroomCache;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.security.api.SecurityContext;
import stroom.util.cache.CacheConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;

import java.util.Objects;
import java.util.Optional;

public class ZstdDictionaryServiceImpl implements ZstdDictionaryService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ZstdDictionaryServiceImpl.class);

    public static final String UUID_TO_DICT_CACHE_NAME = "ZStandard Dictionary Cache";
    public static final String KEY_TO_UUID_CACHE_NAME = "ZStandard Dictionary Cache";

    private final SecurityContext securityContext;
    private final ZstdDictionaryDao zstdDictionaryDao;
    private final ZstdDictionaryTaskDao zstdDictionaryTaskDao;
    private final ZstdDictionaryStore zstdDictionaryStore;

    // TODO we may want the dictionaries to be written to disk as they could
    //  be quite big
    private final StroomCache<String, ZstdDictionary> uuidToDictCache;
    private final LoadingStroomCache<ZstdDictionaryKey, String> keyToUuidCache;

    @Inject
    public ZstdDictionaryServiceImpl(final SecurityContext securityContext,
                                     final ZstdDictionaryDao zstdDictionaryDao,
                                     final ZstdDictionaryTaskDao zstdDictionaryTaskDao,
                                     final ZstdDictionaryStore zstdDictionaryStore,
                                     final CacheManager cacheManager) {
        this.securityContext = securityContext;
        this.zstdDictionaryDao = zstdDictionaryDao;
        this.zstdDictionaryTaskDao = zstdDictionaryTaskDao;
        this.zstdDictionaryStore = zstdDictionaryStore;
        // TODO cache config needs to come from config

        this.uuidToDictCache = cacheManager.create(
                UUID_TO_DICT_CACHE_NAME,
                () -> CacheConfig.builder()
                        .maximumSize(1_000)
                        .expireAfterWrite(StroomDuration.ofMinutes(10))
                        .build(),
                (uuid, zstdDictionary) -> {
                    LOGGER.debug("Evicting uuid: {} => zstdDictionary: {} from cache {}",
                            uuid, zstdDictionary, UUID_TO_DICT_CACHE_NAME);
                });

        this.keyToUuidCache = cacheManager.createLoadingCache(
                KEY_TO_UUID_CACHE_NAME,
                () -> CacheConfig.builder()
                        .maximumSize(1_000)
                        .expireAfterWrite(StroomDuration.ofMinutes(10))
                        .build(),
                this::fetchByDictionaryKey,
                (zstdDictionaryKey, uuid) -> {
                    LOGGER.debug("Evicting zstdDictionaryKey: {} => uuid: {} from cache {}",
                            zstdDictionaryKey, uuid, UUID_TO_DICT_CACHE_NAME);
                });
    }

    @Override
    public Optional<ZstdDictionary> getZstdDictionary(final String dictionaryUuid,
                                                      final DataVolume dataVolume) {
        if (NullSafe.isBlankString(dictionaryUuid)) {
            throw new IllegalArgumentException("Dictionary uuid must not be null or empty");
        } else {
            final ZstdDictionary zstdDictionary = uuidToDictCache.get(
                    dictionaryUuid,
                    uuid ->
                            fetchByUuid(uuid, dataVolume));
            LOGGER.debug("getZstdDictionary() - dictionaryUuid: {}, zstdDictionary: {}",
                    dictionaryUuid, zstdDictionary);
            return Optional.ofNullable(zstdDictionary);
        }
    }

    @Override
    public Optional<ZstdDictionary> getZstdDictionary(final ZstdDictionaryKey zstdDictionaryKey,
                                                      final DataVolume dataVolume) {
        Objects.requireNonNull(zstdDictionaryKey);
        final String uuid = keyToUuidCache.get(zstdDictionaryKey);
        final Optional<ZstdDictionary> optDictionary;
        if (NullSafe.isEmptyString(uuid)) {
            optDictionary = Optional.empty();
        } else {
            optDictionary = getZstdDictionary(uuid, dataVolume);
        }
        LOGGER.debug("getZstdDictionary() - zstdDictionaryKey: {}, optDictionary: {}",
                zstdDictionaryKey, optDictionary);
        return optDictionary;
    }

    @Override
    public void createReCompressTask(final ZstdDictionaryKey zstdDictionaryKey, final FileKey fileKey) {
        LOGGER.debug("createReCompressTask() - zstdDictionaryKey: {}, fileKey: {}", zstdDictionaryKey, fileKey);
        Objects.requireNonNull(zstdDictionaryKey);
        Objects.requireNonNull(fileKey);
        zstdDictionaryTaskDao.create(zstdDictionaryKey, fileKey);
    }

    private ZstdDictionary fetchByUuid(final String dictionaryUuid, final DataVolume dataVolume) {
        LOGGER.debug("fetchByUuid() - dictionaryUuid: {}, dataVolume: {}", dictionaryUuid, dataVolume);
        NullSafe.requireNonBlankString(dictionaryUuid);
        final ZstdDictionary zstdDictionary = zstdDictionaryStore.getZstdDictionary(dictionaryUuid, dataVolume)
                .orElse(null);
        LOGGER.debug("fetchByUuid() - dictionaryUuid: {}, zstdDictionary: {}", dictionaryUuid, zstdDictionary);
        return zstdDictionary;
    }

    private String fetchByDictionaryKey(final ZstdDictionaryKey zstdDictionaryKey) {
        LOGGER.debug("fetchByKey() - zstdDictionaryKey: {}", zstdDictionaryKey);
        Objects.requireNonNull(zstdDictionaryKey);
        final String uuid = zstdDictionaryDao.fetchByKey(zstdDictionaryKey)
                .map(LinkedZstdDictionary::getUuid)
                .orElse(null);
        LOGGER.debug("fetchByKey() - zstdDictionaryKey: {}, uuid: {}", zstdDictionaryKey, uuid);
        return uuid;
    }
}
