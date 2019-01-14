package stroom.security.impl.db;

import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;

import javax.inject.Inject;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

public class DocumentPermissionDaoImpl implements DocumentPermissionDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentPermissionDaoImpl.class);

    private final ConnectionProvider connectionProvider;

    private static final Table<Record> TABLE = table("doc_permission");
    private static final Field<String> FIELD_USER_UUID = field("user_uuid", String.class);
    private static final Field<String> FIELD_DOC_TYPE = field("doc_type", String.class);
    private static final Field<String> FIELD_DOC_UUID = field("doc_uuid", String.class);
    private static final Field<String> FIELD_PERMISSION = field("permission", String.class);

    @Inject
    public DocumentPermissionDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public Set<String> getPermissionsForDocumentForUser(final DocRef document,
                                                        final String userUuid) {
        try (final Connection connection = connectionProvider.getConnection()) {
            return DSL.using(connection, SQLDialect.MYSQL)
                    .select()
                    .from(TABLE)
                    .where(FIELD_USER_UUID.equal(userUuid))
                    .and(FIELD_DOC_TYPE.equal(document.getType()))
                    .and(FIELD_DOC_UUID.equal(document.getUuid()))
                    .fetchSet(FIELD_PERMISSION);
        } catch (final SQLException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw new SecurityException(e.getMessage(), e);
        }
    }

    @Override
    public DocumentPermissionJooq getPermissionsForDocument(final DocRef document) {
        try (final Connection connection = connectionProvider.getConnection()) {
            final DocumentPermissionJooq.Builder permissions = new DocumentPermissionJooq.Builder()
                    .docType(document.getType())
                    .docUuid(document.getUuid());

            DSL.using(connection, SQLDialect.MYSQL)
                    .select(FIELD_USER_UUID, FIELD_PERMISSION)
                    .from(TABLE)
                    .where(FIELD_DOC_TYPE.equal(document.getType()))
                    .and(FIELD_DOC_UUID.equal(document.getUuid()))
                    .fetch()
                    .forEach(r -> permissions.permission(r.get(FIELD_USER_UUID), r.get(FIELD_PERMISSION)));

            return permissions.build();
        } catch (final SQLException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw new SecurityException(e.getMessage(), e);
        }
    }

    @Override
    public void addPermission(final String userUuid,
                              final DocRef document,
                              final String permission) {
        try (final Connection connection = connectionProvider.getConnection()) {
            DSL.using(connection, SQLDialect.MYSQL)
                    .insertInto(TABLE)
                    .columns(FIELD_USER_UUID, FIELD_DOC_TYPE, FIELD_DOC_UUID, FIELD_PERMISSION)
                    .values(userUuid, document.getType(), document.getUuid(), permission)
                    .execute();
        } catch (final SQLException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw new SecurityException(e.getMessage(), e);
        }
    }

    @Override
    public void removePermission(final String userUuid,
                                 final DocRef document,
                                 final String permission) {
        try (final Connection connection = connectionProvider.getConnection()) {
            DSL.using(connection, SQLDialect.MYSQL)
                    .deleteFrom(TABLE)
                    .where(FIELD_USER_UUID.equal(userUuid))
                    .and(FIELD_DOC_TYPE.equal(document.getType()))
                    .and(FIELD_DOC_UUID.equal(document.getUuid()))
                    .and(FIELD_PERMISSION.equal(permission))
                    .execute();
        } catch (final SQLException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw new SecurityException(e.getMessage(), e);
        }
    }

    @Override
    public void clearDocumentPermissions(final DocRef document) {
        try (final Connection connection = connectionProvider.getConnection()) {
            DSL.using(connection, SQLDialect.MYSQL)
                    .deleteFrom(TABLE)
                    .where(FIELD_DOC_TYPE.equal(document.getType()))
                    .and(FIELD_DOC_UUID.equal(document.getUuid()))
                    .execute();
        } catch (final SQLException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw new SecurityException(e.getMessage(), e);
        }
    }

    @Override
    public void clearUserPermissions(final String userUuid) {
        try (final Connection connection = connectionProvider.getConnection()) {
            DSL.using(connection, SQLDialect.MYSQL)
                    .deleteFrom(TABLE)
                    .where(FIELD_USER_UUID.equal(userUuid))
                    .execute();
        } catch (final SQLException | RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw new SecurityException(e.getMessage(), e);
        }
    }
}
