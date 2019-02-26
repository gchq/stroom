package stroom.data.store.impl.fs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.db.util.JooqUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

import static stroom.data.store.impl.fs.db.jooq.tables.FsFeedPath.FS_FEED_PATH;

/**
 * This class exists to map feed id's to file paths using old data from the DB.
 */
@Singleton
class FsFeedPaths {
    private static final Logger LOGGER = LoggerFactory.getLogger(FsFeedPaths.class);

    private final ConnectionProvider connectionProvider;

    private final Map<String, String> feedToPathMap = new HashMap<>();

    @Inject
    FsFeedPaths(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
        refresh();
    }

    String getPath(final String feedName) {
        String path = feedToPathMap.get(feedName);
        if (path == null) {
            insert(feedName);
            path = feedToPathMap.get(feedName);

            if (path == null) {
                throw new RuntimeException("Unable to create path fragment from feed name");
            }
        }

        return path;
    }

    private void insert(final String feedName) {
        final String path = feedName.toUpperCase().replaceAll("\\W", "_");
        if (!path.equals(feedName)) {
            LOGGER.warn("A non standard feed name was found when registering a file path '" + feedName + "'");
        }

        JooqUtil.context(connectionProvider, context -> context
                .insertInto(FS_FEED_PATH, FS_FEED_PATH.NAME, FS_FEED_PATH.PATH)
                .values(feedName, path)
                .execute());

        refresh();
    }

    private void refresh() {
        JooqUtil.context(connectionProvider, context -> context
                .select(FS_FEED_PATH.NAME, FS_FEED_PATH.PATH)
                .from(FS_FEED_PATH)
                .fetch()
                .forEach(r -> put(r.value1(), r.value2())));
    }

    private void put(final String name, final String path) {
        feedToPathMap.put(name, path);
    }
}
