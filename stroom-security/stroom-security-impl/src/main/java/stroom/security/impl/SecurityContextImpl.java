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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.security.impl.exception.AuthenticationException;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.User;
import stroom.security.shared.UserAppPermissions;
import stroom.security.shared.UserToken;
import stroom.security.api.UserTokenUtil;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class SecurityContextImpl implements SecurityContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityContextImpl.class);
    private static final String USER = "user";
    private static final UserToken INTERNAL_PROCESSING_USER_TOKEN = UserTokenUtil.processingUser();
    private static final User INTERNAL_PROCESSING_USER = new User.Builder()
            .id(0)
            .uuid("0")
            .name(INTERNAL_PROCESSING_USER_TOKEN.getUserId())
            .group(false)
            .build();

    private final DocumentPermissionsCache documentPermissionsCache;
    private final UserGroupsCache userGroupsCache;
    private final UserAppPermissionsCache userAppPermissionsCache;
    private final UserCache userCache;
    private final DocumentPermissionService documentPermissionService;
    private final DocumentTypePermissions documentTypePermissions;
    private final ApiTokenCache apiTokenCache;

    @Inject
    SecurityContextImpl(
            final DocumentPermissionsCache documentPermissionsCache,
            final UserGroupsCache userGroupsCache,
            final UserAppPermissionsCache userAppPermissionsCache,
            final UserCache userCache,
            final DocumentPermissionService documentPermissionService,
            final DocumentTypePermissions documentTypePermissions,
            final ApiTokenCache apiTokenCache) {
        this.documentPermissionsCache = documentPermissionsCache;
        this.userGroupsCache = userGroupsCache;
        this.userAppPermissionsCache = userAppPermissionsCache;
        this.userCache = userCache;
        this.documentPermissionService = documentPermissionService;
        this.documentTypePermissions = documentTypePermissions;
        this.apiTokenCache = apiTokenCache;
    }

    @Override
    public void pushUser(final UserToken token) {
        User userRef = null;

        if (token != null) {
            final String type = token.getType();
            final String name = token.getUserId();

            if (INTERNAL_PROCESSING_USER_TOKEN.getType().equals(type)) {
                if (INTERNAL_PROCESSING_USER_TOKEN.getUserId().equals(name)) {
                    userRef = INTERNAL_PROCESSING_USER;
                } else {
                    LOGGER.error("Unexpected system user '" + name + "'");
                    throw new AuthenticationException("Unexpected system user '" + name + "'");
                }
            } else if (USER.equals(type)) {
                if (name.length() > 0) {
                    final Optional<User> optional = userCache.get(name);
                    if (!optional.isPresent()) {
                        final String message = "Unable to push user '" + name + "' as user is unknown";
                        LOGGER.error(message);
                        throw new AuthenticationException(message);
                    } else {
                        userRef = optional.get();
                    }
                }
            } else {
                LOGGER.error("Unexpected token type '" + type + "'");
                throw new AuthenticationException("Unexpected token type '" + type + "'");
            }
        }

        CurrentUserState.push(token, userRef);
    }

    @Override
    public void popUser() {
        CurrentUserState.pop();
    }

    private User getUser() {
        return CurrentUserState.currentUser();
    }

    @Override
    public UserToken getUserToken() {
        return CurrentUserState.currentUserToken();
    }

    @Override
    public String getUserId() {
        final User userRef = getUser();
        if (userRef == null) {
            return null;
        }
        return userRef.getName();
    }

    @Override
    public String getApiToken() {
        return apiTokenCache.get(getUserId());
    }

    @Override
    public boolean isLoggedIn() {
        return getUser() != null;
    }

    @Override
    public boolean isAdmin() {
        return hasAppPermission(PermissionNames.ADMINISTRATOR);
    }

    @Override
    public void elevatePermissions() {
        CurrentUserState.elevatePermissions();
    }

    @Override
    public void restorePermissions() {
        CurrentUserState.restorePermissions();
    }

    @Override
    public boolean hasAppPermission(final String permission) {
        // Get the current user.
        final User userRef = getUser();

        // If there is no logged in user then throw an exception.
        if (userRef == null) {
            throw new AuthenticationException("No user is currently logged in");
        }

        // If the user is the internal processing user then they automatically have permission.
        if (INTERNAL_PROCESSING_USER.equals(userRef)) {
            return true;
        }

        // See if the user has permission.
        boolean result = hasAppPermission(userRef, permission);

        // If the user doesn't have the requested permission see if they are an admin.
        if (!result && !PermissionNames.ADMINISTRATOR.equals(permission)) {
            result = hasAppPermission(userRef, PermissionNames.ADMINISTRATOR);
        }

        return result;
    }

    private boolean hasAppPermission(final User userRef, final String permission) {
        // See if the user has an explicit permission.
        boolean result = hasUserAppPermission(userRef, permission);

        // See if the user belongs to a group that has permission.
        if (!result) {
            final List<User> userGroups = userGroupsCache.get(userRef.getUuid());
            result = hasUserGroupsAppPermission(userGroups, permission);
        }

        return result;
    }

    private boolean hasUserGroupsAppPermission(final List<User> userGroups, final String permission) {
        if (userGroups != null) {
            for (final User userGroup : userGroups) {
                final boolean result = hasUserAppPermission(userGroup, permission);
                if (result) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasUserAppPermission(final User userRef, final String permission) {
        final UserAppPermissions userAppPermissions = userAppPermissionsCache.get(userRef);
        if (userAppPermissions != null) {
            return userAppPermissions.getUserPermissons().contains(permission);
        }
        return false;
    }

    @Override
    public boolean hasDocumentPermission(final String documentType, final String documentId, final String permission) {
        // Let administrators do anything.
        if (isAdmin()) {
            return true;
        }

        // Get the current user.
        final User userRef = getUser();

        // If there is no logged in user then throw an exception.
        if (userRef == null) {
            throw new AuthenticationException("No user is currently logged in");
        }

        final DocRef docRef = new DocRef(documentType, documentId);
        boolean result = hasDocumentPermission(userRef, docRef, permission);

        // If the user doesn't have read permission then check to see if the current task has been set to have elevated permissions.
        if (!result && DocumentPermissionNames.READ.equals(permission)) {
            if (CurrentUserState.isElevatePermissions()) {
                result = hasDocumentPermission(userRef, docRef, DocumentPermissionNames.USE);
            }
        }

        return result;
    }

    private boolean hasDocumentPermission(final User userRef, final DocRef docRef, final String permission) {
        // See if the user has an explicit permission.
        boolean result = hasUserDocumentPermission(userRef, docRef, permission);

        // See if the user belongs to a group that has permission.
        if (!result) {
            final List<User> userGroups = userGroupsCache.get(userRef.getUuid());
            result = hasUserGroupsDocumentPermission(userGroups, docRef, permission);
        }

        return result;
    }

    private boolean hasUserGroupsDocumentPermission(final List<User> userGroups, final DocRef docRef, final String permission) {
        if (userGroups != null) {
            for (final User userGroup : userGroups) {
                final boolean result = hasUserDocumentPermission(userGroup, docRef, permission);
                if (result) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasUserDocumentPermission(final User userRef,
                                              final DocRef docRef,
                                              final String permission) {
        final DocumentPermissions documentPermissions = documentPermissionsCache.get(docRef.getUuid());
        if (documentPermissions != null) {
            final Set<String> permissions = documentPermissions.getPermissionsForUser(userRef.getUuid());
            if (permissions != null) {
                String perm = permission;
                while (perm != null) {
                    if (permissions.contains(perm)) {
                        return true;
                    }

                    // If the user doesn't explicitly have this permission then see if they have a higher permission that infers this one.
                    perm = DocumentPermissionNames.getHigherPermission(perm);
                }
            }
        }
        return false;
    }

    @Override
    public void clearDocumentPermissions(final String documentType, final String documentUuid) {
        // Get the current user.
        final User userRef = getUser();

        // If no user is present then don't create permissions.
        if (userRef != null) {
            if (hasDocumentPermission(documentType, documentUuid, DocumentPermissionNames.OWNER)) {
                final DocRef docRef = new DocRef(documentType, documentUuid);
                documentPermissionService.clearDocumentPermissions(documentUuid);

                // Make sure cache updates for the document.
                documentPermissionsCache.remove(docRef.getUuid());
            }
        }
    }

    @Override
    public void addDocumentPermissions(final String sourceType, final String sourceUuid, final String documentType, final String documentUuid, final boolean owner) {
        // Get the current user.
        final User userRef = getUser();

        // If no user is present then don't create permissions.
        if (userRef != null) {
            if (owner || hasDocumentPermission(documentType, documentUuid, DocumentPermissionNames.OWNER)) {
                final DocRef docRef = new DocRef(documentType, documentUuid);

                if (owner) {
                    // Make the current user the owner of the new document.
                    try {
                        documentPermissionService.addPermission(docRef.getUuid(),
                                userRef.getUuid(),
                                DocumentPermissionNames.OWNER);
                    } catch (final RuntimeException e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                }

                // Inherit permissions from the parent folder if there is one.
                // TODO : This should be part of the explorer service.
                copyPermissions(sourceType, sourceUuid, documentType, documentUuid);

                // Make sure cache updates for the document.
                documentPermissionsCache.remove(docRef.getUuid());
            }
        }
    }

    private void copyPermissions(final String sourceType, final String sourceUuid, final String destType, final String destUuid) {
        if (sourceType != null && sourceUuid != null) {
            final DocRef sourceDocRef = new DocRef(sourceType, sourceUuid);

            final DocumentPermissions documentPermissions = documentPermissionService.getPermissionsForDocument(sourceDocRef.getUuid());
            if (documentPermissions != null) {
                final Map<String, Set<String>> userPermissions = documentPermissions.getUserPermissions();
                if (userPermissions != null && userPermissions.size() > 0) {
                    final DocRef destDocRef = new DocRef(destType, destUuid);
                    final String[] allowedPermissions = documentTypePermissions.getPermissions(destDocRef.getType());

                    for (final Map.Entry<String, Set<String>> entry : userPermissions.entrySet()) {
                        final String userUuid = entry.getKey();
                        final Set<String> permissions = entry.getValue();

                        for (final String allowedPermission : allowedPermissions) {
                            if (permissions.contains(allowedPermission)) {
                                try {
                                    documentPermissionService.addPermission(destDocRef.getUuid(),
                                            userUuid,
                                            allowedPermission);
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
}