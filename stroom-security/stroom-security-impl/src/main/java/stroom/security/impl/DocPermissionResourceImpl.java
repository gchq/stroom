package stroom.security.impl;

import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.DocumentTypes;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.ChangeDocumentPermissionsRequest;
import stroom.security.shared.ChangeDocumentPermissionsRequest.Cascade;
import stroom.security.shared.Changes;
import stroom.security.shared.CheckDocumentPermissionRequest;
import stroom.security.shared.CopyPermissionsFromParentRequest;
import stroom.security.shared.DocPermissionResource;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.FetchAllDocumentPermissionsRequest;
import stroom.security.shared.FilterUsersRequest;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.User;
import stroom.util.NullSafe;
import stroom.util.filter.FilterFieldMapper;
import stroom.util.filter.FilterFieldMappers;
import stroom.util.filter.QuickFilterPredicateFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.PermissionException;
import stroom.util.shared.UserName;

import com.google.common.base.Strings;
import event.logging.AuthorisationActionType;
import event.logging.AuthoriseEventAction;
import event.logging.ComplexLoggedOutcome;
import event.logging.Data;
import event.logging.Data.Builder;
import event.logging.Document;
import event.logging.Event;
import event.logging.MultiObject;
import event.logging.OtherObject;
import event.logging.Outcome;
import event.logging.Permission;
import event.logging.PermissionAttribute;
import event.logging.Permissions;
import event.logging.UpdateEventAction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
class DocPermissionResourceImpl implements DocPermissionResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocPermissionResourceImpl.class);

    private static final FilterFieldMappers<UserName> USER_NAMES_FILTER_FIELD_MAPPERS = FilterFieldMappers.of(
            FilterFieldMapper.of(FindUserCriteria.FIELD_DEF_NAME, UserName::getSubjectId),
            FilterFieldMapper.of(FindUserCriteria.FIELD_DEF_DISPLAY_NAME, UserName::getDisplayName),
            FilterFieldMapper.of(FindUserCriteria.FIELD_DEF_FULL_NAME, UserName::getFullName));

    private final Provider<UserService> userServiceProvider;
    private final Provider<DocumentPermissionServiceImpl> documentPermissionServiceProvider;
    private final Provider<DocumentTypePermissions> documentTypePermissionsProvider;
    private final Provider<ExplorerNodeService> explorerNodeServiceProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;
    private final Provider<DocRefInfoService> docRefInfoServiceProvider;
    //Todo
    // Permission checking should be responsibility of underlying service rather than REST resource impl
    private final Provider<SecurityContext> securityContextProvider;


    @Inject
    DocPermissionResourceImpl(final Provider<UserService> userServiceProvider,
                              final Provider<DocumentPermissionServiceImpl> documentPermissionServiceProvider,
                              final Provider<DocumentTypePermissions> documentTypePermissionsProvider,
                              final Provider<ExplorerNodeService> explorerNodeServiceProvider,
                              final Provider<SecurityContext> securityContextProvider,
                              final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider,
                              final Provider<DocRefInfoService> docRefInfoServiceProvider) {
        this.userServiceProvider = userServiceProvider;
        this.documentPermissionServiceProvider = documentPermissionServiceProvider;
        this.documentTypePermissionsProvider = documentTypePermissionsProvider;
        this.explorerNodeServiceProvider = explorerNodeServiceProvider;
        this.securityContextProvider = securityContextProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
        this.docRefInfoServiceProvider = docRefInfoServiceProvider;
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
            changeDocPermissionsWithLogging(
                    "DocPermissionResourceImpl.changeDocumentPermissions",
                    docRef,
                    changes,
                    affectedDocRefs,
                    affectedUserUuids,
                    false,
                    request.getCascade());

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
        throw new PermissionException(getCurrentUserIdForDisplay(), errorMessage);

    }

    @Override
    @AutoLogged(value = OperationType.VIEW, verb = "Finding permissions of parent")
    public DocumentPermissions copyPermissionFromParent(final CopyPermissionsFromParentRequest request) {
        final DocRef docRef = request.getDocRef();

        LOGGER.debug("copyPermissionFromParent() - docRef: {}", docRef);

        boolean isUserAllowedToChangePermissions = securityContextProvider.get().hasDocumentPermission(
                docRef.getUuid(), DocumentPermissionNames.OWNER);
        if (!isUserAllowedToChangePermissions) {
            final String errorMessage = "Insufficient privileges to change permissions for this document";

            logPermissionChangeError("DocPermissionResourceImpl.copyPermissionFromParent",
                    request.getDocRef(), errorMessage);
            throw new PermissionException(getCurrentUserIdForDisplay(), errorMessage);
        }

        ExplorerNode parent = explorerNodeServiceProvider.get()
                .getParent(docRef)
                .orElseThrow(() ->
                        new EntityServiceException("This node does not have a parent to copy permissions from!"));

        final DocRef parentDocRef = parent.getDocRef();
        LOGGER.debug("parentDocRef: {}", parentDocRef);
        final DocumentPermissions documentPermissions = documentPermissionServiceProvider.get()
                .getPermissionsForDocument(parentDocRef.getUuid());
        final DocumentPermissions updatedDocumentPermissions;

        if (ExplorerConstants.isFolder(parentDocRef)) {
            updatedDocumentPermissions = documentPermissions;
        } else {
            // Not a folder, so we need to exclude all the folder create perms as they are not
            // applicable for a leaf doc
            final Map<String, Set<String>> updatedPerms = documentPermissions.getPermissions()
                    .entrySet()
                    .stream()
                    .map(entry -> {
                        final Set<String> newPermSet = DocumentPermissionNames.excludeCreatePermissions(
                                entry.getValue());
                        if (newPermSet.isEmpty()) {
                            return null;
                        } else {
                            return Map.entry(entry.getKey(), newPermSet);
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

            updatedDocumentPermissions = documentPermissions.copy()
                    .permissions(updatedPerms)
                    .build();
        }

        LOGGER.debug(() -> "Returning permissions:\n  " + String.join("\n  ", mapPerms(
                userServiceProvider.get(),
                updatedDocumentPermissions.getPermissions())));
        return updatedDocumentPermissions;
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public DocumentPermissions fetchAllDocumentPermissions(final FetchAllDocumentPermissionsRequest request) {
        if (securityContextProvider.get().hasDocumentPermission(request.getDocRef().getUuid(),
                DocumentPermissionNames.OWNER)) {
            return documentPermissionServiceProvider.get().getPermissionsForDocument(request.getDocRef().getUuid());
        }

        throw new PermissionException(
                getCurrentUserIdForDisplay(),
                "Insufficient privileges to fetch permissions for this document");
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED) // Only log failures
    public Boolean checkDocumentPermission(final CheckDocumentPermissionRequest request) {
        final boolean hasPerm;
        try {
            Objects.requireNonNull(request);
            hasPerm = securityContextProvider.get().hasDocumentPermission(
                    request.getDocumentUuid(),
                    request.getPermission());
            if (!hasPerm) {
                // Only want to log a failure
                logPermCheckFailure(request, null);
            }
        } catch (Exception e) {
            try {
                logPermCheckFailure(request, e);
            } catch (Exception ex) {
                LOGGER.error("Error logging event: {}", e.getMessage(), e);
            }
            throw new RuntimeException(e);
        }

        return hasPerm;
    }

    private void logPermCheckFailure(final CheckDocumentPermissionRequest request,
                                     final Throwable e) {
        final String uuid = request.getDocumentUuid();
        final String name = NullSafe.getOrElse(uuid,
                uuid2 -> {
                    final Optional<DocRefInfo> optInfo = docRefInfoServiceProvider.get()
                            .info(DocRef.builder().uuid(uuid2).build());
                    return optInfo.map(DocRefInfo::getDocRef)
                            .map(DocRef::getName)
                            .orElse(null);
                },
                "?");

        final String msg = LogUtil.message("User failed permissions check for permission {} document {} ({})",
                request.getPermission(),
                name,
                uuid);

        stroomEventLoggingServiceProvider.get().log(
                StroomEventLoggingUtil.buildTypeId(this, "checkDocumentPermission"),
                msg,
                AuthoriseEventAction.builder()
                        .withAction(AuthorisationActionType.REQUEST)
                        .addDocument(Document.builder()
                                .withId(uuid)
                                .withName(name)
                                .build())
                        .withOutcome(Outcome.builder()
                                .withSuccess(false)
                                .withDescription(NullSafe.getOrElse(e, Throwable::getMessage, msg))
                                .build())
                        .build());
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public List<String> getPermissionForDocType(final String docType) {
        return documentTypePermissionsProvider.get().getPermissions(docType);
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED) // Limited benefit in logging the filtering of a list of user names
    public List<UserName> filterUsers(final FilterUsersRequest filterUsersRequest) {
        if (filterUsersRequest.getUsers() == null) {
            return null;
        } else {
            // Not ideal calling the back end to filter some users but this is the only way to do the filtering
            // consistently across the app.
            return QuickFilterPredicateFactory.filterStream(
                            filterUsersRequest.getQuickFilterInput(),
                            USER_NAMES_FILTER_FIELD_MAPPERS,
                            NullSafe.stream(filterUsersRequest.getUsers()))
                    .collect(Collectors.toList());
        }
    }

    private void changeDocPermissionsWithLogging(final String eventTypeId,
                                                 final DocRef docRef,
                                                 final Changes changes,
                                                 final Set<DocRef> affectedDocRefs,
                                                 final Set<String> affectedUserUuids,
                                                 final boolean clear,
                                                 final Cascade cascade) {

        final DocumentPermissionServiceImpl documentPermissionService = documentPermissionServiceProvider.get();
        final DocumentPermissions documentPermissionsBefore = documentPermissionService.getPermissionsForDocument(
                docRef.getUuid());


//        changes.getRemove()
//                .forEach((uuid, perms) ->
//                        LOGGER.info("Remove: " + uuid + " " + perms.size() + " " + String.join(", ", perms)));
//        changes.getAdd()
//                .forEach((uuid, perms) ->
//                        LOGGER.info("Add: " + uuid + " " + perms.size() + " " + String.join(", ", perms)));

        final int removeCount;
        if (clear) {
            removeCount = documentPermissionsBefore.getPermissions()
                    .values()
                    .stream()
                    .mapToInt(Set::size)
                    .sum();
        } else {
            removeCount = changes.getRemove()
                    .values()
                    .stream()
                    .mapToInt(Set::size)
                    .sum();
        }
        final int addCount = changes.getAdd()
                .values()
                .stream()
                .mapToInt(Set::size)
                .sum();

        final String cascadeText = switch (Objects.requireNonNullElse(cascade, Cascade.NO)) {
            case ALL -> ", cascade all";
            case CHANGES_ONLY -> ", cascade changes";
            default -> "";
        };

        final String description = LogUtil.message("Changing permissions (removing {}, adding {}) on {} {}{}.",
                removeCount,
                addCount,
                docRef.getType(),
                docRef.getName(),
                cascadeText);

        // It is possible in future that we could use Delta rather than Before/After
        // See https://github.com/gchq/event-logging-schema/issues/75
        final MultiObject before = buildPermissionState(docRef, documentPermissionsBefore);
        stroomEventLoggingServiceProvider.get()
                .loggedWorkBuilder()
                .withTypeId(eventTypeId)
                .withDescription(description)
                .withDefaultEventAction(UpdateEventAction.builder()
                        .withBefore(before)
                        .withAfter(before) // If successful we overwrite with after, but ensures we have one
                        .build())
                .withComplexLoggedAction(updateEventAction -> {
                    // Do the actual change
                    changeDocPermissions(
                            docRef,
                            changes,
                            affectedDocRefs,
                            affectedUserUuids,
                            clear,
                            documentPermissionsBefore);

                    final DocumentPermissions documentPermissionsAfter = documentPermissionService
                            .getPermissionsForDocument(docRef.getUuid());

                    // Add in the after state
                    UpdateEventAction modifiedEventAction = updateEventAction.newCopyBuilder()
                            .withAfter(buildPermissionState(docRef, documentPermissionsAfter))
                            .build();
                    return ComplexLoggedOutcome.success(modifiedEventAction);
                })
                .runActionAndLog();
    }

    private void validateOwners(final Changes changes, final DocumentPermissions currentDocumentPermissions) {
        final SecurityContext securityContext = securityContextProvider.get();

        final Set<String> ownerUuidsBefore = NullSafe.stream(currentDocumentPermissions.getOwners())
                .map(User::getUuid)
                .collect(Collectors.toSet());

        final Set<String> effectiveOwnerUuids = new HashSet<>(ownerUuidsBefore);
        // Apply the removes
        NullSafe.map(changes.getRemove())
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().contains(DocumentPermissionNames.OWNER))
                .map(Entry::getKey)
                .forEach(effectiveOwnerUuids::remove);
        // Apply the adds
        NullSafe.map(changes.getAdd())
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().contains(DocumentPermissionNames.OWNER))
                .map(Entry::getKey)
                .forEach(effectiveOwnerUuids::add);
        final boolean hasOwnerChanged = !Objects.equals(effectiveOwnerUuids, ownerUuidsBefore);
        final String changeOwnerPermName = PermissionNames.CHANGE_OWNER_PERMISSION;
        final String permMsg = !securityContext.hasAppPermission(changeOwnerPermName)
                ? " Also, " + changeOwnerPermName + " permission is required to change the owner and you do " +
                "not hold this permission."
                : "";

        if (effectiveOwnerUuids.isEmpty()) {
            throw new PermissionException(
                    securityContextProvider.get().getUserIdentityForAudit(),
                    LogUtil.message("A document/folder must have exactly one owner. " +
                            "Requested changes would result in no owners.{}", permMsg));
        } else if (effectiveOwnerUuids.size() > 1) {
            final UserService userService = userServiceProvider.get();
            final String effectiveOwnersStr = effectiveOwnerUuids.stream()
                    .map(userService::loadByUuid)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(UserName::getUserIdentityForAudit)
                    .collect(Collectors.joining(", "));
            throw new PermissionException(
                    securityContextProvider.get().getUserIdentityForAudit(),
                    LogUtil.message("A document/folder must have exactly one owner. " +
                                    "Requested changes would result in the following owners [{}].{}",
                            effectiveOwnersStr, permMsg));
        }

        if (hasOwnerChanged
                && !securityContextProvider.get().hasAppPermission(changeOwnerPermName)) {
            throw new PermissionException(
                    securityContextProvider.get().getUserIdentityForAudit(),
                    LogUtil.message("{} permission is required to change the owner of a document/folder",
                            changeOwnerPermName));
        }
    }

    private MultiObject buildPermissionState(final DocRef docRef,
                                             final DocumentPermissions documentPermissions) {

        final Permissions.Builder<Void> permissionsBuilder = Permissions.builder();
        final Builder<Void> rootDataBuilder = Data.builder()
                .withName("createPermissionsByUser");

        if (NullSafe.hasEntries(documentPermissions, DocumentPermissions::getPermissions)) {
            documentPermissions.getPermissions().forEach((userUuid, permissions) -> {
                final Optional<User> optUser = securityContextProvider.get().asProcessingUserResult(() ->
                        userServiceProvider.get().loadByUuid(userUuid));

                final Permission.Builder<Void> permissionBuilder = Permission.builder();
                if (optUser.isEmpty()) {
                    LOGGER.warn("Unable to locate user for permission change " + userUuid);
                    permissionBuilder.withUser(event.logging.User.builder()
                            .withId(userUuid)
                            .build());
                } else {
                    final User userOrGroup = optUser.get();
                    if (userOrGroup.isGroup()) {
                        permissionBuilder.withGroup(StroomEventLoggingUtil.createGroup(userOrGroup));
                    } else {
                        permissionBuilder.withUser(StroomEventLoggingUtil.createUser(userOrGroup));
                    }

                    // Have to use Data elements to hold the Create perms as the schema currently has no support
                    // for custom perms. Waiting for https://github.com/gchq/event-logging-schema/issues/76
                    final Data userData = Data.builder()
                            .withName(userOrGroup.getSubjectId())
                            .withValue(userOrGroup.isGroup()
                                    ? "group"
                                    : "user")
                            .addData(documentPermissions.getPermissions().get(userUuid)
                                    .stream()
                                    .filter(perm -> perm != null && perm.startsWith(DocumentPermissionNames.CREATE))
                                    .map(perm -> Data.builder()
                                            .withName(perm)
                                            .build())
                                    .collect(Collectors.toSet()))
                            .build();

                    if (!userData.getData().isEmpty()) {
                        rootDataBuilder
                                .addData(userData)
                                .build();
                    }
                }
                permissionBuilder.withAllowAttributes(mapChangeItemsToPermissions(permissions));
                permissionsBuilder.addPermissions(permissionBuilder.build());
            });
        }

        final OtherObject.Builder<Void> otherObjectBuilder = OtherObject.builder()
                .withDescription(docRef.toInfoString())
                .withId(docRef.getUuid())
                .withName(docRef.getName())
                .withType(docRef.getType());

        final Permissions permissions = permissionsBuilder.build();
        if (NullSafe.hasItems(permissions, Permissions::getPermissions)) {
            otherObjectBuilder.withPermissions(permissions);
        }

        final Data rootData = rootDataBuilder.build();
        if (NullSafe.hasItems(rootData, Data::getData)) {
            otherObjectBuilder.withData(rootDataBuilder.build());
        }

        return MultiObject.builder()
                .withObjects(otherObjectBuilder.build())
                .build();
    }

    private void changeDocPermissions(final DocRef docRef,
                                      final Changes changes,
                                      final Set<DocRef> affectedDocRefs,
                                      final Set<String> affectedUserUuids,
                                      final boolean clear,
                                      final DocumentPermissions documentPermissionsBefore) {
        final DocumentPermissionServiceImpl documentPermissionServiceImpl = documentPermissionServiceProvider.get();
        final DocumentPermissions currentDocumentPermissions = Objects.requireNonNullElseGet(
                documentPermissionsBefore,
                () -> documentPermissionServiceImpl
                        .getPermissionsForDocument(docRef.getUuid()));

        if (LOGGER.isDebugEnabled()) {
            logRequest(changes, currentDocumentPermissions, LOGGER::debug);
        }

        // This is done client side, but to be safe do it again
        validateOwners(changes, currentDocumentPermissions);

        removeInvalidCreatePerms(docRef, documentPermissionServiceImpl, currentDocumentPermissions);

        if (clear) {
            // If we are asked to clear all permissions then use all the current perms for
            // this document and then remove them.
            for (final Map.Entry<String, Set<String>> entry : currentDocumentPermissions.getPermissions().entrySet()) {
                final String userUUid = entry.getKey();
                final Set<String> permissions = entry.getValue();
                try {
                    documentPermissionServiceImpl.removePermissions(docRef.getUuid(), userUUid, permissions);
                    // Remember the affected documents and users so we can clear the relevant caches.
                    affectedDocRefs.add(docRef);
                    affectedUserUuids.add(userUUid);
                } catch (final RuntimeException e) {
                    // Expected.
                    LOGGER.debug(e.getMessage());
                }
            }

        } else {
            // Otherwise remove permissions specified by the change set.
            for (final Entry<String, Set<String>> entry : changes.getRemove().entrySet()) {
                final String userUuid = entry.getKey();
                final Set<String> permissions = entry.getValue();
                try {
                    documentPermissionServiceImpl.removePermissions(docRef.getUuid(), userUuid, permissions);
                    // Remember the affected documents and users so we can clear the relevant caches.
                    affectedDocRefs.add(docRef);
                    affectedUserUuids.add(userUuid);
                } catch (final RuntimeException e) {
                    // Expected.
                    LOGGER.debug(e.getMessage());
                }
            }
        }

        // Add permissions from the change set.
        for (final Entry<String, Set<String>> entry : changes.getAdd().entrySet()) {
            final String userUuid = entry.getKey();
            for (final String permission : entry.getValue()) {
                if (DocumentTypes.isFolder(docRef.getType())
                        || !permission.startsWith(DocumentPermissionNames.CREATE)) {
                    try {
                        if (DocumentPermissionNames.OWNER.equals(permission)) {
                            documentPermissionServiceImpl.setDocumentOwner(docRef.getUuid(), userUuid);
                        } else {
                            documentPermissionServiceImpl.addPermission(docRef.getUuid(), userUuid, permission);
                        }

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

    /**
     * Previous code incorrectly assigned create perms to non-folders. While not causing a problem, they
     * are cluttering up the db. It is not possible to tidy these up via a mig script as the security module
     * does not know the types of the docs in the perms tables, so we will do it gradually via other changes.
     */
    private void removeInvalidCreatePerms(final DocRef docRef,
                                          final DocumentPermissionServiceImpl documentPermissionServiceImpl,
                                          final DocumentPermissions currentDocumentPermissions) {

        try {
            if (!DocumentTypes.isFolder(docRef.getType())) {
                // Not a folder, so should not have create perms on it, so remove them
                securityContextProvider.get().asProcessingUser(() -> {
                    NullSafe.map(currentDocumentPermissions.getPermissions()).forEach((userUuid, perms) -> {
                        final Set<String> createPerms = NullSafe.set(perms)
                                .stream()
                                .filter(DocumentPermissionNames::isDocumentCreatePermission)
                                .collect(Collectors.toSet());
                        if (!createPerms.isEmpty()) {
                            LOGGER.info("Removing {} redundant create permissions from {} for userUuid '{}'. " +
                                            "Permissions removed: {}",
                                    createPerms.size(), docRef, userUuid, createPerms);
                            documentPermissionServiceImpl.removePermissions(
                                    docRef.getUuid(),
                                    userUuid,
                                    createPerms);
                        }
                    });
                });
            }
        } catch (Exception e) {
            LOGGER.error("Error removing redundant create perms from {}: {}",
                    docRef, e.getMessage(), e);
            // Swallow it as this work is only a nice to have
        }
    }

    private List<String> mapPerms(final UserService userService, final Map<String, Set<String>> perms) {
        return NullSafe.map(perms)
                .entrySet()
                .stream()
                .map(entry -> {
                    final String userUuid = entry.getKey();
                    final String userNameStr = userService.loadByUuid(userUuid)
                            .map(user ->
                                    Strings.padEnd(user.getType().toUpperCase(), 5, ' ')
                                            + " "
                                            + user.getUserIdentityForAudit())
                            .orElse("null");
                    final String permStr = entry.getValue()
                            .stream()
                            .sorted()
                            .collect(Collectors.joining(", "));
                    return userNameStr + " => [" + permStr + "]";
                })
                .toList();
    }

    private void logRequest(final Changes changes,
                            final DocumentPermissions currentDocumentPermissions,
                            final Consumer<String> logMessageConsumer) {

        final String ownersStr = currentDocumentPermissions.getOwners()
                .stream()
                .map((User user) -> user.getType().toUpperCase()
                        + " "
                        + user.getUserIdentityForAudit())
                .collect(Collectors.joining(", "));

        final UserService userService = userServiceProvider.get();
        final String currentPerms = String.join("\n  ", mapPerms(
                userService,
                currentDocumentPermissions.getPermissions()));
        final String addsStr = String.join("\n  ", mapPerms(
                userService,
                changes.getAdd()));
        final String removesStr = String.join("\n  ", mapPerms(
                userService,
                changes.getRemove()));

        logMessageConsumer.accept(LogUtil.message("""
                        logRequest:
                          Doc UUID: {}
                          Owners: [{}]
                          Current perms:
                            {}
                          Adds:
                            {}
                          Removes:
                            {}""",
                currentDocumentPermissions.getDocUuid(), ownersStr, currentPerms, addsStr, removesStr));
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
                    changeDocPermissions(descendant.getDocRef(),
                            changes, affectedDocRefs, affectedUserUuids, clear, null);
                } else {
                    LOGGER.debug("User does not have permission to change permissions on " + descendant.toString());
                }
            }
        }
    }

    private String getCurrentUserIdForDisplay() {
        return securityContextProvider.get().getUserIdentityForAudit();
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

    private Set<PermissionAttribute> mapChangeItemsToPermissions(final Set<String> perms) {
        return perms.stream()
                .map(this::mapChangeItemToPermission)
                .collect(Collectors.toSet());
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

    @Override
    public List<UserName> getDocumentOwners(final String documentUuid) {
        final DocumentPermissionService documentPermissionService = documentPermissionServiceProvider.get();
        final UserService userService = userServiceProvider.get();

        final Set<String> ownerUuids = documentPermissionService.getDocumentOwnerUuids(documentUuid);

        return ownerUuids
                .stream()
                .map(userService::loadByUuid)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(user -> (UserName) user)
                .toList();
    }
}
