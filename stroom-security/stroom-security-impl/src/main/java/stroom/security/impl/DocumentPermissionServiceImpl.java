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
import stroom.security.dao.DocumentPermissionDao;
import stroom.security.dao.UserDao;
import stroom.security.service.DocumentPermissionService;
import stroom.security.shared.DocumentPermissionJooq;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.User;
import stroom.security.shared.UserRef;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Singleton
class DocumentPermissionServiceImpl implements DocumentPermissionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentPermissionServiceImpl.class);

    private final DocumentPermissionDao documentPermissionDao;
    private final UserDao userDao;
    private final DocumentTypePermissions documentTypePermissions;

    @Inject
    public DocumentPermissionServiceImpl(final DocumentPermissionDao documentPermissionDao,
                                         final UserDao userDao,
                                         final DocumentTypePermissions documentTypePermissions) {
        this.documentPermissionDao = documentPermissionDao;
        this.userDao = userDao;
        this.documentTypePermissions = documentTypePermissions;
    }

    @Override
    public Set<String> getPermissionsForDocumentForUser(final String docRefUuid,
                                                        final String userUuid) {
        return documentPermissionDao.getPermissionsForDocumentForUser(docRefUuid, userUuid);
    }

    @Override
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

    @Override
    public void addPermission(final String docRefUuid,
                              final String userUuid,
                              final String permission) {
        documentPermissionDao.addPermission(docRefUuid, userUuid, permission);
    }

    @Override
    public void removePermission(final String docRefUuid,
                                 final String userUuid,
                                 final String permission) {
        documentPermissionDao.removePermission(docRefUuid, userUuid, permission);
    }

    @Override
    public void clearDocumentPermissionsForUser(final String docRefUuid,
                                                final String userUuid) {
        documentPermissionDao.clearDocumentPermissionsForUser(docRefUuid, userUuid);

    }

    @Override
    public void clearDocumentPermissions(final String docRefUuid) {
        documentPermissionDao.clearDocumentPermissions(docRefUuid);
    }
}
