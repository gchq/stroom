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
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Singleton
public class DocumentPermissionServiceImpl implements DocumentPermissionService {

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

        if (change instanceof final SetPermission req) {
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

        } else if (change instanceof final RemovePermission req) {
            Objects.requireNonNull(req.getUserRef(), "Null user ref");
            documentPermissionDao.removeDocumentUserPermission(
                    docRef.getUuid(),
                    req.getUserRef().getUuid());
            PermissionChangeEvent.fire(permissionChangeEventBus, req.getUserRef(), docRef);

        } else if (change instanceof final AddDocumentUserCreatePermission req) {
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

        } else if (change instanceof final RemoveDocumentUserCreatePermission req) {
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

        } else if (change instanceof final SetDocumentUserCreatePermissions req) {
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

        } else if (change instanceof final AddAllDocumentUserCreatePermissions req) {
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

        } else if (change instanceof final RemoveAllDocumentUserCreatePermissions req) {
            // Only applies to folders.
            if (ExplorerConstants.isFolderOrSystem(docRef)) {
                Objects.requireNonNull(req.getUserRef(), "Null user ref");
                documentPermissionDao.removeAllDocumentUserCreatePermissions(
                        docRef.getUuid(),
                        req.getUserRef().getUuid());
                PermissionChangeEvent.fire(permissionChangeEventBus, req.getUserRef(), docRef);
            }

        } else if (change instanceof final AddAllPermissionsFrom req) {
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

        } else if (change instanceof final SetAllPermissionsFrom req) {
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

        } else if (change instanceof RemoveAllPermissions) {
            documentPermissionDao.removeAllDocumentPermissions(docRef.getUuid());
            PermissionChangeEvent.fire(permissionChangeEventBus, null, docRef);

        } else {
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

//
//    @Override
//    public Boolean setDocumentUserPermissions(final SetDocumentUserPermissionsRequest request) {
//        checkSetPermission(request.getDocRef());
//
//        final DocumentUserPermissions documentUserPermissions = request.getDocumentUserPermissions();
//        final DocRef docRef = request.getDocRef();
//        final UserRef userRef = documentUserPermissions.getUserRef();
//        documentPermissionDao.setDocumentUserPermission(
//                docRef.getUuid(),
//                userRef.getUuid(),
//                documentUserPermissions.getPermission());
//        if (isFolder(docRef)) {
//            documentPermissionDao.removeDocumentUserCreatePermissions(
//                    docRef.getUuid(),
//                    userRef.getUuid());
//            if (documentUserPermissions.getDocumentCreatePermissions() != null) {
//                documentUserPermissions.getDocumentCreatePermissions().forEach(documentType -> {
//                    documentPermissionDao.addDocumentUserCreatePermission(
//                            docRef.getUuid(),
//                            userRef.getUuid(),
//                            documentType);
//                });
//            }
//        }
//
//        return true;
//    }
//
//    @Override
//    public Boolean copyDocumentPermissions(final ChangeDocumentPermissionsRequest request) {
//        checkGetPermission(request.getSourceDocRef());
//        checkSetPermission(request.getDestDocRef());
//
//        documentPermissionDao.copyDocumentPermissions(request.getSourceDocRef().getUuid(),
//                request.getDestDocRef().getUuid());
//        if (isFolder(request.getDestDocRef())) {
//            documentPermissionDao.copyDocumentCreatePermissions(request.getSourceDocRef().getUuid(),
//                    request.getDestDocRef().getUuid());
//        }
//
//        return true;
//    }
//
//    @Override
//    public Boolean clearDocumentPermissions(final ClearDocumentPermissionsRequest request) {
//        checkSetPermission(request.getDocRef());
//
//        documentPermissionDao.removeAllDocumentPermissions(request.getDocRef().getUuid());
//        if (isFolder(request.getDocRef())) {
//            documentPermissionDao.removeAllDocumentCreatePermissions(request.getDocRef().getUuid());
//        }
//
//        return true;
//    }


    @Override
    public DocumentUserPermissions getPermissions(final DocRef docRef, final UserRef userRef) {
        if (!securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION) &&
            !userRef.equals(securityContext.getUserRef())) {
            throw new PermissionException(securityContext.getUserRef(), "You do not have permission to view user " +
                                                                        "permissions");
        }

        final DocumentPermission permission = documentPermissionDao
                .getDocumentUserPermission(docRef.getUuid(), userRef.getUuid());
        Set<String> documentCreatePermissions = null;
        if (ExplorerConstants.isFolderOrSystem(docRef)) {
            documentCreatePermissions = documentPermissionDao
                    .getDocumentUserCreatePermissions(docRef.getUuid(), userRef.getUuid());
        }
        final DocumentUserPermissions documentUserPermissions =
                new DocumentUserPermissions(userRef, permission, documentCreatePermissions);
        addDeepPermissions(docRef, documentUserPermissions);
        return documentUserPermissions;
    }

