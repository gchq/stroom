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

package stroom.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;

import stroom.entity.util.SqlBuilder;
import stroom.entity.StroomEntityManager;
import stroom.entity.shared.SQLNameConstants;
import stroom.query.api.v2.DocRef;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.UserRef;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.PersistenceException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
// @Transactional
class DocumentPermissionServiceImpl implements DocumentPermissionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentPermissionServiceImpl.class);

    private static final String SQL_INSERT_PERMISSION;
    private static final String SQL_DELETE_PERMISSION;
    private static final String SQL_GET_PERMISSION_FOR_DOCUMENT;
    private static final String SQL_GET_PERMISSION_KEYSET_FOR_USER;
    private static final String SQL_DELETE_DOCUMENT_PERMISSIONS;
    private static final String SQL_DELETE_USER_PERMISSIONS;

    static {
        final StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ");
        sql.append(DocumentPermission.TABLE_NAME);
        sql.append(" (");
        sql.append(DocumentPermission.VERSION);
        sql.append(" ,");
        sql.append(DocumentPermission.USER_UUID);
        sql.append(" ,");
        sql.append(DocumentPermission.DOC_TYPE);
        sql.append(" ,");
        sql.append(DocumentPermission.DOC_UUID);
        sql.append(" ,");
        sql.append(DocumentPermission.PERMISSION);
        sql.append(")");
        sql.append(" VALUES (?,?,?,?,?)");
        SQL_INSERT_PERMISSION = sql.toString();
    }

    static {
        final StringBuilder sql = new StringBuilder();
        sql.append("DELETE FROM ");
        sql.append(DocumentPermission.TABLE_NAME);
        sql.append(" WHERE ");
        sql.append(DocumentPermission.USER_UUID);
        sql.append(" = ?");
        sql.append(" AND ");
        sql.append(DocumentPermission.DOC_TYPE);
        sql.append(" = ?");
        sql.append(" AND ");
        sql.append(DocumentPermission.DOC_UUID);
        sql.append(" = ?");
        sql.append(" AND ");
        sql.append(DocumentPermission.PERMISSION);
        sql.append(" = ?");
        SQL_DELETE_PERMISSION = sql.toString();
    }

    static {
        final StringBuilder sql = new StringBuilder();
        sql.append("DELETE FROM ");
        sql.append(DocumentPermission.TABLE_NAME);
        sql.append(" WHERE ");
        sql.append(DocumentPermission.DOC_TYPE);
        sql.append(" = ?");
        sql.append(" AND ");
        sql.append(DocumentPermission.DOC_UUID);
        sql.append(" = ?");
        SQL_DELETE_DOCUMENT_PERMISSIONS = sql.toString();
    }

    static {
        final StringBuilder sql = new StringBuilder();
        sql.append("DELETE FROM ");
        sql.append(DocumentPermission.TABLE_NAME);
        sql.append(" WHERE ");
        sql.append(DocumentPermission.USER_UUID);
        sql.append(" = ?");
        SQL_DELETE_USER_PERMISSIONS = sql.toString();
    }

    static {
        final StringBuilder sql = new StringBuilder();
        sql.append("SELECT");
        sql.append(" user.");
        sql.append(User.UUID);
        sql.append(", user.");
        sql.append(User.NAME);
        sql.append(", user.");
        sql.append(User.GROUP);
        sql.append(", user.");
        sql.append(SQLNameConstants.STATUS);
        sql.append(", doc.");
        sql.append(DocumentPermission.PERMISSION);

        sql.append(" FROM ");
        sql.append(DocumentPermission.TABLE_NAME);
        sql.append(" AS ");
        sql.append("doc");

        sql.append(" JOIN ");
        sql.append(User.TABLE_NAME);
        sql.append(" AS ");
        sql.append("user");
        sql.append(" ON (");
        sql.append("user." + User.UUID + " = doc." + DocumentPermission.USER_UUID);
        sql.append(")");

        sql.append(" WHERE ");
        sql.append(DocumentPermission.DOC_TYPE);
        sql.append(" = ?");
        sql.append(" AND ");
        sql.append(DocumentPermission.DOC_UUID);
        sql.append(" = ?");
        SQL_GET_PERMISSION_FOR_DOCUMENT = sql.toString();
    }

    static {
        final StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        sql.append("doc.");
        sql.append(DocumentPermission.DOC_TYPE);
        sql.append(", doc.");
        sql.append(DocumentPermission.DOC_UUID);
        sql.append(", doc.");
        sql.append(DocumentPermission.PERMISSION);

        sql.append(" FROM ");
        sql.append(DocumentPermission.TABLE_NAME);
        sql.append(" AS ");
        sql.append("doc");

        sql.append(" WHERE");
        sql.append(" doc.");
        sql.append(DocumentPermission.USER_UUID);
        sql.append(" = ?");

        sql.append(" GROUP BY");
        sql.append(" doc.");
        sql.append(DocumentPermission.DOC_TYPE);
        sql.append(", ");
        sql.append(" doc.");
        sql.append(DocumentPermission.DOC_UUID);
        sql.append(", ");
        sql.append(" doc.");
        sql.append(DocumentPermission.PERMISSION);
        SQL_GET_PERMISSION_KEYSET_FOR_USER = sql.toString();
    }

    private final StroomEntityManager entityManager;
    private final DocumentTypePermissions documentTypePermissions;

    @Inject
    DocumentPermissionServiceImpl(final StroomEntityManager entityManager,
                                  final DocumentTypePermissions documentTypePermissions) {
        this.entityManager = entityManager;
        this.documentTypePermissions = documentTypePermissions;
    }

    @Override
    public DocumentPermissions getPermissionsForDocument(final DocRef document) {
        final Map<UserRef, Set<String>> userPermissions = new HashMap<>();

        try {
            final SqlBuilder sqlBuilder = new SqlBuilder(SQL_GET_PERMISSION_FOR_DOCUMENT, document.getType(), document.getUuid());
            final List list = entityManager.executeNativeQueryResultList(sqlBuilder);
            list.forEach(o -> {
                final Object[] arr = (Object[]) o;
                final String uuid = (String) arr[0];
                final String name = (String) arr[1];
                final boolean group = (Boolean) arr[2];
                final byte status = (Byte) arr[3];
                final String permission = (String) arr[4];

                final UserRef userRef = new UserRef(User.ENTITY_TYPE, uuid, name, group,
                        UserStatus.ENABLED.getPrimitiveValue() == status);

                userPermissions.computeIfAbsent(userRef, k -> new HashSet<>()).add(permission);
            });

        } catch (final RuntimeException e) {
            LOGGER.error("getPermissionsForDocument()", e);
            throw e;
        }

        final String[] permissions = documentTypePermissions.getPermissions(document.getType());
        return new DocumentPermissions(document, permissions, userPermissions);
    }

    @Override
    public void addPermission(final UserRef userRef, final DocRef document, final String permission) {
        try {
            final SqlBuilder sqlBuilder = new SqlBuilder(SQL_INSERT_PERMISSION, 1, userRef.getUuid(), document.getType(), document.getUuid(), permission);
            entityManager.executeNativeUpdate(sqlBuilder);
        } catch (final PersistenceException e) {
            // Expected exception.
            LOGGER.debug("addPermission()", e);
//            throw e;
        } catch (final RuntimeException e) {
            LOGGER.error("addPermission()", e);
            throw e;
        }
    }

    @Override
    public void removePermission(final UserRef userRef, final DocRef document, final String permission) {
        try {
            final SqlBuilder sqlBuilder = new SqlBuilder(SQL_DELETE_PERMISSION, userRef.getUuid(), document.getType(), document.getUuid(), permission);
            entityManager.executeNativeUpdate(sqlBuilder);
        } catch (final RuntimeException e) {
            LOGGER.error("removePermission()", e);
            throw e;
        }
    }

    @Override
    public void clearDocumentPermissions(final DocRef document) {
        try {
            final SqlBuilder sqlBuilder = new SqlBuilder(SQL_DELETE_DOCUMENT_PERMISSIONS, document.getType(), document.getUuid());
            entityManager.executeNativeUpdate(sqlBuilder);
        } catch (final RuntimeException e) {
            LOGGER.error("clearDocumentPermissions()", e);
            throw e;
        }
    }

    @Override
    public void clearUserPermissions(final UserRef userRef) {
        try {
            final SqlBuilder sqlBuilder = new SqlBuilder(SQL_DELETE_USER_PERMISSIONS, userRef.getUuid());
            entityManager.executeNativeUpdate(sqlBuilder);
        } catch (final RuntimeException e) {
            LOGGER.error("clearUserPermissions()", e);
            throw e;
        }
    }
}
