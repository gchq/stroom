package stroom.data.store.impl.fs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * This class exists to map feed id's to file paths using old data from the DB.
 */
@Singleton
public class FileSystemFeedPaths {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemFeedPaths.class);

    private final DataSource dataSource;

    private final Map<String, String> feedToPathMap = new HashMap<>();

    @Inject
    FileSystemFeedPaths(final DataSource dataSource) {
        this.dataSource = dataSource;
        refresh();
    }

    public String getPath(final String feedName) {
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

        try (final Connection connection = dataSource.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO FS_FEED_PATH (NAME, PATH) VALUES (?, ?);")) {
                preparedStatement.setString(1, feedName);
                preparedStatement.setString(2, path);
                preparedStatement.execute();
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        refresh();
    }

    private void refresh() {
        try (final Connection connection = dataSource.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement("SELECT NAME, PATH FROM FS_FEED_PATH;")) {
                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    put(resultSet.getString(1), resultSet.getString(2));
                }
            }
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void put(final String name, final String path) {
        feedToPathMap.put(name, path);
    }
}
