package stroom.security.impl.db;

import stroom.db.util.JooqUtil;
import stroom.security.impl.BasicDocPermissions;
import stroom.security.impl.DocumentPermissionDao;
import stroom.security.impl.UserDocumentPermissions;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static stroom.security.impl.db.jooq.tables.DocPermission.DOC_PERMISSION;

public class DocumentPermissionDaoImpl implements DocumentPermissionDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocumentPermissionDaoImpl.class);

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
    public BasicDocPermissions getPermissionsForDocument(final String docUuid) {
        final BasicDocPermissions docPermissions = new BasicDocPermissions(docUuid);
//        final Map<String, Set<String>> permissions = new HashMap<>();

        JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select()
                        .from(DOC_PERMISSION)
                        .where(DOC_PERMISSION.DOC_UUID.eq(docUuid))
                        .fetch())
                .forEach(rec ->
                        docPermissions.add(
                                rec.get(DOC_PERMISSION.USER_UUID),
                                rec.get(DOC_PERMISSION.PERMISSION)));

        return docPermissions;
    }

    @Override
    public Map<String, BasicDocPermissions> getPermissionsForDocuments(final Collection<String> docUuids) {
        final Map<String, BasicDocPermissions> docUuidToDocPermissionsMap = new HashMap<>();

        JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .select()
                        .from(DOC_PERMISSION)
                        .where(DOC_PERMISSION.DOC_UUID.in(docUuids))
                        .fetch())
                .forEach(rec -> {
                    final String userOrGroupUuid = rec.get(DOC_PERMISSION.USER_UUID);
                    final String docUuid = rec.get(DOC_PERMISSION.DOC_UUID);
                    final String permName = rec.get(DOC_PERMISSION.PERMISSION);

                    docUuidToDocPermissionsMap.computeIfAbsent(docUuid, BasicDocPermissions::new)
                            .add(userOrGroupUuid, permName);
                });

        return docUuidToDocPermissionsMap;
    }

    @Override
    public Set<String> getDocumentOwnerUuids(final String docUuid) {
        return new HashSet<>(JooqUtil.contextResult(securityDbConnProvider, context -> context
                .select(DOC_PERMISSION.USER_UUID)
                .from(DOC_PERMISSION)
                .where(DOC_PERMISSION.DOC_UUID.eq(docUuid))
                .and(DOC_PERMISSION.PERMISSION.eq(DocumentPermissionNames.OWNER))
                .fetch(DOC_PERMISSION.USER_UUID)));
    }

    @Override
    public UserDocumentPermissions getPermissionsForUser(final String userUuid) {
        final UserDocumentPermissions userDocumentPermissions = new UserDocumentPermissions();

        JooqUtil.contextResult(securityDbConnProvider, context -> context
                        .selectDistinct(DOC_PERMISSION.DOC_UUID, DOC_PERMISSION.PERMISSION)
                        .from(DOC_PERMISSION)
                        .where(DOC_PERMISSION.USER_UUID.eq(userUuid))
                        .fetch())
                .forEach(r -> userDocumentPermissions.addPermission(r.get(DOC_PERMISSION.DOC_UUID),
                        r.get(DOC_PERMISSION.PERMISSION)));

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
    public void removePermissions(final String docRefUuid,
                                  final String userUuid,
                                  final Set<String> permissions) {
        if (permissions != null && !permissions.isEmpty()) {
            Objects.requireNonNull(docRefUuid);
            Objects.requireNonNull(userUuid);
            JooqUtil.context(securityDbConnProvider, context -> context
                    .deleteFrom(DOC_PERMISSION)
                    .where(DOC_PERMISSION.USER_UUID.eq(userUuid))
                    .and(DOC_PERMISSION.DOC_UUID.equal(docRefUuid))
                    .and(DOC_PERMISSION.PERMISSION.in(permissions))
                    .execute()
            );
        }
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

    @Override
    public void setOwner(final String docRefUuid,
                         final String ownerUuid) {
        boolean didSucceed = false;
        try {
            int retriesRemaining = 10;
            while (!didSucceed && retriesRemaining-- > 0) {
                try {
                    JooqUtil.transaction(securityDbConnProvider, context -> {
                        // Delete all existing owners, except the one we want
                        final int delCount = context.deleteFrom(DOC_PERMISSION)
                                .where(DOC_PERMISSION.USER_UUID.notEqual(ownerUuid))
                                .and(DOC_PERMISSION.DOC_UUID.equal(docRefUuid))
                                .and(DOC_PERMISSION.PERMISSION.eq(DocumentPermissionNames.OWNER))
                                .execute();
                        final int insertCount = context.insertInto(DOC_PERMISSION)
                                .set(DOC_PERMISSION.DOC_UUID, docRefUuid)
                                .set(DOC_PERMISSION.USER_UUID, ownerUuid)
                                .set(DOC_PERMISSION.PERMISSION, DocumentPermissionNames.OWNER)
                                .onDuplicateKeyIgnore()
                                .execute();
                        LOGGER.debug("docRefUuid: {}, ownerUuid: {}, delCount: {}, insertCount: {}",
                                docRefUuid, ownerUuid, delCount, insertCount);
                    });
                    // Success so break out of the loop
                    didSucceed = true;
                } catch (Exception e) {
                    LOGGER.debug("Error setting owner of document {} to user/group {}", docRefUuid, ownerUuid, e);
                    if (e.getMessage().contains("Deadlock")) {
                        // If we get a deadlock then have a tiny sleep to give the other thread a chance to finish
                        // and go round again
                        ThreadUtil.sleepIgnoringInterrupts(20);
                    } else {
                        throw e;
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message("Error setting owner of document {} to user/group {}",
                    docRefUuid, ownerUuid), e);
        }

        if (!didSucceed) {
            throw new RuntimeException(LogUtil.message("Error setting owner of document {} to user/group {} " +
                            "after 10 tries", docRefUuid, ownerUuid));
        }
    }
}
