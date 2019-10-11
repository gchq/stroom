package stroom.data.store.impl.fs.db;

import stroom.data.store.impl.fs.FsFeedPathDao;
import stroom.db.util.JooqUtil;
import stroom.util.logging.LambdaLogUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static stroom.data.store.impl.fs.db.jooq.tables.FsFeedPath.FS_FEED_PATH;

/**
 * This class exists to map feed id's to file paths using old data from the DB.
 */
@Singleton
class FsFeedPathDaoImpl implements FsFeedPathDao {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsFeedPathDaoImpl.class);

    // TODO : @66 Replace with a proper cache.
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    private final FsDataStoreDbConnProvider fsDataStoreDbConnProvider;

    @Inject
    FsFeedPathDaoImpl(final FsDataStoreDbConnProvider fsDataStoreDbConnProvider) {
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
                .insertInto(FS_FEED_PATH, FS_FEED_PATH.NAME, FS_FEED_PATH.PATH)
                .values(name, path)
                .onDuplicateKeyIgnore()
                .execute());
    }

    private Optional<String> getPath(final String name) {
        return JooqUtil.contextResult(fsDataStoreDbConnProvider, context -> context
                .select(FS_FEED_PATH.PATH)
                .from(FS_FEED_PATH)
                .where(FS_FEED_PATH.NAME.eq(name))
                .fetchOptional(FS_FEED_PATH.PATH));
    }
}