    @Override
    public ResultPage<DocumentUserPermissions> fetchDocumentUserPermissions(
            final FetchDocumentUserPermissionsRequest request) {
        checkGetPermission(request.getDocRef());
        final ResultPage<DocumentUserPermissions> resultPage =
                documentPermissionDao.fetchDocumentUserPermissions(request);

        // Add inherited permissions.
        for (final DocumentUserPermissions permissions : resultPage.getValues()) {
            addDeepPermissions(request.getDocRef(), permissions);
        }

        return resultPage;
    }

    public DocumentUserPermissionsReport getDocUserPermissionsReport(final DocumentUserPermissionsRequest request) {
        final DocRef docRef = request.getDocRef();
        final UserRef userRef = request.getUserRef();
        checkGetPermission(docRef);

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

        return new DocumentUserPermissionsReport(
                explicitPermission,
                explicitCreatePermissions,
                convertToPaths(inheritedPermissions),
                convertToPaths(inheritedCreatePermissions));
    }

    private <T> Map<T, List<String>> convertToPaths(final Map<T, List<List<UserRef>>> map) {
        return map
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Entry::getKey, entry -> {
                    return entry.getValue()
                            .stream()
                            .map(list -> list.stream()
                                    .map(UserRef::toDisplayString)
                                    .collect(Collectors.joining(" > ")))
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

                    final List<UserRef> path = new ArrayList<>(parentPath);
                    path.add(group);

                    final DocumentPermission permission = documentPermissionDao
                            .getDocumentUserPermission(docRef.getUuid(), group.getUuid());
                    if (permission != null) {
                        inheritedPermissions.computeIfAbsent(permission, k -> new ArrayList<>()).add(path);
                    }

                    final Set<String> createPermissions = documentPermissionDao
                            .getDocumentUserCreatePermissions(docRef.getUuid(), group.getUuid());
                    if (createPermissions != null) {
                        createPermissions.forEach(createPermission -> {
                            inheritedCreatePermissions.computeIfAbsent(createPermission, k ->
                                    new ArrayList<>()).add(path);
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


//    private void addDeepPermissionsAndPaths(final UserRef userRef,
//                                            final List<UserRef> currentPath,
//                                            final AtomicReference<DocumentPermission> inheritedPermission,
//                                            final Set<String> inheritedDocumentCreatePermissions,
//                                            final Set<UserRef> cyclicPrevention) {
//        final Set<AppPermission> directPermissions = appPermissionDao.getPermissionsForUser(userRef.getUuid());
//        directPermissions.forEach(appPermission -> {
//            inheritedPermissions.computeIfAbsent(appPermission, k -> new ArrayList<>()).add(currentPath);
//        });
//
//        if (cyclicPrevention.add(userRef)) {
//            final Set<UserRef> parentGroups = userGroupsCache.getGroups(userRef);
//            if (parentGroups != null) {
//                for (final UserRef group : parentGroups) {
//                    final List<UserRef> parentPath = new ArrayList<>(currentPath);
//                    parentPath.add(group);
//                    addDeepPermissionsAndPaths(group, parentPath, inheritedPermissions, cyclicPrevention);
//                }
//            }
//        }
//    }

    private void addDeepPermissions(final DocRef docRef,
                                    final DocumentUserPermissions documentUserPermissions) {
        final AtomicReference<DocumentPermission> inheritedPermission = new AtomicReference<>();
        final Set<String> inheritedDocumentCreatePermissions = new HashSet<>();
        final Set<UserRef> cyclicPrevention = new HashSet<>();
        addDeepPermissions(
                docRef,
                documentUserPermissions.getUserRef(),
                inheritedPermission,
                inheritedDocumentCreatePermissions,
                cyclicPrevention);
        documentUserPermissions.setInheritedPermission(inheritedPermission.get());
        documentUserPermissions.setInheritedDocumentCreatePermissions(inheritedDocumentCreatePermissions);
    }

    private void addDeepPermissions(final DocRef docRef,
                                    final UserRef userRef,
                                    final AtomicReference<DocumentPermission> inheritedPermission,
                                    final Set<String> inheritedDocumentCreatePermissions,
                                    final Set<UserRef> cyclicPrevention) {
        if (cyclicPrevention.add(userRef)) {
            final Set<UserRef> parentGroups = userGroupsCache.getGroups(userRef);
            if (parentGroups != null) {
                for (final UserRef group : parentGroups) {
                    final DocumentPermission documentPermission = documentPermissionDao
                            .getDocumentUserPermission(docRef.getUuid(), group.getUuid());
                    if (documentPermission != null) {
                        if (inheritedPermission.get() == null ||
                            !inheritedPermission.get().isEqualOrHigher(documentPermission)) {
                            inheritedPermission.set(documentPermission);
                        }
                    }
                    final Set<String> documentUserCreatePermissions = documentPermissionDao
                            .getDocumentUserCreatePermissions(docRef.getUuid(), group.getUuid());
                    inheritedDocumentCreatePermissions.addAll(documentUserCreatePermissions);

                    addDeepPermissions(
                            docRef,
                            group,
                            inheritedPermission,
                            inheritedDocumentCreatePermissions,
                            cyclicPrevention);
                }
            }
        }
    }


//
//    @Override
//    public Set<DocRef> expandScope(final DocumentPermissionScope scope) {
//        // Check permissions on all scopes.
//        final Set<DocRef> allDocRefs = new HashSet<>();
//        boolean addChildrenOrDescendants = scope.isAllDocuments();
//
//        if (!addChildrenOrDescendants && scope.getDocRefScopes() != null) {
//            for (final DocRefScope docRefScope : scope.getDocRefScopes()) {
//                allDocRefs.add(docRefScope.getDocRef());
//                if (docRefScope.isPlusChildren() || docRefScope.isPlusDescendants()) {
//                    addChildrenOrDescendants = true;
//                }
//                if (!securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION) &&
//                        !securityContext.hasDocumentPermission(docRefScope.getDocRef(), DocumentPermission.OWNER)) {
//                    throw new PermissionException(securityContext.getUserRef(),
//                            "You do not have permission to find permissions for " +
//                                    docRefScope.getDocRef().getDisplayValue());
//                }
//            }
//        }
//
//        // Now see if we need to expand the list to children or descendants.
//        if (addChildrenOrDescendants) {
//            final ExplorerTreeFilter explorerTreeFilter = new ExplorerTreeFilter(
//                    null,
//                    Set.of(ExplorerConstants.SYSTEM),
//                    null,
//                    null,
//                    Set.of(DocumentPermission.OWNER),
//                    null,
//                    false,
//                    null);
//            final FetchExplorerNodesRequest fetchExplorerNodesRequest = new FetchExplorerNodesRequest(
//                    null,
//                    null,
//                    explorerTreeFilter,
//                    10000,
//                    null,
//                    false);
//            final FetchExplorerNodeResult result = explorerServiceProvider.get().getData(fetchExplorerNodesRequest);
//            addChildren(result.getRootNodes(), scope, allDocRefs);
//        }
//
//        return allDocRefs;
//    }
//
//    private void addChildren(final List<ExplorerNode> children,
//                             final DocumentPermissionScope scope,
//                             final Set<DocRef> result) {
//        if (children != null) {
//            children.forEach(node -> {
//                final DocRef docRef = node.getDocRef();
//                boolean addChildren = scope.isAllDocuments();
//                boolean addDescendants = scope.isAllDocuments();
//                boolean addThis = scope.isAllDocuments();
//
//                if (!addDescendants && scope.getDocRefScopes() != null) {
//                    for (final DocRefScope docRefScope : scope.getDocRefScopes()) {
//                        if (docRefScope.getDocRef() == docRef) {
//                            addThis = true;
//
//                            if (docRefScope.isPlusChildren()) {
//                                addChildren = true;
//                            }
//                            if (docRefScope.isPlusDescendants()) {
//                                addDescendants = true;
//                            }
//                        }
//                    }
//                }
//
//                if (addThis) {
//                    result.add(docRef);
//                }
//
//                if (addDescendants) {
//                    // We are adding everything from here down so no need to do any further filtering.
//                    addDescendants(node.getChildren(), result);
//
//                } else {
//                    if (addChildren) {
//                        if (node.getChildren() != null) {
//                            result.addAll(node
//                                    .getChildren()
//                                    .stream()
//                                    .map(ExplorerNode::getDocRef)
//                                    .collect(Collectors.toSet()));
//                        }
//                    }
//
//                    // Recurse.
//                    addChildren(node.getChildren(), scope, result);
//                }
//            });
//        }
//    }
//
//    private void addDescendants(final List<ExplorerNode> children,
//                                final Set<DocRef> result) {
//        if (children != null) {
//            children.forEach(node -> {
//                result.add(node.getDocRef());
//                addDescendants(node.getChildren(), result);
//            });
//        }
//    }

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
