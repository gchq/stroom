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
import stroom.explorer.shared.DocumentTypes;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.impl.event.AddPermissionEvent;
import stroom.security.impl.event.ClearDocumentPermissionsEvent;
import stroom.security.impl.event.PermissionChangeEventBus;
import stroom.security.impl.event.RemovePermissionEvent;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.HasStroomUserIdentity;
import stroom.security.shared.User;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class DocumentPermissionServiceImpl implements DocumentPermissionService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocumentPermissionServiceImpl.class);

    private final DocumentPermissionDao documentPermissionDao;
    private final UserDao userDao;
    private final PermissionChangeEventBus permissionChangeEventBus;
    private final SecurityContext securityContext;

    @Inject
    DocumentPermissionServiceImpl(final DocumentPermissionDao documentPermissionDao,
                                  final UserDao userDao,
                                  final PermissionChangeEventBus permissionChangeEventBus,
                                  final SecurityContext securityContext) {
        this.documentPermissionDao = documentPermissionDao;
        this.userDao = userDao;
        this.permissionChangeEventBus = permissionChangeEventBus;
        this.securityContext = securityContext;
    }

    public Set<String> getPermissionsForDocumentForUser(final String docUuid,
                                                        final String userUuid) {
        return documentPermissionDao.getPermissionsForDocumentForUser(docUuid, userUuid);
    }

    private DocumentPermissions getPermissionsForDocument(final String docUuid,
                                                          final BasicDocPermissions docPermissions,
                                                          final Map<String, User> userUuidToUserMap) {
        final List<User> users = new ArrayList<>();
        final List<User> groups = new ArrayList<>();
        final Map<String, Set<String>> userPermissions = new HashMap<>();

        // Filters out any perms for users that don't exist anymore
        docPermissions.forEachUserUuid((userUuid, permissions) ->
                Optional.ofNullable(userUuidToUserMap.get(userUuid))
                        .ifPresent(user -> {
                            if (user.isGroup()) {
                                groups.add(user);
                            } else {
                                users.add(user);
                            }
                            userPermissions.put(user.getUuid(), permissions);
                        }));

        return new DocumentPermissions(docUuid, users, groups, userPermissions);
    }

    public DocumentPermissions getPermissionsForDocument(final String docUuid) {
        try {
            final BasicDocPermissions docPermissions = documentPermissionDao.getPermissionsForDocument(
                    docUuid);
            // Temporary cache of the users involved
            final Map<String, User> userUuidToUserMap = getUsersMap(Collections.singleton(docPermissions));

            return getPermissionsForDocument(docUuid, docPermissions, userUuidToUserMap);

        } catch (final RuntimeException e) {
            LOGGER.error("getPermissionsForDocument()", e);
            throw e;
        }
    }

    public Map<String, DocumentPermissions> getPermissionsForDocuments(final Collection<String> docUuids) {
        if (NullSafe.isEmptyCollection(docUuids)) {
            return Collections.emptyMap();
        } else {
            final Map<String, DocumentPermissions> docUuidToDocumentPermissionsMap = new HashMap<>(docUuids.size());
            try {
                final Map<String, BasicDocPermissions> docUuidToDocPermsMap =
                        documentPermissionDao.getPermissionsForDocuments(docUuids);
                // Temporary cache of the users involved
                final Map<String, User> userUuidToUserMap = getUsersMap(docUuidToDocPermsMap.values());

                docUuidToDocPermsMap.forEach((docUuid, docPermissions) -> {

                    final DocumentPermissions documentPermissions = getPermissionsForDocument(docUuid,
                            docPermissions,
                            userUuidToUserMap);

                    docUuidToDocumentPermissionsMap.put(docUuid, documentPermissions);
                });

            } catch (final RuntimeException e) {
                LOGGER.error("getPermissionsForDocument()", e);
                throw e;
            }

            return docUuidToDocumentPermissionsMap;
        }
    }

    /**
     * Get a map of userUuid => User from all the users in the collection of docPermissions
     */
    private Map<String, User> getUsersMap(final Collection<BasicDocPermissions> docPermissionsCollection) {
        if (NullSafe.isEmptyCollection(docPermissionsCollection)) {
            return Collections.emptyMap();
        } else {
            final Set<String> userUuids = NullSafe.stream(docPermissionsCollection)
                    .flatMap(docPermissions -> docPermissions.getUserUuids().stream())
                    .collect(Collectors.toSet());

            final Set<User> users = userDao.getByUuids(userUuids);

            return users.stream()
                    .collect(Collectors.toMap(User::getUuid, Function.identity()));
        }
    }

    public void addPermission(final String docUuid,
                              final String userUuid,
                              final String permission) {
        documentPermissionDao.addPermission(docUuid, userUuid, permission);
        AddPermissionEvent.fire(permissionChangeEventBus, userUuid, docUuid, permission);
    }

    public void removePermissions(final String docUuid,
                                  final String userUuid,
                                  final Set<String> permissions) {
        documentPermissionDao.removePermissions(docUuid, userUuid, permissions);
        permissions.forEach(permission ->
                RemovePermissionEvent.fire(permissionChangeEventBus, userUuid, docUuid, permission)
        );
    }

    void clearDocumentPermissionsForUser(final String docUuid,
                                         final String userUuid) {
        documentPermissionDao.clearDocumentPermissionsForUser(docUuid, userUuid);
        RemovePermissionEvent.fire(permissionChangeEventBus, userUuid, docUuid, null);
    }

    @Override
    public void clearDocumentPermissions(final String docUuid) {
        LOGGER.debug("clearDocumentPermissions() - docUuid: {}", docUuid);
        // Get the current user.
        final UserIdentity userIdentity = securityContext.getUserIdentity();

        // If no user is present then don't clear permissions.
        if (userIdentity != null) {
            if (securityContext.hasDocumentPermission(docUuid, DocumentPermissionNames.OWNER)) {
                documentPermissionDao.clearDocumentPermissions(docUuid);
            }
        }

        ClearDocumentPermissionsEvent.fire(permissionChangeEventBus, docUuid);
    }

    @Override
    public void addDocumentPermissions(DocRef sourceDocRef,
                                       DocRef documentDocRef,
                                       boolean owner) {
        LOGGER.debug("addDocumentPermissions() - sourceDocRef: {}, documentDocRef: {}, owner: {}",
                sourceDocRef, documentDocRef, owner);
        Objects.requireNonNull(documentDocRef, "documentDocRef not provided");
        // Get the current user.
        final UserIdentity userIdentity = securityContext.getUserIdentity();

        // If no user is present or doesn't have a UUID then don't create permissions.
        if (userIdentity instanceof final HasStroomUserIdentity stroomUserIdentity) {
            if (owner || securityContext.hasDocumentPermission(documentDocRef, DocumentPermissionNames.OWNER)) {
                if (owner) {
                    // Make the current user the owner of the new document.
                    try {
                        addPermission(documentDocRef.getUuid(),
                                stroomUserIdentity.getUuid(),
                                DocumentPermissionNames.OWNER);
                    } catch (final RuntimeException e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                }

                // Inherit permissions from the parent folder if there is one.
                // TODO : This should be part of the explorer service.
                final boolean excludeCreatePermissions = !DocumentTypes.isFolder(documentDocRef.getType());
                copyPermissions(
                        NullSafe.get(sourceDocRef, DocRef::getUuid),
                        documentDocRef.getUuid(),
                        excludeCreatePermissions,
                        owner);
            }
        } else {
            LOGGER.debug(() -> LogUtil.message(
                    "User {} of type {} does not have a stroom user identity",
                    userIdentity, NullSafe.get(userIdentity, Object::getClass, Class::getSimpleName)));
        }
    }

    @Override
    public void setDocumentOwner(final String documentUuid, final String userUuid) {
        final Set<String> currentOwnerUuids = documentPermissionDao.getDocumentOwnerUuids(documentUuid);
        final Set<String> ownersToRemove = new HashSet<>(currentOwnerUuids);
        ownersToRemove.remove(userUuid);

        documentPermissionDao.setOwner(documentUuid, userUuid);

        ownersToRemove.forEach(ownerUuid -> {
            RemovePermissionEvent.fire(
                    permissionChangeEventBus,
                    ownerUuid,
                    documentUuid,
                    DocumentPermissionNames.OWNER);
        });
        AddPermissionEvent.fire(
                permissionChangeEventBus,
                userUuid,
                documentUuid,
                DocumentPermissionNames.OWNER);

    }

    @Override
    public Set<String> getDocumentOwnerUuids(final String documentUuid) {
        return documentPermissionDao.getDocumentOwnerUuids(documentUuid);
    }

    private void copyPermissions(final String sourceUuid,
                                 final String destUuid,
                                 final boolean excludeCreatePermissions,
                                 final boolean owner) {
        LOGGER.debug("copyPermissions() - sourceUuid: {}, destUuid: {}", sourceUuid, destUuid);
        if (sourceUuid != null) {
            final stroom.security.shared.DocumentPermissions sourceDocumentPermissions =
                    getPermissionsForDocument(sourceUuid);

            if (sourceDocumentPermissions != null) {
                final Map<String, Set<String>> userPermissions = sourceDocumentPermissions.getPermissions();
                if (NullSafe.hasEntries(userPermissions)) {
                    for (final Map.Entry<String, Set<String>> entry : userPermissions.entrySet()) {
                        final String userUuid = entry.getKey();

                        Set<String> sourcePermissions = excludeCreatePermissions
                                ? DocumentPermissionNames.excludeCreatePermissions(entry.getValue())
                                : entry.getValue();

                        if (owner) {
                            // We don't want to copy the ownership from the source as current user is
                            // the owner
                            sourcePermissions = DocumentPermissionNames.excludePermissions(
                                    sourcePermissions,
                                    DocumentPermissionNames.OWNER);
                        }

                        for (final String permission : sourcePermissions) {
                            try {
                                addPermission(destUuid,
                                        userUuid,
                                        permission);
                            } catch (final RuntimeException e) {
                                LOGGER.error(e.getMessage(), e);
                            }
                        }
                    }
                }
            }
        }
    }
}
