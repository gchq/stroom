/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.docstore.impl.db;

import stroom.docref.DocAuditEntry;
import stroom.docref.DocAuditEntry.AuditAction;
import stroom.docref.DocAuditUser;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.docstore.api.RWLockFactory;
import stroom.docstore.impl.GenericDoc;
import stroom.docstore.impl.Persistence;
import stroom.importexport.api.ByteArrayImportExportAsset;
import stroom.importexport.api.ImportExportAsset;
import stroom.importexport.api.ImportExportDocument;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.string.PatternUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.sql.DataSource;

@Singleton
public class DBPersistence implements Persistence {

    private static final Logger LOGGER = LoggerFactory.getLogger(DBPersistence.class);

    private static final RWLockFactory LOCK_FACTORY = new NoLockFactory();

    private static final String SELECT_BY_TYPE_UUID_SQL = """
            SELECT
              ext,
              data
            FROM doc
            WHERE type = ?
            AND uuid = ?""";

    private static final String SELECT_NAME_BY_TYPE_UUID_SQL = """
            SELECT
              name
            FROM doc
            WHERE type = ?
            AND uuid = ?
            AND ext = 'meta'""";

    private static final String SELECT_EXTENSIONS_BY_TYPE_UUID_SQL = """
            SELECT
              id,
              ext
            FROM doc
            WHERE type = ?
            AND uuid = ?""";

    private static final String DELETE_BY_UUID_SQL = """
            DELETE FROM doc
            WHERE type = ?
            AND uuid = ?""";

    private static final String DELETE_BY_ID_SQL = """
            DELETE FROM doc
            WHERE id = ?""";

    private static final String LIST_BY_TYPE_SQL = """
            SELECT DISTINCT
              uuid,
              name
            FROM doc
            WHERE type = ?
            AND ext = 'meta'
            ORDER BY uuid""";

    private static final String SELECT_BY_TYPE_NAME_EQUALS_SQL = """
            SELECT DISTINCT
              uuid,
              name
            FROM doc
            WHERE type = ?
            AND name COLLATE utf8mb4_0900_as_cs = ?
            AND ext = 'meta'
            ORDER BY uuid""";

    private static final String SELECT_BY_TYPE_NAME_WILDCARD_SQL = """
            SELECT DISTINCT
              uuid,
              name
            FROM doc
            WHERE type = ?
            AND name COLLATE utf8mb4_0900_as_cs LIKE ?
            AND ext = 'meta'
            ORDER BY uuid""";

    private static final String SELECT_ID_BY_TYPE_UUID_SQL = """
            SELECT
              id
            FROM doc
            WHERE type = ?
            AND uuid = ?
            LIMIT 1""";

    private static final String UPDATE_SQL = """
            UPDATE doc
            SET
              type = ?,
              uuid = ?,
              name = ?,
              ext = ?,
              data = ?
            WHERE id = ?""";

    private static final String INSERT_SQL = """
            INSERT INTO doc (
              type,
              uuid,
              name,
              ext,
              data)
            VALUES (?, ?, ?, ?, ?)""";

    private static final String FIND_BY_EMBEDDED_IN = """
            SELECT d.uuid, d.type, d.name
            FROM doc d
            CROSS JOIN JSON_TABLE(
                CAST(data AS CHAR),
                '$' COLUMNS(
                    parent_uuid VARCHAR(50) PATH '$.embeddedIn.uuid'
                )
            ) AS jt
            WHERE data IS NOT NULL
              AND LENGTH(data) > 0
              AND JSON_VALID(CAST(data AS CHAR)) = 1
              and ext = 'meta'
              and parent_uuid = ?""";

    // --- Cross-type query SQL ---

    private static final String READ_INFO_BY_UUID_SQL = """
            SELECT
              data
            FROM doc
            WHERE uuid = ?
            AND ext = 'meta'""";

    private static final String EXISTS_BY_UUID_SQL = """
            SELECT 1
            FROM doc
            WHERE uuid = ?
            LIMIT 1""";

    private final DataSource dataSource;

