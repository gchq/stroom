package stroom.data.store.impl.fs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.db.util.JooqUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

import static stroom.data.store.impl.fs.db.jooq.tables.FsTypePath.FS_TYPE_PATH;

/**
 * This class exists to map feed id's to file paths using old data from the DB.
 */
@Singleton
class FsTypePathsImpl implements FsTypePaths {
    private static final Logger LOGGER = LoggerFactory.getLogger(FsTypePathsImpl.class);

    private final ConnectionProvider connectionProvider;

    private final Map<String, String> typeToPathMap = new HashMap<>();
    private final Map<String, String> pathToTypeMap = new HashMap<>();

    @Inject
    FsTypePathsImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
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

        JooqUtil.context(connectionProvider, context -> context
                .insertInto(FS_TYPE_PATH, FS_TYPE_PATH.NAME, FS_TYPE_PATH.PATH)
                .values(typeName, path)
                .execute());

        refresh();
    }

    private void refresh() {
        JooqUtil.context(connectionProvider, context -> context
                .select(FS_TYPE_PATH.NAME, FS_TYPE_PATH.PATH)
                .from(FS_TYPE_PATH)
                .fetch()
                .forEach(r -> put(r.value1(), r.value2())));
    }

    private void put(final String name, final String path) {
        typeToPathMap.put(name, path);
        pathToTypeMap.put(path, name);
    }
}
