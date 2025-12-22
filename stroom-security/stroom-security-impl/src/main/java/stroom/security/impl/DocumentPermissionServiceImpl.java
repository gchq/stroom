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

package stroom.security.impl;

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerConstants;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.impl.event.PermissionChangeEvent;
import stroom.security.impl.event.PermissionChangeEventBus;
import stroom.security.shared.AbstractDocumentPermissionsChange;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddAllDocumentUserCreatePermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddAllPermissionsFrom;
import stroom.security.shared.AbstractDocumentPermissionsChange.AddDocumentUserCreatePermission;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveAllDocumentUserCreatePermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveAllPermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveDocumentUserCreatePermission;
import stroom.security.shared.AbstractDocumentPermissionsChange.RemovePermission;
import stroom.security.shared.AbstractDocumentPermissionsChange.SetAllPermissionsFrom;
import stroom.security.shared.AbstractDocumentPermissionsChange.SetDocumentUserCreatePermissions;
import stroom.security.shared.AbstractDocumentPermissionsChange.SetPermission;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.DocumentUserPermissionsReport;
import stroom.security.shared.DocumentUserPermissionsRequest;
import stroom.security.shared.FetchDocumentUserPermissionsRequest;
import stroom.security.shared.SingleDocumentPermissionChangeRequest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class DocumentPermissionServiceImpl implements DocumentPermissionService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocumentPermissionServiceImpl.class);

    private final DocumentPermissionDao documentPermissionDao;
    private final UserGroupsCache userGroupsCache;
    private final PermissionChangeEventBus permissionChangeEventBus;
    private final SecurityContext securityContext;

    @Inject
    DocumentPermissionServiceImpl(final DocumentPermissionDao documentPermissionDao,
                                  final UserGroupsCache userGroupsCache,
                                  final PermissionChangeEventBus permissionChangeEventBus,
                                  final SecurityContext securityContext) {
        this.documentPermissionDao = documentPermissionDao;
        this.userGroupsCache = userGroupsCache;
        this.permissionChangeEventBus = permissionChangeEventBus;
        this.securityContext = securityContext;
    }

    @Override
    public DocumentPermission getPermission(final DocRef docRef, final UserRef userRef) {
        checkGetPermission(docRef);
        return documentPermissionDao.getDocumentUserPermission(docRef.getUuid(), userRef.getUuid());
    }

    @Override
    public void setPermission(final DocRef docRef, final UserRef userRef, final DocumentPermission permission) {
        checkSetPermission(docRef);
        documentPermissionDao.setDocumentUserPermission(docRef.getUuid(), userRef.getUuid(), permission);
        PermissionChangeEvent.fire(permissionChangeEventBus, userRef, docRef);
    }

    @Override
    public Boolean changeDocumentPermissions(final SingleDocumentPermissionChangeRequest request) {
        final DocRef docRef = request.getDocRef();
        final AbstractDocumentPermissionsChange change = request.getChange();
        Objects.requireNonNull(docRef, "docRef is null");
        Objects.requireNonNull(docRef.getUuid(), "docRef UUID is null");

        // Check we have permission to change permissions of the supplied document.
        checkSetPermission(docRef);

        switch (change) {
            case final SetPermission req -> {
                if (req.getPermission() == null) {
                    Objects.requireNonNull(req.getUserRef(), "Null user ref");
                    documentPermissionDao.removeDocumentUserPermission(
                            docRef.getUuid(),
                            req.getUserRef().getUuid());
                    PermissionChangeEvent.fire(permissionChangeEventBus, req.getUserRef(), docRef);

                } else {
                    Objects.requireNonNull(req.getUserRef(), "Null user ref");
                    documentPermissionDao.setDocumentUserPermission(
                            docRef.getUuid(),
                            req.getUserRef().getUuid(),
                            req.getPermission());
                    PermissionChangeEvent.fire(permissionChangeEventBus, req.getUserRef(), docRef);
                }
            }
            case final RemovePermission req -> {
                Objects.requireNonNull(req.getUserRef(), "Null user ref");
                documentPermissionDao.removeDocumentUserPermission(
                        docRef.getUuid(),
                        req.getUserRef().getUuid());
                PermissionChangeEvent.fire(permissionChangeEventBus, req.getUserRef(), docRef);
            }
            case final AddDocumentUserCreatePermission req -> {
                // Only applies to folders.
                if (ExplorerConstants.isFolderOrSystem(docRef)) {
                    Objects.requireNonNull(req.getUserRef(), "Null user ref");
                    Objects.requireNonNull(req.getDocumentType(), "Null documentType");
                    documentPermissionDao.addDocumentUserCreatePermission(
                            docRef.getUuid(),
                            req.getUserRef().getUuid(),
                            req.getDocumentType());
                    PermissionChangeEvent.fire(permissionChangeEventBus, req.getUserRef(), docRef);
                }
            }
            case final RemoveDocumentUserCreatePermission req -> {
                // Only applies to folders.
                if (ExplorerConstants.isFolderOrSystem(docRef)) {
                    Objects.requireNonNull(req.getUserRef(), "Null user ref");
                    Objects.requireNonNull(req.getDocumentType(), "Null documentType");
                    documentPermissionDao.removeDocumentUserCreatePermission(
                            docRef.getUuid(),
                            req.getUserRef().getUuid(),
                            req.getDocumentType());
                    PermissionChangeEvent.fire(permissionChangeEventBus, req.getUserRef(), docRef);
                }
            }
            case final SetDocumentUserCreatePermissions req -> {
                // Only applies to folders.
                if (ExplorerConstants.isFolderOrSystem(docRef)) {
                    Objects.requireNonNull(req.getUserRef(), "Null user ref");
                    Objects.requireNonNull(req.getDocumentTypes(), "Null documentType");
                    documentPermissionDao.setDocumentUserCreatePermissions(
                            docRef.getUuid(),
                            req.getUserRef().getUuid(),
                            req.getDocumentTypes());
                    PermissionChangeEvent.fire(permissionChangeEventBus, req.getUserRef(), docRef);
                }
            }
            case final AddAllDocumentUserCreatePermissions req -> {
                // Only applies to folders.
                if (ExplorerConstants.isFolderOrSystem(docRef)) {
                    Objects.requireNonNull(req.getUserRef(), "Null user ref");
                    documentPermissionDao.removeAllDocumentUserCreatePermissions(
                            docRef.getUuid(),
                            req.getUserRef().getUuid());
                    documentPermissionDao.addDocumentUserCreatePermission(
                            docRef.getUuid(),
                            req.getUserRef().getUuid(),
                            ExplorerConstants.ALL_CREATE_PERMISSIONS);
                    PermissionChangeEvent.fire(permissionChangeEventBus, req.getUserRef(), docRef);
                }
            }
            case final RemoveAllDocumentUserCreatePermissions req -> {
                // Only applies to folders.
                if (ExplorerConstants.isFolderOrSystem(docRef)) {
                    Objects.requireNonNull(req.getUserRef(), "Null user ref");
                    documentPermissionDao.removeAllDocumentUserCreatePermissions(
                            docRef.getUuid(),
                            req.getUserRef().getUuid());
                    PermissionChangeEvent.fire(permissionChangeEventBus, req.getUserRef(), docRef);
                }
            }
            case final AddAllPermissionsFrom req -> {
                Objects.requireNonNull(req.getSourceDocRef(), "Null sourceDocRef");
                documentPermissionDao.addDocumentPermissions(
                        req.getSourceDocRef().getUuid(),
                        docRef.getUuid());
                // Only applies to folders.
                if (ExplorerConstants.isFolderOrSystem(docRef)) {
                    documentPermissionDao.addDocumentCreatePermissions(
                            req.getSourceDocRef().getUuid(),
                            docRef.getUuid());
                }
                PermissionChangeEvent.fire(permissionChangeEventBus, null, docRef);
            }
            case final SetAllPermissionsFrom req -> {
                Objects.requireNonNull(req.getSourceDocRef(), "Null sourceDocRef");
                documentPermissionDao.setDocumentPermissions(
                        req.getSourceDocRef().getUuid(),
                        docRef.getUuid());
                // Only applies to folders.
                if (ExplorerConstants.isFolderOrSystem(docRef)) {
                    documentPermissionDao.setDocumentCreatePermissions(
                            req.getSourceDocRef().getUuid(),
                            docRef.getUuid());
                }
                PermissionChangeEvent.fire(permissionChangeEventBus, null, docRef);
            }
            case final RemoveAllPermissions ignored -> {
                documentPermissionDao.removeAllDocumentPermissions(docRef.getUuid());
                PermissionChangeEvent.fire(permissionChangeEventBus, null, docRef);
            }
            case null, default ->
                    throw new RuntimeException("Unexpected request type: " + request.getClass().getName());
        }

        return true;
    }

    @Override
    public void removeAllDocumentPermissions(final DocRef docRef) {
        checkSetPermission(docRef);
        documentPermissionDao.removeAllDocumentPermissions(docRef.getUuid());
        if (ExplorerConstants.isFolderOrSystem(docRef)) {
            documentPermissionDao.removeAllDocumentCreatePermissions(docRef.getUuid());
        }
        PermissionChangeEvent.fire(permissionChangeEventBus, null, docRef);
    }

    @Override
    public void removeAllDocumentPermissions(final Set<DocRef> docRefs) {
        docRefs.forEach(this::removeAllDocumentPermissions);
    }

    @Override
    public void addDocumentPermissions(final DocRef sourceDocRef, final DocRef destDocRef) {
        checkSetPermission(destDocRef);
        documentPermissionDao.addDocumentPermissions(sourceDocRef.getUuid(), destDocRef.getUuid());
        // Copy create permissions if the source is a folder.
        if (ExplorerConstants.isFolderOrSystem(sourceDocRef)) {
            documentPermissionDao.addDocumentCreatePermissions(sourceDocRef.getUuid(), destDocRef.getUuid());
        }
        PermissionChangeEvent.fire(permissionChangeEventBus, null, destDocRef);
    }

    private boolean canUserChangePermission(final DocRef docRef) {
        return securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION) ||
               securityContext.hasDocumentPermission(docRef, DocumentPermission.OWNER);
    }

    private void checkSetPermission(final DocRef docRef) {
        if (!canUserChangePermission(docRef)) {
            throw new PermissionException(securityContext.getUserRef(), "You do not have permission to change " +
                                                                        "permissions of " +
                                                                        docRef.getDisplayValue());
        }
    }

    private void checkGetPermission(final DocRef docRef) {
        if (!canUserChangePermission(docRef)) {
            throw new PermissionException(securityContext.getUserRef(), "You do not have permission to get " +
                                                                        "permissions of " +
                                                                        docRef.getDisplayValue());
        }
    }

    @Override
    public ResultPage<DocumentUserPermissions> fetchDocumentUserPermissions(
            final FetchDocumentUserPermissionsRequest request) {
        return securityContext.secureResult(() -> {
            final UserRef userRef = securityContext.getUserRef();
            Objects.requireNonNull(userRef, "Null user");

            FetchDocumentUserPermissionsRequest modified = request;

            // If the current user is not allowed to change permissions then only show them permissions for themselves.
            if (!canUserChangePermission(request.getDocRef())) {
                modified = new FetchDocumentUserPermissionsRequest
                        .Builder(request)
                        .userRef(userRef)
                        .build();
            }

            return documentPermissionDao.fetchDocumentUserPermissions(modified);
        });
    }

    public DocumentUserPermissionsReport getDocUserPermissionsReport(final DocumentUserPermissionsRequest request) {
        final DocRef docRef = request.getDocRef();
        final UserRef userRef = request.getUserRef();

        // If the current user is not allowed to change the permissions of the specified document then only allow them
        // to see a permissions report for themselves.
        if (!canUserChangePermission(request.getDocRef())) {
            final UserRef currentUser = securityContext.getUserRef();
            if (currentUser == null) {
                throw new PermissionException(currentUser, "No user logged in");
            } else if (!currentUser.equals(userRef)) {
                throw new PermissionException(currentUser, "You can only get a permissions report for yourself");
            }
        }

        final Map<DocumentPermission, List<List<UserRef>>> inheritedPermissions = new HashMap<>();
        final Map<String, List<List<UserRef>>> inheritedCreatePermissions = new HashMap<>();
        final Set<UserRef> cyclicPrevention = new HashSet<>();
        final List<UserRef> parentPath = Collections.emptyList();
        addDeepPermissionsAndPaths(
                userRef,
                docRef,
                parentPath,
                inheritedPermissions,
                inheritedCreatePermissions,
                cyclicPrevention);

        final DocumentPermission explicitPermission = documentPermissionDao
                .getDocumentUserPermission(docRef.getUuid(), userRef.getUuid());
        final Set<String> explicitCreatePermissions = documentPermissionDao
                .getDocumentUserCreatePermissions(docRef.getUuid(), userRef.getUuid());

        LOGGER.debug("Inherited permissions: {}: {}", inheritedPermissions, convertToPaths(inheritedPermissions));

        return new DocumentUserPermissionsReport(
                explicitPermission,
                explicitCreatePermissions,
                convertToPaths(inheritedPermissions),
                convertToPaths(inheritedCreatePermissions));
    }

    private <T> Map<String, List<String>> convertToPaths(final Map<T, List<List<UserRef>>> map) {
        return map.entrySet()
                .stream()
                .collect(Collectors.toMap(entry -> entry.getKey().toString(), entry -> {
                    return entry.getValue()
                            .stream()
                            .map(list -> list.stream()
                                    .map(UserRef::toDisplayString)
                                    .collect(Collectors.joining(" --> ")))
                            .toList();
                }));
    }

    private void addDeepPermissionsAndPaths(final UserRef userRef,
                                            final DocRef docRef,
                                            final List<UserRef> parentPath,
                                            final Map<DocumentPermission, List<List<UserRef>>> inheritedPermissions,
                                            final Map<String, List<List<UserRef>>> inheritedCreatePermissions,
                                            final Set<UserRef> cyclicPrevention) {
        if (cyclicPrevention.add(userRef)) {
            final Set<UserRef> parentGroups = userGroupsCache.getGroups(userRef);
            if (parentGroups != null) {
                for (final UserRef group : parentGroups) {

                    final List<UserRef> path = new ArrayList<>(parentPath.size() + 1);
                    // Add the ancestor at the head of the list, so we get 'grandparent --> parent'
                    path.add(group);
                    path.addAll(parentPath);

                    final DocumentPermission permission = documentPermissionDao
                            .getDocumentUserPermission(docRef.getUuid(), group.getUuid());
                    if (permission != null) {
                        inheritedPermissions.computeIfAbsent(permission, k -> new ArrayList<>())
                                .add(path);
                    }

                    final Set<String> createPermissions = documentPermissionDao
                            .getDocumentUserCreatePermissions(docRef.getUuid(), group.getUuid());
                    if (createPermissions != null) {
                        createPermissions.forEach(createPermission -> {
                            inheritedCreatePermissions.computeIfAbsent(createPermission, k ->
                                            new ArrayList<>())
                                    .add(path);
                        });
                    }

                    addDeepPermissionsAndPaths(
                            group,
                            docRef,
                            path,
                            inheritedPermissions,
                            inheritedCreatePermissions,
                            cyclicPrevention);
                }
            }
        }
    }
}