    @Inject
    DBPersistence(final DocStoreDbConnProvider dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean exists(final DocRef docRef) {
        try (final Connection connection = dataSource.getConnection()) {
            final Long id = getId(connection, docRef);
            return id != null;
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public Optional<String> getName(final DocRef docRef) {
        try (final Connection connection = dataSource.getConnection()) {
            try (final PreparedStatement preparedStatement = connection
                    .prepareStatement(SELECT_NAME_BY_TYPE_UUID_SQL)) {
                preparedStatement.setString(1, docRef.getType());
                preparedStatement.setString(2, docRef.getUuid());
                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        return Optional.of(resultSet.getString(1));
                    }
                }
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        return Optional.empty();
    }

//    @Override
//    public List<DocRef> findByName(final String nameFilter) {
//        final List<DocRef> list = new ArrayList<>();
//
//        try (final Connection connection = dataSource.getConnection()) {
//            try (final PreparedStatement preparedStatement = connection
//                    .prepareStatement(SELECT_BY_NAME_EQUALS_SQL)) {
//                preparedStatement.setString(1, nameFilter);
//
//                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
//                    while (resultSet.next()) {
//                        final String type = resultSet.getString(1);
//                        final String uuid = resultSet.getString(2);
//                        final String name = resultSet.getString(3);
//                        list.add(new DocRef(type, uuid, name));
//                    }
//                }
//            }
//        } catch (final SQLException e) {
//            LOGGER.debug(e.getMessage(), e);
//            throw new RuntimeException(e.getMessage(), e);
//        }
//
//        return list;
//    }

    @Override
    public ImportExportDocument read(final DocRef docRef) {
        final ImportExportDocument importExportDocument = new ImportExportDocument();
        try (final Connection connection = dataSource.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(SELECT_BY_TYPE_UUID_SQL)) {
                preparedStatement.setString(1, docRef.getType());
                preparedStatement.setString(2, docRef.getUuid());

                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        final ImportExportAsset asset =
                                new ByteArrayImportExportAsset(
                                        resultSet.getString(1),
                                        resultSet.getBytes(2));
                        importExportDocument.addExtAsset(asset);
                    }
                }
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        if (importExportDocument.getExtAssets().isEmpty()) {
            throw new DocumentNotFoundException(docRef);
        }

        return importExportDocument;
    }

    @Override
    public void write(final DocRef docRef, final boolean update, final ImportExportDocument importExportDocument) {
        try (final Connection connection = dataSource.getConnection()) {
            // Get the auto commit status.
            final boolean autoCommit = connection.getAutoCommit();

            // Turn auto commit off.
            connection.setAutoCommit(false);

            try {
                final boolean exists = getId(connection, docRef) != null;
                if (update) {
                    if (!exists) {
                        throw new RuntimeException("Document does not exist with uuid=" + docRef.getUuid());
                    }
                } else if (exists) {
                    throw new RuntimeException("Document already exists with uuid=" + docRef.getUuid());
                }

                // Get existing ids.
                final Map<String, Long> existingExtensionToIdMap = getExtensionIds(docRef);
                for (final ImportExportAsset asset : importExportDocument.getExtAssets()) {
                    if (update) {
                        final Long existingId = existingExtensionToIdMap.get(asset.getKey());
                        if (existingId != null) {
                            update(connection, existingId, docRef, asset.getKey(), asset.getInputData());
                        } else {
                            save(connection, docRef, asset.getKey(), asset.getInputData());
                        }
                    } else {
                        save(connection, docRef, asset.getKey(), asset.getInputData());
                    }
                }

                // Remove any old extensions.
                existingExtensionToIdMap.forEach((ext, id) -> {
                    if (!importExportDocument.containsExtAssetWithKey(ext)) {
                        LOGGER.debug("Deleting doc entry {}", id);
                        delete(id);
                    }
                });

                // Commit all of the changes.
                connection.commit();

            } catch (final IOException | RuntimeException e) {
                // Rollback any changes.
                connection.rollback();

                LOGGER.error(e.getMessage(), e);
                throw new RuntimeException(e.getMessage(), e);
            } finally {
                // Turn auto commit back on.
                connection.setAutoCommit(autoCommit);
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private Map<String, Long> getExtensionIds(final DocRef docRef) {
        // Get existing ids.
        final Map<String, Long> existingExtensionToIdMap = new HashMap<>();
        try (final Connection connection = dataSource.getConnection()) {
            try (final PreparedStatement preparedStatement = connection
                    .prepareStatement(SELECT_EXTENSIONS_BY_TYPE_UUID_SQL)) {
                preparedStatement.setString(1, docRef.getType());
                preparedStatement.setString(2, docRef.getUuid());

                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        existingExtensionToIdMap.put(resultSet.getString(2), resultSet.getLong(1));
                    }
                }
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
        return existingExtensionToIdMap;
    }

    private void delete(final long id) {
        try (final Connection connection = dataSource.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(DELETE_BY_ID_SQL)) {
                preparedStatement.setLong(1, id);
                preparedStatement.execute();
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public void delete(final DocRef docRef) {
        try (final Connection connection = dataSource.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(DELETE_BY_UUID_SQL)) {
                preparedStatement.setString(1, docRef.getType());
                preparedStatement.setString(2, docRef.getUuid());

                preparedStatement.execute();
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public List<DocRef> list(final String type) {
        final List<DocRef> list = new ArrayList<>();

        try (final Connection connection = dataSource.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(LIST_BY_TYPE_SQL)) {
                preparedStatement.setString(1, type);

                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        final String uuid = resultSet.getString(1);
                        final String name = resultSet.getString(2);
                        list.add(new DocRef(type, uuid, name));
                    }
                }
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        return list;
    }

    @Override
    public List<DocRef> find(final String type,
                             final String nameFilter,
                             final boolean allowWildCards) {
        final List<DocRef> list = new ArrayList<>();

        final String nameFilterSqlValue = allowWildCards
                ? PatternUtil.createSqlLikeStringFromWildCardFilter(nameFilter)
                : nameFilter;
        final String sql = allowWildCards
                ? SELECT_BY_TYPE_NAME_WILDCARD_SQL
                : SELECT_BY_TYPE_NAME_EQUALS_SQL;

        try (final Connection connection = dataSource.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setString(1, type);
                preparedStatement.setString(2, nameFilterSqlValue);

                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        final String uuid = resultSet.getString(1);
                        final String name = resultSet.getString(2);
                        list.add(new DocRef(type, uuid, name));
                    }
                }
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        return list;
    }

    @Override
    public List<DocRef> find(final String type,
                             final List<String> nameFilters,
                             final boolean allowWildCards) {
        if (nameFilters == null || nameFilters.isEmpty()) {
            return Collections.emptyList();
        }

        final List<DocRef> list = new ArrayList<>();

        final String sql;
        final List<String> nameFilterSqlValues;
        if (allowWildCards) {
            nameFilterSqlValues = nameFilters.stream()
                    .map(PatternUtil::createSqlLikeStringFromWildCardFilter)
                    .collect(Collectors.toList());
            final String orConditions = nameFilterSqlValues.stream()
                    .map(v -> "name COLLATE utf8mb4_0900_as_cs LIKE ?")
                    .collect(Collectors.joining(" OR "));
            sql = "SELECT DISTINCT uuid, name FROM doc WHERE type = ? AND (" +
                  orConditions + ") ORDER BY uuid";
        } else {
            nameFilterSqlValues = nameFilters;
            final String placeholders = nameFilterSqlValues.stream()
                    .map(v -> "?")
                    .collect(Collectors.joining(", "));
            sql = "SELECT DISTINCT uuid, name FROM doc WHERE type = ? AND name COLLATE utf8mb4_0900_as_cs IN (" +
                  placeholders + ") ORDER BY uuid";
        }

        try (final Connection connection = dataSource.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setString(1, type);
                for (int i = 0; i < nameFilterSqlValues.size(); i++) {
                    preparedStatement.setString(i + 2, nameFilterSqlValues.get(i));
                }

                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        final String uuid = resultSet.getString(1);
                        final String name = resultSet.getString(2);
                        list.add(new DocRef(type, uuid, name));
                    }
                }
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        return list;
    }

    @Override
    public List<DocRef> find(final Collection<String> types,
                             final List<String> nameFilters,
                             final boolean allowWildCards) {
        if (nameFilters == null || nameFilters.isEmpty()) {
            return Collections.emptyList();
        }

        final List<DocRef> list = new ArrayList<>();
        final boolean allTypes = NullSafe.isEmptyCollection(types);

        // Build dynamic SQL
        final StringBuilder sqlBuilder =
                new StringBuilder("SELECT DISTINCT type, uuid, name FROM doc WHERE ext = 'meta'");
        final List<String> params = new ArrayList<>();

        // Type filter
        if (!allTypes) {
            final String typePlaceholders = types.stream()
                    .map(t -> "?")
                    .collect(Collectors.joining(", "));
            sqlBuilder.append(" AND type IN (").append(typePlaceholders).append(")");
            params.addAll(types);
        }

        // Name filter
        final List<String> nameFilterSqlValues;
        if (allowWildCards) {
            nameFilterSqlValues = nameFilters.stream()
                    .map(PatternUtil::createSqlLikeStringFromWildCardFilter)
                    .collect(Collectors.toList());
            final String orConditions = nameFilterSqlValues.stream()
                    .map(v -> "name COLLATE utf8mb4_0900_as_cs LIKE ?")
                    .collect(Collectors.joining(" OR "));
            sqlBuilder.append(" AND (").append(orConditions).append(")");
        } else {
            nameFilterSqlValues = nameFilters;
            final String placeholders = nameFilterSqlValues.stream()
                    .map(v -> "?")
                    .collect(Collectors.joining(", "));
            sqlBuilder.append(" AND name COLLATE utf8mb4_0900_as_cs IN (").append(placeholders).append(")");
        }
        params.addAll(nameFilterSqlValues);

        sqlBuilder.append(" ORDER BY uuid");
        final String sql = sqlBuilder.toString();

        try (final Connection connection = dataSource.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                for (int i = 0; i < params.size(); i++) {
                    preparedStatement.setString(i + 1, params.get(i));
                }

                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        final String type = resultSet.getString(1);
                        final String uuid = resultSet.getString(2);
                        final String name = resultSet.getString(3);
                        list.add(new DocRef(type, uuid, name));
                    }
                }
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        return list;
    }

    @Override
    public ResultPage<DocAuditEntry> getAuditInfo(final DocRef docRef) {
        try (final Connection connection = dataSource.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(READ_INFO_BY_UUID_SQL)) {
                preparedStatement.setString(1, docRef.getUuid());

                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        final byte[] data = resultSet.getBytes(1);

                        // TODO : @66 This is just a temporary way to create audit records for now until we add the
                        //  doc_audit table.
                        // Deserialise only the common AbstractDoc fields
                        final GenericDoc document = JsonUtil.readValue(data, GenericDoc.class);
                        final List<DocAuditEntry> list = new ArrayList<>();
                        list.add(new DocAuditEntry(document.getCreateTimeMs(),
                                new DocAuditUser(null, document.getCreateUser()), AuditAction.CREATE));
                        list.add(new DocAuditEntry(document.getUpdateTimeMs(),
                                new DocAuditUser(null, document.getUpdateUser()), AuditAction.UPDATE));
                        return ResultPage.createUnboundedList(list);
                    }
                }
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        return ResultPage.empty();
    }

    @Override
    public List<DocRef> list(final Collection<String> types) {
        if (NullSafe.isEmptyCollection(types)) {
            return Collections.emptyList();
        }

        final List<DocRef> list = new ArrayList<>();
        final String placeholders = types.stream()
                .map(t -> "?")
                .collect(Collectors.joining(", "));
        final String sql = "SELECT DISTINCT type, uuid, name FROM doc WHERE type IN (" +
                           placeholders + ") AND ext = 'meta' ORDER BY uuid";

        try (final Connection connection = dataSource.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                int idx = 1;
                for (final String type : types) {
                    preparedStatement.setString(idx++, type);
                }

                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        final String type = resultSet.getString(1);
                        final String uuid = resultSet.getString(2);
                        final String name = resultSet.getString(3);
                        list.add(new DocRef(type, uuid, name));
                    }
                }
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        return list;
    }

//    @Override
//    public boolean exists(final String uuid) {
//        try (final Connection connection = dataSource.getConnection()) {
//            try (final PreparedStatement preparedStatement = connection.prepareStatement(EXISTS_BY_UUID_SQL)) {
//                preparedStatement.setString(1, uuid);
//
//                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
//                    return resultSet.next();
//                }
//            }
//        } catch (final SQLException e) {
//            LOGGER.debug(e.getMessage(), e);
//            throw new RuntimeException(e.getMessage(), e);
//        }
//    }

    @Override
    public RWLockFactory getLockFactory() {
        return LOCK_FACTORY;
    }

    private Long getId(final Connection connection, final DocRef docRef) {
        try (final PreparedStatement preparedStatement = connection.prepareStatement(SELECT_ID_BY_TYPE_UUID_SQL)) {
            preparedStatement.setString(1, docRef.getType());
            preparedStatement.setString(2, docRef.getUuid());

            try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong(1);
                }
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        return null;
    }

    private void save(final Connection connection, final DocRef docRef, final String ext, final byte[] bytes) {
        try (final PreparedStatement preparedStatement = connection.prepareStatement(INSERT_SQL)) {
            preparedStatement.setString(1, docRef.getType());
            preparedStatement.setString(2, docRef.getUuid());
            preparedStatement.setString(3, docRef.getName());
            preparedStatement.setString(4, ext);
            preparedStatement.setBytes(5, bytes);

            preparedStatement.execute();
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void update(final Connection connection,
                        final Long id,
                        final DocRef docRef,
                        final String ext,
                        final byte[] bytes) {
        try (final PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_SQL)) {
            preparedStatement.setString(1, docRef.getType());
            preparedStatement.setString(2, docRef.getUuid());
            preparedStatement.setString(3, docRef.getName());
            preparedStatement.setString(4, ext);
            preparedStatement.setBytes(5, bytes);
            preparedStatement.setLong(6, id);

            preparedStatement.execute();
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public List<DocRef> findDocRefsEmbeddedIn(final DocRef parent) {
        final List<DocRef> list = new ArrayList<>();

        try (final Connection connection = dataSource.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(FIND_BY_EMBEDDED_IN)) {
                preparedStatement.setString(1, parent.getUuid());

                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        final String uuid = resultSet.getString(1);
                        final String type = resultSet.getString(2);
                        final String name = resultSet.getString(3);
                        list.add(new DocRef(type, uuid, name));
                    }
                }
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        return list;
    }
}
