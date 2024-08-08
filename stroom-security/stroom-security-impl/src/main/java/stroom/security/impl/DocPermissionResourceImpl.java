/*
 * Copyright 2024 Crown Copyright
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
import stroom.security.shared.PermissionChangeImpactSummary;
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
import stroom.util.shared.StringUtil;
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
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import java.util.stream.Stream;

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
    private final Provider<UserCache> userCacheProvider;
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
                              final Provider<DocRefInfoService> docRefInfoServiceProvider,
                              final Provider<UserCache> userCacheProvider) {
        this.userServiceProvider = userServiceProvider;
        this.documentPermissionServiceProvider = documentPermissionServiceProvider;
        this.documentTypePermissionsProvider = documentTypePermissionsProvider;
        this.explorerNodeServiceProvider = explorerNodeServiceProvider;
        this.securityContextProvider = securityContextProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
        this.docRefInfoServiceProvider = docRefInfoServiceProvider;
        this.userCacheProvider = userCacheProvider;
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
                    docRef,
                    changes,
                    affectedDocRefs,
                    affectedUserUuids,
                    request.getCascade());
            return true;
        }

        final String errorMessage = "Insufficient privileges to change permissions for this document";

        logPermissionChangeError("DocPermissionResourceImpl.changeDocumentPermissions",
                request.getDocRef(), errorMessage);
        throw new PermissionException(getCurrentUserIdForDisplay(), errorMessage);
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public PermissionChangeImpactSummary fetchPermissionChangeImpact(final ChangeDocumentPermissionsRequest request) {
        final PermissionState permissionState = buildPermissionState(
                request.getDocRef(), request.getChanges(), request.getCascade());

        final List<String> errorMessages = validateChanges(permissionState);
        if (!errorMessages.isEmpty()) {
            throw new PermissionException(getCurrentUserIdForDisplay(), String.join("\n", errorMessages));
        }

        String summary = "";
        final List<String> impactDetail = new ArrayList<>();
        if (permissionState.hasDescendants()) {
            final SecurityContext securityContext = securityContextProvider.get();
            final UserCache userCache = userCacheProvider.get();

            final List<DocRef> descendantsWithOwnerPerm = permissionState.descendants.stream()
                    .filter(descendantDocRef -> securityContext.hasDocumentPermission(
                            descendantDocRef, DocumentPermissionNames.OWNER))
                    .toList();

            if (descendantsWithOwnerPerm.isEmpty()) {
                summary = "You do not have the required permission to change the permissions of " +
                        "any descendant documents. Only this document will be modified.";
            } else {
                final int descendantsWithOwnerPermCount = descendantsWithOwnerPerm.size();
                final String descendantCountStr = permissionState.descendants.size() != descendantsWithOwnerPermCount
                        ? descendantsWithOwnerPermCount
                        + " descendant documents. You do not have permission to change some descendant documents."
                        : "all " + permissionState.descendants.size() + " descendant documents.";

                String ownerChangeMsg = "";
                if (shouldChangeOwner(permissionState, permissionState.getTopLevelDocRef().getUuid())) {
                    final int ownerChangeCount = Math.toIntExact(descendantsWithOwnerPerm.stream()
                            .filter(descendantDocRef ->
                                    permissionState.isOwnerChanging(descendantDocRef.getUuid()))
                            .count());

                    if (ownerChangeCount > 0) {
                        ownerChangeMsg = buildOwnerChangeMessage(ownerChangeCount, permissionState, userCache);

                        // Legacy code allowed a doc to have multiple users, this is no longer allowed in the UI, but
                        // we have to tolerate legacy data.
                        final List<DocRef> descendantsWithMultipleOwners = descendantsWithOwnerPerm.stream()
                                .filter(docRef -> permissionState.getCurrentOwnerUuids(docRef.getUuid()).size() > 1)
                                .toList();
                        if (!descendantsWithMultipleOwners.isEmpty()) {
                            impactDetail.add("The following "
                                    + StringUtil.plural("document", descendantsWithMultipleOwners.size())
                                    + " currently "
                                    + StringUtil.plural(
                                    "has", "have", descendantsWithMultipleOwners.size())
                                    + " multiple owners.");
                            impactDetail.add("Ownership" +
                                    " will be REMOVED from all existing owners and assigned to "
                                    + convertToTypedUserName(
                                    permissionState.getTopLevelOwnerUuid(), userCache, true)
                                    + ".");
                            for (final DocRef docRef : descendantsWithMultipleOwners) {
                                impactDetail.add(indent(getDocIdentity(docRef), 1, false)
                                        + " with existing owners:");
                                permissionState.getCurrentOwnerUuids(docRef.getUuid())
                                        .stream()
                                        .map(userUuid -> convertToTypedUserName(userUuid, userCache, false))
                                        .map(str -> indent(str, 2, false))
                                        .forEach(impactDetail::add);
                            }
                        }
                    }
                }

                switch (request.getCascade()) {
                    case NO -> summary = null;
                    case ALL -> summary = "All permissions assigned to this document will be applied to "
                            + descendantCountStr
                            + ownerChangeMsg;
                    case CHANGES_ONLY -> {
                        impactDetail.addAll(getChangeDetail(permissionState.changes, userCache));
                        summary = "The following permission changes will be applied to "
                                + descendantCountStr
                                + ownerChangeMsg;
                    }
                }
            }
        } else {
            summary = "There are no descendant documents. Only this document will be modified.";
        }
        if (!NullSafe.isBlankString(summary)) {
            summary += "\nDo you wish to continue?";
        }
        return new PermissionChangeImpactSummary(summary, String.join("\n", impactDetail));
    }

    private String buildOwnerChangeMessage(final int ownerChangeCount,
                                           final PermissionState permissionState,
                                           final UserCache userCache) {
        String ownerChangeMsg;
        ownerChangeMsg = "\nThe ownership of "
                + ownerChangeCount
                + " descendant "
                + StringUtil.plural("document", ownerChangeCount)
                + " will be changed to "
                + convertToTypedUserName(permissionState.getTopLevelOwnerUuid(), userCache, true)
                + ". Any existing owners will lose the permissions implied by the Owner permission.";
        return ownerChangeMsg;
    }

    private List<String> getChangeDetail(final Changes changes, final UserCache userCache) {

        final List<String> lines = new ArrayList<>();
        if (NullSafe.test(changes, Changes::hasChanges)) {
            final List<UserUuidAndTypedName> allUsers = getUsersFromChanges(changes, userCache);

            if (allUsers.isEmpty()) {
                lines.add("No changes to apply.");
            } else {
                lines.add("Permission changes:");
                allUsers.forEach(userUuidAndTypedName -> {
                    final List<String> removes = permsAsListWithoutOwner(
                            changes.getRemove().get(userUuidAndTypedName.userUuid));
                    final List<String> adds = permsAsListWithoutOwner(
                            changes.getAdd().get(userUuidAndTypedName.userUuid));
                    if (!removes.isEmpty() || !adds.isEmpty()) {
                        final StringBuilder sb = new StringBuilder();
                        lines.add(indent(userUuidAndTypedName.typedName + ":", 1, false));

                        final String permIndent = indent(3, false);
                        addChanges(removes, lines, indent("REMOVE: ", 2, false), permIndent);
                        addChanges(adds, lines, indent("ADD: ", 2, false), permIndent);
                    }
                });
                return lines;
            }
        } else {
            // No changes
            lines.add("No changes to apply.");
        }
        return lines;
    }

    private static void addChanges(final List<String> removes,
                                   final List<String> lines,
                                   final String heading,
                                   final String level3Indent) {
        if (!removes.isEmpty()) {
            if (removes.size() == 1) {
                lines.add(heading + removes.get(0));
            } else {
                lines.add(heading);
                removes.stream()
                        .map(str -> level3Indent + str)
                        .forEach(lines::add);
            }
        }
    }

    private List<UserUuidAndTypedName> getUsersFromChanges(final Changes changes, final UserCache userCache) {
        final List<UserUuidAndTypedName> allUsers = Stream.of(
                        changes.getAdd(),
                        changes.getRemove())
                .filter(Objects::nonNull)
                .flatMap(map -> map.keySet().stream())
                .distinct()
                .map(userUuid -> new UserUuidAndTypedName(
                        userUuid,
                        convertToTypedUserName(userUuid, userCache, false)))
                .sorted(Comparator.comparing(UserUuidAndTypedName::typedName))
                .toList();
        return allUsers;
    }

    private static <T> List<T> permsAsListWithoutOwner(final Set<T> set) {
        return NullSafe.stream(set)
                .filter(perm -> !DocumentPermissionNames.OWNER.equals(perm))
                .sorted()
                .toList();
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
                userCacheProvider.get(),
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

    private void changeDocPermissionsWithLogging(final DocRef docRef,
                                                 final Changes changes,
                                                 final Set<DocRef> affectedDocRefs,
                                                 final Set<String> affectedUserUuids,
                                                 final Cascade cascade) {

        final PermissionState permissionState = buildPermissionState(docRef, changes, cascade);
        final DocumentPermissions documentPermissionsBefore = permissionState.getCurrentPermissions(docRef.getUuid());

        final int removeCount = changes.getRemove()
                .values()
                .stream()
                .mapToInt(Set::size)
                .sum();
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
        final MultiObject before = buildEventState(docRef, documentPermissionsBefore);
        stroomEventLoggingServiceProvider.get()
                .loggedWorkBuilder()
                .withTypeId("DocPermissionResourceImpl.changeDocumentPermissions")
                .withDescription(description)
                .withDefaultEventAction(UpdateEventAction.builder()
                        .withBefore(before)
                        .withAfter(before) // If successful we overwrite with after, but ensures we have one
                        .build())
                .withComplexLoggedAction(updateEventAction -> {
                    doPermissionChange(permissionState, affectedDocRefs, affectedUserUuids);

                    final DocumentPermissions documentPermissionsAfter = documentPermissionServiceProvider.get()
                            .getPermissionsForDocument(docRef.getUuid());

                    // Add in the after state
                    UpdateEventAction modifiedEventAction = updateEventAction.newCopyBuilder()
                            .withAfter(buildEventState(docRef, documentPermissionsAfter))
                            .build();
                    return ComplexLoggedOutcome.success(modifiedEventAction);
                })
                .runActionAndLog();
    }

    private void doPermissionChange(final PermissionState permissionState,
                                    final Set<DocRef> affectedDocRefs,
                                    final Set<String> affectedUserUuids) {
        final List<String> messages = new ArrayList<>(validateChanges(permissionState));
        if (!messages.isEmpty()) {
            throw new PermissionException(
                    securityContextProvider.get().getUserIdentityForAudit(),
                    String.join("\n", messages));
        }

        // Do the actual change
        changeDocPermissions(
                permissionState,
                permissionState.docRef, // The top level docRef
                affectedDocRefs,
                affectedUserUuids,
                false);

        // Cascade changes if this is a folder and we have been asked to do so.
        if (permissionState.isCascading()) {
            messages.addAll(cascadeChanges(permissionState, affectedDocRefs, affectedUserUuids));
            if (!messages.isEmpty()) {
                throw new PermissionException(
                        securityContextProvider.get().getUserIdentityForAudit(),
                        String.join("\n", messages));
            }
        }
    }

    private List<String> validateChanges(final PermissionState permissionState) {
        final SecurityContext securityContext = securityContextProvider.get();
        final DocRef topLevelDocRef = permissionState.getTopLevelDocRef();

//        final String changeOwnerPermName = PermissionNames.CHANGE_OWNER_PERMISSION;
//        if (permissionState.isOwnerChanging(topLevelDocRef.getUuid())
//                && !securityContextProvider.get().hasAppPermission(changeOwnerPermName)) {
//            messages.add(LogUtil.message("{} permission is required to change the owner of a document/folder",
//                    changeOwnerPermName));
//        }

        // Validate the owners on the top level doc
        final List<String> messages = new ArrayList<>(validateDoc(securityContext, permissionState, topLevelDocRef));

        // No point validating the cascade if the top level doc has issues.
        if (messages.isEmpty()) {
            // We may be making a change that does not change the owner of the top level, but does
            // for one or more descendants. Also, the owner of the top level may not be the owner
            // of all descendants
            for (final DocRef descendantDocRef : permissionState.descendants) {
                if (securityContext.hasDocumentPermission(descendantDocRef, DocumentPermissionNames.OWNER)) {
                    messages.addAll(validateDoc(securityContext, permissionState, descendantDocRef));
                }
            }
        }
        return messages;
    }

    private List<String> validateDoc(final SecurityContext securityContext,
                                     final PermissionState permissionState,
                                     final DocRef docRef) {
        final DocumentPermissions currentDocumentPermissions = permissionState.getCurrentPermissions(docRef.getUuid());
        final List<String> messages = new ArrayList<>();

        if (!securityContextProvider.get().hasDocumentPermission(docRef.getUuid(), DocumentPermissionNames.OWNER)) {
            messages.add(LogUtil.message("You need to be the owner of {} to change its permissions.",
                    getDocIdentity(docRef)));
        }

        final Set<String> effectiveOwnerUuids = permissionState.requestedTopLevelOwnerUuids;
        final boolean isOwnerChanging = permissionState.isOwnerChanging(docRef.getUuid());
        final boolean isDescendant = permissionState.isDescendant(docRef.getUuid());
        final String changeOwnerPermName = PermissionNames.CHANGE_OWNER_PERMISSION;

        if (isOwnerChanging
                && !securityContextProvider.get().hasAppPermission(changeOwnerPermName)) {
            messages.add(LogUtil.message("{} permission is required to change the ownership of {}",
                    changeOwnerPermName, getDocIdentity(docRef)));
        }

        // We can't validate the descendants as they may not have any owners due to legacy behaviour
        if (!isDescendant) {
            final String permMsg = !securityContext.hasAppPermission(changeOwnerPermName)
                    ? " Also, " + changeOwnerPermName + " permission is required to change the owner and you do " +
                    "not hold this permission."
                    : "";
            if (effectiveOwnerUuids.isEmpty()) {
                messages.add(LogUtil.message("{} must have exactly one owner. " +
                                "Requested changes would result in no owners.{}",
                        getDocIdentity(docRef),
                        permMsg));
            } else if (effectiveOwnerUuids.size() > 1) {
                final UserService userService = userServiceProvider.get();
                final String effectiveOwnersStr = effectiveOwnerUuids.stream()
                        .map(userService::loadByUuid)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .map(UserName::getUserIdentityForAudit)
                        .collect(Collectors.joining(", "));
                messages.add(LogUtil.message("{} must have exactly one owner. " +
                                "Requested changes would result in the following owners [{}].{}",
                        getDocIdentity(docRef),
                        effectiveOwnersStr,
                        permMsg));
            }
        }
        return messages;
    }

    private MultiObject buildEventState(final DocRef docRef,
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

    private void changeDocPermissions(final PermissionState permissionState,
                                      final DocRef docRef,
                                      final Set<DocRef> affectedDocRefs,
                                      final Set<String> affectedUserUuids,
                                      final boolean cleanAllPermsFirst) {
        final DocumentPermissionServiceImpl documentPermissionServiceImpl = documentPermissionServiceProvider.get();
        final String docUuid = docRef.getUuid();
        final DocumentPermissions currentDocumentPermissions = permissionState.getCurrentPermissions(docUuid);

        if (LOGGER.isDebugEnabled()) {
            logRequest(permissionState.changes, currentDocumentPermissions, LOGGER::debug);
        }

        removeInvalidCreatePerms(docRef, documentPermissionServiceImpl, currentDocumentPermissions);

        if (cleanAllPermsFirst) {
            // If we are asked to clear all permissions then use all the current perms for
            // this document and then remove them.

            currentDocumentPermissions.getPermissions().forEach((userUuid, permissions) -> {
                try {
                    documentPermissionServiceImpl.removePermissions(docRef.getUuid(), userUuid, permissions);
                    // Remember the affected documents and users, so we can clear the relevant caches.
                    affectedDocRefs.add(docRef);
                    affectedUserUuids.add(userUuid);
                } catch (final RuntimeException e) {
                    // Expected.
                    LOGGER.debug(e.getMessage());
                }
            });

        } else {
            // Otherwise remove permissions specified by the change set.
            for (final Entry<String, Set<String>> entry : permissionState.changes.getRemove().entrySet()) {
                final String userUuid = entry.getKey();
                // Owner perm is special so ignore them
                final Set<String> permissions = entry.getValue().stream()
                        .filter(perm -> !DocumentPermissionNames.OWNER.equals(perm))
                        .collect(Collectors.toSet());
                try {
                    documentPermissionServiceImpl.removePermissions(docUuid, userUuid, permissions);
                    // Remember the affected documents and users so we can clear the relevant caches.
                    affectedDocRefs.add(docRef);
                    affectedUserUuids.add(userUuid);
                } catch (final RuntimeException e) {
                    // Expected.
                    LOGGER.debug(e.getMessage());
                }
            }
        }

        final Map<String, Set<String>> permsToAdd;
        if (cleanAllPermsFirst) {
            // We've cleared out all the perms, so just add all the effective perms of the top level doc
            permsToAdd = permissionState.effectiveTopLevelPerms;
        } else {
            permsToAdd = permissionState.changes.getAdd();
        }

        // Add permissions from the change set.
        permsToAdd.forEach((userUuid, perms) -> {
            perms.forEach(permission -> {
                if (DocumentTypes.isFolder(docRef.getType())
                        || !permission.startsWith(DocumentPermissionNames.CREATE)) {
                    try {
                        // Owner perm is special so ignore them
                        if (!DocumentPermissionNames.OWNER.equals(permission)) {
                            documentPermissionServiceImpl.addPermission(docUuid, userUuid, permission);
                        }
                        // Remember the affected documents and users so we can clear the relevant caches.
                        affectedDocRefs.add(docRef);
                        affectedUserUuids.add(userUuid);
                    } catch (final RuntimeException e) {
                        // Expected.
                        LOGGER.debug(e.getMessage());
                    }
                }
            });
        });

        // Only change owner for CHANGES_ONLY if there has been a change to the top level owner
        // and in that case set the descendants to have the same owner as top level.
        if (shouldChangeOwner(permissionState, docUuid)) {
            // Check if this doc already has the right owner
            if (cleanAllPermsFirst || permissionState.isOwnerChanging(docUuid)) {
                LOGGER.debug(() -> LogUtil.message("Setting owner of doc {} to {}",
                        getDocIdentity(docRef), permissionState.getTopLevelOwnerUuid()));
                documentPermissionServiceImpl.setDocumentOwner(docUuid, permissionState.getTopLevelOwnerUuid());
            } else {
                LOGGER.debug(() -> LogUtil.message("Owner of doc {} is already {}",
                        getDocIdentity(docRef), permissionState.getTopLevelOwnerUuid()));
            }
        }
    }

    private boolean shouldChangeOwner(final PermissionState permissionState,
                                      final String docUuid) {

        final Cascade cascade = permissionState.cascade;
        return cascade == Cascade.ALL
                || (cascade == Cascade.NO && !permissionState.isDescendant(docUuid))
                || (cascade == Cascade.CHANGES_ONLY && permissionState.isTopLevelOwnerChange());
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

    private String convertToTypedUserName(final String userUuid,
                                          final UserCache userCache,
                                          final boolean lowerCaseType) {
        return userCache.getByUuid(userUuid)
                .map(user -> {
                    String type = user.getType();
                    if (type != null && lowerCaseType) {
                        type = type.toLowerCase();
                    }
                    return type
                            + " '"
                            + user.getUserIdentityForAudit()
                            + "'";
                })
                .orElseGet(() -> "Unknown user '" + userUuid + "'");
    }

    private List<String> mapPerms(final UserCache userCache, final Map<String, Set<String>> perms) {
        return NullSafe.map(perms)
                .entrySet()
                .stream()
                .map(entry -> {
                    final String userUuid = entry.getKey();
                    final String userNameStr = userCache.getByUuid(userUuid)
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

        final UserCache userCache = userCacheProvider.get();
        final String currentPerms = String.join("\n  ", mapPerms(
                userCache,
                currentDocumentPermissions.getPermissions()));
        final String addsStr = String.join("\n  ", mapPerms(
                userCache,
                changes.getAdd()));
        final String removesStr = String.join("\n  ", mapPerms(
                userCache,
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

    private List<String> cascadeChanges(final PermissionState permissionState,
                                        final Set<DocRef> affectedDocRefs,
                                        final Set<String> affectedUserUuids) {
        final List<String> validationMessages;
        if (DocumentTypes.isFolder(permissionState.docRef.getType())) {
            switch (permissionState.cascade) {
                // permissionState.changes will differ depending on the cascade type, see
                // stroom.security.impl.DocPermissionResourceImpl.PermissionState.buildChanges
                case CHANGES_ONLY:
                case ALL:
                    // We are only cascading changes so just pass on the change set.
                    validationMessages = changeDescendantPermissions(
                            permissionState, affectedDocRefs, affectedUserUuids);
                    break;

                case NO:
                    // Do nothing.
                    validationMessages = Collections.emptyList();
                    break;
                default:
                    throw new RuntimeException("Unexpected cascade " + permissionState.cascade);
            }
        } else {
            validationMessages = Collections.emptyList();
        }
        return validationMessages;
    }

    private List<String> changeDescendantPermissions(final PermissionState permissionState,
                                                     final Set<DocRef> affectedDocRefs,
                                                     final Set<String> affectedUserUuids) {
        final List<DocRef> descendants = permissionState.descendants;
        final List<String> validationMessages = new ArrayList<>();
        for (final DocRef descendantDocRef : descendants) {
            // Ensure that the user has permission to change the permissions of this child.
            final String docUuid = descendantDocRef.getUuid();
            if (securityContextProvider.get().hasDocumentPermission(docUuid, DocumentPermissionNames.OWNER)) {
                try {
                    final boolean cleanAllPermsFirst = permissionState.cascade == Cascade.ALL;
                    changeDocPermissions(
                            permissionState,
                            descendantDocRef,
                            affectedDocRefs,
                            affectedUserUuids,
                            cleanAllPermsFirst);
                } catch (PermissionException e) {
                    validationMessages.add(e.getMessage());
                }
            } else {
                LOGGER.debug("User does not have permission to change permissions on " + descendantDocRef);
            }
        }
        return validationMessages;
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

    private String getDocIdentity(final DocRef docRef) {
        if (docRef == null) {
            return null;
        }
        final DocRef decoratedDocRef = docRefInfoServiceProvider.get().decorate(docRef);
        if (decoratedDocRef == null) {
            return LogUtil.message("Document/folder {} ", docRef.getUuid());
        } else {
            return LogUtil.message("{} '{}' ({})",
                    decoratedDocRef.getType(),
                    decoratedDocRef.getName(),
                    decoratedDocRef.getUuid());
        }
    }

    private PermissionState buildPermissionState(final DocRef docRef,
                                                 final Changes changes,
                                                 final Cascade cascade) {
        final List<String> docUuids = new ArrayList<>();
        // Add the top level doc
        docUuids.add(docRef.getUuid());
        final List<DocRef> descendants;
        if (DocumentTypes.isFolder(docRef.getType())) {
            // getDescendants includes self, so filter it out as we only want the actual descendants
            descendants = explorerNodeServiceProvider.get().getDescendants(docRef)
                    .stream()
                    .map(ExplorerNode::getDocRef)
                    .filter(descendantDocRef -> !Objects.equals(docRef, descendantDocRef))
                    .collect(Collectors.toList());
            // Add any descendants
            descendants.stream()
                    .map(DocRef::getUuid)
                    .forEach(docUuids::add);
        } else {
            descendants = Collections.emptyList();
        }

        // Get the current perms for all documents that we are working on
        final Map<String, DocumentPermissions> permissionsMap = documentPermissionServiceProvider.get()
                .getPermissionsForDocuments(docUuids);

        return new PermissionState(
                docRef,
                changes,
                cascade,
                permissionsMap,
                descendants);
    }

    private void indent(final StringBuilder sb,
                        final String str,
                        final int level,
                        final boolean newLine) {
        if (newLine) {
            sb.append("\n");
        }
        sb.append(Strings.repeat("  ", level));
        NullSafe.consume(str, sb::append);
    }

    private String indent(final int level, final boolean newLine) {
        final StringBuilder sb = new StringBuilder();
        indent(sb, null, level, newLine);
        return sb.toString();
    }

    private String indent(final String str, final int level, final boolean newLine) {
        final StringBuilder sb = new StringBuilder();
        indent(sb, str, level, newLine);
        return sb.toString();
    }


    // --------------------------------------------------------------------------------


    /**
     * Holds all the state and helper methods required to process a
     * {@link ChangeDocumentPermissionsRequest}
     */
    private static class PermissionState {

        // 'top level' refers to the document that the user clicked on in the UI,
        // i.e. the ancestor for any cascaded changes.

        private final DocRef docRef;
        private final Changes changes;
        private final Cascade cascade;
        // docUuid => DocumentPermissions
        private final Map<String, DocumentPermissions> currentPermissions;
        private final List<DocRef> descendants;
        private final Set<String> descendantDocUuids;

        // For the top level
        private final Set<String> currentTopLevelOwnerUuids;
        // For the top level. This should only contain one owner as the UI should enforce a single
        // owner.
        private final Set<String> requestedTopLevelOwnerUuids;
        // userUuid => set<perm>
        private final Map<String, Set<String>> effectiveTopLevelPerms;

        private PermissionState(final DocRef docRef,
                                final Changes changes,
                                final Cascade cascade,
                                final Map<String, DocumentPermissions> currentPermissions,
                                final List<DocRef> descendants) {
            this.docRef = docRef;
            this.cascade = cascade;
            this.currentPermissions = currentPermissions;
            this.descendants = descendants;
            this.descendantDocUuids = descendants.stream()
                    .map(DocRef::getUuid)
                    .collect(Collectors.toSet());
            this.currentTopLevelOwnerUuids = getCurrentOwnerUuids(getTopLevelDocRef().getUuid());
            this.requestedTopLevelOwnerUuids = getRequestedOwnerUuids(changes);
            this.changes = changes;
            this.effectiveTopLevelPerms = buildEffectivePermissions(
                    changes, currentPermissions.get(docRef.getUuid()));
        }

        private Set<String> getRequestedOwnerUuids(final Changes changes) {
            final Set<String> requestedOwnerUuids = new HashSet<>(currentTopLevelOwnerUuids);
            // Apply the removes
            NullSafe.map(changes.getRemove())
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getValue().contains(DocumentPermissionNames.OWNER))
                    .map(Entry::getKey)
                    .forEach(requestedOwnerUuids::remove);
            // Apply the adds
            NullSafe.map(changes.getAdd())
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getValue().contains(DocumentPermissionNames.OWNER))
                    .map(Entry::getKey)
                    .forEach(requestedOwnerUuids::add);
            return requestedOwnerUuids;
        }

        private Map<String, Set<String>> buildEffectivePermissions(final Changes changes,
                                                                   final DocumentPermissions documentPermissions) {

            final Map<String, Set<String>> effectivePermsMap = new HashMap<>();
            final Map<String, Set<String>> removes = changes != null
                    ? NullSafe.map(changes.getRemove())
                    : Collections.emptyMap();
            final Map<String, Set<String>> adds = changes != null
                    ? NullSafe.map(changes.getAdd())
                    : Collections.emptyMap();

            // Build the perms map without any of the remove set
            if (documentPermissions != null) {
                documentPermissions.getPermissions().forEach((userUuid, permissions) -> {
                    permissions.forEach(perm -> {
                        final Set<String> removePerms = removes.get(userUuid);
                        if (removePerms == null || !removePerms.contains(perm)) {
                            // Not in the remove set so put it in ours
                            effectivePermsMap.computeIfAbsent(userUuid, k -> new HashSet<>())
                                    .add(perm);
                        }
                    });
                });
            }

            // Now add in the adds
            adds.forEach((userUuid, permissions) -> {
                effectivePermsMap.computeIfAbsent(userUuid, k -> new HashSet<>())
                        .addAll(permissions);
            });
            return effectivePermsMap;
        }

        private boolean isCascading() {
            return Cascade.isCascading(cascade);
        }

        boolean hasDescendants() {
            return !descendants.isEmpty();
        }

        DocRef getTopLevelDocRef() {
            return docRef;
        }

        DocumentPermissions getCurrentPermissions(final String docUuid) {
            return Objects.requireNonNullElseGet(
                    currentPermissions.get(docUuid),
                    () -> DocumentPermissions.empty(docUuid));
        }

        boolean isOwnerChanging(final String docUuid) {
            final Set<String> currentOwnerUuids = getCurrentOwnerUuids(docUuid);
            return !Objects.equals(currentOwnerUuids, requestedTopLevelOwnerUuids);
        }

        boolean isTopLevelOwnerChange() {
            return !Objects.equals(currentTopLevelOwnerUuids, requestedTopLevelOwnerUuids);
        }

        Set<String> getCurrentOwnerUuids(final String docUuid) {
            return NullSafe.stream(getCurrentPermissions(docUuid).getOwners())
                    .map(User::getUuid)
                    .collect(Collectors.toSet());
        }

        boolean isDescendant(final String docUuid) {
            return docUuid != null && descendantDocUuids.contains(docUuid);
        }

        String getTopLevelOwnerUuid() {
            return requestedTopLevelOwnerUuids.iterator().next();
        }
    }


    // --------------------------------------------------------------------------------


    private record UserUuidAndTypedName(String userUuid,
                                        String typedName) {

    }
}
