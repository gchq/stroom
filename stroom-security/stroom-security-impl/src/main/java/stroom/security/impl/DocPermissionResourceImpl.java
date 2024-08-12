package stroom.security.impl;

import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.explorer.api.ExplorerNodeService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.CheckDocumentPermissionRequest;
import stroom.security.shared.DocPermissionResource;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.FetchDocumentUserPermissionsRequest;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResultPage;

import event.logging.AuthorisationActionType;
import event.logging.AuthoriseEventAction;
import event.logging.Document;
import event.logging.Outcome;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Objects;

@AutoLogged
class DocPermissionResourceImpl implements DocPermissionResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocPermissionResourceImpl.class);

//    private static final FilterFieldMappers<UserName> USER_NAMES_FILTER_FIELD_MAPPERS = FilterFieldMappers.of(
//            FilterFieldMapper.of(FindUserCriteria.FIELD_DEF_NAME, UserName::getSubjectId),
//            FilterFieldMapper.of(FindUserCriteria.FIELD_DEF_DISPLAY_NAME, UserName::getDisplayName),
//            FilterFieldMapper.of(FindUserCriteria.FIELD_DEF_FULL_NAME, UserName::getFullName));

    private final Provider<DocumentPermissionServiceImpl> documentPermissionServiceProvider;
    private final Provider<ExplorerNodeService> explorerNodeServiceProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;
    private final Provider<DocRefInfoService> docRefInfoServiceProvider;
    //Todo
    // Permission checking should be responsibility of underlying service rather than REST resource impl
    private final Provider<SecurityContext> securityContextProvider;

    @Inject
    DocPermissionResourceImpl(final Provider<DocumentPermissionServiceImpl> documentPermissionServiceProvider,
                              final Provider<ExplorerNodeService> explorerNodeServiceProvider,
                              final Provider<SecurityContext> securityContextProvider,
                              final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider,
                              final Provider<DocRefInfoService> docRefInfoServiceProvider) {
        this.documentPermissionServiceProvider = documentPermissionServiceProvider;
        this.explorerNodeServiceProvider = explorerNodeServiceProvider;
        this.securityContextProvider = securityContextProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
        this.docRefInfoServiceProvider = docRefInfoServiceProvider;
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public ResultPage<DocumentUserPermissions> fetchDocumentUserPermissions(final FetchDocumentUserPermissionsRequest
                                                                                    request) {
        return documentPermissionServiceProvider.get().fetchDocumentUserPermissions(request);
    }


    //    @Override
//    public DocumentPermissionSet fetchDocumentPermissions(final FetchDocumentPermissionsRequest request) {
//        return null;
//    }

    //    @Override
//    @AutoLogged(OperationType.MANUALLY_LOGGED)
//    public Boolean changeDocumentPermissions(final ChangeDocumentPermissionsRequest request) {
//        final DocRef docRef = request.getDocRef();
//
//        // Check that the current user has permission to change the permissions of the document.
//        if (securityContextProvider.get().hasDocumentOwnership(docRef.getUuid())) {
//            // Record what documents and what users are affected by these changes
//            // so we can clear the relevant caches.
//            final Set<DocRef> affectedDocRefs = new HashSet<>();
//            final Set<String> affectedUserUuids = new HashSet<>();
//
//            // Change the permissions of the document.
//            final Changes changes = request.getChanges();
//            changeDocPermissionsWithLogging(
//                    docRef,
//                    changes,
//                    affectedDocRefs,
//                    affectedUserUuids,
//                    request.getCascade());
//            return true;
//        }
//
//        final String errorMessage = "Insufficient privileges to change permissions for this document";
//
//        logPermissionChangeError("DocPermissionResourceImpl.changeDocumentPermissions",
//                request.getDocRef(), errorMessage);
//        throw new PermissionException(getCurrentUserRef(), errorMessage);
//    }
//
//    @Override
//    @AutoLogged(OperationType.UNLOGGED)
//    public PermissionChangeImpactSummary fetchPermissionChangeImpact(final ChangeDocumentPermissionsRequest request) {
////        final PermissionState permissionState = buildPermissionState(
////                request.getDocRef(), request.getChanges(), request.getCascade());
////
////        final List<String> errorMessages = validateChanges(permissionState);
////        if (!errorMessages.isEmpty()) {
////            throw new PermissionException(getCurrentUserRef(), String.join("\n", errorMessages));
////        }
////
////        String summary = "";
////        final List<String> impactDetail = new ArrayList<>();
////        if (permissionState.hasDescendants()) {
////            final SecurityContext securityContext = securityContextProvider.get();
////            final UserRefLookup userRefLookup = userRefLookupProvider.get();
////
////            final List<DocRef> descendantsWithOwnerPerm = permissionState.descendants.stream()
////                    .filter(securityContext::hasDocumentOwnership)
////                    .toList();
////
////            if (descendantsWithOwnerPerm.isEmpty()) {
////                summary = "You do not have the required permission to change the permissions of " +
////                        "any descendant documents. Only this document will be modified.";
////            } else {
////                final int descendantsWithOwnerPermCount = descendantsWithOwnerPerm.size();
////                final String descendantCountStr = permissionState.descendants.size() !=
// descendantsWithOwnerPermCount
////                        ? descendantsWithOwnerPermCount
////                        + " descendant documents. You do not have permission to change some descendant documents."
////                        : "all " + permissionState.descendants.size() + " descendant documents.";
////
////                String ownerChangeMsg = "";
////                if (shouldChangeOwner(permissionState, permissionState.getTopLevelDocRef().getUuid())) {
////                    final int ownerChangeCount = Math.toIntExact(descendantsWithOwnerPerm.stream()
////                            .filter(descendantDocRef ->
////                                    permissionState.isOwnerChanging(descendantDocRef.getUuid()))
////                            .count());
////
////                    if (ownerChangeCount > 0) {
////                        ownerChangeMsg = buildOwnerChangeMessage(ownerChangeCount, permissionState, userRefLookup);
////
//////                        // Legacy code allowed a doc to have multiple users, this is no longer allowed in the UI,
//// but
//////                        // we have to tolerate legacy data.
//////                        final List<DocRef> descendantsWithMultipleOwners = descendantsWithOwnerPerm.stream()
//////                                .filter(docRef ->
// permissionState.getCurrentOwnerUuid(docRef.getUuid()).size() > 1)
//////                                .toList();
//////                        if (!descendantsWithMultipleOwners.isEmpty()) {
//////                            impactDetail.add("The following "
//////                                    + StringUtil.plural("document", descendantsWithMultipleOwners.size())
//////                                    + " currently "
//////                                    + StringUtil.plural(
//////                                    "has", "have", descendantsWithMultipleOwners.size())
//////                                    + " multiple owners.");
//////                            impactDetail.add("Ownership" +
//////                                    " will be REMOVED from existing owner and assigned to "
//////                                    + convertToTypedUserName(
//////                                    permissionState.getTopLevelOwnerUuid(), userRefLookup, true)
//////                                    + ".");
//////                            for (final DocRef docRef : descendantsWithMultipleOwners) {
//////                                impactDetail.add(indent(getDocIdentity(docRef.getUuid()), 1, false)
//////                                        + " with existing owner:");
//////                                Optional.ofNullable(permissionState.getCurrentOwnerUuid(docRef.getUuid()))
//////                                        .map(userUuid -> convertToTypedUserName(userUuid, userRefLookup, false))
//////                                        .map(str -> indent(str, 2, false))
//////                                        .map(impactDetail::add);
//////                            }
//////                        }
////                    }
////                }
////
////                switch (request.getCascade()) {
////                    case NO -> summary = null;
////                    case ALL -> summary = "All permissions assigned to this document will be applied to "
////                            + descendantCountStr
////                            + ownerChangeMsg;
////                    case CHANGES_ONLY -> {
////                        impactDetail.addAll(getChangeDetail(permissionState.changes, userRefLookup));
////                        summary = "The following permission changes will be applied to "
////                                + descendantCountStr
////                                + ownerChangeMsg;
////                    }
////                }
////            }
////        } else {
////            summary = "There are no descendant documents. Only this document will be modified.";
////        }
////        if (!NullSafe.isBlankString(summary)) {
////            summary += "\nDo you wish to continue?";
////        }
////        return new PermissionChangeImpactSummary(summary, String.join("\n", impactDetail));
//
//        return null;
//    }

//    private String buildOwnerChangeMessage(final int ownerChangeCount,
//                                           final PermissionState permissionState,
//                                           final UserRefLookup userRefLookup) {
//        String ownerChangeMsg;
//        ownerChangeMsg = "\nThe ownership of "
//                + ownerChangeCount
//                + " descendant "
//                + StringUtil.plural("document", ownerChangeCount)
//                + " will be changed to "
//                + convertToTypedUserName(permissionState.getTopLevelOwnerUuid(), userRefLookup, true)
//                + ". Any existing owners will lose the permissions implied by the Owner permission.";
//        return ownerChangeMsg;
//    }
//
//    private List<String> getChangeDetail(final Changes changes, final UserRefLookup userRefLookup) {
//
//        final List<String> lines = new ArrayList<>();
//        if (NullSafe.test(changes, Changes::hasChanges)) {
//            final List<UserUuidAndTypedName> allUsers = getUsersFromChanges(changes, userRefLookup);
//
//            if (allUsers.isEmpty()) {
//                lines.add("No changes to apply.");
//            } else {
//                lines.add("Permission changes:");
//                allUsers.forEach(userUuidAndTypedName -> {
//                    final DocumentPermissionSet removes =
//                            changes.getRemove().get(userUuidAndTypedName.userUuid);
//                    final DocumentPermissionSet adds =
//                            changes.getAdd().get(userUuidAndTypedName.userUuid);
//                    if (!removes.isEmpty() || !adds.isEmpty()) {
//                        lines.add(indent(userUuidAndTypedName.typedName + ":", 1, false));
//
//                        final String permIndent = indent(3, false);
//                        addChanges(removes, lines, indent("REMOVE: ", 2, false), permIndent);
//                        addChanges(adds, lines, indent("ADD: ", 2, false), permIndent);
//                    }
//                });
//                return lines;
//            }
//        } else {
//            // No changes
//            lines.add("No changes to apply.");
//        }
//        return lines;
//    }
//
//    private static void addChanges(final DocumentPermissionSet removes,
//                                   final List<String> lines,
//                                   final String heading,
//                                   final String level3Indent) {
//        if (!removes.isEmpty()) {
//            if (removes.getPermissions().size() + removes.getFolderCreatePermissions().size() == 1) {
//                if (removes.getPermissions().size() > 0) {
//                    lines.add(heading + removes.getPermissions().iterator().next().getDisplayValue());
//                } else if (removes.getFolderCreatePermissions().size() > 0) {
//                    lines.add(heading + removes.getFolderCreatePermissions().iterator().next());
//                }
//            } else {
//                lines.add(heading);
//                removes.getPermissions().stream()
//                        .map(str -> level3Indent + str.getDisplayValue())
//                        .forEach(lines::add);
//                removes.getFolderCreatePermissions().stream()
//                        .map(str -> level3Indent + str)
//                        .forEach(lines::add);
//            }
//        }
//    }
//
//    private List<UserUuidAndTypedName> getUsersFromChanges(final Changes changes, final UserRefLookup userRefLookup) {
//        return Stream.of(
//                        changes.getAdd(),
//                        changes.getRemove())
//                .filter(Objects::nonNull)
//                .flatMap(map -> map.keySet().stream())
//                .distinct()
//                .sorted(Comparator.comparing(UserRef::getDisplayName))
//                .toList();
//    }
//
//    private static <T> List<T> permsAsListWithoutOwner(final Set<T> set) {
//        return NullSafe.stream(set)
//                .sorted()
//                .toList();
//    }
//
//    @Override
//    @AutoLogged(value = OperationType.VIEW, verb = "Finding permissions of parent")
//    public DocumentPermissions copyPermissionFromParent(final CopyPermissionsFromParentRequest request) {
//        final DocRef docRef = request.getDocRef();
//
//        LOGGER.debug("copyPermissionFromParent() - docRef: {}", docRef);
//
//        boolean isUserAllowedToChangePermissions = securityContextProvider.get().hasDocumentPermission(
//                docRef.getUuid(), DocumentPermissionEnum.OWNER);
//        if (!isUserAllowedToChangePermissions) {
//            final String errorMessage = "Insufficient privileges to change permissions for this document";
//
//            logPermissionChangeError("DocPermissionResourceImpl.copyPermissionFromParent",
//                    request.getDocRef(), errorMessage);
//            throw new PermissionException(getCurrentUserRef(), errorMessage);
//        }
//
//        ExplorerNode parent = explorerNodeServiceProvider.get()
//                .getParent(docRef)
//                .orElseThrow(() ->
//                        new EntityServiceException("This node does not have a parent to copy permissions from!"));
//
//        final DocRef parentDocRef = parent.getDocRef();
//        LOGGER.debug("parentDocRef: {}", parentDocRef);
//        final DocumentPermissions documentPermissions = documentPermissionServiceProvider.get()
//                .getPermissionsForDocument(parentDocRef.getUuid());
//        final DocumentPermissions updatedDocumentPermissions;
//
//        if (ExplorerConstants.isFolder(parentDocRef)) {
//            updatedDocumentPermissions = documentPermissions;
//        } else {
//            // Not a folder, so we need to exclude all the folder create perms as they are not
//            // applicable for a leaf doc
//            final Map<String, DocumentPermissionSet> updatedPerms = documentPermissions.getPermissions()
//                    .entrySet()
//                    .stream()
//                    .filter(Objects::nonNull)
//                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
//
//            updatedDocumentPermissions = documentPermissions.copy()
//                    .permissions(updatedPerms)
//                    .build();
//        }
//
//        LOGGER.debug(() -> "Returning permissions:\n  " + String.join("\n  ", mapPerms(
//                userRefLookupProvider.get(),
//                updatedDocumentPermissions.getPermissions())));
//        return updatedDocumentPermissions;
//    }
//
//    @Override
//    @AutoLogged(OperationType.VIEW)
//    public DocumentPermissions fetchAllDocumentPermissions(final FetchDocumentPermissionsRequest request) {
//        if (securityContextProvider.get().hasDocumentPermission(request.getDocRef().getUuid(),
//                DocumentPermissionEnum.OWNER)) {
//            return documentPermissionServiceProvider.get().getPermissionsForDocument(request.getDocRef().getUuid());
//        }
//
//        throw new PermissionException(
//                getCurrentUserRef(),
//                "Insufficient privileges to fetch permissions for this document");
//    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED) // Only log failures
    public Boolean checkDocumentPermission(final CheckDocumentPermissionRequest request) {
        final boolean hasPerm;
        try {
            Objects.requireNonNull(request);
            hasPerm = securityContextProvider.get().hasDocumentPermission(
                    request.getDocRef(),
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
        final DocRef docRef = request.getDocRef();
        final String msg = LogUtil.message("User failed permissions check for permission {} document {}",
                request.getPermission(),
                docRef);

        stroomEventLoggingServiceProvider.get().log(
                StroomEventLoggingUtil.buildTypeId(this, "checkDocumentPermission"),
                msg,
                AuthoriseEventAction.builder()
                        .withAction(AuthorisationActionType.REQUEST)
                        .addDocument(Document.builder()
                                .withId(docRef.getUuid())
                                .withName(docRef.getName())
                                .build())
                        .withOutcome(Outcome.builder()
                                .withSuccess(false)
                                .withDescription(NullSafe.getOrElse(e, Throwable::getMessage, msg))
                                .build())
                        .build());
    }

//    @Override
//    @AutoLogged(OperationType.UNLOGGED) // Limited benefit in logging the filtering of a list of user names
//    public List<UserName> filterUsers(final FilterUsersRequest filterUsersRequest) {
//        if (filterUsersRequest.getUsers() == null) {
//            return null;
//        } else {
//            // Not ideal calling the back end to filter some users but this is the only way to do the filtering
//            // consistently across the app.
//            return QuickFilterPredicateFactory.filterStream(
//                            filterUsersRequest.getQuickFilterInput(),
//                            USER_NAMES_FILTER_FIELD_MAPPERS,
//                            NullSafe.stream(filterUsersRequest.getUsers()))
//                    .collect(Collectors.toList());
//        }
//    }
//
//    private void changeDocPermissionsWithLogging(final DocRef docRef,
//                                                 final Changes changes,
//                                                 final Set<DocRef> affectedDocRefs,
//                                                 final Set<String> affectedUserUuids,
//                                                 final Cascade cascade) {
//
//        final PermissionState permissionState = buildPermissionState(docRef, changes, cascade);
//        final DocumentPermissions documentPermissionsBefore = permissionState.getCurrentPermissions(docRef.getUuid());
//
//        final int removeCount = changes.getRemove()
//                .values()
//                .stream()
//                .mapToInt(DocumentPermissionSet::size)
//                .sum();
//        final int addCount = changes.getAdd()
//                .values()
//                .stream()
//                .mapToInt(DocumentPermissionSet::size)
//                .sum();
//
//        final String cascadeText = switch (Objects.requireNonNullElse(cascade, Cascade.NO)) {
//            case ALL -> ", cascade all";
//            case CHANGES_ONLY -> ", cascade changes";
//            default -> "";
//        };
//
//        final String description = LogUtil.message("Changing permissions (removing {}, adding {}) on {} {}{}.",
//                removeCount,
//                addCount,
//                docRef.getType(),
//                docRef.getName(),
//                cascadeText);
//
//        // It is possible in future that we could use Delta rather than Before/After
//        // See https://github.com/gchq/event-logging-schema/issues/75
//        final MultiObject before = buildEventState(docRef, documentPermissionsBefore);
//        stroomEventLoggingServiceProvider.get()
//                .loggedWorkBuilder()
//                .withTypeId("DocPermissionResourceImpl.changeDocumentPermissions")
//                .withDescription(description)
//                .withDefaultEventAction(UpdateEventAction.builder()
//                        .withBefore(before)
//                        .withAfter(before) // If successful we overwrite with after, but ensures we have one
//                        .build())
//                .withComplexLoggedAction(updateEventAction -> {
//                    doPermissionChange(permissionState, affectedDocRefs, affectedUserUuids);
//
//                    final DocumentPermissions documentPermissionsAfter = documentPermissionServiceProvider.get()
//                            .getPermissionsForDocument(docRef.getUuid());
//
//                    // Add in the after state
//                    UpdateEventAction modifiedEventAction = updateEventAction.newCopyBuilder()
//                            .withAfter(buildEventState(docRef, documentPermissionsAfter))
//                            .build();
//                    return ComplexLoggedOutcome.success(modifiedEventAction);
//                })
//                .runActionAndLog();
//    }
//
//    private void doPermissionChange(final PermissionState permissionState,
//                                    final Set<DocRef> affectedDocRefs,
//                                    final Set<String> affectedUserUuids) {
//        final List<String> messages = new ArrayList<>(validateChanges(permissionState));
//        if (!messages.isEmpty()) {
//            throw new PermissionException(
//                    securityContextProvider.get().getUserRef(),
//                    String.join("\n", messages));
//        }
//
//        // Do the actual change
//        changeDocPermissions(
//                permissionState,
//                permissionState.docRef, // The top level docRef
//                affectedDocRefs,
//                affectedUserUuids,
//                false);
//
//        // Cascade changes if this is a folder and we have been asked to do so.
//        if (permissionState.isCascading()) {
//            messages.addAll(cascadeChanges(permissionState, affectedDocRefs, affectedUserUuids));
//            if (!messages.isEmpty()) {
//                throw new PermissionException(
//                        securityContextProvider.get().getUserRef(),
//                        String.join("\n", messages));
//            }
//        }
//    }
//
//    private List<String> validateChanges(final PermissionState permissionState) {
//        final SecurityContext securityContext = securityContextProvider.get();
//        final DocRef topLevelDocRef = permissionState.getTopLevelDocRef();
//
//        final String changeOwnerPermName = PermissionNames.CHANGE_OWNER_PERMISSION;
//        if (permissionState.isOwnerChanging(topLevelDocRef.getUuid())
//                && !securityContextProvider.get().hasAppPermission(changeOwnerPermName)) {
//            messages.add(LogUtil.message("{} permission is required to change the owner of a document/folder",
//                    changeOwnerPermName));
//        }
//
//        // Validate the owners on the top level doc
//        final List<String> messages = new ArrayList<>(validateDoc(securityContext, permissionState, topLevelDocRef));
//
//        // No point validating the cascade if the top level doc has issues.
//        if (messages.isEmpty()) {
//            // We may be making a change that does not change the owner of the top level, but does
//            // for one or more descendants. Also, the owner of the top level may not be the owner
//            // of all descendants
//            for (final DocRef descendantDocRef : permissionState.descendants) {
//                if (securityContext.hasDocumentOwnership(descendantDocRef)) {
//                    messages.addAll(validateDoc(securityContext, permissionState, descendantDocRef));
//                }
//            }
//        }
//        return messages;
//    }
//
//    private List<String> validateDoc(final SecurityContext securityContext,
//                                     final PermissionState permissionState,
//                                     final DocRef docRef) {
//        final DocumentPermissions currentDocumentPermissions = permissionState
//        .getCurrentPermissions(docRef.getUuid());
//        final List<String> messages = new ArrayList<>();
//
//        if (!securityContextProvider.get().hasDocumentOwnership(docRef)) {
//            messages.add(LogUtil.message("You need to be the owner of {} to change its permissions.",
//                    getDocIdentity(docRef.getUuid())));
//        }
//
//        final Set<String> effectiveOwnerUuids = permissionState.requestedTopLevelOwnerUuids;
//        final boolean isOwnerChanging = permissionState.isOwnerChanging(docRef.getUuid());
//        final boolean isDescendant = permissionState.isDescendant(docRef.getUuid());
//        final AppPermissionEnum changeOwnerPermName = AppPermissionEnum.CHANGE_OWNER_PERMISSION;
//
//        if (isOwnerChanging
//                && !securityContextProvider.get().hasAppPermission(changeOwnerPermName)) {
//            messages.add(LogUtil.message("{} permission is required to change the ownership of {}",
//                    changeOwnerPermName, getDocIdentity(docRef.getUuid())));
//        }
//
//        // We can't validate the descendants as they may not have any owners due to legacy behaviour
//        if (!isDescendant) {
//            final String permMsg = !securityContext.hasAppPermission(changeOwnerPermName)
//                    ? " Also, " + changeOwnerPermName + " permission is required to change the owner and you do " +
//                    "not hold this permission."
//                    : "";
//            if (effectiveOwnerUuids.isEmpty()) {
//                messages.add(LogUtil.message("{} must have exactly one owner. " +
//                                "Requested changes would result in no owners.{}",
//                        getDocIdentity(currentDocumentPermissions.getDocUuid()),
//                        permMsg));
//            } else if (effectiveOwnerUuids.size() > 1) {
//                final UserRefLookup userRefLookup = userRefLookupProvider.get();
//                final String effectiveOwnersStr = effectiveOwnerUuids.stream()
//                        .map(userRefLookup::getByUuid)
//                        .filter(Optional::isPresent)
//                        .map(Optional::get)
//                        .map(UserRef::toDisplayString)
//                        .collect(Collectors.joining(", "));
//                messages.add(LogUtil.message("{} must have exactly one owner. " +
//                                "Requested changes would result in the following owners [{}].{}",
//                        getDocIdentity(currentDocumentPermissions.getDocUuid()),
//                        effectiveOwnersStr,
//                        permMsg));
//            }
//        }
//        return messages;
//    }
//
//    private MultiObject buildEventState(final DocRef docRef,
//                                        final DocumentPermissions documentPermissions) {
//
//        final Permissions.Builder<Void> permissionsBuilder = Permissions.builder();
//        final Builder<Void> rootDataBuilder = Data.builder()
//                .withName("createPermissionsByUser");
//
//        if (NullSafe.hasEntries(documentPermissions, DocumentPermissions::getPermissions)) {
//            documentPermissions.getPermissions().forEach((userUuid, permissions) -> {
//                final Optional<UserRef> optUser = securityContextProvider.get().asProcessingUserResult(() ->
//                        userRefLookupProvider.get().getByUuid(userUuid));
//
//                final Permission.Builder<Void> permissionBuilder = Permission.builder();
//                if (optUser.isEmpty()) {
//                    LOGGER.warn("Unable to locate user for permission change " + userUuid);
//                    permissionBuilder.withUser(event.logging.User.builder()
//                            .withId(userUuid)
//                            .build());
//                } else {
//                    final UserRef userOrGroup = optUser.get();
//                    if (userOrGroup.isGroup()) {
//                        permissionBuilder.withGroup(StroomEventLoggingUtil.createGroup(userOrGroup));
//                    } else {
//                        permissionBuilder.withUser(StroomEventLoggingUtil.createUser(userOrGroup));
//                    }
//
//                    // Have to use Data elements to hold the Create perms as the schema currently has no support
//                    // for custom perms. Waiting for https://github.com/gchq/event-logging-schema/issues/76
//                    final Data userData = Data.builder()
//                            .withName(userOrGroup.getSubjectId())
//                            .withValue(userOrGroup.isGroup()
//                                    ? "group"
//                                    : "user")
//                            .addData(documentPermissions.getPermissions().get(userUuid)
//                                    .getFolderCreatePermissions()
//                                    .stream()
//                                    .map(perm -> Data.builder()
//                                            .withName(DocumentPermissionSet.CREATE_PREFIX + perm)
//                                            .build())
//                                    .collect(Collectors.toSet()))
//                            .build();
//
//                    if (!userData.getData().isEmpty()) {
//                        rootDataBuilder
//                                .addData(userData)
//                                .build();
//                    }
//                }
//                permissionBuilder.withAllowAttributes(mapChangeItemsToPermissions(permissions));
//                permissionsBuilder.addPermissions(permissionBuilder.build());
//            });
//        }
//
//        final OtherObject.Builder<Void> otherObjectBuilder = OtherObject.builder()
//                .withDescription(docRef.toInfoString())
//                .withId(docRef.getUuid())
//                .withName(docRef.getName())
//                .withType(docRef.getType());
//
//        final Permissions permissions = permissionsBuilder.build();
//        if (NullSafe.hasItems(permissions, Permissions::getPermissions)) {
//            otherObjectBuilder.withPermissions(permissions);
//        }
//
//        final Data rootData = rootDataBuilder.build();
//        if (NullSafe.hasItems(rootData, Data::getData)) {
//            otherObjectBuilder.withData(rootDataBuilder.build());
//        }
//
//        return MultiObject.builder()
//                .withObjects(otherObjectBuilder.build())
//                .build();
//    }
//
//    private void changeDocPermissions(final PermissionState permissionState,
//                                      final DocRef docRef,
//                                      final Set<DocRef> affectedDocRefs,
//                                      final Set<String> affectedUserUuids,
//                                      final boolean cleanAllPermsFirst) {
//        final DocumentPermissionServiceImpl documentPermissionServiceImpl = documentPermissionServiceProvider.get();
//        final String docUuid = docRef.getUuid();
//        final DocumentPermissions currentDocumentPermissions = permissionState.getCurrentPermissions(docUuid);
//
//        if (LOGGER.isDebugEnabled()) {
//            logRequest(permissionState.changes, currentDocumentPermissions, LOGGER::debug);
//        }
//
//        removeInvalidCreatePerms(docRef, documentPermissionServiceImpl, currentDocumentPermissions);
//
//        if (cleanAllPermsFirst) {
//            // If we are asked to clear all permissions then use all the current perms for
//            // this document and then remove them.
//
//            currentDocumentPermissions.getPermissions().forEach((userUuid, permissions) -> {
//                try {
//                    documentPermissionServiceImpl.removePermissions(docRef.getUuid(), userUuid, permissions);
//                    // Remember the affected documents and users, so we can clear the relevant caches.
//                    affectedDocRefs.add(docRef);
//                    affectedUserUuids.add(userUuid);
//                } catch (final RuntimeException e) {
//                    // Expected.
//                    LOGGER.debug(e.getMessage());
//                }
//            });
//
//        } else {
//            // Otherwise remove permissions specified by the change set.
//            permissionState.changes.getRemove().forEach((userUuid, permissions) -> {
//                    try {
//                        documentPermissionServiceImpl.removePermissions(docUuid, userUuid, permissions);
//                        // Remember the affected documents and users so we can clear the relevant caches.
//                        affectedDocRefs.add(docRef);
//                        affectedUserUuids.add(userUuid);
//                    } catch (final RuntimeException e) {
//                        // Expected.
//                        LOGGER.debug(e.getMessage());
//                    }
//            });
//        }
//
//        final Map<String, DocumentPermissionSet> permsToAdd;
//        if (cleanAllPermsFirst) {
//            // We've cleared out all the perms, so just add all the effective perms of the top level doc
//            permsToAdd = permissionState.effectiveTopLevelPerms;
//        } else {
//            permsToAdd = permissionState.changes.getAdd();
//        }
//
//        // Add permissions from the change set.
//        permsToAdd.forEach((userUuid, permissions) -> {
//            try {
//                documentPermissionServiceImpl.addPermissions(docUuid, userUuid, permissions);
//                // Remember the affected documents and users so we can clear the relevant caches.
//                affectedDocRefs.add(docRef);
//                affectedUserUuids.add(userUuid);
//            } catch (final RuntimeException e) {
//                // Expected.
//                LOGGER.debug(e.getMessage());
//            }
//        });
//
//        // Only change owner for CHANGES_ONLY if there has been a change to the top level owner
//        // and in that case set the descendants to have the same owner as top level.
//        if (shouldChangeOwner(permissionState, docUuid)) {
//            // Check if this doc already has the right owner
//            if (cleanAllPermsFirst || permissionState.isOwnerChanging(docUuid)) {
//                LOGGER.debug(() -> LogUtil.message("Setting owner of doc {} to {}",
//                        getDocIdentity(docUuid), permissionState.getTopLevelOwnerUuid()));
//                documentPermissionServiceImpl.setDocumentOwner(docUuid, permissionState.getTopLevelOwnerUuid());
//            } else {
//                LOGGER.debug(() -> LogUtil.message("Owner of doc {} is already {}",
//                        getDocIdentity(docUuid), permissionState.getTopLevelOwnerUuid()));
//            }
//        }
//    }
//
//    private boolean shouldChangeOwner(final PermissionState permissionState,
//                                      final String docUuid) {
//
//        final Cascade cascade = permissionState.cascade;
//        return cascade == Cascade.ALL
//                || (cascade == Cascade.NO && !permissionState.isDescendant(docUuid))
//                || (cascade == Cascade.CHANGES_ONLY && permissionState.isTopLevelOwnerChange());
//    }
//
//    /**
//     * Previous code incorrectly assigned create perms to non-folders. While not causing a problem, they
//     * are cluttering up the db. It is not possible to tidy these up via a mig script as the security module
//     * does not know the types of the docs in the perms tables, so we will do it gradually via other changes.
//     */
//    private void removeInvalidCreatePerms(final DocRef docRef,
//                                          final DocumentPermissionServiceImpl documentPermissionServiceImpl,
//                                          final DocumentPermissions currentDocumentPermissions) {
//
//        try {
//            if (!DocumentTypes.isFolder(docRef.getType())) {
//                // Not a folder, so should not have create perms on it, so remove them
//                securityContextProvider.get().asProcessingUser(() ->
//                        NullSafe.map(currentDocumentPermissions.getPermissions()).forEach((userUuid, perms) -> {
//                            final Set<String> createPerms = NullSafe.set(perms)
//                                    .stream()
//                                    .filter(DocumentPermissionNames::isDocumentCreatePermission)
//                                    .collect(Collectors.toSet());
//                            if (!createPerms.isEmpty()) {
//                                LOGGER.info("Removing {} redundant create permissions from {} for userUuid '{}'. " +
//                                                "Permissions removed: {}",
//                                        createPerms.size(), docRef, userUuid, createPerms);
//                                documentPermissionServiceImpl.removePermissions(
//                                        docRef.getUuid(),
//                                        userUuid,
//                                        createPerms);
//                            }
//                        }));
//            }
//        } catch (Exception e) {
//            LOGGER.error("Error removing redundant create perms from {}: {}",
//                    docRef, e.getMessage(), e);
//            // Swallow it as this work is only a nice to have
//        }
//    }
//
//    private String convertToTypedUserName(final String userUuid,
//                                          final UserRefLookup userRefLookup,
//                                          final boolean lowerCaseType) {
//        return userRefLookup.getByUuid(userUuid)
//                .map(user -> {
//                    String type = user.getType();
//                    if (lowerCaseType) {
//                        type = type.toLowerCase();
//                    }
//                    return type
//                            + " '"
//                            + user.toDisplayString()
//                            + "'";
//                })
//                .orElseGet(() -> "Unknown user '" + userUuid + "'");
//    }
//
//    private List<String> mapPerms(final UserRefLookup userRefLookup,
//                                  final Map<String, DocumentPermissionSet> perms) {
//        return NullSafe.map(perms)
//                .entrySet()
//                .stream()
//                .map(entry -> {
//                    final String userUuid = entry.getKey();
//                    final String userNameStr = userRefLookup.getByUuid(userUuid)
//                            .map(user ->
//                                    Strings.padEnd(user.getType().toUpperCase(), 5, ' ')
//                                            + " "
//                                            + user.toDisplayString())
//                            .orElse("null");
//                    final String permStr = entry.getValue().toString();
//                    return userNameStr + " => [" + permStr + "]";
//                })
//                .toList();
//    }
//
//    private void logRequest(final Changes changes,
//                            final DocumentPermissions currentDocumentPermissions,
//                            final Consumer<String> logMessageConsumer) {
//
//        final String ownersStr = currentDocumentPermissions.getOwnerUuid();
//
//        final UserRefLookup userRefLookup = userRefLookupProvider.get();
//        final String currentPerms = String.join("\n  ", mapPerms(
//                userRefLookup,
//                currentDocumentPermissions.getPermissions()));
//        final String addsStr = String.join("\n  ", mapPerms(
//                userRefLookup,
//                changes.getAdd()));
//        final String removesStr = String.join("\n  ", mapPerms(
//                userRefLookup,
//                changes.getRemove()));
//
//        logMessageConsumer.accept(LogUtil.message("""
//                        logRequest:
//                          Doc UUID: {}
//                          Owners: [{}]
//                          Current perms:
//                            {}
//                          Adds:
//                            {}
//                          Removes:
//                            {}""",
//                currentDocumentPermissions.getDocUuid(), ownersStr, currentPerms, addsStr, removesStr));
//    }
//
//    private List<String> cascadeChanges(final PermissionState permissionState,
//                                        final Set<DocRef> affectedDocRefs,
//                                        final Set<String> affectedUserUuids) {
//        final List<String> validationMessages;
//        if (DocumentTypes.isFolder(permissionState.docRef.getType())) {
//            switch (permissionState.cascade) {
//                // permissionState.changes will differ depending on the cascade type, see
//                // stroom.security.impl.DocPermissionResourceImpl.PermissionState.buildChanges
//                case CHANGES_ONLY:
//                case ALL:
//                    // We are only cascading changes so just pass on the change set.
//                    validationMessages = changeDescendantPermissions(
//                            permissionState, affectedDocRefs, affectedUserUuids);
//                    break;
//
//                case NO:
//                    // Do nothing.
//                    validationMessages = Collections.emptyList();
//                    break;
//                default:
//                    throw new RuntimeException("Unexpected cascade " + permissionState.cascade);
//            }
//        } else {
//            validationMessages = Collections.emptyList();
//        }
//        return validationMessages;
//    }
//
//    private List<String> changeDescendantPermissions(final PermissionState permissionState,
//                                                     final Set<DocRef> affectedDocRefs,
//                                                     final Set<String> affectedUserUuids) {
//        final List<DocRef> descendants = permissionState.descendants;
//        final List<String> validationMessages = new ArrayList<>();
//        for (final DocRef descendantDocRef : descendants) {
//            // Ensure that the user has permission to change the permissions of this child.
//            final String docUuid = descendantDocRef.getUuid();
//            if (securityContextProvider.get().hasDocumentPermission(docUuid, DocumentPermissionEnum.OWNER)) {
//                try {
//                    final boolean cleanAllPermsFirst = permissionState.cascade == Cascade.ALL;
//                    changeDocPermissions(
//                            permissionState,
//                            descendantDocRef,
//                            affectedDocRefs,
//                            affectedUserUuids,
//                            cleanAllPermsFirst);
//                } catch (PermissionException e) {
//                    validationMessages.add(e.getMessage());
//                }
//            } else {
//                LOGGER.debug("User does not have permission to change permissions on " + descendantDocRef);
//            }
//        }
//        return validationMessages;
//    }
//
//    private UserRef getCurrentUserRef() {
//        return securityContextProvider.get().getUserRef();
//    }
//
//    private void logPermissionChangeError(final String typeId,
//                                          final DocRef docRefModified,
//                                          final String outcomeDescription) {
//        final Event event = stroomEventLoggingServiceProvider.get().createEvent(
//                typeId,
//                "Modify permission attempt failed",
//                UpdateEventAction.builder().withBefore(
//                                MultiObject.builder()
//                                        .addObject(
//                                                OtherObject.builder()
//                                                        .withDescription(docRefModified.toInfoString())
//                                                        .withId(docRefModified.getUuid())
//                                                        .withName(docRefModified.getName())
//                                                        .withType(docRefModified.getType())
//                                                        .build()
//                                        )
//                                        .build())
//                        .withOutcome(
//                                Outcome.builder()
//                                        .withSuccess(false)
//                                        .withDescription(outcomeDescription)
//                                        .build())
//                        .build());
//
//        stroomEventLoggingServiceProvider.get().log(event);
//    }
//
//    private Set<PermissionAttribute> mapChangeItemsToPermissions(final DocumentPermissionSet perms) {
//        return Streams.concat(
//                        perms.getPermissions().stream().map(this::mapChangeItemToPermission),
//                        perms.getFolderCreatePermissions().stream().map(this::mapChangeItemToCreatePermission))
//                .collect(Collectors.toSet());
//    }
//
//    private PermissionAttribute mapChangeItemToPermission(final DocumentPermissionEnum perm) {
//        if (DocumentPermissionEnum.DELETE.equals(perm)) {
//            return PermissionAttribute.WRITE;
//        } else if (DocumentPermissionEnum.OWNER.equals(perm)) {
//            return PermissionAttribute.OWNER;
//        } else if (DocumentPermissionEnum.READ.equals(perm)) {
//            return PermissionAttribute.READ;
//        } else if (DocumentPermissionEnum.UPDATE.equals(perm)) {
//            return PermissionAttribute.WRITE;
//        } else if (DocumentPermissionEnum.USE.equals(perm)) {
//            return PermissionAttribute.EXECUTE;
//        } else {
//            LOGGER.error("Unrecognised permission assigned " + perm);
//            return null;
//        }
//    }
//
//    private PermissionAttribute mapChangeItemToCreatePermission(final String create) {
//        if (create != null) {
//            // Create perms are used on folders only and the perm is of the form 'Create - Feed'
//            return PermissionAttribute.AUTHOR;
//        } else {
//            LOGGER.error("Unrecognised permission assigned " + create);
//            return null;
//        }
//    }
//    private String getDocIdentity(final String docUuid) {
//        final DocRef docRef = DocRef.builder()
//                .uuid(docUuid)
//                .build();
//        final DocRef decoratedDocRef = docRefInfoServiceProvider.get().decorate(docRef);
//        if (decoratedDocRef == null) {
//            return LogUtil.message("Document/folder {} ", docUuid);
//        } else {
//            return LogUtil.message("{} '{}' ({})",
//                    decoratedDocRef.getType(),
//                    decoratedDocRef.getName(),
//                    decoratedDocRef.getUuid());
//        }
//    }
//
//    private PermissionState buildPermissionState(final DocRef docRef,
//                                                 final Changes changes,
//                                                 final Cascade cascade) {
//        final List<String> docUuids = new ArrayList<>();
//        // Add the top level doc
//        docUuids.add(docRef.getUuid());
//        final List<DocRef> descendants;
//        if (DocumentTypes.isFolder(docRef.getType())) {
//            // getDescendants includes self, so filter it out as we only want the actual descendants
//            descendants = explorerNodeServiceProvider.get().getDescendants(docRef)
//                    .stream()
//                    .map(ExplorerNode::getDocRef)
//                    .filter(descendantDocRef -> !Objects.equals(docRef, descendantDocRef))
//                    .collect(Collectors.toList());
//            // Add any descendants
//            descendants.stream()
//                    .map(DocRef::getUuid)
//                    .forEach(docUuids::add);
//        } else {
//            descendants = Collections.emptyList();
//        }
//
//        // Get the current perms for all documents that we are working on
//        final Map<String, DocumentPermissions> permissionsMap = documentPermissionServiceProvider.get()
//                .getPermissionsForDocuments(docUuids);
//
//        return new PermissionState(
//                docRef,
//                changes,
//                cascade,
//                permissionsMap,
//                descendants);
//    }
//
//    private void indent(final StringBuilder sb,
//                        final String str,
//                        final int level,
//                        final boolean newLine) {
//        if (newLine) {
//            sb.append("\n");
//        }
//        sb.append(Strings.repeat("  ", level));
//        NullSafe.consume(str, sb::append);
//    }
//
//    private String indent(final int level, final boolean newLine) {
//        final StringBuilder sb = new StringBuilder();
//        indent(sb, null, level, newLine);
//        return sb.toString();
//    }
//
//    private String indent(final String str, final int level, final boolean newLine) {
//        final StringBuilder sb = new StringBuilder();
//        indent(sb, str, level, newLine);
//        return sb.toString();
//    }
//
//
//    // --------------------------------------------------------------------------------
//
//
//    /**
//     * Holds all the state and helper methods required to process a
//     * {@link ChangeDocumentPermissionsRequest}
//     */
//    private static class PermissionState {
//
//        // 'top level' refers to the document that the user clicked on in the UI,
//        // i.e. the ancestor for any cascaded changes.
//
//        private final DocRef docRef;
//        private final Changes changes;
//        private final Cascade cascade;
//        // docUuid => DocumentPermissions
//        private final Map<DocRef, DocumentPermissions> currentPermissions;
//        private final List<DocRef> descendants;
//
//        // For the top level
//        private final UserRef currentTopLevelOwner;
//        // For the top level. This should only contain one owner as the UI should enforce a single
//        // owner.
//        private final UserRef requestedTopLevelOwner;
//        // userUuid => set<perm>
//        private final Map<String, DocumentPermissionSet> effectiveTopLevelPerms;
//
//        private PermissionState(final DocRef docRef,
//                                final Changes changes,
//                                final Cascade cascade,
//                                final Map<DocRef, DocumentPermissions> currentPermissions,
//                                final List<DocRef> descendants) {
//            this.docRef = docRef;
//            this.cascade = cascade;
//            this.currentPermissions = currentPermissions;
//            this.descendants = descendants;
//            this.currentTopLevelOwnerUuid = getCurrentOwnerUuid(getTopLevelDocRef().getUuid());
//            this.requestedTopLevelOwnerUuid = getRequestedOwnerUuids(changes);
//            this.changes = changes;
//            this.effectiveTopLevelPerms = buildEffectivePermissions(
//                    changes, currentPermissions.get(docRef.getUuid()));
//        }
//
//        private Map<String, DocumentPermissionSet> buildEffectivePermissions(final Changes changes,
//                                                                   final DocumentPermissions documentPermissions) {
//
//            final Map<String, DocumentPermissionSet> effectivePermsMap = new HashMap<>();
//            final Map<String, DocumentPermissionSet> removes = changes != null
//                    ? NullSafe.map(changes.getRemove())
//                    : Collections.emptyMap();
//            final Map<String, DocumentPermissionSet> adds = changes != null
//                    ? NullSafe.map(changes.getAdd())
//                    : Collections.emptyMap();
//
//            // Build the perms map without any of the remove set
//            if (documentPermissions != null) {
//                documentPermissions.getPermissions().forEach((userUuid, permissions) ->
//                        permissions.getPermissions().forEach(perm -> {
//                            final DocumentPermissionSet removePerms = removes.get(userUuid);
//                            if (removePerms == null || !removePerms.getPermissions().contains(perm)) {
//                                // Not in the remove set so put it in ours
//                                effectivePermsMap.computeIfAbsent(userUuid, k -> new DocumentPermissionSet())
//                                        .addPermission(perm);
//                            }
//                        }));
//            }
//
//            // Now add in the adds
//            adds.forEach((userUuid, permissions) -> {
//                final DocumentPermissionSet documentPermissionSet = effectivePermsMap.computeIfAbsent(userUuid,
//                        k -> new DocumentPermissionSet());
//                documentPermissionSet.getPermissions().addAll(permissions.getPermissions());
//                documentPermissionSet.getFolderCreatePermissions().addAll(permissions.getFolderCreatePermissions());
//            });
//            return effectivePermsMap;
//        }
//
//        private boolean isCascading() {
//            return Cascade.isCascading(cascade);
//        }
//
//        boolean hasDescendants() {
//            return !descendants.isEmpty();
//        }
//
//        DocRef getTopLevelDocRef() {
//            return docRef;
//        }
//
//        DocumentPermissions getCurrentPermissions(final String docUuid) {
//            return Objects.requireNonNullElseGet(
//                    currentPermissions.get(docUuid),
//                    () -> DocumentPermissions.empty(docUuid));
//        }
//
//        boolean isOwnerChanging(final String docUuid) {
//            final Set<String> currentOwnerUuids = getCurrentOwnerUuids(docUuid);
//            return !Objects.equals(currentOwnerUuids, requestedTopLevelOwnerUuids);
//        }
//
//        boolean isTopLevelOwnerChange() {
//            return !Objects.equals(currentTopLevelOwnerUuids, requestedTopLevelOwnerUuids);
//        }
//
//        String getCurrentOwnerUuid(final String docUuid) {
//            return NullSafe.get(getCurrentPermissions(docUuid), DocumentPermissions::getOwnerUuid);
//        }
//
//        boolean isDescendant(final String docUuid) {
//            return docUuid != null && descendantDocUuids.contains(docUuid);
//        }
//
//        String getTopLevelOwnerUuid() {
//            return requestedTopLevelOwnerUuids.iterator().next();
//        }
//    }
//
//
//    // --------------------------------------------------------------------------------
//
//
//    private record UserUuidAndTypedName(String userUuid,
//                                        String typedName) {
//
//    }
}
