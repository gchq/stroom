package stroom.data.store.impl.fs.db;

import stroom.data.store.impl.fs.FsTypePathDao;
import stroom.db.util.JooqUtil;
import stroom.util.logging.LambdaLogUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static stroom.data.store.impl.fs.db.jooq.tables.FsTypePath.FS_TYPE_PATH;

/**
 * This class exists to map feed id's to file paths using old data from the DB.
 */
@Singleton
class FsTypePathDaoImpl implements FsTypePathDao {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsTypePathDaoImpl.class);

    // TODO : @66 Replace with a proper cache.
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    private final FsDataStoreDbConnProvider fsDataStoreDbConnProvider;

    @Inject
    FsTypePathDaoImpl(final FsDataStoreDbConnProvider fsDataStoreDbConnProvider) {
        this.fsDataStoreDbConnProvider = fsDataStoreDbConnProvider;
    }

    @Override
    public String getOrCreatePath(final String name) {
        // Try and get the id from the cache.
        return Optional.ofNullable(cache.get(name))
                .or(() -> {
                    // Try and get the existing id from the DB.
                    return getPath(name)
                            .or(() -> {
                                createPath(name);
                                return getPath(name);
                            })
                            .map(path -> {
                                // Cache for next time.
                                cache.put(name, path);
                                return path;
                            });
                }).orElseThrow();
    }

    private void createPath(final String name) {
        final String path = name.toUpperCase().replaceAll("[^A-Z0-9_-]", "_");
        if (!path.equals(name)) {
            LOGGER.warn(LambdaLogUtil.message("A non standard feed name was found when registering a file path '{}'", name));
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

    @Override
    public String getType(final String path) {
        // Try and get the id from the cache.
        return Optional.ofNullable(cache.get(path))
                .or(() -> {
                    // Try and get the existing id from the DB.
                    return getName(path)
                            .map(name -> {
                                // Cache for next time.
                                cache.put(path, name);
                                return name;
                            });
                }).orElseThrow(() -> new RuntimeException("Unable to get type from path"));
    }
}
