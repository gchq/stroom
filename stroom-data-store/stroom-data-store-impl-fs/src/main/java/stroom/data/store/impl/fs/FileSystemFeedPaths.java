package stroom.data.store.impl.fs;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static stroom.data.store.impl.fs.db.stroom.tables.FileFeedPath.FILE_FEED_PATH;

/**
 * This class exists to map feed id's to file paths using old data from the DB.
 */
@Singleton
class FileSystemFeedPaths {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemFeedPaths.class);

    private final ConnectionProvider connectionProvider;

    private final Map<String, String> feedToPathMap = new HashMap<>();

    @Inject
    FileSystemFeedPaths(final ConnectionProvider connectionProvider) {
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

        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            create.insertInto(FILE_FEED_PATH, FILE_FEED_PATH.NAME, FILE_FEED_PATH.PATH)
                    .values(feedName, path)
                    .execute();
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        refresh();
    }

    private void refresh() {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            create.select(FILE_FEED_PATH.NAME, FILE_FEED_PATH.PATH)
                    .from(FILE_FEED_PATH)
                    .fetch()
                    .forEach(r -> put(r.value1(), r.value2()));
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void put(final String name, final String path) {
        feedToPathMap.put(name, path);
    }
}
