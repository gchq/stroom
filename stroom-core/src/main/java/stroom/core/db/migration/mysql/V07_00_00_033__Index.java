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

package stroom.core.db.migration.mysql;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.core.db.migration._V07_00_00.doc.index._V07_00_00_IndexDoc;
import stroom.core.db.migration._V07_00_00.doc.index._V07_00_00_IndexDoc.PartitionBy;
import stroom.core.db.migration._V07_00_00.doc.index._V07_00_00_IndexFields;
import stroom.core.db.migration._V07_00_00.doc.index._V07_00_00_IndexSerialiser;
import stroom.index.shared.IndexDoc;
import stroom.util.xml.XMLMarshallerUtil;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class V07_00_00_033__Index extends BaseJavaMigration {
    private static final Logger LOGGER = LoggerFactory.getLogger(V07_00_00_033__Index.class);

    @Override
    public void migrate(final Context context) throws Exception {
        migrate(context.getConnection());
    }

    private void migrate(final Connection connection) throws Exception {
        // Create index volume group names.
        final Set<Integer> volumeIdSet = new HashSet<>();
        final Map<String, List<Integer>> indexUuidToVolumeIdListMap = new HashMap<>();
        try (final PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT FK_VOL_ID, IDX_UUID FROM IDX_VOL")) {
            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    final Integer volId = resultSet.getInt(1);
                    final String indexUuid = resultSet.getString(2);
                    volumeIdSet.add(volId);
                    indexUuidToVolumeIdListMap.computeIfAbsent(indexUuid, k -> new ArrayList<>()).add(volId);
                }
            }
        }

        final AtomicInteger groupNumber = new AtomicInteger();
        final Map<List<Integer>, String> volumeIdListToGroupMap = new HashMap<>();
        final List<String> groupNames = new ArrayList<>();
        final Map<String, String> indexUuidToGroupNameMap = new HashMap<>();
        indexUuidToVolumeIdListMap.forEach((indexUuid, volumeIdList) -> {
            volumeIdList.sort(Comparator.naturalOrder());
            final String groupName = volumeIdListToGroupMap.computeIfAbsent(volumeIdList, k -> {
                final String name = "Group " + groupNumber.incrementAndGet();
                groupNames.add(name);
                return name;
            });
            indexUuidToGroupNameMap.put(indexUuid, groupName);
        });

        // Create index volumes
        createIndexVolumes(connection, volumeIdSet);

        // Create index volume groups
        final Map<String, Integer> groupNameToIdMap = createGroups(connection, groupNames);

        // Create index volume to index group links
        createVolumeToGroupLinks(connection, groupNameToIdMap, volumeIdListToGroupMap);

        // Migrate index docs.
        migrateIndexDocs(connection, indexUuidToGroupNameMap);
    }

    private void createIndexVolumes(final Connection connection, final Set<Integer> volumeIdSSet) throws SQLException {
        String idSet = volumeIdSSet.stream().map(String::valueOf).collect(Collectors.joining(","));
        if (idSet.length() > 0) {
            idSet = " v.ID IN (" + idSet + ") OR";
        }

        // Create index volumes.
        final String selectSql = "" +
                "SELECT" +
                " v.ID," +
                " v.CRT_MS," +
                " v.CRT_USER," +
                " v.UPD_MS," +
                " v.UPD_USER," +
                " v.PATH," +
                " v.IDX_STAT," +
                " v.BYTES_LMT," +
                " n.NAME," +
                " vs.BYTES_USED," +
                " vs.BYTES_FREE," +
                " vs.BYTES_TOTL," +
                " vs.STAT_MS" +
                " FROM VOL v" +
                " JOIN ND n ON (n.ID = v.FK_ND_ID)" +
                " JOIN VOL_STATE vs ON (vs.ID = v.FK_VOL_STATE_ID)" +
                " WHERE" +
                idSet +
                " v.IDX_STAT = 0";
        final String insertSql = "" +
                "INSERT INTO index_volume (" +
                " id," +
                " version," +
                " create_time_ms," +
                " create_user," +
                " update_time_ms," +
                " update_user," +
                " node_name," +
                " path," +
                " state," +
                " bytes_limit," +
                " bytes_used," +
                " bytes_free," +
                " bytes_total," +
                " status_ms)" +
                " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (final PreparedStatement insert = connection.prepareStatement(insertSql)) {
            try (final PreparedStatement select = connection.prepareStatement(selectSql)) {
                try (final ResultSet resultSet = select.executeQuery()) {
                    while (resultSet.next()) {
                        final int volId = resultSet.getInt(1);
                        final long crtMs = resultSet.getLong(2);
                        final String crtUser = resultSet.getString(3);
                        final long updMs = resultSet.getLong(4);
                        final String updUser = resultSet.getString(5);
                        final String path = resultSet.getString(6);
                        final byte state = resultSet.getByte(7);
                        final long bytesLimit = resultSet.getByte(8);
                        final String node = resultSet.getString(9);
                        final long bytesUsed = resultSet.getByte(10);
                        final long bytesFree = resultSet.getByte(11);
                        final long bytesTotal = resultSet.getByte(12);
                        final long statMs = resultSet.getByte(13);

                        insert.setInt(1, volId);
                        insert.setInt(2, 1);
                        insert.setLong(3, crtMs);
                        insert.setString(4, crtUser);
                        insert.setLong(5, updMs);
                        insert.setString(6, updUser);
                        insert.setString(7, node);
                        insert.setString(8, path);
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

    private Map<String, Integer> createGroups(final Connection connection, final List<String> groups) throws SQLException {
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

    private void createVolumeToGroupLinks(final Connection connection, final Map<String, Integer> groupNameToIdMap, final Map<List<Integer>, String> volumeIdListToGroupMap) throws SQLException {
        // Create index volume group links
        final String insertSql = "" +
                "INSERT INTO index_volume_group_link (" +
                " fk_index_volume_group_id," +
                " fk_index_volume_id)" +
                " VALUES (?,?)";

        try (final PreparedStatement insert = connection.prepareStatement(insertSql)) {
            for (final Entry<List<Integer>, String> entry : volumeIdListToGroupMap.entrySet()) {
                final List<Integer> volumeIds = entry.getKey();
                final String groupName = entry.getValue();
                final int groupId = groupNameToIdMap.get(groupName);
                for (final int volumeId : volumeIds) {
                    insert.setInt(1, groupId);
                    insert.setInt(2, volumeId);
                    insert.executeUpdate();
                }
            }
        }
    }

    private void migrateIndexDocs(final Connection connection, final Map<String, String> indexUuidToGroupNameMap) throws Exception {
        final _V07_00_00_IndexSerialiser serialiser = new _V07_00_00_IndexSerialiser();

        final String selectSql = "" +
                "SELECT" +
                " CRT_MS," +
                " CRT_USER," +
                " UPD_MS," +
                " UPD_USER," +
                " NAME," +
                " UUID," +
                " DESCRIP," +
                " MAX_DOC," +
                " MAX_SHRD," +
                " PART_BY," +
                " PART_SZ," +
                " RETEN_DAY_AGE," +
                " FLDS" +
                " FROM IDX";
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
                        final int retenDayAge = resultSet.getInt(12);
                        final String fields = resultSet.getString(13);

                        final _V07_00_00_IndexDoc document = new _V07_00_00_IndexDoc();
                        document.setType(IndexDoc.DOCUMENT_TYPE);
                        document.setUuid(uuid);
                        document.setName(name);
                        document.setVersion(UUID.randomUUID().toString());
                        document.setCreateTime(crtMs);
                        document.setUpdateTime(updMs);
                        document.setCreateUser(crtUser);
                        document.setUpdateUser(updUser);
                        document.setDescription(descrip);
                        document.setMaxDocsPerShard(maxDoc);
                        document.setShardsPerPartition(maxShrd);
                        document.setPartitionBy(PartitionBy.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(partBy));
                        document.setPartitionSize(partSz);
                        document.setRetentionDayAge(retenDayAge);

                        if (fields != null && fields.length() > 0) {
                            final _V07_00_00_IndexFields indexFields = getIndexFieldsFromLegacyXML(fields);
                            if (indexFields != null && indexFields.getIndexFields().size() > 0) {
                                document.setIndexFields(indexFields.getIndexFields());
                            }
                        }

                        document.setVolumeGroupName(indexUuidToGroupNameMap.get(uuid));

                        final Map<String, byte[]> dataMap = serialiser.write(document);

                        // Add the records.
                        dataMap.forEach((k, v) -> {
                            try {
                                insert.setString(1, _V07_00_00_IndexDoc.DOCUMENT_TYPE);
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

    private _V07_00_00_IndexFields getIndexFieldsFromLegacyXML(final String xml) {
        if (xml != null) {
            try {
                final JAXBContext jaxbContext = JAXBContext.newInstance(_V07_00_00_IndexFields.class);
                return XMLMarshallerUtil.unmarshal(jaxbContext, _V07_00_00_IndexFields.class, xml);
            } catch (final JAXBException | RuntimeException e) {
                LOGGER.error("Unable to unmarshal index config", e);
            }
        }

        return null;
    }
}