package stroom.security.impl;

import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerNode;
import stroom.security.api.SecurityContext;
import stroom.security.shared.ChangeDocumentPermissionsRequest;
import stroom.security.shared.Changes;
import stroom.security.shared.CheckDocumentPermissionRequest;
import stroom.security.shared.CopyPermissionsFromParentRequest;
import stroom.security.shared.DocPermissionResource;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.FetchAllDocumentPermissionsRequest;
import stroom.security.shared.FilterUsersRequest;
import stroom.security.shared.User;
import stroom.util.filter.QuickFilterPredicateFactory;
import stroom.util.shared.EntityServiceException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

class DocPermissionResourceImpl implements DocPermissionResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocPermissionResourceImpl.class);

    private final DocumentPermissionServiceImpl documentPermissionService;
    private final DocumentTypePermissions documentTypePermissions;
    private final ExplorerNodeService explorerNodeService;
    private final SecurityContext securityContext;

    @Inject
    DocPermissionResourceImpl(final DocumentPermissionServiceImpl documentPermissionService,
                              final DocumentTypePermissions documentTypePermissions,
                              final ExplorerNodeService explorerNodeService,
                              final SecurityContext securityContext) {
        this.documentPermissionService = documentPermissionService;
        this.documentTypePermissions = documentTypePermissions;
        this.explorerNodeService = explorerNodeService;
        this.securityContext = securityContext;
    }

    @Override
    public Boolean changeDocumentPermissions(final ChangeDocumentPermissionsRequest request) {
        return securityContext.insecureResult(() -> {
            final DocRef docRef = request.getDocRef();

            // Check that the current user has permission to change the permissions of the document.
            if (securityContext.hasDocumentPermission(docRef.getUuid(), DocumentPermissionNames.OWNER)) {
                // Record what documents and what users are affected by these changes so we can clear the relevant caches.
                final Set<DocRef> affectedDocRefs = new HashSet<>();
                final Set<String> affectedUserUuids = new HashSet<>();

                // Change the permissions of the document.
                final Changes changes = request.getChanges();
                changeDocPermissions(docRef, changes, affectedDocRefs, affectedUserUuids, false);

                // Cascade changes if this is a folder and we have been asked to do so.
                if (request.getCascade() != null) {
                    cascadeChanges(docRef, changes, affectedDocRefs, affectedUserUuids, request.getCascade());
                }

                return true;
            }

            throw new EntityServiceException("You do not have sufficient privileges to change permissions for this document");
        });
    }

    @Override
    public DocumentPermissions copyPermissionFromParent(final CopyPermissionsFromParentRequest request) {
        final DocRef docRef = request.getDocRef();

        boolean isUserAllowedToChangePermissions = securityContext.
                hasDocumentPermission(docRef.getUuid(), DocumentPermissionNames.OWNER);
        if (!isUserAllowedToChangePermissions) {
            throw new EntityServiceException("You do not have sufficient privileges to change permissions for this document!");
        }

        Optional<ExplorerNode> parent = explorerNodeService.getParent(docRef);
        if (!parent.isPresent()) {
            throw new EntityServiceException("This node does not have a parent to copy permissions from!");
        }

        return documentPermissionService.getPermissionsForDocument(parent.get().getDocRef().getUuid());
    }

    @Override
    public DocumentPermissions fetchAllDocumentPermissions(final FetchAllDocumentPermissionsRequest request) {
        return securityContext.insecureResult(() -> {
            if (securityContext.hasDocumentPermission(request.getDocRef().getUuid(), DocumentPermissionNames.OWNER)) {
                return documentPermissionService.getPermissionsForDocument(request.getDocRef().getUuid());
            }

            throw new EntityServiceException("You do not have sufficient privileges to fetch permissions for this document");
        });
    }

    @Override
    public Boolean checkDocumentPermission(final CheckDocumentPermissionRequest request) {
        return securityContext.insecureResult(() -> securityContext.hasDocumentPermission(request.getDocumentUuid(), request.getPermission()));
    }

    @Override
    public List<String> getPermissionForDocType(final String docType) {
        return documentTypePermissions.getPermissions(docType);
    }

    @Override
    public List<User> filterUsers(final FilterUsersRequest filterUsersRequest) {
        // Not ideal calling the back end to filter some users but this is the only way to do the filtering
        // consistently across the app.
        final Predicate<User> quickFilterPredicate = QuickFilterPredicateFactory.createFuzzyMatchPredicate(
                filterUsersRequest.getQuickFilterInput(), UserDao.FILTER_FIELD_MAPPERS);
        if (filterUsersRequest.getUsers() == null) {
            return null;
        } else {
            return filterUsersRequest.getUsers().stream()
                    .filter(quickFilterPredicate)
                    .collect(Collectors.toList());
        }
    }

    private void changeDocPermissions(final DocRef docRef,
                                      final Changes changes,
                                      final Set<DocRef> affectedDocRefs,
                                      final Set<String> affectedUserUuids,
                                      final boolean clear) {
        if (clear) {
            // If we are asked to clear all permissions then get them for this document and then remove them.
            final DocumentPermissions documentPermissions = documentPermissionService.getPermissionsForDocument(docRef.getUuid());
            for (final Map.Entry<String, Set<String>> entry : documentPermissions.getPermissions().entrySet()) {
                final String userUUid = entry.getKey();
                for (final String permission : entry.getValue()) {
                    try {
                        documentPermissionService.removePermission(docRef.getUuid(), userUUid, permission);
                        // Remember the affected documents and users so we can clear the relevant caches.
                        affectedDocRefs.add(docRef);
                        affectedUserUuids.add(userUUid);
                    } catch (final RuntimeException e) {
                        // Expected.
                        LOGGER.debug(e.getMessage());
                    }
                }
            }

        } else {
            // Otherwise remove permissions specified by the change set.
            for (final Entry<String, Set<String>> entry : changes.getRemove().entrySet()) {
                final String userUuid = entry.getKey();
                for (final String permission : entry.getValue()) {
                    try {
                        documentPermissionService.removePermission(docRef.getUuid(), userUuid, permission);
                        // Remember the affected documents and users so we can clear the relevant caches.
                        affectedDocRefs.add(docRef);
                        affectedUserUuids.add(userUuid);
                    } catch (final RuntimeException e) {
                        // Expected.
                        LOGGER.debug(e.getMessage());
                    }
                }
            }
        }

        // Add permissions from the change set.
        for (final Entry<String, Set<String>> entry : changes.getAdd().entrySet()) {
            final String userUuid = entry.getKey();
            for (final String permission : entry.getValue()) {
                // Don't add create permissions to items that aren't folders as it makes no sense.
                if (DocumentTypes.isFolder(docRef.getType()) || !permission.startsWith(DocumentPermissionNames.CREATE)) {
                    try {
                        documentPermissionService.addPermission(docRef.getUuid(), userUuid, permission);
                        // Remember the affected documents and users so we can clear the relevant caches.
                        affectedDocRefs.add(docRef);
                        affectedUserUuids.add(userUuid);
                    } catch (final RuntimeException e) {
                        // Expected.
                        LOGGER.debug(e.getMessage());
                    }
                }
            }
        }
    }

