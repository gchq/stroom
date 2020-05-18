/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.legacy.db.migration;

import stroom.db.util.DbUtil;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.index.impl.IndexSerialiser;
import stroom.index.shared.IndexDoc;
import stroom.legacy.impex_6_1.LegacyXmlSerialiser;
import stroom.legacy.impex_6_1.MappingUtil;
import stroom.legacy.model_6_1.Index;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class V07_00_00_1502__Index extends BaseJavaMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger(V07_00_00_1502__Index.class);

    @Override
    public void migrate(final Context context) throws Exception {
        migrate(context.getConnection());
    }


    /**
     * This method migrates index volumes to their new table, and creates index volume groups.
     * <p>
     * Index volume groups are new so it will create a new index volume group for each index.
     * The user can easily sort out how they want to organise their volumes after that.
     * <p>
     * We might end up creating several index volumes where a single one existed before. The
     * relationship required is one-to-many, not many-to-many, so we don't have a choice about this.
     */
    private void migrate(final Connection connection) throws Exception {
        if (DbUtil.doesTableExist(connection, "OLD_IDX_VOL")) {

            final var indexUuidToVolumeIdListMap = getVolumesToMigrate(connection);

            if (!indexUuidToVolumeIdListMap.isEmpty()) {
                final var indexUuidToGroupNameMap = generateVolumeGroupNames(indexUuidToVolumeIdListMap.keySet());

                final Set<String> groupNames = new HashSet<>(indexUuidToGroupNameMap.values());

                final var groupNameToIdMap = createGroups(connection, groupNames);

                final var indexUuidToGroupIdMap = indexUuidToGroupNameMap.entrySet()
                        .stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> groupNameToIdMap.get(entry.getValue())));

                for (var indexUuid : indexUuidToGroupNameMap.keySet()) {
                    final var volumeIdsToCreateForIndex = indexUuidToVolumeIdListMap.get(indexUuid);
                    final int groupIdForIndexes = indexUuidToGroupIdMap.get(indexUuid);
                    createIndexVolumes(connection, volumeIdsToCreateForIndex, groupIdForIndexes);
                }

                migrateIndexDocs(connection, indexUuidToGroupNameMap);
            } else {
                LOGGER.info("OLD_IDX_VOL table is empty so nothing to migrate");
            }
        } else {
            LOGGER.info("OLD_IDX_VOL table doesn't exist so nothing to migrate");
        }
    }

    /**
     * @return A map of index UUIDs to lists of the volumes they use. We'll need both, hence the map.
     */
    private Map<String, List<Integer>> getVolumesToMigrate(final Connection connection) throws Exception {
        final var indexUuidToVolumeIdListMap = new HashMap<String, List<Integer>>();
        try (final var preparedStatement = connection.prepareStatement(
                "SELECT " +
                        "  FK_VOL_ID, " +
                        "  IDX_UUID " +
                        "FROM " +
                        "  OLD_IDX_VOL")) {

            try (final var resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    final var volId = resultSet.getInt(1);
                    final var indexUuid = resultSet.getString(2);
                    indexUuidToVolumeIdListMap.computeIfAbsent(indexUuid, k -> new ArrayList<>()).add(volId);
                }
            }
        }
        return indexUuidToVolumeIdListMap;
    }


    /**
     * @return A map of index uuids to the newly generated group names.
     */
    private Map<String, String> generateVolumeGroupNames(Set<String> indexUuids) {
        final var groupNumber = new AtomicInteger();
        final var indexUuidToGroupNameMap = new HashMap<String, String>();
        indexUuids.forEach((indexUuid -> {
            final String name = "Group " + groupNumber.incrementAndGet();
            indexUuidToGroupNameMap.put(indexUuid, name);
        }));
        return indexUuidToGroupNameMap;
    }

    private void createIndexVolumes(final Connection connection,
                                    final List<Integer> volumeIdSSet,
                                    final int groupId) throws SQLException {
        final String idSet = volumeIdSSet.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        final String idSetPredicateStr;
        if (idSet.length() > 0) {
            idSetPredicateStr = " AND v.ID IN (" + idSet + ")";
        } else {
            idSetPredicateStr = "";
        }

        final String selectVolumesToMigrate = "" +
                "SELECT" +
                "  v.ID," +
                "  v.CRT_MS," +
                "  v.CRT_USER," +
                "  v.UPD_MS," +
                "  v.UPD_USER," +
                "  v.PATH," +
                "  v.IDX_STAT," +
                "  v.BYTES_LMT," +
                "  n.NAME," +
                "  vs.BYTES_USED," +
                "  vs.BYTES_FREE," +
                "  vs.BYTES_TOTL," +
                "  vs.STAT_MS " +
                " FROM OLD_VOL v" +
                " JOIN OLD_ND n ON (n.ID = v.FK_ND_ID)" +
                " JOIN OLD_VOL_STATE vs ON (vs.ID = v.FK_VOL_STATE_ID)" +
                " WHERE v.VOL_TP = 1 " + // Only want 'private' volumes as 'public' ones are for streams
                idSetPredicateStr;

        final String insertMigratedIndexVolume = "" +
                "INSERT INTO index_volume (" +
                " version," +
                " create_time_ms," +
                " create_user," +
                " update_time_ms," +
                " update_user," +
                " node_name," +
                " path," +
                " fk_index_volume_group_id," +
                " state," +
                " bytes_limit," +
                " bytes_used," +
                " bytes_free," +
                " bytes_total," +
                " status_ms)" +
                " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (final PreparedStatement insert = connection.prepareStatement(insertMigratedIndexVolume)) {
            try (final PreparedStatement select = connection.prepareStatement(selectVolumesToMigrate)) {
                try (final ResultSet resultSet = select.executeQuery()) {
                    while (resultSet.next()) {
                        final long crtMs = resultSet.getLong(2);
                        final String crtUser = resultSet.getString(3);
                        final long updMs = resultSet.getLong(4);
                        final String updUser = resultSet.getString(5);
                        final String path = resultSet.getString(6);
                        final byte state = resultSet.getByte(7);
                        final long bytesLimit = resultSet.getLong(8);
                        final String node = resultSet.getString(9);
                        final long bytesUsed = resultSet.getLong(10);
                        final long bytesFree = resultSet.getLong(11);
                        final long bytesTotal = resultSet.getLong(12);
                        final long statMs = resultSet.getLong(13);

                        insert.setInt(1, 1);
                        insert.setLong(2, crtMs);
                        insert.setString(3, crtUser);
                        insert.setLong(4, updMs);
                        insert.setString(5, updUser);
                        insert.setString(6, node);
                        insert.setString(7, path);
                        insert.setInt(8, groupId);
                        insert.setByte(9, state);
                        insert.setLong(10, bytesLimit);
                        insert.setLong(11, bytesUsed);
                        insert.setLong(12, bytesFree);
                        insert.setLong(13, bytesTotal);
                        insert.setLong(14, statMs);
                        insert.executeUpdate();
                    }
                }
            }
        }
    }


    private Map<String, Integer> createGroups(final Connection connection,
                                              final Set<String> groups) throws SQLException {
        final long now = System.currentTimeMillis();

        // Create index volume groups
        final Map<String, Integer> groupNameToIdMap = new HashMap<>();
        final String insertSql = "" +
                "INSERT INTO index_volume_group (" +
                " id," +
                " version," +
                " create_time_ms," +
                " create_user," +
                " update_time_ms," +
                " update_user," +
                " name)" +
                " VALUES (?,?,?,?,?,?,?)";

        try (final PreparedStatement insert = connection.prepareStatement(insertSql)) {
            int i = 1;
            for (String groupName : groups) {
                insert.setInt(1, i);
                insert.setInt(2, 1);
                insert.setLong(3, now);
                insert.setString(4, "migration");
                insert.setLong(5, now);
                insert.setString(6, "migration");
                insert.setString(7, groupName);
                insert.executeUpdate();

                groupNameToIdMap.put(groupName, i);
                i++;
            }
        }

        return groupNameToIdMap;
    }


    private void migrateIndexDocs(final Connection connection,
                                  final Map<String, String> indexUuidToGroupNameMap) throws Exception {
        final IndexSerialiser serialiser = new IndexSerialiser(new Serialiser2FactoryImpl());

        final String selectSql = "" +
                "SELECT" +
                "  CRT_MS," +
                "  CRT_USER," +
                "  UPD_MS," +
                "  UPD_USER," +
                "  NAME," +
                "  UUID," +
                "  DESCRIP," +
                "  MAX_DOC," +
                "  MAX_SHRD," +
                "  PART_BY," +
                "  PART_SZ," +
                "  RETEN_DAY_AGE," +
                "  FLDS " +
                "FROM OLD_IDX";

        final String insertSql = "" +
                " INSERT INTO doc (" +
                " type," +
                " uuid," +
                " name," +
                " ext," +
                " data)" +
                " VALUES (?, ?, ?, ?, ?)";

        try (final PreparedStatement insert = connection.prepareStatement(insertSql)) {
            try (final PreparedStatement select = connection.prepareStatement(selectSql)) {
                try (final ResultSet resultSet = select.executeQuery()) {
                    while (resultSet.next()) {
                        final Long crtMs = resultSet.getLong(1);
                        final String crtUser = resultSet.getString(2);
                        final Long updMs = resultSet.getLong(3);
                        final String updUser = resultSet.getString(4);
                        final String name = resultSet.getString(5);
                        final String uuid = resultSet.getString(6);
                        final String descrip = resultSet.getString(7);
                        final int maxDoc = resultSet.getInt(8);
                        final int maxShrd = resultSet.getInt(9);
                        final byte partBy = resultSet.getByte(10);
                        final int partSz = resultSet.getInt(11);
                        final Integer retenDayAge = DbUtil.getInteger(resultSet, 12);
                        final String fields = resultSet.getString(13);

                        final IndexDoc document = new IndexDoc();
                        document.setType(IndexDoc.DOCUMENT_TYPE);
                        document.setUuid(uuid);
                        document.setName(name);
                        document.setVersion(UUID.randomUUID().toString());
                        document.setCreateTimeMs(crtMs);
                        document.setUpdateTimeMs(updMs);
                        document.setCreateUser(crtUser);
                        document.setUpdateUser(updUser);
                        document.setDescription(descrip);
                        document.setMaxDocsPerShard(maxDoc);
                        document.setShardsPerPartition(maxShrd);
                        document.setPartitionBy(IndexDoc.PartitionBy.valueOf(Index.PartitionBy.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(partBy).name()));
                        document.setPartitionSize(partSz);
                        document.setRetentionDayAge(retenDayAge);
                        document.setFields(MappingUtil.map(LegacyXmlSerialiser.getIndexFieldsFromLegacyXml(fields)));
                        document.setVolumeGroupName(indexUuidToGroupNameMap.get(uuid));

                        final Map<String, byte[]> dataMap = serialiser.write(document);

                        // Add the records.
                        dataMap.forEach((k, v) -> {
                            try {
                                insert.setString(1, IndexDoc.DOCUMENT_TYPE);
                                insert.setString(2, uuid);
                                insert.setString(3, name);
                                insert.setString(4, k);
                                insert.setBytes(5, v);
                                insert.executeUpdate();
                            } catch (final SQLException e) {
                                throw new RuntimeException(e.getMessage(), e);
                            }
                        });
                    }
                }
            }
        }
    }
}