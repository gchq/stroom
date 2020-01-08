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

package stroom.security.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import stroom.query.api.v2.DocRef;
import stroom.security.Insecure;
import stroom.security.ProcessingUserIdentity;
import stroom.security.SecurityContext;
import stroom.security.server.exception.AuthenticationException;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.UserAppPermissions;
import stroom.security.shared.UserIdentity;
import stroom.security.shared.UserRef;
import stroom.security.spring.SecurityConfiguration;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import javax.persistence.RollbackException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
@Profile(SecurityConfiguration.PROD_SECURITY)
@Scope(value = StroomScope.SINGLETON, proxyMode = ScopedProxyMode.INTERFACES)
class SecurityContextImpl implements SecurityContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityContextImpl.class);

    private final UserDocumentPermissionsCache userDocumentPermissionsCache;
    private final UserGroupsCache userGroupsCache;
    private final UserAppPermissionsCache userAppPermissionsCache;
    private final UserCache userCache;
    private final DocumentPermissionService documentPermissionService;
    private final DocumentTypePermissions documentTypePermissions;

    @Inject
    SecurityContextImpl(
            final UserDocumentPermissionsCache userDocumentPermissionsCache,
            final UserGroupsCache userGroupsCache,
            final UserAppPermissionsCache userAppPermissionsCache,
            final UserCache userCache,
            final DocumentPermissionService documentPermissionService,
            final DocumentTypePermissions documentTypePermissions) {
        this.userDocumentPermissionsCache = userDocumentPermissionsCache;
        this.userGroupsCache = userGroupsCache;
        this.userAppPermissionsCache = userAppPermissionsCache;
        this.userCache = userCache;
        this.documentPermissionService = documentPermissionService;
        this.documentTypePermissions = documentTypePermissions;
    }

    @Override
    public void pushUser(final UserIdentity userIdentity) {
        CurrentUserState.push(userIdentity);
    }

    @Override
    public UserIdentity popUser() {
        return CurrentUserState.pop();
    }

    @Override
    public String getUserId() {
        final UserIdentity userIdentity = getUserIdentity();
        if (userIdentity == null) {
            return null;
        }
        return userIdentity.getId();
    }

    @Override
    public UserIdentity getUserIdentity() {
        return CurrentUserState.current();
    }

    @Override
    public UserIdentity createIdentity(final String userId) {
        Objects.requireNonNull(userId, "Null user id provided");
        final Optional<UserRef> optional = userCache.get(userId);
        if (!optional.isPresent()) {
            throw new AuthenticationException("Unable to find user with id=" + userId);
        }
        return new UserIdentityImpl(optional.get(), userId, null, null);
    }

    @Override
    public boolean isLoggedIn() {
        return getUserIdentity() != null;
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

    private UserRef getUserRef(final UserIdentity userIdentity) {
        if (!(userIdentity instanceof UserIdentityImpl)) {
            throw new AuthenticationException("Expecting a real user identity");
        }
        return ((UserIdentityImpl) userIdentity).getUserRef();
    }

    @Override
    @Insecure
    public boolean hasAppPermission(final String permission) {
        // Get the current user.
        final UserIdentity userIdentity = getUserIdentity();

        // If there is no logged in user then throw an exception.
        if (userIdentity == null) {
            throw new AuthenticationException("No user is currently logged in");
        }

        // If the user is the internal processing user then they automatically have permission.
        if (ProcessingUserIdentity.INSTANCE.equals(userIdentity)) {
            return true;
        }

        // See if the user has permission.
        final UserRef userRef = getUserRef(userIdentity);
        boolean result = hasAppPermission(userRef, permission);

        // If the user doesn't have the requested permission see if they are an admin.
        if (!result && !PermissionNames.ADMINISTRATOR.equals(permission)) {
            result = hasAppPermission(userRef, PermissionNames.ADMINISTRATOR);
        }

        return result;
    }

    private boolean hasAppPermission(final UserRef userRef, final String permission) {
        // See if the user has an explicit permission.
        if (hasUserAppPermission(userRef, permission)) {
            return true;
        }

        // See if the user belongs to a group that has permission.
        final List<UserRef> userGroups = userGroupsCache.get(userRef);
        if (userGroups != null) {
            for (final UserRef userGroup : userGroups) {
                if (hasUserAppPermission(userGroup, permission)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasUserAppPermission(final UserRef userRef, final String permission) {
        final UserAppPermissions userAppPermissions = userAppPermissionsCache.get(userRef);
        if (userAppPermissions != null) {
            return userAppPermissions.getUserPermissons().contains(permission);
        }
        return false;
    }

    @Override
    public boolean hasDocumentPermission(final String documentType, final String documentUuid, final String permission) {
        // Let administrators do anything.
        if (isAdmin()) {
            return true;
        }

        // Get the current user.
        final UserIdentity userIdentity = getUserIdentity();

        // If there is no logged in user then throw an exception.
        if (userIdentity == null) {
            throw new AuthenticationException("No user is currently logged in");
        }

        // If we are currently allowing users with only `Use` permission to `Read` (elevate permissions) then test for `Use` instead of `Read`.
        String perm = permission;
        if (CurrentUserState.isElevatePermissions() && DocumentPermissionNames.READ.equals(perm)) {
            perm = DocumentPermissionNames.USE;
        }

        final UserRef userRef = getUserRef(userIdentity);
        return hasDocumentPermission(userRef, documentUuid, perm);
    }

    private boolean hasDocumentPermission(final UserRef userRef, final String documentUuid, final String permission) {
        // See if the user has an explicit permission.
        if (hasUserDocumentPermission(userRef.getUuid(), documentUuid, permission)) {
            return true;
        }

        // See if the user belongs to a group that has permission.
        final List<UserRef> userGroups = userGroupsCache.get(userRef);
        if (userGroups != null) {
            for (final UserRef userGroup : userGroups) {
                if (hasUserDocumentPermission(userGroup.getUuid(), documentUuid, permission)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasUserDocumentPermission(final String userUuid, final String documentUuid, final String permission) {
        final UserDocumentPermissions userDocumentPermissions = userDocumentPermissionsCache.get(userUuid);
        if (userDocumentPermissions != null) {
            return userDocumentPermissions.hasDocumentPermission(documentUuid, permission);
        }
        return false;
    }

    @Override
    public void clearDocumentPermissions(final String documentType, final String documentUuid) {
        // Get the current user.
        final UserIdentity userIdentity = getUserIdentity();

        // If no user is present then don't create permissions.
        if (userIdentity != null) {
            if (hasDocumentPermission(documentType, documentUuid, DocumentPermissionNames.OWNER)) {
                final DocRef docRef = new DocRef(documentType, documentUuid);
                documentPermissionService.clearDocumentPermissions(docRef);
            }
        }
    }

    @Override
    public void addDocumentPermissions(final String sourceType, final String sourceUuid, final String documentType, final String documentUuid, final boolean owner) {
        // Get the current user.
        final UserIdentity userIdentity = getUserIdentity();

        // If no user is present then don't create permissions.
        if (userIdentity != null) {
            if (owner || hasDocumentPermission(documentType, documentUuid, DocumentPermissionNames.OWNER)) {
                final DocRef docRef = new DocRef(documentType, documentUuid);

                if (owner) {
                    // Make the current user the owner of the new document.
                    try {
                        final UserRef userRef = getUserRef(userIdentity);
                        documentPermissionService.addPermission(userRef, docRef, DocumentPermissionNames.OWNER);
                    } catch (final RollbackException | TransactionException e) {
                        LOGGER.debug(e.getMessage(), e);
                    } catch (final Exception e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                }

                // Inherit permissions from the parent folder if there is one.
                // TODO : This should be part of the explorer service.
                copyPermissions(sourceType, sourceUuid, documentType, documentUuid);
            }
        }
    }

    private void copyPermissions(final String sourceType, final String sourceUuid, final String destType, final String destUuid) {
        if (sourceType != null && sourceUuid != null) {
            final DocRef sourceDocRef = new DocRef(sourceType, sourceUuid);

            final DocumentPermissions documentPermissions = documentPermissionService.getPermissionsForDocument(sourceDocRef);
            if (documentPermissions != null) {
                final Map<UserRef, Set<String>> userPermissions = documentPermissions.getUserPermissions();
                if (userPermissions != null && userPermissions.size() > 0) {
                    final DocRef destDocRef = new DocRef(destType, destUuid);
                    final String[] allowedPermissions = documentTypePermissions.getPermissions(destDocRef.getType());

                    for (final Map.Entry<UserRef, Set<String>> entry : userPermissions.entrySet()) {
                        final UserRef userRef = entry.getKey();
                        final Set<String> permissions = entry.getValue();

                        for (final String allowedPermission : allowedPermissions) {
                            if (permissions.contains(allowedPermission)) {
                                try {
                                    documentPermissionService.addPermission(userRef, destDocRef, allowedPermission);
                                } catch (final RollbackException | TransactionException e) {
                                    LOGGER.debug(e.getMessage(), e);
                                } catch (final Exception e) {
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