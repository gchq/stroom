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
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.UserIdentity;
import stroom.security.shared.DocumentPermissionJooq;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.User;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Singleton
public class DocumentPermissionServiceImpl implements DocumentPermissionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentPermissionServiceImpl.class);

    private final DocumentPermissionDao documentPermissionDao;
    private final UserDao userDao;
    private final DocumentTypePermissions documentTypePermissions;
    private final SecurityContextImpl securityContext;

    @Inject
    DocumentPermissionServiceImpl(final DocumentPermissionDao documentPermissionDao,
                                  final UserDao userDao,
                                  final DocumentTypePermissions documentTypePermissions,
                                  final SecurityContextImpl securityContext) {
        this.documentPermissionDao = documentPermissionDao;
        this.userDao = userDao;
        this.documentTypePermissions = documentTypePermissions;
        this.securityContext = securityContext;
    }

    public Set<String> getPermissionsForDocumentForUser(final String docRefUuid,
                                                        final String userUuid) {
        return documentPermissionDao.getPermissionsForDocumentForUser(docRefUuid, userUuid);
    }

    public DocumentPermissions getPermissionsForDocument(final String docRefUuid) {
        final Map<String, Set<String>> userPermissions = new HashMap<>();

        try {
            final DocumentPermissionJooq documentPermission = documentPermissionDao.getPermissionsForDocument(docRefUuid);

            documentPermission.getPermissions().forEach((userUuid, permissions) -> {
                final User user = userDao.getByUuid(userUuid);
                userPermissions.put(user.getUuid(), permissions);
            });


        } catch (final RuntimeException e) {
            LOGGER.error("getPermissionsForDocument()", e);
            throw e;
        }

        return new DocumentPermissions(docRefUuid, userPermissions);
    }

    public UserDocumentPermissions getPermissionsForUser(final String userUuid) {
        return documentPermissionDao.getPermissionsForUser(userUuid);
    }

    public void addPermission(final String docRefUuid,
                              final String userUuid,
                              final String permission) {
        documentPermissionDao.addPermission(docRefUuid, userUuid, permission);
    }

    public void removePermission(final String docRefUuid,
                                 final String userUuid,
                                 final String permission) {
        documentPermissionDao.removePermission(docRefUuid, userUuid, permission);
    }

    void clearDocumentPermissionsForUser(final String docRefUuid,
                                         final String userUuid) {
        documentPermissionDao.clearDocumentPermissionsForUser(docRefUuid, userUuid);

    }

    void clearDocumentPermissions(final String docRefUuid) {
        documentPermissionDao.clearDocumentPermissions(docRefUuid);
    }

    @Override
    public void clearDocumentPermissions(final String documentType, final String documentUuid) {
        // Get the current user.
        final UserIdentity userIdentity = securityContext.getUserIdentity();

        // If no user is present then don't clear permissions.
        if (userIdentity != null) {
            if (securityContext.hasDocumentPermission(documentType, documentUuid, DocumentPermissionNames.OWNER)) {
                clearDocumentPermissions(documentUuid);
            }
        }
    }

    @Override
    public void addDocumentPermissions(final String sourceType, final String sourceUuid, final String documentType, final String documentUuid, final boolean owner) {
        // Get the current user.
        final UserIdentity userIdentity = securityContext.getUserIdentity();

        // If no user is present then don't create permissions.
        if (userIdentity != null) {
            final User user = securityContext.getUser(userIdentity);
            if (owner || securityContext.hasDocumentPermission(documentType, documentUuid, DocumentPermissionNames.OWNER)) {
                final DocRef docRef = new DocRef(documentType, documentUuid);

                if (owner) {
                    // Make the current user the owner of the new document.
                    try {
                        addPermission(docRef.getUuid(),
                                user.getUuid(),
                                DocumentPermissionNames.OWNER);
                    } catch (final RuntimeException e) {
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

            final DocumentPermissions documentPermissions = getPermissionsForDocument(sourceDocRef.getUuid());
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
                                    addPermission(destDocRef.getUuid(),
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
