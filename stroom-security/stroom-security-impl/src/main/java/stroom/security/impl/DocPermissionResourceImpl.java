package stroom.security.impl;

import stroom.docref.DocRef;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
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
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.SimpleUser;
import stroom.security.shared.User;
import stroom.util.filter.FilterFieldMapper;
import stroom.util.filter.FilterFieldMappers;
import stroom.util.filter.QuickFilterPredicateFactory;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.PermissionException;

import event.logging.Event;
import event.logging.Group;
import event.logging.MultiObject;
import event.logging.OtherObject;
import event.logging.Outcome;
import event.logging.Permission;
import event.logging.PermissionAttribute;
import event.logging.Permissions;
import event.logging.UpdateEventAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
class DocPermissionResourceImpl implements DocPermissionResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocPermissionResourceImpl.class);

    private static final FilterFieldMappers<SimpleUser> SIMPLE_USERS_FILTER_FIELD_MAPPERS = FilterFieldMappers.of(
            FilterFieldMapper.of(FindUserCriteria.FIELD_DEF_NAME, SimpleUser::getName));

    private final Provider<UserService> userServiceProvider;
    private final Provider<DocumentPermissionServiceImpl> documentPermissionServiceProvider;
    private final Provider<DocumentTypePermissions> documentTypePermissionsProvider;
    private final Provider<ExplorerNodeService> explorerNodeServiceProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;
    //Todo
    // Permission checking should be responsibility of underlying service rather than REST resource impl
    private final Provider<SecurityContext> securityContextProvider;


    @Inject
    DocPermissionResourceImpl(final Provider<UserService> userServiceProvider,
                              final Provider<DocumentPermissionServiceImpl> documentPermissionServiceProvider,
                              final Provider<DocumentTypePermissions> documentTypePermissionsProvider,
                              final Provider<ExplorerNodeService> explorerNodeServiceProvider,
                              final Provider<SecurityContext> securityContextProvider,
                              final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider) {
        this.userServiceProvider = userServiceProvider;
        this.documentPermissionServiceProvider = documentPermissionServiceProvider;
        this.documentTypePermissionsProvider = documentTypePermissionsProvider;
        this.explorerNodeServiceProvider = explorerNodeServiceProvider;
        this.securityContextProvider = securityContextProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public Boolean changeDocumentPermissions(final ChangeDocumentPermissionsRequest request) {
        final DocRef docRef = request.getDocRef();

        // Check that the current user has permission to change the permissions of the document.
        if (securityContextProvider.get().hasDocumentPermission(docRef.getUuid(), DocumentPermissionNames.OWNER)) {
            // Record what documents and what users are affected by these changes
            // so we can clear the relevant caches.
            final Set<DocRef> affectedDocRefs = new HashSet<>();
            final Set<String> affectedUserUuids = new HashSet<>();

            // Change the permissions of the document.
            final Changes changes = request.getChanges();
            changeDocPermissions("DocPermissionResourceImpl.changeDocumentPermissions",
                    docRef, changes, affectedDocRefs, affectedUserUuids, false);

            // Cascade changes if this is a folder and we have been asked to do so.
            if (request.getCascade() != null) {
                cascadeChanges("DocPermissionResourceImpl.cascadeDocumentPermissions",
                        docRef, changes, affectedDocRefs, affectedUserUuids, request.getCascade());
            }

            return true;
        }

        final String errorMessage = "Insufficient privileges to change permissions for this document";

        logPermissionChangeError("DocPermissionResourceImpl.changeDocumentPermissions",
                request.getDocRef(), errorMessage);
        throw new PermissionException(getCurrentUserId(), errorMessage);

    }

    @Override
    @AutoLogged(value = OperationType.VIEW, verb = "Finding permissions of parent")
    public DocumentPermissions copyPermissionFromParent(final CopyPermissionsFromParentRequest request) {
        final DocRef docRef = request.getDocRef();

        boolean isUserAllowedToChangePermissions = securityContextProvider.get().hasDocumentPermission(
                docRef.getUuid(), DocumentPermissionNames.OWNER);
        if (!isUserAllowedToChangePermissions) {
            final String errorMessage = "Insufficient privileges to change permissions for this document";

            logPermissionChangeError("DocPermissionResourceImpl.copyPermissionFromParent",
                    request.getDocRef(), errorMessage);
            throw new PermissionException(getCurrentUserId(), errorMessage);
        }

        Optional<ExplorerNode> parent = explorerNodeServiceProvider.get().getParent(docRef);
        if (parent.isEmpty()) {
            throw new EntityServiceException("This node does not have a parent to copy permissions from!");
        }

        return documentPermissionServiceProvider.get().getPermissionsForDocument(parent.get().getDocRef().getUuid());
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public DocumentPermissions fetchAllDocumentPermissions(final FetchAllDocumentPermissionsRequest request) {
        if (securityContextProvider.get().hasDocumentPermission(request.getDocRef().getUuid(),
                DocumentPermissionNames.OWNER)) {
            return documentPermissionServiceProvider.get().getPermissionsForDocument(request.getDocRef().getUuid());
        }

        throw new PermissionException(getCurrentUserId(), "Insufficient privileges to fetch " +
                "permissions for this document");
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public Boolean checkDocumentPermission(final CheckDocumentPermissionRequest request) {
        return securityContextProvider.get().hasDocumentPermission(request.getDocumentUuid(), request.getPermission());
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public List<String> getPermissionForDocType(final String docType) {
        return documentTypePermissionsProvider.get().getPermissions(docType);
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED) // Limited benefit in logging the filtering of a list of user names
    public List<SimpleUser> filterUsers(final FilterUsersRequest filterUsersRequest) {
        if (filterUsersRequest.getUsers() == null) {
            return null;
        } else {
            // Not ideal calling the back end to filter some users but this is the only way to do the filtering
            // consistently across the app.
            return QuickFilterPredicateFactory.filterStream(
                    filterUsersRequest.getQuickFilterInput(),
                    SIMPLE_USERS_FILTER_FIELD_MAPPERS,
                    filterUsersRequest.getUsers().stream())
                    .collect(Collectors.toList());
        }
    }

    private void changeDocPermissions(final String eventTypeId,
                                      final DocRef docRef,
                                      final Changes changes,
                                      final Set<DocRef> affectedDocRefs,
                                      final Set<String> affectedUserUuids,
                                      final boolean clear) {
        if (clear) {
            // If we are asked to clear all permissions then get them for this document and then remove them.
            final DocumentPermissions documentPermissions = documentPermissionServiceProvider.get()
                    .getPermissionsForDocument(docRef.getUuid());
            for (final Map.Entry<String, Set<String>> entry : documentPermissions.getPermissions().entrySet()) {
                final String userUUid = entry.getKey();
                for (final String permission : entry.getValue()) {
                    try {
                        documentPermissionServiceProvider.get()
                                .removePermission(docRef.getUuid(), userUUid, permission);
                        logPermissionChange(eventTypeId, userUUid, docRef, permission, false);
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
                        documentPermissionServiceProvider.get()
                                .removePermission(docRef.getUuid(), userUuid, permission);
                        logPermissionChange(eventTypeId, userUuid, docRef, permission, false);
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
                if (DocumentTypes.isFolder(docRef.getType())
                        || !permission.startsWith(DocumentPermissionNames.CREATE)) {
                    try {
                        documentPermissionServiceProvider.get()
                                .addPermission(docRef.getUuid(), userUuid, permission);
                        logPermissionChange(eventTypeId, userUuid, docRef, permission, true);
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

//    private void cascadeChanges(final DocRef docRef, final ChangeSet<UserPermission> changeSet,
//    final Set<DocRef> affectedDocRefs,
//    final Set<User> affectedUsers, final ChangeDocumentPermissionsAction.Cascade cascade) {
//        final BaseEntity entity = genericEntityService.loadByUuid(docRef.getType(), docRef.getUuid());
//        if (entity != null) {
//            if (entity instanceof Folder) {
//                final Folder folder = (Folder) entity;
//
//                switch (cascade) {
//                    case CHANGES_ONLY:
//                        // We are only cascading changes so just pass on the change set.
//                        changeChildPermissions(
//                        DocRefUtil.create(folder), changeSet, affectedDocRefs, affectedUsers, false);
//                        break;
//
//                    case ALL:
//                        // We are replicating the permissions of the parent folder on all children
//                        so create a change set from the parent folder.
//                        final DocumentPermissions parentPermissions =
//                        documentPermissionService.getPermissionsForDocument(DocRefUtil.create(folder));
//                        final ChangeSet<UserPermission> fullChangeSet = new ChangeSet<>();
//                        for (final Map.Entry<User, Set<String>> entry :
//                        parentPermissions.getUserPermissions().entrySet()) {
//                            final User userRef = entry.getKey();
//                            for (final String permission : entry.getValue()) {
//                                fullChangeSet.add(new UserPermission(userRef, permission));
//                            }
//                        }
//
//                        // Set child permissions to that of the parent folder after clearing all
//                        permissions from child documents.
//                        changeChildPermissions(
//                        DocRefUtil.create(folder), fullChangeSet, affectedDocRefs, affectedUsers, true);
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
//    private void changeChildPermissions(
//    final DocRef folder,
//    final ChangeSet<UserPermission> changeSet,
//    final Set<DocRef> affectedDocRefs,
//    final Set<User> affectedUsers,
//    final boolean clear) {
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

    private void cascadeChanges(final String eventTypeId,
                                final DocRef docRef,
                                final Changes changes,
                                final Set<DocRef> affectedDocRefs,
                                final Set<String> affectedUserUuids,
                                final ChangeDocumentPermissionsRequest.Cascade cascade) {
        if (DocumentTypes.isFolder(docRef.getType())) {
            switch (cascade) {
                case CHANGES_ONLY:
                    // We are only cascading changes so just pass on the change set.
                    changeDescendantPermissions(eventTypeId, docRef, changes, affectedDocRefs,
                            affectedUserUuids, false);
                    break;

                case ALL:
                    // We are replicating the permissions of the parent folder on all children so create a change
                    // set from the parent folder.
                    final DocumentPermissions parentPermissions = documentPermissionServiceProvider
                            .get().getPermissionsForDocument(docRef.getUuid());
                    final Map<String, Set<String>> add = new HashMap<>();
                    for (final Entry<String, Set<String>> entry : parentPermissions.getPermissions().entrySet()) {
                        final String userUuid = entry.getKey();
                        for (final String permission : entry.getValue()) {
                            add.computeIfAbsent(userUuid, k -> new HashSet<>()).add(permission);
                        }
                    }

                    final Changes fullChangeSet = new Changes(add, new HashMap<>());

                    // Set child permissions to that of the parent folder after clearing all permissions from
                    // child documents.
                    changeDescendantPermissions(eventTypeId, docRef, fullChangeSet, affectedDocRefs,
                            affectedUserUuids, true);

                    break;

                case NO:
                    // Do nothing.
                    break;
                default:
                    throw new RuntimeException("Unexpected cascade " + cascade);
            }
        }
    }

    private void changeDescendantPermissions(final String eventTypeId,
                                             final DocRef folder,
                                             final Changes changes,
                                             final Set<DocRef> affectedDocRefs,
                                             final Set<String> affectedUserUuids,
                                             final boolean clear) {
        final List<ExplorerNode> descendants = explorerNodeServiceProvider.get().getDescendants(folder);
        if (descendants != null && descendants.size() > 0) {
            for (final ExplorerNode descendant : descendants) {
                // Ensure that the user has permission to change the permissions of this child.
                if (securityContextProvider.get().hasDocumentPermission(descendant.getUuid(),
                        DocumentPermissionNames.OWNER)) {
                    changeDocPermissions(eventTypeId, descendant.getDocRef(),
                            changes, affectedDocRefs, affectedUserUuids, clear);
                } else {
                    LOGGER.debug("User does not have permission to change permissions on " + descendant.toString());
                }
            }
        }
    }

    private String getCurrentUserId() {
        return securityContextProvider.get().getUserId();
    }

    private void logPermissionChange(final String typeId,
                                     final String userUuid,
                                     final DocRef docRefModified,
                                     final String permission,
                                     final boolean add) {
        try {
            final Optional<User> user = securityContextProvider.get().asProcessingUserResult(() ->
                    userServiceProvider.get().loadByUuid(userUuid));

            final Permission.Builder<Void> permissionBuilder = Permission.builder()
                    .addAllowAttributes(mapChangeItemToPermission(permission));
            if (user.isEmpty()) {
                LOGGER.warn("Unable to locate user for permission change " + userUuid);
                permissionBuilder.withUser(event.logging.User.builder().withId(docRefModified.getUuid()).build());
            } else if (user.get().isGroup()) {
                permissionBuilder.withGroup(Group.builder().withName(user.get().getName())
                        .withId(user.get().getUuid()).build());
            } else {
                permissionBuilder.withUser(event.logging.User.builder().withName(user.get().getName())
                        .withId(user.get().getUuid()).build());
            }

            final OtherObject object = OtherObject.builder()
                    .withDescription(docRefModified.toInfoString())
                    .withId(docRefModified.getUuid())
                    .withName(docRefModified.getName())
                    .withType(docRefModified.getType())
                    .withPermissions(
                            Permissions.builder().addPermissions(
                                    permissionBuilder.build())
                                    .build())
                    .build();

            final UpdateEventAction.Builder<Void> actionBuilder = UpdateEventAction.builder();
            if (add) {
                actionBuilder.withAfter(MultiObject.builder().addObject(object).build());
            } else {
                actionBuilder.withBefore(MultiObject.builder().addObject(object).build());
            }

            final Event event = stroomEventLoggingServiceProvider.get().createEvent(
                    typeId,
                    (add
                            ? "Adding"
                            : "Removing") +
                            " permission " + permission +
                            (add
                                    ? " to "
                                    : " from ") +
                            docRefModified.getType(),
                    actionBuilder.build());

            stroomEventLoggingServiceProvider.get().log(event);
        } catch (final RuntimeException e) {
            LOGGER.error("Unable to create authorisation event!", e);
        }
    }

    private void logPermissionChangeError(final String typeId,
                                          final DocRef docRefModified,
                                          final String outcomeDescription) {
        final Event event = stroomEventLoggingServiceProvider.get().createEvent(
                typeId,
                "Modify permission attempt failed",
                UpdateEventAction.builder().withBefore(
                        MultiObject.builder()
                                .addObject(
                                        OtherObject.builder()
                                                .withDescription(docRefModified.toInfoString())
                                                .withId(docRefModified.getUuid())
                                                .withName(docRefModified.getName())
                                                .withType(docRefModified.getType())
                                                .build()
                                )
                                .build())
                        .withOutcome(
                                Outcome.builder()
                                        .withSuccess(false)
                                        .withDescription(outcomeDescription)
                                        .build())
                        .build());

        stroomEventLoggingServiceProvider.get().log(event);
    }

    private PermissionAttribute mapChangeItemToPermission(final String perm) {
        if (DocumentPermissionNames.DELETE.equals(perm)) {
            return PermissionAttribute.WRITE;
        } else if (DocumentPermissionNames.OWNER.equals(perm)) {
            return PermissionAttribute.OWNER;
        } else if (DocumentPermissionNames.READ.equals(perm)) {
            return PermissionAttribute.READ;
        } else if (DocumentPermissionNames.UPDATE.equals(perm)) {
            return PermissionAttribute.WRITE;
        } else if (DocumentPermissionNames.USE.equals(perm)) {
            return PermissionAttribute.EXECUTE;
        } else if (perm != null && perm.startsWith(DocumentPermissionNames.CREATE)) {
            // Create perms are used on folders only and the perm is of the form 'Create - Feed'
            return PermissionAttribute.AUTHOR;
        } else {
            LOGGER.error("Unrecognised permission assigned " + perm);
            return null;
        }
    }
}
