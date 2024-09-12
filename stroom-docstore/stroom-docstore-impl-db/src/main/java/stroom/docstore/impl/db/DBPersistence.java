package stroom.docstore.impl.db;

import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.docstore.api.RWLockFactory;
import stroom.docstore.impl.Persistence;
import stroom.util.logging.LogUtil;
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
            final String sql = "" +
                    "SELECT " +
                    "  ext, " +
                    "  data " +
                    "FROM doc " +
                    "WHERE type = ? " +
                    "AND uuid = ?";
            try (final PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
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

        if (data.size() == 0) {
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

                data.forEach((ext, bytes) -> {
                    if (update) {
                        final Long existingId = getId(connection, docRef, ext);
                        if (existingId != null) {
                            update(connection, existingId, docRef, ext, bytes);
                        } else {
                            save(connection, docRef, ext, bytes);
                        }
                    } else {
                        save(connection, docRef, ext, bytes);
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

    @Override
    public void delete(final DocRef docRef) {
        final String sql = "" +
                "DELETE FROM doc " +
                "WHERE type = ? " +
                "AND uuid = ?";
        try (final Connection connection = dataSource.getConnection()) {
            try (final PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
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
            final String sql = "" +
                    "SELECT DISTINCT " +
                    "  uuid, " +
                    "  name " +
                    "FROM doc " +
                    "WHERE type = ? " +
                    "ORDER BY uuid";
            try (final PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
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
                             final boolean allowWildCards,
                             final boolean isCaseSensitive) {
        final List<DocRef> list = new ArrayList<>();

        final String nameFilterSqlValue = allowWildCards
                ? PatternUtil.createSqlLikeStringFromWildCardFilter(nameFilter)
                : nameFilter;

        // By default the collation in mysql is case-insensitive
        String condition = isCaseSensitive
                ? "collate " + JooqUtil.CASE_SENSITIVE_COLLATION_NAME + " "
                : "";

        condition = allowWildCards
                ? condition + "like"
                : condition + "=";

        try (final Connection connection = dataSource.getConnection()) {
            final String sql = LogUtil.message("""
                    SELECT DISTINCT
                      uuid,
                      name
                    FROM doc
                    WHERE type = ?
                    AND name {} ?
                    ORDER BY uuid""", condition);
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
        final String sql = "" +
                "SELECT " +
                "  id " +
                "FROM doc " +
                "WHERE type = ? " +
                "AND uuid = ? " +
                "LIMIT 1";
        try (final PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
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

    private Long getId(final Connection connection, final DocRef docRef, final String ext) {
        final String sql = "" +
                "SELECT " +
                "id " +
                "FROM doc " +
                "WHERE type = ? " +
                "AND uuid = ? " +
                "AND ext = ?";
        try (final PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, docRef.getType());
            preparedStatement.setString(2, docRef.getUuid());
            preparedStatement.setString(3, ext);

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
        final String sql = "" +
                "INSERT INTO doc (" +
                "  type, " +
                "  uuid, " +
                "  name, " +
                "  ext, " +
                "  data) " +
                "VALUES (?, ?, ?, ?, ?)";
        try (final PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
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
        final String sql = "" +
                "UPDATE doc " +
                "SET " +
                "  type = ?, " +
                "  uuid = ?, " +
                "  name = ?, " +
                "  ext = ?, " +
                "  data = ? " +
                "WHERE id = ?";
        try (final PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
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
