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
import stroom.data.store.impl.fs.FsFeedPathDao;
import stroom.data.store.impl.fs.FsVolumeConfig;
import stroom.db.util.JooqUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Optional;

import static stroom.data.store.impl.fs.db.jooq.tables.FsFeedPath.FS_FEED_PATH;

/**
 * This class exists to map feed id's to file paths using old data from the DB.
 */
@Singleton
class FsFeedPathDaoImpl implements FsFeedPathDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsFeedPathDaoImpl.class);

    private static final String CACHE_NAME = "Feed Path Cache";

    private final LoadingStroomCache<String, String> cache;
    private final FsDataStoreDbConnProvider fsDataStoreDbConnProvider;

    @Inject
    FsFeedPathDaoImpl(final FsDataStoreDbConnProvider fsDataStoreDbConnProvider,
                      final CacheManager cacheManager,
                      final Provider<FsVolumeConfig> fsVolumeConfigProvider) {
        this.fsDataStoreDbConnProvider = fsDataStoreDbConnProvider;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> fsVolumeConfigProvider.get().getFeedPathCache(),
                this::load);
    }

    private String load(final String name) {
        // Try and get the existing id from the DB.
        return getPath(name)
                .or(() -> {
                    createPath(name);
                    return getPath(name);
                })
                .orElseThrow();
    }

    @Override
    public String getOrCreatePath(final String name) {
        return cache.get(name);
    }

    void createPath(final String name) {
        final String path = name.toUpperCase().replaceAll("[^A-Z0-9_-]", "_");
        if (!path.equals(name)) {
            LOGGER.warn(() -> LogUtil.message("A non standard feed name was found when registering a file path '{}'",
                    name));
        }

        JooqUtil.onDuplicateKeyIgnore(() ->
                JooqUtil.context(fsDataStoreDbConnProvider, context -> context
                        .insertInto(FS_FEED_PATH, FS_FEED_PATH.NAME, FS_FEED_PATH.PATH)
                        .values(name, path)
                        .execute()));
    }

    private Optional<String> getPath(final String name) {
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                .select(FS_FEED_PATH.PATH)
                .from(FS_FEED_PATH)
                .where(FS_FEED_PATH.NAME.eq(name))
                .fetchOptional(FS_FEED_PATH.PATH));
    }
}
