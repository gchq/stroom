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
import stroom.security.SecurityContext;
import stroom.security.server.exception.AuthenticationException;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.UserAppPermissions;
import stroom.security.shared.UserRef;
import stroom.security.spring.SecurityConfiguration;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import javax.persistence.RollbackException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Profile(SecurityConfiguration.PROD_SECURITY)
@Scope(value = StroomScope.PROTOTYPE, proxyMode = ScopedProxyMode.INTERFACES)
class SecurityContextImpl implements SecurityContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityContextImpl.class);
    private static final String INTERNAL = "INTERNAL";
    private static final String SYSTEM = "system";
    private static final String USER = "user";
    private static final UserRef INTERNAL_PROCESSING_USER = new UserRef(User.ENTITY_TYPE, "0", INTERNAL, false, true);

    private final DocumentPermissionsCache documentPermissionsCache;
    private final UserGroupsCache userGroupsCache;
    private final UserAppPermissionsCache userAppPermissionsCache;
    private final UserService userService;
    private final DocumentPermissionService documentPermissionService;
    private final DocumentTypePermissions documentTypePermissions;
    private final AuthenticationServiceClients authenticationServiceClients;

    @Inject
    SecurityContextImpl(
            final DocumentPermissionsCache documentPermissionsCache,
            final UserGroupsCache userGroupsCache,
            final UserAppPermissionsCache userAppPermissionsCache,
            final UserService userService,
            final DocumentPermissionService documentPermissionService,
            final DocumentTypePermissions documentTypePermissions,
            final AuthenticationServiceClients authenticationServiceClients) {
        this.documentPermissionsCache = documentPermissionsCache;
        this.userGroupsCache = userGroupsCache;
        this.userAppPermissionsCache = userAppPermissionsCache;
        this.userService = userService;
        this.documentPermissionService = documentPermissionService;
        this.documentTypePermissions = documentTypePermissions;
        this.authenticationServiceClients = authenticationServiceClients;
    }

    @Override
    public void pushUser(final String token) {
        UserRef userRef = null;

        if (token != null) {
            final String[] parts = token.split("\\|", -1);
            if (parts.length < 3) {
                LOGGER.error("Unexpected token format '" + token + "'");
                throw new AuthenticationException("Unexpected token format '" + token + "'");
            }

            final String type = parts[0];
            final String name = parts[1];
            final String jSessionId = parts[2];

            if (SYSTEM.equals(type)) {
                if (INTERNAL.equals(name)) {
                    userRef = INTERNAL_PROCESSING_USER;
                } else {
                    LOGGER.error("Unexpected system user '" + name + "'");
                    throw new AuthenticationException("Unexpected system user '" + name + "'");
                }
            } else if (USER.equals(type)) {
                if (name.length() > 0) {
                    userRef = userService.getUserByName(name);
                    if (userRef == null) {
                        final String message = "Unable to push user '" + name + "' as user is unknown";
                        LOGGER.error(message);
                        throw new AuthenticationException(message);
                    }
                }
            } else {
                LOGGER.error("Unexpected token type '" + type + "'");
                throw new AuthenticationException("Unexpected token type '" + type + "'");
            }
        }

        CurrentUserState.pushUserRef(userRef);
    }

    @Override
    public String popUser() {
        final UserRef userRef = CurrentUserState.popUserRef();

        if (userRef != null) {
            return userRef.getName();
        }

        return null;
    }

    private UserRef getUserRef() {
        return CurrentUserState.currentUserRef();
    }

    @Override
    public String getUserId() {
        final UserRef userRef = getUserRef();
        if (userRef == null) {
            return null;
        }
        return userRef.getName();
    }

    @Override
    public String getApiToken() {
        return authenticationServiceClients.getUsersApiToken(getUserId());
    }

    @Override
    public boolean isLoggedIn() {
        return getUserRef() != null;
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
    @Insecure
    public boolean hasAppPermission(final String permission) {
        // Get the current user.
        final UserRef userRef = getUserRef();

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

    private boolean hasAppPermission(final UserRef userRef, final String permission) {
        // See if the user has an explicit permission.
        boolean result = hasUserAppPermission(userRef, permission);

        // See if the user belongs to a group that has permission.
        if (!result) {
            final List<UserRef> userGroups = userGroupsCache.get(userRef);
            result = hasUserGroupsAppPermission(userGroups, permission);
        }

        return result;
    }

    private boolean hasUserGroupsAppPermission(final List<UserRef> userGroups, final String permission) {
        if (userGroups != null) {
            for (final UserRef userGroup : userGroups) {
                final boolean result = hasUserAppPermission(userGroup, permission);
                if (result) {
                    return result;
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
    public boolean hasDocumentPermission(final String documentType, final String documentId, final String permission) {
        // Let administrators do anything.
        if (isAdmin()) {
            return true;
        }

        // Get the current user.
        final UserRef userRef = getUserRef();

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

    private boolean hasDocumentPermission(final UserRef userRef, final DocRef docRef, final String permission) {
        // See if the user has an explicit permission.
        boolean result = hasUserDocumentPermission(userRef, docRef, permission);

        // See if the user belongs to a group that has permission.
        if (!result) {
            final List<UserRef> userGroups = userGroupsCache.get(userRef);
            result = hasUserGroupsDocumentPermission(userGroups, docRef, permission);
        }

        return result;
    }

    private boolean hasUserGroupsDocumentPermission(final List<UserRef> userGroups, final DocRef docRef, final String permission) {
        if (userGroups != null) {
            for (final UserRef userGroup : userGroups) {
                final boolean result = hasUserDocumentPermission(userGroup, docRef, permission);
                if (result) {
                    return result;
                }
            }
        }
        return false;
    }

    private boolean hasUserDocumentPermission(final UserRef userRef, final DocRef docRef, final String permission) {
        final DocumentPermissions documentPermissions = documentPermissionsCache.get(docRef);
        if (documentPermissions != null) {
            final Set<String> permissions = documentPermissions.getPermissionsForUser(userRef);
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
        final UserRef userRef = getUserRef();

        // If no user is present then don't create permissions.
        if (userRef != null) {
            if (hasDocumentPermission(documentType, documentUuid, DocumentPermissionNames.OWNER)) {
                final DocRef docRef = new DocRef(documentType, documentUuid);
                documentPermissionService.clearDocumentPermissions(docRef);

                // Make sure cache updates for the document.
                documentPermissionsCache.remove(docRef);
            }
        }
    }

    @Override
    public void addDocumentPermissions(final String sourceType, final String sourceUuid, final String documentType, final String documentUuid, final boolean owner) {
        // Get the current user.
        final UserRef userRef = getUserRef();

        // If no user is present then don't create permissions.
        if (userRef != null) {
            if (owner || hasDocumentPermission(documentType, documentUuid, DocumentPermissionNames.OWNER)) {
                final DocRef docRef = new DocRef(documentType, documentUuid);

                if (owner) {
                    // Make the current user the owner of the new document.
                    try {
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

                // Make sure cache updates for the document.
                documentPermissionsCache.remove(docRef);
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