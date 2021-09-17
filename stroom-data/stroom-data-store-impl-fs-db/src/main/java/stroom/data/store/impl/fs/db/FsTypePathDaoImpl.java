package stroom.data.store.impl.fs.db;

import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.data.store.impl.fs.FsTypePathDao;
import stroom.data.store.impl.fs.FsVolumeConfig;
import stroom.db.util.JooqUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

import static stroom.data.store.impl.fs.db.jooq.tables.FsTypePath.FS_TYPE_PATH;

/**
 * This class exists to map feed id's to file paths using old data from the DB.
 */
@Singleton
class FsTypePathDaoImpl implements FsTypePathDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsTypePathDaoImpl.class);

    private static final String NAME_TO_PATH_CACHE_NAME = "Name To Path Cache";

    private final ICache<String, String> nameToPathCache;
    private final FsDataStoreDbConnProvider fsDataStoreDbConnProvider;

    @Inject
    FsTypePathDaoImpl(final FsDataStoreDbConnProvider fsDataStoreDbConnProvider,
                      final CacheManager cacheManager,
                      final FsVolumeConfig fsVolumeConfig) {
        this.fsDataStoreDbConnProvider = fsDataStoreDbConnProvider;
        nameToPathCache = cacheManager.create(NAME_TO_PATH_CACHE_NAME,
                fsVolumeConfig::getTypePathCache,
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

    private void createPath(final String name) {
        final String path = name.toUpperCase().replaceAll("[^A-Z0-9_-]", "_");
        if (!path.equals(name)) {
            LOGGER.debug(() -> LogUtil.message("A non standard type name was found when registering a file path '{}'",
                    name));
        }

        JooqUtil.context(fsDataStoreDbConnProvider, context -> context
                .insertInto(FS_TYPE_PATH, FS_TYPE_PATH.NAME, FS_TYPE_PATH.PATH)
                .values(name, path)
                .onDuplicateKeyIgnore()
                .execute());
    }

    private Optional<String> getPath(final String name) {
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                .select(FS_TYPE_PATH.PATH)
                .from(FS_TYPE_PATH)
                .where(FS_TYPE_PATH.NAME.eq(name))
                .fetchOptional(FS_TYPE_PATH.PATH));
    }
}