//    private void cascadeChanges(final DocRef docRef, final ChangeSet<UserPermission> changeSet, final Set<DocRef> affectedDocRefs, final Set<User> affectedUsers, final ChangeDocumentPermissionsAction.Cascade cascade) {
//        final BaseEntity entity = genericEntityService.loadByUuid(docRef.getType(), docRef.getUuid());
//        if (entity != null) {
//            if (entity instanceof Folder) {
//                final Folder folder = (Folder) entity;
//
//                switch (cascade) {
//                    case CHANGES_ONLY:
//                        // We are only cascading changes so just pass on the change set.
//                        changeChildPermissions(DocRefUtil.create(folder), changeSet, affectedDocRefs, affectedUsers, false);
//                        break;
//
//                    case ALL:
//                        // We are replicating the permissions of the parent folder on all children so create a change set from the parent folder.
//                        final DocumentPermissions parentPermissions = documentPermissionService.getPermissionsForDocument(DocRefUtil.create(folder));
//                        final ChangeSet<UserPermission> fullChangeSet = new ChangeSet<>();
//                        for (final Map.Entry<User, Set<String>> entry : parentPermissions.getUserPermissions().entrySet()) {
//                            final User userRef = entry.getKey();
//                            for (final String permission : entry.getValue()) {
//                                fullChangeSet.add(new UserPermission(userRef, permission));
//                            }
//                        }
//
//                        // Set child permissions to that of the parent folder after clearing all permissions from child documents.
//                        changeChildPermissions(DocRefUtil.create(folder), fullChangeSet, affectedDocRefs, affectedUsers, true);
//
//                    break;
//
//                case NO:
//                    // Do nothing.
//                    break;
//            }
//        }
//    }
//
//    private void changeChildPermissions(final DocRef folder, final ChangeSet<UserPermission> changeSet, final Set<DocRef> affectedDocRefs, final Set<User> affectedUsers, final boolean clear) {
//        final List<String> types = getTypeList();
//        for (final String type : types) {
//            final List<DocumentEntity> children = genericEntityService.findByFolder(type, folder, null);
//            if (children != null && children.size() > 0) {
//                for (final DocumentEntity child : children) {
//                    final DocRef childDocRef = DocRefUtil.create(child);
//                    changeDocPermissions(childDocRef, changeSet, affectedDocRefs, affectedUsers, clear);
//
//                    if (child instanceof Folder) {
//                        changeChildPermissions(childDocRef, changeSet, affectedDocRefs, affectedUsers, clear);
//                    }
//                }
//            }
//        }
//    }

    private void cascadeChanges(final DocRef docRef,
                                final Changes changes,
                                final Set<DocRef> affectedDocRefs,
                                final Set<String> affectedUserUuids,
                                final ChangeDocumentPermissionsRequest.Cascade cascade) {
        if (DocumentTypes.isFolder(docRef.getType())) {
            switch (cascade) {
                case CHANGES_ONLY:
                    // We are only cascading changes so just pass on the change set.
                    changeDescendantPermissions(docRef, changes, affectedDocRefs, affectedUserUuids, false);
                    break;

                case ALL:
                    // We are replicating the permissions of the parent folder on all children so create a change set from the parent folder.
                    final DocumentPermissions parentPermissions = documentPermissionService.getPermissionsForDocument(docRef.getUuid());
                    final Map<String, Set<String>> add = new HashMap<>();
                    for (final Entry<String, Set<String>> entry : parentPermissions.getPermissions().entrySet()) {
                        final String userUuid = entry.getKey();
                        for (final String permission : entry.getValue()) {
                            add.computeIfAbsent(userUuid, k -> new HashSet<>()).add(permission);
                        }
                    }

                    final Changes fullChangeSet = new Changes(add, new HashMap<>());

                    // Set child permissions to that of the parent folder after clearing all permissions from child documents.
                    changeDescendantPermissions(docRef, fullChangeSet, affectedDocRefs, affectedUserUuids, true);

                    break;

                case NO:
                    // Do nothing.
                    break;
            }
        }
    }

    private void changeDescendantPermissions(final DocRef folder,
                                             final Changes changes,
                                             final Set<DocRef> affectedDocRefs,
                                             final Set<String> affectedUserUuids,
                                             final boolean clear) {
        final List<ExplorerNode> descendants = explorerNodeService.getDescendants(folder);
        if (descendants != null && descendants.size() > 0) {
            for (final ExplorerNode descendant : descendants) {
                // Ensure that the user has permission to change the permissions of this child.
                if (securityContext.hasDocumentPermission(descendant.getUuid(), DocumentPermissionNames.OWNER)) {
                    changeDocPermissions(descendant.getDocRef(), changes, affectedDocRefs, affectedUserUuids, clear);
                } else {
                    LOGGER.debug("User does not have permission to change permissions on " + descendant.toString());
                }
            }
        }
    }
}
