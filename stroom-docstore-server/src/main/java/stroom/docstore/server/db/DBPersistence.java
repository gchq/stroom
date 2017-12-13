package stroom.docstore.server.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.docstore.server.Persistence;
import stroom.docstore.server.RWLockFactory;
import stroom.query.api.v2.DocRef;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Component
@Primary
@Transactional
public class DBPersistence implements Persistence {
    private static final Logger LOGGER = LoggerFactory.getLogger(DBPersistence.class);

    private static final RWLockFactory LOCK_FACTORY = new NoLockFactory();

    private final DataSource dataSource;

    @Inject
    DBPersistence(final DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean exists(final DocRef docRef) {
        final Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            try (final PreparedStatement preparedStatement = connection.prepareStatement("SELECT id FROM doc WHERE type = ? AND uuid = ?")) {
                preparedStatement.setString(1, docRef.getType());
                preparedStatement.setString(2, docRef.getUuid());

                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        return true;
                    }
                }
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }

        return false;
    }

    @Override
    public InputStream getInputStream(final DocRef docRef) {
        final DocEntity doc = load(docRef);
        return new ByteArrayInputStream(doc.getData());
    }

    @Override
    public OutputStream getOutputStream(final DocRef docRef, final boolean update) {
        return new DBOutputStream(docRef, update);
    }

    @Override
    public void delete(final DocRef docRef) {
        final Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            try (final PreparedStatement preparedStatement = connection.prepareStatement("DELETE FROM doc WHERE type = ? AND uuid = ?")) {
                preparedStatement.setString(1, docRef.getType());
                preparedStatement.setString(2, docRef.getUuid());

                preparedStatement.execute();
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    @Override
    public List<DocRef> list(final String type) {
        final List<DocRef> list = new ArrayList<>();

        final Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            try (final PreparedStatement preparedStatement = connection.prepareStatement("SELECT uuid FROM doc WHERE type = ? ORDER BY id")) {
                preparedStatement.setString(1, type);

                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        final String uuid = resultSet.getString(1);
                        list.add(new DocRef(type, uuid));
                    }
                }
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }

        return list;
    }

    @Override
    public RWLockFactory getLockFactory() {
        return LOCK_FACTORY;
    }

    private DocEntity load(final DocRef docRef) {
        final Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            try (final PreparedStatement preparedStatement = connection.prepareStatement("SELECT id, type, uuid, name, data FROM doc WHERE type = ? AND uuid = ?")) {
                preparedStatement.setString(1, docRef.getType());
                preparedStatement.setString(2, docRef.getUuid());

                try (final ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        final long id = resultSet.getLong(1);
                        final String type = resultSet.getString(2);
                        final String uid = resultSet.getString(3);
                        final String name = resultSet.getString(4);
                        final byte[] data = resultSet.getBytes(5);
                        return new DocEntity(id, type, uid, name, data);
                    }
                }
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }

        throw new RuntimeException("Document not found " + docRef);
    }

    private void save(final DocEntity entity) {
        final Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            try (final PreparedStatement preparedStatement = connection.prepareStatement("INSERT INTO doc (type, uuid, name, data) VALUES (?, ?, ?, ?)")) {
                preparedStatement.setString(1, entity.getType());
                preparedStatement.setString(2, entity.getUuid());
                preparedStatement.setString(3, entity.getName());
                preparedStatement.setBytes(4, entity.getData());

                preparedStatement.execute();
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private void update(final DocEntity entity) {
        final Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            try (final PreparedStatement preparedStatement = connection.prepareStatement("UPDATE doc SET type = ?, uuid = ?, name = ?, data = ? WHERE id = ?")) {
                preparedStatement.setString(1, entity.getType());
                preparedStatement.setString(2, entity.getUuid());
                preparedStatement.setString(3, entity.getName());
                preparedStatement.setBytes(4, entity.getData());
                preparedStatement.setLong(5, entity.getId());

                preparedStatement.execute();
            }
        } catch (final SQLException e) {
            LOGGER.debug(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private class DBOutputStream extends ByteArrayOutputStream {
        private final DocRef docRef;
        private final boolean update;
        private volatile boolean closed;

        public DBOutputStream(final DocRef docRef, final boolean update) {
            this.docRef = docRef;
            this.update = update;
        }

        @Override
        public synchronized void close() throws IOException {
            super.close();
            if (!closed) {
                closed = true;

                if (update) {
                    final DocEntity entity = load(docRef);
                    entity.setData(toByteArray());
                    update(entity);
                } else {
                    final DocEntity entity = new DocEntity(-1, docRef.getType(), docRef.getUuid(), docRef.getName(), toByteArray());
                    save(entity);
                }
            }
        }
    }
}