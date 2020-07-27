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

import stroom.security.api.DocumentPermissionService;
import stroom.security.api.UserIdentity;
import stroom.security.impl.event.AddPermissionEvent;
import stroom.security.impl.event.ClearDocumentPermissionsEvent;
import stroom.security.impl.event.PermissionChangeEventBus;
import stroom.security.impl.event.RemovePermissionEvent;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.User;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public class DocumentPermissionServiceImpl implements DocumentPermissionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentPermissionServiceImpl.class);

    private final DocumentPermissionDao documentPermissionDao;
    private final UserDao userDao;
    private final PermissionChangeEventBus permissionChangeEventBus;
    private final SecurityContextImpl securityContext;

    @Inject
    DocumentPermissionServiceImpl(final DocumentPermissionDao documentPermissionDao,
                                  final UserDao userDao,
                                  final PermissionChangeEventBus permissionChangeEventBus,
                                  final SecurityContextImpl securityContext) {
        this.documentPermissionDao = documentPermissionDao;
        this.userDao = userDao;
        this.permissionChangeEventBus = permissionChangeEventBus;
        this.securityContext = securityContext;
    }

    public Set<String> getPermissionsForDocumentForUser(final String docUuid,
                                                        final String userUuid) {
        return documentPermissionDao.getPermissionsForDocumentForUser(docUuid, userUuid);
    }

    public DocumentPermissions getPermissionsForDocument(final String docUuid) {
        final List<User> users = new ArrayList<>();
        final List<User> groups = new ArrayList<>();
        final Map<String, Set<String>> userPermissions = new HashMap<>();

        try {
            final Map<String, Set<String>> documentPermission = documentPermissionDao.getPermissionsForDocument(docUuid);

            documentPermission.forEach((userUuid, permissions) -> {
                final User user = userDao.getByUuid(userUuid);
                if (user != null) {
                    if (user.isGroup()) {
                        groups.add(user);
                    } else {
                        users.add(user);
                    }
                    userPermissions.put(user.getUuid(), permissions);
                }
            });


        } catch (final RuntimeException e) {
            LOGGER.error("getPermissionsForDocument()", e);
            throw e;
        }

        return new DocumentPermissions(docUuid, users, groups, userPermissions);
    }

    public void addPermission(final String docUuid,
                              final String userUuid,
                              final String permission) {
        documentPermissionDao.addPermission(docUuid, userUuid, permission);
        AddPermissionEvent.fire(permissionChangeEventBus, userUuid, docUuid, permission);
    }

    public void removePermission(final String docUuid,
                                 final String userUuid,
                                 final String permission) {
        documentPermissionDao.removePermission(docUuid, userUuid, permission);
        RemovePermissionEvent.fire(permissionChangeEventBus, userUuid, docUuid, permission);
    }

    void clearDocumentPermissionsForUser(final String docUuid,
                                         final String userUuid) {
        documentPermissionDao.clearDocumentPermissionsForUser(docUuid, userUuid);
        RemovePermissionEvent.fire(permissionChangeEventBus, userUuid, docUuid, null);
    }

    @Override
    public void clearDocumentPermissions(final String docUuid) {
        // Get the current user.
        final UserIdentity userIdentity = securityContext.getUserIdentity();

        // If no user is present then don't clear permissions.
        if (userIdentity != null) {
            if (securityContext.hasDocumentPermission(docUuid, DocumentPermissionNames.OWNER)) {
                documentPermissionDao.clearDocumentPermissions(docUuid);
            }
        }

        ClearDocumentPermissionsEvent.fire(permissionChangeEventBus, docUuid);
    }

    @Override
    public void addDocumentPermissions(final String sourceUuid, final String documentUuid, final boolean owner) {
        // Get the current user.
        final UserIdentity userIdentity = securityContext.getUserIdentity();

        // If no user is present then don't create permissions.
        if (userIdentity != null) {
            final String userUuid = securityContext.getUserUuid(userIdentity);
            if (owner || securityContext.hasDocumentPermission(documentUuid, DocumentPermissionNames.OWNER)) {
                if (owner) {
                    // Make the current user the owner of the new document.
                    try {
                        addPermission(documentUuid,
                                userUuid,
                                DocumentPermissionNames.OWNER);
                    } catch (final RuntimeException e) {
                        LOGGER.error(e.getMessage(), e);
                    }
                }

                // Inherit permissions from the parent folder if there is one.
                // TODO : This should be part of the explorer service.
                copyPermissions(sourceUuid, documentUuid);
            }
        }
    }

    private void copyPermissions(final String sourceUuid, final String destUuid) {
        if (sourceUuid != null) {
            final stroom.security.shared.DocumentPermissions documentPermissions = getPermissionsForDocument(sourceUuid);
            if (documentPermissions != null) {
                final Map<String, Set<String>> userPermissions = documentPermissions.getPermissions();
                if (userPermissions != null && userPermissions.size() > 0) {
                    for (final Map.Entry<String, Set<String>> entry : userPermissions.entrySet()) {
                        final String userUuid = entry.getKey();
                        final Set<String> permissions = entry.getValue();

                        for (final String permission : permissions) {
                            try {
                                addPermission(destUuid,
                                        userUuid,
                                        permission);
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
