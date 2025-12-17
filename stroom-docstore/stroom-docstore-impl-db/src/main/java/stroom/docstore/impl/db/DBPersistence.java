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

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.docstore.api.RWLockFactory;
import stroom.docstore.impl.Persistence;
import stroom.util.string.PatternUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            ORDER BY uuid""";

    private static final String SELECT_BY_TYPE_NAME_EQUALS_SQL = """
            SELECT DISTINCT
              uuid,
              name
            FROM doc
            WHERE type = ?
            AND name = ?
            ORDER BY uuid""";

    private static final String SELECT_BY_TYPE_NAME_WILDCARD_SQL = """
            SELECT DISTINCT
              uuid,
              name
            FROM doc
            WHERE type = ?
            AND name like ?
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

    @Override
    public Map<String, byte[]> read(final DocRef docRef) {
        final Map<String, byte[]> data = new HashMap<>();
        try (final Connection connection = dataSource.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(SELECT_BY_TYPE_UUID_SQL)) {
                preparedStatement.setString(1, docRef.getType());
                preparedStatement.setString(2, docRef.getUuid());

                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        data.put(resultSet.getString(1), resultSet.getBytes(2));
                    }
                }
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        if (data.isEmpty()) {
            throw new DocumentNotFoundException(docRef);
        }

        return data;
    }

    @Override
    public void write(final DocRef docRef, final boolean update, final Map<String, byte[]> data) {
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
                data.forEach((ext, bytes) -> {
                    if (update) {
                        final Long existingId = existingExtensionToIdMap.get(ext);
                        if (existingId != null) {
                            update(connection, existingId, docRef, ext, bytes);
                        } else {
                            save(connection, docRef, ext, bytes);
                        }
                    } else {
                        save(connection, docRef, ext, bytes);
                    }
                });

                // Remove any old extensions.
                existingExtensionToIdMap.forEach((ext, id) -> {
                    if (!data.containsKey(ext)) {
                        LOGGER.debug("Deleting doc entry {}", id);
                        delete(id);
                    }
                });

                // Commit all of the changes.
                connection.commit();

            } catch (final RuntimeException e) {
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
}
