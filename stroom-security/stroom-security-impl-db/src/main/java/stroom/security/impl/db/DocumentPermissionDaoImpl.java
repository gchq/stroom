package stroom.security.impl.db;

import org.jooq.Record;
import stroom.docref.DocRef;
import stroom.security.dao.DocumentPermissionDao;
import stroom.security.impl.db.tables.records.DocPermissionRecord;
import stroom.security.shared.DocumentPermissionJooq;
import stroom.util.jooq.JooqUtil;

import javax.inject.Inject;
import java.util.Set;

import static stroom.security.impl.db.tables.DocPermission.DOC_PERMISSION;
import static stroom.security.impl.db.tables.StroomUser.STROOM_USER;

public class DocumentPermissionDaoImpl implements DocumentPermissionDao {

    private final ConnectionProvider connectionProvider;

    @Inject
    public DocumentPermissionDaoImpl(final ConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    @Override
    public Set<String> getPermissionsForDocumentForUser(final DocRef document,
                                                        final String userUuid) {
        return JooqUtil.contextResult(connectionProvider, context ->
                context.select()
                        .from(DOC_PERMISSION)
                        .where(DOC_PERMISSION.USER_UUID.eq(userUuid))
                        .and(DOC_PERMISSION.DOC_TYPE.eq(document.getType()))
                        .and(DOC_PERMISSION.DOC_UUID.eq(document.getUuid()))
                        .fetchSet(DOC_PERMISSION.PERMISSION)
        );
    }

    @Override
    public DocumentPermissionJooq getPermissionsForDocument(final DocRef document) {

        final DocumentPermissionJooq.Builder permissions = new DocumentPermissionJooq.Builder()
                .docType(document.getType())
                .docUuid(document.getUuid());

        JooqUtil.context(connectionProvider, context ->
                context.select()
                        .from(DOC_PERMISSION)
                        .where(DOC_PERMISSION.DOC_TYPE.eq(document.getType()))
                        .and(DOC_PERMISSION.DOC_UUID.eq(document.getUuid()))
                        .fetch()
                        .forEach(r -> permissions.permission(r.get(DOC_PERMISSION.USER_UUID), r.get(DOC_PERMISSION.PERMISSION)))
        );

        return permissions.build();
    }

    @Override
    public void addPermission(final String userUuid,
                              final DocRef document,
                              final String permission) {
        JooqUtil.context(connectionProvider, context -> {
            final Record user = context.fetchOne(STROOM_USER, STROOM_USER.UUID.eq(userUuid));
            if (null == user) {
                throw new SecurityException(String.format("Could not find user: %s", userUuid));
            }

            final DocPermissionRecord r = context.newRecord(DOC_PERMISSION);
            r.setUserUuid(userUuid);
            r.setPermission(permission);
            r.setDocType(document.getType());
            r.setDocUuid(document.getUuid());
            r.store();
        });
    }

    @Override
    public void removePermission(final String userUuid,
                                 final DocRef document,
                                 final String permission) {
        JooqUtil.context(connectionProvider, context ->
                context.deleteFrom(DOC_PERMISSION)
                        .where(DOC_PERMISSION.USER_UUID.eq(userUuid))
                        .and(DOC_PERMISSION.DOC_TYPE.equal(document.getType()))
                        .and(DOC_PERMISSION.DOC_UUID.equal(document.getUuid()))
                        .and(DOC_PERMISSION.PERMISSION.eq(permission))
                        .execute()
        );
    }

    @Override
    public void clearDocumentPermissions(final DocRef document) {
        JooqUtil.context(connectionProvider, context ->
                context.deleteFrom(DOC_PERMISSION)
                        .where(DOC_PERMISSION.DOC_TYPE.equal(document.getType()))
                        .and(DOC_PERMISSION.DOC_UUID.equal(document.getUuid()))
                        .execute()
        );
    }

    @Override
    public void clearUserPermissions(final String userUuid) {
        JooqUtil.context(connectionProvider, context ->
                context.deleteFrom(DOC_PERMISSION)
                        .where(DOC_PERMISSION.USER_UUID.eq(userUuid))
                        .execute()
        );
    }
}
