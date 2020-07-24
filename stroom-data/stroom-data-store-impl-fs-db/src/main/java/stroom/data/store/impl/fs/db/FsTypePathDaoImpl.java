package stroom.data.store.impl.fs.db;

import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.data.store.impl.fs.FsTypePathDao;
import stroom.data.store.impl.fs.FsVolumeConfig;
import stroom.db.util.JooqUtil;
import stroom.util.logging.LambdaLogUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

import static stroom.data.store.impl.fs.db.jooq.tables.FsTypePath.FS_TYPE_PATH;

/**
 * This class exists to map feed id's to file paths using old data from the DB.
 */
@Singleton
class FsTypePathDaoImpl implements FsTypePathDao {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsTypePathDaoImpl.class);

    private static final String NAME_TO_PATH_CACHE_NAME = "Name To Path Cache";
    private static final String PATH_TO_NAME_CACHE_NAME = "Path To Name Cache";

    private final ICache<String, String> nameToPathCache;
    private final ICache<String, String> pathToNameCache;
    private final FsDataStoreDbConnProvider fsDataStoreDbConnProvider;

    @Inject
    FsTypePathDaoImpl(final FsDataStoreDbConnProvider fsDataStoreDbConnProvider,
                      final CacheManager cacheManager,
                      final FsVolumeConfig fsVolumeConfig) {
        this.fsDataStoreDbConnProvider = fsDataStoreDbConnProvider;
        nameToPathCache = cacheManager.create(NAME_TO_PATH_CACHE_NAME, fsVolumeConfig::getTypePathCache, this::loadPath);
        pathToNameCache = cacheManager.create(PATH_TO_NAME_CACHE_NAME, fsVolumeConfig::getTypePathCache, this::loadName);
    }

    @Override
    public String getOrCreatePath(final String name) {
        return nameToPathCache.get(name);
    }

    @Override
    public String getType(final String path) {
        return pathToNameCache.get(path);
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

    private String loadName(final String path) {
        // Try and get the existing id from the DB.
        return getName(path)
                .orElseThrow(() -> new RuntimeException("Unable to get type name from path"));
    }

    private void createPath(final String name) {
        final String path = name.toUpperCase().replaceAll("[^A-Z0-9_-]", "_");
        if (!path.equals(name)) {
            LOGGER.warn(LambdaLogUtil.message("A non standard type name was found when registering a file path '{}'", name));
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

    private Optional<String> getName(final String path) {
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                .select(FS_TYPE_PATH.NAME)
                .from(FS_TYPE_PATH)
                .where(FS_TYPE_PATH.PATH.eq(path))
                .fetchOptional(FS_TYPE_PATH.NAME));
    }
}
