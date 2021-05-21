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
import stroom.index.shared.IndexDoc.PartitionBy;
import stroom.legacy.impex_6_1.LegacyXmlSerialiser;
import stroom.legacy.impex_6_1.MappingUtil;
import stroom.legacy.model_6_1.Index;
import stroom.legacy.model_6_1.Volume.VolumeUseStatus;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("unused")
@Deprecated
public class V07_00_00_1502__Index extends BaseJavaMigration {

    private static final Logger LOGGER = LoggerFactory.getLogger(V07_00_00_1502__Index.class);

    private final AtomicInteger currentGroupId = new AtomicInteger();
    private final AtomicInteger currentVolumeId = new AtomicInteger();

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

            // Start by getting all old volumes.
            final Map<Integer, OldVolume> oldVolumes = getOldVolumes(connection);

            // Get a map of index UUIDs to the list of volume ids for each index.
            // All sets in the map will be non empty
            final Map<String, Set<Integer>> indexUuidToVolumeIdListMap = getVolumesToMigrate(connection);

            if (!indexUuidToVolumeIdListMap.isEmpty()) {
                final Map<List<Integer>, VolumeGroup> uniqueIndexVolumesToGroupIdMap = new HashMap<>();
                final Map<String, VolumeGroup> indexUuidToVolumeGroupMap = new HashMap<>();

                // Create sets of unique groups.
                LOGGER.info("Creating index volume groups");
                for (final Entry<String, Set<Integer>> entry : indexUuidToVolumeIdListMap.entrySet()) {
                    final String indexUuid = entry.getKey();
                    final Set<Integer> indexVolIdSet = entry.getValue();

                    // Create a sorted list of the volume ids to ensure comparison as a map key
                    final List<Integer> sortedIndexVolumeIds = indexVolIdSet
                            .stream()
                            .sorted()
                            .collect(Collectors.toUnmodifiableList());

                    if (sortedIndexVolumeIds.isEmpty()) {
                        throw new RuntimeException("sortedIndexVolumeIds is empty for index uuid " + indexUuid);
                    }

                    // Get or create a new volume group for the volumes.
                    final VolumeGroup volumeGroup = uniqueIndexVolumesToGroupIdMap.computeIfAbsent(
                            sortedIndexVolumeIds,
                            sortedIndexVolIds2 ->
                                    createVolumeGroup(connection, sortedIndexVolIds2));

                    Objects.requireNonNull(volumeGroup, "Null volume group");

                    indexUuidToVolumeGroupMap.put(indexUuid, volumeGroup);
                }

                // Create and attach index volumes to groups.
                LOGGER.info("Migrating index volumes");
                createIndexVolumes(connection, uniqueIndexVolumesToGroupIdMap.values(), oldVolumes);

                // Now assign the index volume group to each index.
                LOGGER.info("Migrating index docs");
                migrateIndexDocs(connection, indexUuidToVolumeGroupMap);

                // Migrate shards.
                LOGGER.info("Migrating index shards");
                copyIndexShards(connection, indexUuidToVolumeGroupMap, oldVolumes);

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
    private Map<String, Set<Integer>> getVolumesToMigrate(final Connection connection) throws Exception {
        final Map<String, Set<Integer>> indexUuidToVolumeIdListMap = new HashMap<>();

        // Add all current index volumes associated with an index.
        try (final PreparedStatement preparedStatement = connection.prepareStatement(
                "SELECT " +
                        "IDX_UUID, " +
                        "FK_VOL_ID " +
                        "FROM " +
                        "OLD_IDX_VOL")) {

            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    final String indexUuid = resultSet.getString(1);
                    final int volId = resultSet.getInt(2);
                    indexUuidToVolumeIdListMap.computeIfAbsent(indexUuid, k -> new HashSet<>())
                            .add(volId);
                }
            }
        }

        // Add any historic index volumes.

