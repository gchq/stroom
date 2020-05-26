package stroom.security.impl.db;

import stroom.db.util.JooqUtil;
import stroom.security.impl.DocumentPermissionDao;
import stroom.security.impl.UserDocumentPermissions;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static stroom.security.impl.db.jooq.tables.DocPermission.DOC_PERMISSION;

public class DocumentPermissionDaoImpl implements DocumentPermissionDao {
    private final SecurityDbConnProvider securityDbConnProvider;

    @Inject
    public DocumentPermissionDaoImpl(final SecurityDbConnProvider securityDbConnProvider) {
        this.securityDbConnProvider = securityDbConnProvider;
    }

    @Override
    public Set<String> getPermissionsForDocumentForUser(final String docUuid,
                                                        final String userUuid) {
        return JooqUtil.contextResult(securityDbConnProvider, context -> context
                .select()
                .from(DOC_PERMISSION)
                .where(DOC_PERMISSION.USER_UUID.eq(userUuid))
                .and(DOC_PERMISSION.DOC_UUID.eq(docUuid))
                .fetchSet(DOC_PERMISSION.PERMISSION)
        );
    }

    @Override
    public Map<String, Set<String>> getPermissionsForDocument(final String docUuid) {
        final Map<String, Set<String>> permissions = new HashMap<>();

        JooqUtil.context(securityDbConnProvider, context -> context
                .select()
                .from(DOC_PERMISSION)
                .where(DOC_PERMISSION.DOC_UUID.eq(docUuid))
                .fetch()
                .forEach(r -> {
                    permissions.computeIfAbsent(r.get(DOC_PERMISSION.USER_UUID), k -> new HashSet<>()).add(r.get(DOC_PERMISSION.PERMISSION));
                })
        );

        return permissions;
    }

    @Override
    public UserDocumentPermissions getPermissionsForUser(final String userUuid) {
        final UserDocumentPermissions userDocumentPermissions = new UserDocumentPermissions();

        JooqUtil.context(securityDbConnProvider, context -> context
                .selectDistinct(DOC_PERMISSION.DOC_UUID, DOC_PERMISSION.PERMISSION)
                .from(DOC_PERMISSION)
                .where(DOC_PERMISSION.USER_UUID.eq(userUuid))
                .fetch()
                .forEach(r -> userDocumentPermissions.addPermission(r.get(DOC_PERMISSION.DOC_UUID), r.get(DOC_PERMISSION.PERMISSION)))
        );

        return userDocumentPermissions;
    }

    @Override
    public void addPermission(final String docRefUuid,
                              final String userUuid,
                              final String permission) {
        JooqUtil.context(securityDbConnProvider, context -> context
                .insertInto(DOC_PERMISSION)
                .set(DOC_PERMISSION.DOC_UUID, docRefUuid)
                .set(DOC_PERMISSION.USER_UUID, userUuid)
                .set(DOC_PERMISSION.PERMISSION, permission)
                .onDuplicateKeyIgnore()
                .execute());
    }

    @Override
    public void removePermission(final String docRefUuid,
                                 final String userUuid,
                                 final String permission) {
        JooqUtil.context(securityDbConnProvider, context -> context
                .deleteFrom(DOC_PERMISSION)
                .where(DOC_PERMISSION.USER_UUID.eq(userUuid))
                .and(DOC_PERMISSION.DOC_UUID.equal(docRefUuid))
                .and(DOC_PERMISSION.PERMISSION.eq(permission))
                .execute()
        );
    }

    @Override
    public void clearDocumentPermissionsForUser(String docRefUuid, String userUuid) {
        JooqUtil.context(securityDbConnProvider, context -> context
                .deleteFrom(DOC_PERMISSION)
                .where(DOC_PERMISSION.USER_UUID.eq(userUuid))
                .and(DOC_PERMISSION.DOC_UUID.equal(docRefUuid))
                .execute()
        );
    }

    @Override
    public void clearDocumentPermissions(final String docRefUuid) {
        JooqUtil.context(securityDbConnProvider, context -> context
                .deleteFrom(DOC_PERMISSION)
                .where(DOC_PERMISSION.DOC_UUID.equal(docRefUuid))
                .execute()
        );
    }
}
