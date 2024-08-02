/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.security.impl;

import stroom.docref.DocRef;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.impl.event.AddDocumentCreatePermissionEvent;
import stroom.security.impl.event.ClearDocumentPermissionsEvent;
import stroom.security.impl.event.PermissionChangeEventBus;
import stroom.security.impl.event.RemoveDocumentCreatePermissionEvent;
import stroom.security.impl.event.SetPermissionEvent;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PermissionException;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Set;

@Singleton
public class DocumentPermissionServiceImpl implements DocumentPermissionService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocumentPermissionServiceImpl.class);

    private final DocumentPermissionDao documentPermissionDao;
    private final UserCache userCache;
    private final PermissionChangeEventBus permissionChangeEventBus;
    private final SecurityContext securityContext;

    @Inject
    DocumentPermissionServiceImpl(final DocumentPermissionDao documentPermissionDao,
                                  final UserCache userCache,
                                  final PermissionChangeEventBus permissionChangeEventBus,
                                  final SecurityContext securityContext) {
        this.documentPermissionDao = documentPermissionDao;
        this.userCache = userCache;
        this.permissionChangeEventBus = permissionChangeEventBus;
        this.securityContext = securityContext;
    }

    @Override
    public DocumentPermission getPermission(final DocRef docRef, final UserRef userRef) {
        checkGetPermission(docRef);
        return documentPermissionDao.getPermission(docRef.getUuid(), userRef.getUuid());
    }

    @Override
    public void setPermission(final DocRef docRef, final UserRef userRef, final DocumentPermission permission) {
        checkSetPermission(docRef);
        documentPermissionDao.setPermission(docRef.getUuid(), userRef.getUuid(), permission);
        SetPermissionEvent.fire(permissionChangeEventBus, userRef, docRef, permission);
    }

    @Override
    public void clearPermission(final DocRef docRef, final UserRef userRef) {
        checkSetPermission(docRef);
        documentPermissionDao.clearPermission(docRef.getUuid(), userRef.getUuid());
        SetPermissionEvent.fire(permissionChangeEventBus, userRef, docRef, null);
    }

//    @Override
//    public Set<String> getDocumentCreatePermissions(final DocRef docRef, final UserRef userRef) {
//        checkGetPermission(docRef);
//        return documentPermissionDao.getDocumentCreatePermissions(docRef.getUuid(), userRef.getUuid());
//    }


    @Override
    public void addDocumentCreatePermission(final DocRef docRef, final UserRef userRef, final String documentType) {
        if (isFolder(docRef)) {
            checkSetPermission(docRef);
            documentPermissionDao.addDocumentCreatePermission(docRef.getUuid(), userRef.getUuid(), documentType);
            AddDocumentCreatePermissionEvent.fire(permissionChangeEventBus, userRef, docRef, documentType);
        }
    }

    @Override
    public void removeDocumentCreatePermission(final DocRef docRef, final UserRef userRef, final String documentType) {
        if (isFolder(docRef)) {
            checkSetPermission(docRef);
            documentPermissionDao.removeDocumentCreatePermission(docRef.getUuid(), userRef.getUuid(), documentType);
            RemoveDocumentCreatePermissionEvent.fire(permissionChangeEventBus, userRef, docRef, documentType);
        }
    }

    @Override
    public void clearDocumentCreatePermissions(final DocRef docRef, final UserRef userRef) {
        if (isFolder(docRef)) {
            checkSetPermission(docRef);
            documentPermissionDao.clearDocumentCreatePermissions(docRef.getUuid(), userRef.getUuid());
            ClearDocumentPermissionsEvent.fire(permissionChangeEventBus, docRef);
        }
    }

    @Override
    public void removeAllDocumentPermissions(final DocRef docRef) {
        checkSetPermission(docRef);
        documentPermissionDao.removeAllDocumentPermissions(docRef.getUuid());
        if (isFolder(docRef)) {
            documentPermissionDao.removeAllDocumentCreatePermissions(docRef.getUuid());
        }
        ClearDocumentPermissionsEvent.fire(permissionChangeEventBus, docRef);
    }

    @Override
    public void removeAllDocumentPermissions(final Set<DocRef> docRefs) {
        docRefs.forEach(this::removeAllDocumentPermissions);
    }

    @Override
    public void copyDocumentPermissions(final DocRef sourceDocRef, final DocRef destDocRef) {
        checkSetPermission(destDocRef);
        documentPermissionDao.copyDocumentPermissions(sourceDocRef.getUuid(), destDocRef.getUuid());
        // Copy create permissions if the source is a folder.
        if (isFolder(sourceDocRef)) {
            documentPermissionDao.copyDocumentCreatePermissions(sourceDocRef.getUuid(), destDocRef.getUuid());
        }
        ClearDocumentPermissionsEvent.fire(permissionChangeEventBus, destDocRef);
    }

    private void checkGetPermission(final DocRef docRef) {
        if (!securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION) &&
                !securityContext.hasDocumentPermission(docRef, DocumentPermission.OWNER)) {
            throw new PermissionException(securityContext.getUserRef(), "You do not have permission to get " +
                    "permissions of " +
                    docRef.getDisplayValue());
        }
    }

    private void checkSetPermission(final DocRef docRef) {
        if (!securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION) &&
                !securityContext.hasDocumentPermission(docRef, DocumentPermission.OWNER)) {
            throw new PermissionException(securityContext.getUserRef(), "You do not have permission to change " +
                    "permissions of " +
                    docRef.getDisplayValue());
        }
    }

    private boolean isFolder(final DocRef docRef) {
        return docRef.getType() != null && "Folder".equalsIgnoreCase(docRef.getType());
    }


    //    public DocumentPermissionSet getPermissionsForDocumentForUser(final String docUuid,
