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

package stroom.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.StroomEntityManager;
import stroom.entity.util.HqlBuilder;
import stroom.entity.util.SqlBuilder;
import stroom.jobsystem.ClusterLockService;
import stroom.security.shared.ApplicationPermissionNames;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.UserAppPermissions;
import stroom.security.shared.UserRef;
import stroom.util.lifecycle.StroomStartup;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.PersistenceException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
// @Transactional
class UserAppPermissionServiceImpl implements UserAppPermissionService {
    private static final String LOCK_NAME = "UserAppPermissionService";

    private static final String SQL_INSERT_USER_PERMISSIONS;
    private static final String SQL_DELETE_USER_PERMISSIONS;
    private static final String SQL_GET_PERMISSION_KEYSET_FOR_USER;
    private static final Logger LOGGER = LoggerFactory.getLogger(UserAppPermissionServiceImpl.class);

    static {
        SQL_INSERT_USER_PERMISSIONS = ""
                + "INSERT INTO "
                + AppPermission.TABLE_NAME
                + " ("
                + AppPermission.VERSION
                + " ,"
                + AppPermission.USER_UUID
                + " ,"
                + AppPermission.PERMISSION
                + ")"
                + " VALUES (?,?,?)";
    }

    static {
        SQL_DELETE_USER_PERMISSIONS = ""
                + "DELETE FROM "
                + AppPermission.TABLE_NAME
                + " WHERE "
                + AppPermission.USER_UUID
                + " = ?"
                + " AND "
                + AppPermission.PERMISSION
                + " = ?";
    }

    static {
        SQL_GET_PERMISSION_KEYSET_FOR_USER = ""
                + "SELECT"
                + " p."
                + Permission.NAME

                + " FROM "
                + Permission.TABLE_NAME
                + " AS "
                + "p"

                + " JOIN "
                + AppPermission.TABLE_NAME
                + " AS "
                + "ap"
                + " ON ("
                + "ap." + AppPermission.PERMISSION + " = p." + Permission.ID
                + ")"

                + " WHERE"
                + " ap."
                + AppPermission.USER_UUID
                + " = ?"

                + " GROUP BY"
                + " p."
                + Permission.NAME;
    }

    private final ClusterLockService clusterLockService;
    private final AtomicBoolean doneInit = new AtomicBoolean();
    private final StroomEntityManager entityManager;

    private final Set<String> allPermissions = new HashSet<>();
    private final Map<String, Long> permissionIdMap = new HashMap<>();

    @Inject
    UserAppPermissionServiceImpl(final StroomEntityManager entityManager,
                                 final ClusterLockService clusterLockService) {
        this.entityManager = entityManager;
        this.clusterLockService = clusterLockService;
    }

    /**
     * Static startup code .... can be called by the UI if you wana log in
     * before we have started
     */
    @Override
    @StroomStartup
    public synchronized void init() {
        if (doneInit.compareAndSet(false, true)) {
            // Get a set of permissions that Stroom requires according to code
            // annotations present in the current version.
            final Set<String> requiredPermissionSet = getRequiredPermissionSet();

            // Add a special permission for administrators.
            requiredPermissionSet.add(PermissionNames.ADMINISTRATOR);

            // Add missing permissions to the DB and remove existing permissions
            // that are no longer required.
            createAll(requiredPermissionSet);
        }
    }

