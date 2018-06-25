package stroom.data.store.impl.fs;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static stroom.data.store.impl.fs.db.stroom.tables.FileTypePath.FILE_TYPE_PATH;

/**
 * This class exists to map feed id's to file paths using old data from the DB.
 */
@Singleton
class FileSystemTypePathsImpl implements FileSystemTypePaths {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemTypePathsImpl.class);

    private final DataSource dataSource;

    private final Map<String, String> typeToPathMap = new HashMap<>();
    private final Map<String, String> pathToTypeMap = new HashMap<>();

    @Inject
    FileSystemTypePathsImpl(final DataSource dataSource) {
        this.dataSource = dataSource;
        refresh();
    }

    @Override
    public String getPath(final String typeName) {
        String path = typeToPathMap.get(typeName);
        if (path == null) {
            insert(typeName);
            path = typeToPathMap.get(typeName);

            if (path == null) {
                throw new RuntimeException("Unable to create path from type");
            }
        }

        return path;
    }

    @Override
    public String getType(final String path) {
        final String type = pathToTypeMap.get(path);
        if (type == null) {
            throw new RuntimeException("Unable to get type from path");
        }
        return type;
    }

    private void insert(final String typeName) {
        final String path = typeName.toUpperCase().replaceAll("\\W", "_");
        if (!path.equals(typeName)) {
            LOGGER.warn("A non standard type name was found when registering a file path '" + typeName + "'");
        }

        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            create.insertInto(FILE_TYPE_PATH, FILE_TYPE_PATH.NAME, FILE_TYPE_PATH.PATH)
                    .values(typeName, path)
                    .execute();
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        refresh();
    }

    private void refresh() {
        try (final Connection connection = dataSource.getConnection()) {
            final DSLContext create = DSL.using(connection, SQLDialect.MYSQL);
            create.select(FILE_TYPE_PATH.NAME, FILE_TYPE_PATH.PATH)
                    .from(FILE_TYPE_PATH)
                    .fetch()
                    .forEach(r -> put(r.value1(), r.value2()));
        } catch (final SQLException e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void put(final String name, final String path) {
        typeToPathMap.put(name, path);
        pathToTypeMap.put(path, name);
    }
}
