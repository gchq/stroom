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
    public Set<String> getPermissionsForDocumentForUser(final DocRef document,
                                                        final String userUuid) {
        return documentPermissionDao.getPermissionsForDocumentForUser(document, userUuid);
    }

    @Override
    public DocumentPermissions getPermissionsForDocument(final DocRef document) {
        final Map<UserRef, Set<String>> userPermissions = new HashMap<>();

        try {
            final DocumentPermissionJooq documentPermission = documentPermissionDao.getPermissionsForDocument(document);

            documentPermission.getPermissions().forEach((userUuid, permissions) -> {
                final User user = userDao.getByUuid(userUuid);
                final UserRef userRef = UserRefFactory.create(user);
                userPermissions.put(userRef, permissions);
            });


        } catch (final RuntimeException e) {
            LOGGER.error("getPermissionsForDocument()", e);
            throw e;
        }

        final String[] permissions = documentTypePermissions.getPermissions(document.getType());
        return new DocumentPermissions(document, permissions, userPermissions);
    }

    @Override
    public void addPermission(final String userUuid, final DocRef document, final String permission) {
        documentPermissionDao.addPermission(userUuid, document, permission);
    }

    @Override
    public void removePermission(final String userUuid, final DocRef document, final String permission) {
        documentPermissionDao.removePermission(userUuid, document, permission);
    }

    @Override
    public void clearDocumentPermissions(final DocRef document) {
        documentPermissionDao.clearDocumentPermissions(document);
    }
}