    @Override
    public void createAll(final Set<String> requiredSet) {
        clusterLockService.lock(LOCK_NAME);

        final HqlBuilder sql = new HqlBuilder();
        sql.append("SELECT p FROM ");
        sql.append(Permission.class.getName());
        sql.append(" as p");

        @SuppressWarnings("unchecked") final List<Permission> existingPermissionList = entityManager.executeQueryResultList(sql, null);
        final Map<String, Permission> existingPermissionMap = new HashMap<>();
        for (final Permission permission : existingPermissionList) {
            existingPermissionMap.put(permission.getName(), permission);
        }

        // Add all the missing ones
        for (final String requiredPermission : requiredSet) {
            allPermissions.add(requiredPermission);

            final Permission existingPermission = existingPermissionMap.remove(requiredPermission);
            if (existingPermission == null) {
                LOGGER.info("createAll() - Persisting {}", requiredPermission);
                Permission newPermission = Permission.create(
                        requiredPermission);
                newPermission = entityManager.saveEntity(newPermission);
                permissionIdMap.put(requiredPermission, newPermission.getId());
            } else {
                permissionIdMap.put(requiredPermission, existingPermission.getId());
            }
        }

        // Delete the remaining existing ones that are no longer needed.
        for (final Permission existingPermission : existingPermissionMap.values()) {
            LOGGER.info("createAll() - Removing {}", existingPermission);

            // Delete old application permissions.
            deleteOldAppPerms(existingPermission);

            // Delete old permissions.
            deleteOldPerms(existingPermission);
        }
    }

    private void deleteOldAppPerms(final Permission existingPermission) {
        // Delete old application permissions.
        final SqlBuilder sql = new SqlBuilder();
        sql.append("DELETE FROM ");
        sql.append(AppPermission.TABLE_NAME);
        sql.append(" WHERE ");
        sql.append(AppPermission.PERMISSION);
        sql.append(" = ");
        sql.arg(existingPermission.getId());
        entityManager.executeNativeUpdate(sql);
    }

    private void deleteOldPerms(final Permission existingPermission) {
        // Delete old permissions.
        final SqlBuilder sql = new SqlBuilder();
        sql.append("DELETE FROM ");
        sql.append(Permission.TABLE_NAME);
        sql.append(" WHERE ");
        sql.append(Permission.ID);
        sql.append(" = ");
        sql.arg(existingPermission.getId());
        entityManager.executeNativeUpdate(sql);
    }

    @Override
    public UserAppPermissions getPermissionsForUser(final UserRef userRef) {
        final Set<String> userPermissions = getPermissionSetForUser(userRef);
        return new UserAppPermissions(userRef, allPermissions, userPermissions);
    }

    @SuppressWarnings("unchecked")
    private Set<String> getPermissionSetForUser(final UserRef userRef) {
        try {
            final SqlBuilder sqlBuilder = new SqlBuilder(SQL_GET_PERMISSION_KEYSET_FOR_USER, userRef.getUuid());
            return new HashSet<>(entityManager.executeNativeQueryResultList(sqlBuilder));

        } catch (final RuntimeException e) {
            LOGGER.error("getPermissionKeySetForUser()", e);
            throw e;
        }
    }

    @Override
    public void addPermission(final UserRef userRef, final String permission) {
        final Long permissionId = permissionIdMap.get(permission);
        if (permissionId == null) {
            LOGGER.error("Unknown permission: " + permission);
        } else {
            try {
                final SqlBuilder sqlBuilder = new SqlBuilder(SQL_INSERT_USER_PERMISSIONS, 1, userRef.getUuid(), permissionId);
                entityManager.executeNativeUpdate(sqlBuilder);
            } catch (final PersistenceException e) {
                // Expected exception.
                LOGGER.debug("addPermission()", e);
                throw e;
            } catch (final RuntimeException e) {
                LOGGER.error("addPermission()", e);
                throw e;
            }
        }
    }

    @Override
    public void removePermission(final UserRef userRef, final String permission) {
        final Long permissionId = permissionIdMap.get(permission);
        if (permissionId == null) {
            LOGGER.error("Unknown permission: " + permission);
        } else {
            try {
                final SqlBuilder sqlBuilder = new SqlBuilder(SQL_DELETE_USER_PERMISSIONS, userRef.getUuid(), permissionId);
                entityManager.executeNativeUpdate(sqlBuilder);
            } catch (final RuntimeException e) {
                LOGGER.error("removePermission()", e);
                throw e;
            }
        }
    }

    Set<String> getRequiredPermissionSet() {
        return new HashSet<>(Arrays.asList(ApplicationPermissionNames.PERMISSIONS));
    }
}
