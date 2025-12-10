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

package stroom.data.store.impl.fs.db;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.data.store.impl.fs.FsTypePathDao;
import stroom.data.store.impl.fs.FsVolumeConfig;
import stroom.db.util.JooqUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Optional;

import static stroom.data.store.impl.fs.db.jooq.tables.FsTypePath.FS_TYPE_PATH;

/**
 * This class exists to map feed id's to file paths using old data from the DB.
 * e.g. Legacy system may have "Test Events" linked to the /EVENTS/ path which differs
 * from the default of "Test Events" => /TEST_EVENTS/
 */
@Singleton
class FsTypePathDaoImpl implements FsTypePathDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsTypePathDaoImpl.class);

    private static final String NAME_TO_PATH_CACHE_NAME = "Name To Path Cache";

    private final LoadingStroomCache<String, String> nameToPathCache;
    private final FsDataStoreDbConnProvider fsDataStoreDbConnProvider;

    @Inject
    FsTypePathDaoImpl(final FsDataStoreDbConnProvider fsDataStoreDbConnProvider,
                      final CacheManager cacheManager,
                      final Provider<FsVolumeConfig> fsVolumeConfigProvider) {
        this.fsDataStoreDbConnProvider = fsDataStoreDbConnProvider;
        nameToPathCache = cacheManager.createLoadingCache(
                NAME_TO_PATH_CACHE_NAME,
                () -> fsVolumeConfigProvider.get().getTypePathCache(),
                this::loadPath);
    }

    @Override
    public String getOrCreatePath(final String name) {
        return nameToPathCache.get(name);
    }

    private String loadPath(final String typeName) {
        // Try and get the existing id from the DB.
        return getPath(typeName)
                .or(() -> {
                    createPath(typeName);
                    return getPath(typeName);
                })
                .orElseThrow();
    }

    void createPath(final String name) {
        final String path = name.toUpperCase().replaceAll("[^A-Z0-9_-]", "_");
        if (!path.equals(name)) {
            LOGGER.debug(() -> LogUtil.message("A non standard type name was found when registering a file path '{}'",
                    name));
        }

        JooqUtil.onDuplicateKeyIgnore(() ->
                JooqUtil.context(fsDataStoreDbConnProvider, context -> context
                        .insertInto(FS_TYPE_PATH, FS_TYPE_PATH.NAME, FS_TYPE_PATH.PATH)
                        .values(name, path)
                        .execute()));
    }

    private Optional<String> getPath(final String name) {
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                .select(FS_TYPE_PATH.PATH)
                .from(FS_TYPE_PATH)
                .where(FS_TYPE_PATH.NAME.eq(name))
                .fetchOptional(FS_TYPE_PATH.PATH));
    }
}
