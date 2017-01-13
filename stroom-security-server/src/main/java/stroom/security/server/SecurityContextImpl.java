/*
 * Copyright 2016 Crown Copyright
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

package stroom.security.server;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.UnavailableSecurityManagerException;
import org.apache.shiro.session.InvalidSessionException;
import org.apache.shiro.subject.Subject;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import stroom.entity.server.GenericEntityService;
import stroom.entity.shared.DocRef;
import stroom.entity.shared.DocumentEntityService;
import stroom.entity.shared.EntityService;
import stroom.entity.shared.Folder;
import stroom.security.SecurityContext;
import stroom.security.server.exception.AuthenticationServiceException;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.User;
import stroom.security.shared.UserRef;
import stroom.security.shared.UserService;
import stroom.security.spring.SecurityConfiguration;
import stroom.util.logging.StroomLogger;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import javax.persistence.PersistenceException;
import java.util.Map;
import java.util.Set;

@Component
@Profile(SecurityConfiguration.PROD_SECURITY)
@Scope(value = StroomScope.PROTOTYPE, proxyMode = ScopedProxyMode.INTERFACES)
class SecurityContextImpl implements SecurityContext {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(SecurityContextImpl.class);
    private static final UserRef INTERNAL_PROCESSING_USER = new UserRef(User.ENTITY_TYPE, "0", "INTERNAL_PROCESSING_USER", false);
    private final UserPermissionsCache userPermissionCache;
    private final UserService userService;
    private final DocumentPermissionService documentPermissionService;
    private final GenericEntityService genericEntityService;

    @Inject
    SecurityContextImpl(final UserPermissionsCache userPermissionCache, final UserService userService, final DocumentPermissionService documentPermissionService, final GenericEntityService genericEntityService) {
        this.userPermissionCache = userPermissionCache;
        this.userService = userService;
        this.documentPermissionService = documentPermissionService;
        this.genericEntityService = genericEntityService;
    }

    @Override
    public void pushUser(final String name) {
        UserRef userRef = INTERNAL_PROCESSING_USER;
        if (name != null && !"INTERNAL_PROCESSING_USER".equals(name)) {
            userRef = userService.getUserByName(name);
        }

        if (userRef == null) {
            final String message = "Unable to push user '" + name + "' as user is unknown";
            LOGGER.error(message);
            throw new AuthenticationServiceException(message);
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

    private Subject getSubject() {
        try {
            return SecurityUtils.getSubject();
        } catch (final UnavailableSecurityManagerException e) {
            // If the call is an internal one then there won't be a security manager.
            LOGGER.debug(e.getMessage(), e);
        }

        return null;
    }

    private UserRef getUserRef() {
        UserRef userRef = CurrentUserState.currentUserRef();
        try {
            if (userRef == null) {
                final Subject subject = getSubject();
                if (subject != null && subject.isAuthenticated()) {
                    final User user = (User) subject.getPrincipal();
                    if (user != null) {
                        userRef = UserRef.create(user);
                    }
                }
            }
        } catch (final InvalidSessionException e) {
            // If the session has expired then the user will need to login again.
            LOGGER.debug(e.getMessage(), e);
        }

        return userRef;
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
    public boolean hasAppPermission(final String permission) {
        // Get the current user.
        final UserRef userRef = getUserRef();

        // If there is no logged in user then throw an exception.
        if (userRef == null) {
            throw new AuthenticationServiceException("No user is currently logged in");
        }

        // If the user is the internal processing user then they automatically have permission.
        if (INTERNAL_PROCESSING_USER.equals(userRef)) {
            return true;
        }

        final UserPermissions userPermissions = userPermissionCache.get(userRef);
        boolean result = userPermissions.hasAppPermission(permission);

        // If the user doesn't have the requested permission see if they are an admin.
        if (!result && !PermissionNames.ADMINISTRATOR.equals(permission)) {
            result = userPermissions.hasAppPermission(PermissionNames.ADMINISTRATOR);
        }

        return result;
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
            throw new AuthenticationServiceException("No user is currently logged in");
        }

        final UserPermissions userPermissions = userPermissionCache.get(userRef);
        boolean result = userPermissions.hasDocumentPermission(documentType, documentId, permission);

        // If the user doesn't have read permission then check to see if the current task has been set to have elevated permissions.
        if (!result && DocumentPermissionNames.READ.equals(permission)) {
            if (CurrentUserState.isElevatePermissions()) {
                result = userPermissions.hasDocumentPermission(documentType, documentId, DocumentPermissionNames.USE);
            }
        }

        return result;
    }

    @Override
    public void createInitialDocumentPermissions(final String documentType, final String documentUuid, final String folderUuid) {
        // Get the current user.
        final UserRef userRef = getUserRef();

        // If no user is present then don't create permissions.
        if (userRef != null) {
            final DocRef docRef = new DocRef(documentType, documentUuid);

            // Make the current user the owner of the new document.
            documentPermissionService.addPermission(userRef, docRef, DocumentPermissionNames.OWNER);

            // Inherit permissions from the parent folder if there is one.
            // TODO : This should be part of the explorer service.
            if (folderUuid != null) {
                addInheritedPermissions(documentType, documentUuid, folderUuid);
            }

            // Make sure the cache updates for the current user.
            userPermissionCache.remove(userRef);
        }
    }

    private void addInheritedPermissions(final String documentType, final String documentUuid, final String folderUuid) {
        final DocRef docRef = new DocRef(documentType, documentUuid);

        final DocumentPermissions documentPermissions = documentPermissionService.getPermissionsForDocument(new DocRef(Folder.ENTITY_TYPE, folderUuid));
        if (documentPermissions != null) {
            final Map<UserRef, Set<String>> userPermissions = documentPermissions.getUserPermissions();
            if (userPermissions != null && userPermissions.size() > 0) {
                final EntityService<?> entityService = genericEntityService.getEntityService(documentType);
                if (entityService != null && entityService instanceof DocumentEntityService) {
                    final DocumentEntityService<?> documentEntityService = (DocumentEntityService) entityService;
                    final String[] allowedPermissions = documentEntityService.getPermissions();

                    for (final Map.Entry<UserRef, Set<String>> entry : userPermissions.entrySet()) {
                        final UserRef userRef = entry.getKey();
                        final Set<String> permissions = entry.getValue();

                        for (final String allowedPermission : allowedPermissions) {
                            if (permissions.contains(allowedPermission)) {
                                // Don't allow owner permissions to be inherited.
                                if (!DocumentPermissionNames.OWNER.equals(allowedPermission)) {
                                    try {
                                        documentPermissionService.addPermission(userRef, docRef, allowedPermission);
                                    } catch (final PersistenceException e) {
                                        // Ignore.
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
