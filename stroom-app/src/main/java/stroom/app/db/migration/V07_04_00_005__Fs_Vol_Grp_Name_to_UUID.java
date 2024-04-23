package stroom.app.db.migration;

import stroom.data.store.impl.fs.db.FsDataStoreDbConnProvider;
import stroom.db.util.DbUtil;
import stroom.docstore.impl.db.DocStoreDbConnProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
class V07_04_00_005__Fs_Vol_Grp_Name_to_UUID extends AbstractCrossModuleJavaDbMigration {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(V07_04_00_005__Fs_Vol_Grp_Name_to_UUID.class);

    private final FsDataStoreDbConnProvider fsDataStoreDbConnProvider;
    private final DocStoreDbConnProvider docStoreDbConnProvider;

    @Inject
    public V07_04_00_005__Fs_Vol_Grp_Name_to_UUID(final FsDataStoreDbConnProvider fsDataStoreDbConnProvider,
                                                  final DocStoreDbConnProvider docStoreDbConnProvider) {
        this.fsDataStoreDbConnProvider = fsDataStoreDbConnProvider;
        this.docStoreDbConnProvider = docStoreDbConnProvider;
    }

    @Override
    public void migrate(final Context context) throws Exception {

        LOGGER.info("Running " + getVersion());

        final Map<String, String> volGroupNameToUuidMap = new HashMap<>();

        String sql = """
                select name, uuid
                from fs_volume_group""";

        // Fetch all known names and their uuids
        DbUtil.forEachBiValue(
                fsDataStoreDbConnProvider,
                sql,
                String.class,
                String.class,
                volGroupNameToUuidMap::put);

        LOGGER.info("Found {} volume groups", volGroupNameToUuidMap.size());

        sql = """
                select distinct json_unquote(json_extract(convert(data using UTF8MB4), '$.volumeGroup'))
                from doc
                where type = 'Feed'
                and ext = 'meta'
                and json_contains_path(convert(data using UTF8MB4), 'all', '$.volumeGroup')""";

        // Fetch all the vol group names actually used by Feeds
        final List<String> distinctVolGroupNames = new ArrayList<>();
        DbUtil.forEachValue(docStoreDbConnProvider, sql, String.class, name -> {
            if (name != null) {
                distinctVolGroupNames.add(name);
            }
        });
        LOGGER.info("Found {} distinct volume group names in Feeds", distinctVolGroupNames.size());

        final String addKeySql = """
                update doc
                set data = json_set(convert(data using UTF8MB4), '$.volumeGroupDocRef', CAST(? AS JSON))
                where type = 'Feed'
                and ext = 'meta'
                and json_unquote(json_extract(convert(data using UTF8MB4), '$.volumeGroup')) = ?""";

        final String removeKeySql = """
                update doc
                set data = json_remove(convert(data using UTF8MB4), '$.volumeGroup')
                where type = 'Feed'
                and ext = 'meta'
                and json_unquote(json_extract(convert(data using UTF8MB4), '$.volumeGroup')) = ?""";

        // Now set the volumeGroupDocRef key and remove the volumeGroup key
        int cnt = 0;
        try (Connection connection = docStoreDbConnProvider.getConnection()) {
            try (final PreparedStatement addKeyStmt = connection.prepareStatement(addKeySql);
                    final PreparedStatement removeKeyStmt = connection.prepareStatement(removeKeySql)) {

                for (final String volGroupName : distinctVolGroupNames) {
                    addKeyStmt.clearParameters();
                    removeKeyStmt.clearParameters();
                    final String uuid = volGroupNameToUuidMap.get(volGroupName);
                    if (uuid != null) {
                        final String docRefJson = volGroupUuidToDocRefJson(uuid, volGroupName);
                        addKeyStmt.setString(1, docRefJson);
                        addKeyStmt.setString(2, volGroupName);
                        LOGGER.info("Adding 'volumeGroupDocRef' key with value '{}' on Feeds", docRefJson);
                        cnt = addKeyStmt.executeUpdate();
                        LOGGER.info("Updated {} rows", cnt);
                    } else {
                        LOGGER.warn("No DocRef found for FS volume group '{}'", volGroupName);
                    }
                    removeKeyStmt.setString(1, volGroupName);
                    LOGGER.info("Removing 'volumeGroup' key from Feeds");
                    cnt = removeKeyStmt.executeUpdate();
                    LOGGER.info("Updated {} rows", cnt);
                }
            }
        }
        LOGGER.info("Completed " + getVersion());
    }

    private String volGroupUuidToDocRefJson(final String uuid, final String name) {
        return "{" +
                "\"name\": \"" + name + "\", " +
                "\"type\": \"FsVolumeGroup\", " +
                "\"uuid\": \"" + uuid + "\"}";
    }
}