        // NOTE ois.FK_IDX_ID is renamed in V07_00_00_017__IDX_SHRD.sql to OLD_IDX_ID
        // There is no FK constraint on OLD_IDX_ID so it is possible we may ignore shards with no
        // corresponding idx, but there was an FK in V07_00_00_017__IDX_SHRD.sql so as long as we have
        // not deleted idxc recs since then we are ok.
        final String indexShardVolsSql =
                "SELECT DISTINCT " +
                        "  oi.UUID, " +
                        "  ois.FK_VOL_ID " +
                        "FROM " +
                        "OLD_IDX_SHRD ois " +
                        "JOIN OLD_IDX oi ON (oi.ID = ois.OLD_IDX_ID) ";
        try (final PreparedStatement preparedStatement = connection.prepareStatement(indexShardVolsSql)) {

            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    final String indexUuid = resultSet.getString(1);
                    final int volId = resultSet.getInt(2);
                    indexUuidToVolumeIdListMap.computeIfAbsent(indexUuid, k -> new HashSet<>())
                            .add(volId);
                }
            }
        }

        return indexUuidToVolumeIdListMap;
    }

    private Map<Integer, OldVolume> getOldVolumes(final Connection connection) throws Exception {
        final Map<Integer, OldVolume> oldVolumes = new HashMap<>();

        final String selectVolumes = "" +
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
                " JOIN OLD_VOL_STATE vs ON (vs.ID = v.FK_VOL_STATE_ID)";

        try (final PreparedStatement select = connection.prepareStatement(selectVolumes)) {
            try (final ResultSet resultSet = select.executeQuery()) {
                while (resultSet.next()) {
                    final int volId = resultSet.getInt(1);
                    final Long crtMs = DbUtil.getLong(resultSet, 2);
                    final String crtUser = resultSet.getString(3);
                    final Long updMs = DbUtil.getLong(resultSet, 4);
                    final String updUser = resultSet.getString(5);
                    final String path = resultSet.getString(6);
                    final byte state = resultSet.getByte(7);
                    final Long bytesLimit = DbUtil.getLong(resultSet, 8);
                    final String node = resultSet.getString(9);
                    final Long bytesUsed = DbUtil.getLong(resultSet, 10);
                    final Long bytesFree = DbUtil.getLong(resultSet, 11);
                    final Long bytesTotal = DbUtil.getLong(resultSet, 12);
                    final Long statMs = DbUtil.getLong(resultSet, 13);

                    oldVolumes.put(volId, new OldVolume(
                            volId,
                            crtMs,
                            crtUser,
                            updMs,
                            updUser,
                            path,
                            VolumeUseStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(state),
                            bytesLimit,
                            node,
                            bytesUsed,
                            bytesFree,
                            bytesTotal,
                            statMs));
                }
            }
        }

        return oldVolumes;
    }

    private void createIndexVolumes(final Connection connection,
                                    final Collection<VolumeGroup> volumeGroups,
                                    final Map<Integer, OldVolume> oldVolumes) throws SQLException {
        final String insertMigratedIndexVolume = "" +
                "INSERT INTO index_volume (" +
                " id," +
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
                " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        for (final VolumeGroup volumeGroup : volumeGroups) {
            for (final int oldVolumeId : volumeGroup.getOldVolumeIds()) {
                final OldVolume oldVolume = oldVolumes.get(oldVolumeId);
                final int newVolumeId = currentVolumeId.incrementAndGet();

                try (final PreparedStatement insert = connection.prepareStatement(insertMigratedIndexVolume)) {
                    insert.setInt(1, newVolumeId);
                    insert.setInt(2, 1);
                    insert.setLong(3, oldVolume.crtMs);
                    insert.setString(4, oldVolume.crtUser);
                    insert.setLong(5, oldVolume.updMs);
                    insert.setString(6, oldVolume.updUser);
                    insert.setString(7, oldVolume.nodeName);
                    insert.setString(8, oldVolume.path);
                    insert.setInt(9, volumeGroup.id);
                    insert.setByte(10, oldVolume.status.getPrimitiveValue());
                    DbUtil.setLong(insert, 11, oldVolume.bytesLimit);
                    DbUtil.setLong(insert, 12, oldVolume.bytesUsed);
                    DbUtil.setLong(insert, 13, oldVolume.bytesFree);
                    DbUtil.setLong(insert, 14, oldVolume.bytesTotal);
                    DbUtil.setLong(insert, 15, oldVolume.statMs);
                    insert.executeUpdate();
                }

                volumeGroup.addVolumeIdMapping(oldVolume.id, newVolumeId);
            }
        }
    }

    private VolumeGroup createVolumeGroup(final Connection connection,
                                          final List<Integer> oldVolumeIds) {
        final int id = currentGroupId.incrementAndGet();
        final String groupName = "Group " + id;
        createGroup(connection, id, groupName);

        LOGGER.info("Created index volume group (id {}, name {}) with old vol ids [{}]",
                id,
                groupName,
                oldVolumeIds.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(",")));

        return new VolumeGroup(id, groupName, oldVolumeIds);
    }

    private void createGroup(final Connection connection,
                             final int id,
                             final String name) {
        final long now = System.currentTimeMillis();

        // Create index volume groups
//        final Map<String, Integer> groupNameToIdMap = new HashMap<>();
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
            insert.setInt(1, id);
            insert.setInt(2, 1);
            insert.setLong(3, now);
            insert.setString(4, "migration");
            insert.setLong(5, now);
            insert.setString(6, "migration");
            insert.setString(7, name);
            insert.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Error creating index vol group with id " + id + ", name " + name +
                    ": " + e.getMessage(), e);
        }
    }


    private void migrateIndexDocs(final Connection connection,
                                  final Map<String, VolumeGroup> indexUuidToVolumeGroupMap) throws Exception {
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
                        final Long crtMs = DbUtil.getLong(resultSet, 1);
                        final String crtUser = resultSet.getString(2);
                        final Long updMs = DbUtil.getLong(resultSet, 3);
                        final String updUser = resultSet.getString(4);
                        final String name = resultSet.getString(5);
                        final String uuid = resultSet.getString(6);
                        final String descrip = resultSet.getString(7);
                        final Integer maxDoc = DbUtil.getInteger(resultSet, 8);
                        final Integer maxShrd = DbUtil.getInteger(resultSet, 9);
                        final Byte partBy = DbUtil.getByte(resultSet, 10);
                        final Integer partSz = DbUtil.getInteger(resultSet, 11);
                        final Integer retenDayAge = DbUtil.getInteger(resultSet, 12);
                        final String fields = resultSet.getString(13);

                        final PartitionBy newPartitionBy = partBy == null
                                ? null
                                : Optional.ofNullable(
                                        Index.PartitionBy.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(partBy))
                                        .map(legacyPartitionBy ->
                                                IndexDoc.PartitionBy.valueOf(legacyPartitionBy.name()))
                                        .orElse(null);

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
                        if (maxDoc != null) {
                            document.setMaxDocsPerShard(maxDoc);
                        }
                        if (maxShrd != null) {
                            document.setShardsPerPartition(maxShrd);
                        }
                        document.setPartitionBy(newPartitionBy);
                        if (partSz != null) {
                            document.setPartitionSize(partSz);
                        }
                        document.setRetentionDayAge(retenDayAge);
                        document.setFields(MappingUtil.map(LegacyXmlSerialiser.getIndexFieldsFromLegacyXml(fields)));
                        Optional.ofNullable(indexUuidToVolumeGroupMap.get(uuid))
                                .map(VolumeGroup::getName)
                                .ifPresent(document::setVolumeGroupName);

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

    private void copyIndexShards(final Connection connection,
                                 final Map<String, VolumeGroup> indexUuidToVolumeGroupMap,
                                 final Map<Integer, OldVolume> oldVolumes) {
        final String selectSql = "" +
                "SELECT " +
                "s.ID, " +
                "n.NAME, " +
                "s.FK_VOL_ID, " +
                "s.OLD_IDX_ID, " +
                "s.IDX_UUID, " +
                "s.CMT_DOC_CT, " +
                "s.CMT_DUR_MS, " +
                "s.CMT_MS, " +
                "s.DOC_CT, " +
                "s.FILE_SZ, " +
                "s.STAT, " +
                "s.IDX_VER, " +
                "s.PART, " +
                "s.PART_FROM_MS, " +
                "s.PART_TO_MS " +
                "FROM OLD_IDX_SHRD s " +
                "INNER JOIN OLD_ND n ON(n.ID = s.FK_ND_ID)";

        final String insertSql = "" +
                "INSERT INTO index_shard (" +
                "id, " +
                "node_name, " +
                "fk_volume_id, " +
                "index_uuid, " +
                "commit_document_count, " +
                "commit_duration_ms, " +
                "commit_ms, " +
                "document_count, " +
                "file_size, " +
                "status, " +
                "index_version, " +
                "partition_name, " +
                "partition_from_ms, " +
                "partition_to_ms) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (final PreparedStatement insert = connection.prepareStatement(insertSql)) {
            try (final PreparedStatement select = connection.prepareStatement(selectSql)) {
                try (final ResultSet resultSet = select.executeQuery()) {
                    while (resultSet.next()) {
                        final Long id = DbUtil.getLong(resultSet, 1);
                        final String nodeName = resultSet.getString(2);
                        final Integer volumeId = DbUtil.getInteger(resultSet, 3);
                        final Integer oldIndexId = DbUtil.getInteger(resultSet, 4);
                        final String indexUuid = resultSet.getString(5);
                        final Integer commitDocumentCount = DbUtil.getInteger(resultSet, 6);
                        final Long commitDurationMs = DbUtil.getLong(resultSet, 7);
                        final Long commitMs = DbUtil.getLong(resultSet, 8);
                        final Integer documentCount = DbUtil.getInteger(resultSet, 9);
                        final Long fileSize = DbUtil.getLong(resultSet, 10);
                        final Byte status = DbUtil.getByte(resultSet, 11);
                        final String indexVersion = resultSet.getString(12);
                        final String part = resultSet.getString(13);
                        final Long partFrom = DbUtil.getLong(resultSet, 14);
                        final Long partTo = DbUtil.getLong(resultSet, 15);

                        // Get target volume id.
                        final OldVolume oldVolume = oldVolumes.get(volumeId);
                        Objects.requireNonNull(oldVolume,
                                "oldVolume should not be null for volumeId " + volumeId);

                        final VolumeGroup volumeGroup = indexUuidToVolumeGroupMap.get(indexUuid);
                        Objects.requireNonNull(volumeGroup,
                                "volumeGroup should not be null for indexUuid " + indexUuid);

                        final Integer newVolumeId = volumeGroup.oldToNewVolumeIdMapping.get(volumeId);
                        Objects.requireNonNull(newVolumeId,
                                "newVolumeId should not be null for volumeId " + volumeId);

                        // Move shard.
                        moveShard(oldVolume.path, oldIndexId, indexUuid);

                        try {
                            DbUtil.setLong(insert, 1, id);
                            insert.setString(2, nodeName);
                            DbUtil.setInteger(insert, 3, newVolumeId);
                            insert.setString(4, indexUuid);
                            DbUtil.setInteger(insert, 5, commitDocumentCount);
                            DbUtil.setLong(insert, 6, commitDurationMs);
                            DbUtil.setLong(insert, 7, commitMs);
                            DbUtil.setInteger(insert, 8, documentCount);
                            DbUtil.setLong(insert, 9, fileSize);
                            DbUtil.setByte(insert, 10, status);
                            insert.setString(11, indexVersion);
                            insert.setString(12, part);
                            DbUtil.setLong(insert, 13, partFrom);
                            DbUtil.setLong(insert, 14, partTo);
                            insert.executeUpdate();
                        } catch (final SQLException e) {
                            throw new RuntimeException("Error creating index shard with id " + id +
                                    " and uuid " + indexUuid +
                                    ": " + e.getMessage(), e);
                        }
                    }
                }
            }
        } catch (final SQLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static void moveShard(final String volumePath,
                                 final Integer oldIndexId,
                                 final String indexUuid) {
        Path path = Paths.get(volumePath);

        if (!Files.isDirectory(path)) {
            LOGGER.error("Volume path not found: " + volumePath);
        } else if (oldIndexId != null) {
            // If we have a legacy shard then see if we can create a path for it.
            Path legacyPath = path.resolve("index");
            legacyPath = legacyPath.resolve(String.valueOf(oldIndexId));
            if (Files.isDirectory(legacyPath)) {
                Path newPath = path.resolve("index");
                newPath = newPath.resolve(indexUuid);
                move(legacyPath, newPath);
            }
        }
    }

    private static void move(final Path source,
                             final Path target) {
        if (Files.isDirectory(target)) {
            // Recurse.
            try (final Stream<Path> children = Files.list(source)) {
                children.forEach(child -> move(child, target.resolve(child.getFileName())));
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
            }

        } else {
            try {
                Files.move(source, target);
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    private static class VolumeGroup {

        private final int id;
        private final String name;
        private final List<Integer> oldVolumeIds;
        private final Map<Integer, Integer> oldToNewVolumeIdMapping = new HashMap<>();

        public VolumeGroup(final int id,
                           final String name,
                           final List<Integer> oldVolumeIds) {
            this.id = id;
            this.name = name;
            this.oldVolumeIds = oldVolumeIds;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public List<Integer> getOldVolumeIds() {
            return oldVolumeIds;
        }

        public void addVolumeIdMapping(final int oldId, final int newId) {
            oldToNewVolumeIdMapping.put(oldId, newId);
        }
    }

    private static class OldVolume {

        private final int id;
        private final Long crtMs;
        private final String crtUser;
        private final Long updMs;
        private final String updUser;
        private final String path;
        private final VolumeUseStatus status;
        private final Long bytesLimit;
        private final String nodeName;
        private final Long bytesUsed;
        private final Long bytesFree;
        private final Long bytesTotal;
        private final Long statMs;

        public OldVolume(final int id,
                         final Long crtMs,
                         final String crtUser,
                         final Long updMs,
                         final String updUser,
                         final String path,
                         final VolumeUseStatus status,
                         final Long bytesLimit,
                         final String nodeName,
                         final Long bytesUsed,
                         final Long bytesFree,
                         final Long bytesTotal, final Long statMs) {
            this.id = id;
            this.crtMs = crtMs;
            this.crtUser = crtUser;
            this.updMs = updMs;
            this.updUser = updUser;
            this.path = path;
            this.status = status;
            this.bytesLimit = bytesLimit;
            this.nodeName = nodeName;
            this.bytesUsed = bytesUsed;
            this.bytesFree = bytesFree;
            this.bytesTotal = bytesTotal;
            this.statMs = statMs;
        }

        public int getId() {
            return id;
        }

        public Long getCrtMs() {
            return crtMs;
        }

        public String getCrtUser() {
            return crtUser;
        }

        public Long getUpdMs() {
            return updMs;
        }

        public String getUpdUser() {
            return updUser;
        }

        public String getPath() {
            return path;
        }

        public VolumeUseStatus getStatus() {
            return status;
        }

        public Long getBytesLimit() {
            return bytesLimit;
        }

        public String getNodeName() {
            return nodeName;
        }

        public Long getBytesUsed() {
            return bytesUsed;
        }

        public Long getBytesFree() {
            return bytesFree;
        }

        public Long getBytesTotal() {
            return bytesTotal;
        }

        public Long getStatMs() {
            return statMs;
        }
    }
}