//                                                                  final String userUuid) {
//        return documentPermissionDao.getPermissionsForDocumentForUser(docUuid, userUuid);
//    }

//    private DocumentPermissions getPermissionsForDocument(final String docUuid,
//                                                          final String ownerUuid,
//                                                          final BasicDocPermissions docPermissions,
//                                                          final Map<String, UserRef> userUuidToUserMap) {
//        final List<UserRef> users = new ArrayList<>();
//        final Map<String, DocumentPermissionSet> userPermissions = new HashMap<>();
//
//        // Filters out any perms for users that don't exist anymore
//        docPermissions.forEachUserUuid((userUuid, permissions) ->
//                Optional.ofNullable(userUuidToUserMap.get(userUuid))
//                        .ifPresent(user -> {
//                            users.add(user);
//                            userPermissions.put(user.getUuid(), permissions);
//                        }));
//
//        return new DocumentPermissions(docUuid, ownerUuid, users, userPermissions);
//    }
//
//    public DocumentPermissions getPermissionsForDocument(final String docUuid) {
//        try {
//            final BasicDocPermissions docPermissions = documentPermissionDao.getPermissionsForDocument(
//                    docUuid);
//            final String ownerUuid = documentPermissionDao.getDocumentOwnerUuid(docUuid);
//            // Temporary cache of the users involved
//            final Map<String, UserRef> userUuidToUserMap = getUsersMap(Collections.singleton(docPermissions));
//
//            return getPermissionsForDocument(docUuid, ownerUuid, docPermissions, userUuidToUserMap);
//
//        } catch (final RuntimeException e) {
//            LOGGER.error("getPermissionsForDocument()", e);
//            throw e;
//        }
//    }
//
//    public Map<String, DocumentPermissions> getPermissionsForDocuments(final Collection<String> docUuids) {
//        if (NullSafe.isEmptyCollection(docUuids)) {
//            return Collections.emptyMap();
//        } else {
//            final Map<String, DocumentPermissions> docUuidToDocumentPermissionsMap = new HashMap<>(docUuids.size());
//            try {
//                final Map<String, BasicDocPermissions> docUuidToDocPermsMap =
//                        documentPermissionDao.getPermissionsForDocuments(docUuids);
//                // Temporary cache of the users involved
//                final Map<String, UserRef> userUuidToUserMap = getUsersMap(docUuidToDocPermsMap.values());
//
//                docUuidToDocPermsMap.forEach((docUuid, docPermissions) -> {
//                    final String ownerUuid = documentPermissionDao.getDocumentOwnerUuid(docUuid);
//                    final DocumentPermissions documentPermissions = getPermissionsForDocument(
//                            docUuid,
//                            ownerUuid,
//                            docPermissions,
//                            userUuidToUserMap);
//
//                    docUuidToDocumentPermissionsMap.put(docUuid, documentPermissions);
//                });
//
//            } catch (final RuntimeException e) {
//                LOGGER.error("getPermissionsForDocument()", e);
//                throw e;
//            }
//
//            return docUuidToDocumentPermissionsMap;
//        }
//    }
//
//    /**
//     * Get a map of userUuid => User from all the users in the collection of docPermissions
//     */
//    private Map<String, UserRef> getUsersMap(final Collection<BasicDocPermissions> docPermissionsCollection) {
//        if (NullSafe.isEmptyCollection(docPermissionsCollection)) {
//            return Collections.emptyMap();
//        } else {
//            final Set<String> userUuids = NullSafe.stream(docPermissionsCollection)
//                    .flatMap(docPermissions -> docPermissions.getUserUuids().stream())
//                    .collect(Collectors.toSet());
//
//            final Set<User> users = userDao.getByUuids(userUuids);
//
//            return users.stream()
//                    .collect(Collectors.toMap(User::getUuid, User::asRef));
//        }
//    }
//
//    public void setOwner(final DocRef docRef,
//                         final UserRef userRef) {
//        documentPermissionDao.addPermission(docRef.getUuid(), userRef.getUuid(), DocumentPermissionEnum.OWNER);
//        SetPermissionEvent.fire(permissionChangeEventBus, userRef, docRef, DocumentPermissionEnum.OWNER);
//    }
//
//    public void addPermission(final DocRef docRef,
//                              final UserRef userRef,
//                              final DocumentPermissionEnum permission) {
//        documentPermissionDao.addPermission(docRef.getUuid(), userRef.getUuid(), permission);
//        SetPermissionEvent.fire(permissionChangeEventBus, userRef, docRef, permission);
//    }
//
//    public void addPermissions(final DocRef docRef,
//                               final UserRef userRef,
//                               final DocumentPermissionSet permissions) {
//        documentPermissionDao.addPermissions(docRef.getUuid(), userRef.getUuid(), permissions);
//        permissions.getPermissions().forEach(permission ->
//                SetPermissionEvent.fire(permissionChangeEventBus, userRef, docRef, permission)
//        );
//        permissions.getDocumentCreatePermissions().forEach(documentType ->
//                AddDocumentCreatePermissionEvent.fire(permissionChangeEventBus, userRef, docRef, documentType)
//        );
//    }
//
//    public void removePermissions(final DocRef docRef,
//                                  final UserRef userRef,
//                                  final DocumentPermissionSet permissions) {
//        documentPermissionDao.removePermissions(docRef.getUuid(), userRef.getUuid(), permissions);
//        permissions.getPermissions().forEach(permission ->
//                RemovePermissionEvent.fire(permissionChangeEventBus, userRef, docRef, permission)
//        );
//        permissions.getDocumentCreatePermissions().forEach(permission ->
//                RemoveDocumentCreatePermissionEvent.fire(permissionChangeEventBus, userRef, docRef, permission)
//        );
//    }
//
//    public void addFolderCreatePermission(final DocRef docRef,
//                                          final UserRef userRef,
//                                          final String documentType) {
//        documentPermissionDao.addFolderCreatePermission(docRef.getUuid(), userRef.getUuid(), documentType);
//        AddDocumentCreatePermissionEvent.fire(permissionChangeEventBus, userRef, docRef, documentType);
//    }
//
//    public void removeFolderCreatePermission(final DocRef docRef,
//                                             final UserRef userRef,
//                                             final String documentType) {
//        documentPermissionDao.removeFolderCreatePermission(docRef.getUuid(), userRef.getUuid(), documentType);
//        RemoveDocumentCreatePermissionEvent.fire(permissionChangeEventBus, userRef, docRef, documentType);
//    }
//
//    void clearDocumentPermissionsForUser(final DocRef docRef,
//                                         final UserRef userRef) {
//        documentPermissionDao.clearDocumentPermissionsForUser(docRef.getUuid(), userRef.getUuid());
//        RemovePermissionEvent.fire(permissionChangeEventBus, userRef, docRef, null);
//    }
//
//    @Override
//    public void clearDocumentPermissions(final DocRef docRef) {
//        LOGGER.debug("clearDocumentPermissions() - docRef: {}", docRef);
//        // This is changing the perms of an existing doc, so needs OWNER perms.
//
//        // Get the current user.
//        final UserIdentity userIdentity = securityContext.getUserIdentity();
//
//        // If no user is present then don't clear permissions.
//        if (userIdentity != null) {
//            if (securityContext.hasDocumentPermission(docRef, DocumentPermission.OWNER)) {
//                documentPermissionDao.clearDocumentPermissionsForDoc(docRef.getUuid());
//            }
//        }
//        ClearDocumentPermissionsEvent.fire(permissionChangeEventBus, docRef);
//    }
//
//    @Override
//    public void deleteDocumentPermissions(final DocRef docRef) {
//        LOGGER.debug("deleteDocumentPermissions() - docRef: {}", docRef);
//        // This is deleting perms of a deleted doc, so only needs DELETE perms.
//
//        // Get the current user.
//        final UserIdentity userIdentity = securityContext.getUserIdentity();
//
//        // If no user is present then don't clear permissions.
//        if (userIdentity != null) {
//            if (securityContext.hasDocumentPermission(docRef, DocumentPermission.DELETE)) {
//                documentPermissionDao.clearDocumentPermissionsForDoc(docRef.getUuid());
//            }
//        }
//        ClearDocumentPermissionsEvent.fire(permissionChangeEventBus, docRef);
//    }
//
//    @Override
//    public void deleteDocumentPermissions(final Set<DocRef> docRefs) {
//        if (NullSafe.hasItems(docRefs)) {
//            documentPermissionDao.clearDocumentPermissionsForDocs(docRefs
//                    .stream()
//                    .map(DocRef::getUuid)
//                    .collect(Collectors.toSet()));
//            docRefs.forEach(docUuid ->
//                    ClearDocumentPermissionsEvent.fire(permissionChangeEventBus, docUuid));
//        }
//    }
//
//    @Override
//    public void addDocumentPermissions(DocRef sourceDocRef,
//                                       DocRef documentDocRef,
//                                       boolean owner) {
//        LOGGER.debug("addDocumentPermissions() - sourceDocRef: {}, documentDocRef: {}, owner: {}",
//                sourceDocRef, documentDocRef, owner);
//        Objects.requireNonNull(documentDocRef, "documentDocRef not provided");
//        // Get the current user.
//        final UserIdentity userIdentity = securityContext.getUserIdentity();
//
//        // If no user is present or doesn't have a UUID then don't create permissions.
//        if (userIdentity instanceof final HasUserRef hasUserRef) {
//            if (owner || securityContext.hasDocumentPermission(documentDocRef, DocumentPermission.OWNER)) {
//                // Inherit permissions from the parent folder if there is one.
//                // TODO : This should be part of the explorer service.
//                final boolean excludeCreatePermissions = !DocumentTypes.isFolder(documentDocRef.getType());
//                copyPermissions(
//                        NullSafe.get(sourceDocRef, DocRef::getUuid),
//                        documentDocRef.getUuid(),
//                        excludeCreatePermissions,
//                        owner);
//            }
//        } else {
//            LOGGER.debug(() -> LogUtil.message(
//                    "User {} of type {} does not have a stroom user identity",
//                    userIdentity, NullSafe.get(userIdentity, Object::getClass, Class::getSimpleName)));
//        }
//    }
//
//    private void copyPermissions(final String sourceUuid,
//                                 final String destUuid,
//                                 final boolean excludeCreatePermissions,
//                                 final boolean owner) {
//        LOGGER.debug("copyPermissions() - sourceUuid: {}, destUuid: {}", sourceUuid, destUuid);
//        if (sourceUuid != null) {
//            final stroom.security.shared.DocumentPermissions sourceDocumentPermissions =
//                    getPermissionsForDocument(sourceUuid);
//
//            if (sourceDocumentPermissions != null) {
//                final Map<String, DocumentPermissionSet> userPermissions =
//                        sourceDocumentPermissions.getPermissions();
//                if (NullSafe.hasEntries(userPermissions)) {
//                    for (final Map.Entry<String, DocumentPermissionSet> entry : userPermissions.entrySet()) {
//                        final String userUuid = entry.getKey();
//
//                        DocumentPermissionSet sourcePermissions = entry.getValue();
//                        if (owner) {
//                            // We don't want to copy the ownership from the source as current user is
//                            // the owner
//                            sourcePermissions = DocumentPermissionEnum.excludePermissions(
//                                    sourcePermissions,
//                                    DocumentPermissionEnum.OWNER);
//                        }
//
//                        for (final DocumentPermissionEnum permission : sourcePermissions.getPermissions()) {
//                            try {
//                                addPermission(destUuid,
//                                        userUuid,
//                                        permission);
//                            } catch (final RuntimeException e) {
//                                LOGGER.error(e.getMessage(), e);
//                            }
//                        }
//                    }
//                }
//            }
//        }
//    }
}
