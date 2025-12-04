/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.storedquery.impl;

import stroom.dashboard.shared.FindStoredQueryCriteria;
import stroom.dashboard.shared.StoredQuery;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.storedquery.api.StoredQueryService;
import stroom.util.AuditUtil;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;
import java.util.UUID;

public class StoredQueryServiceImpl implements StoredQueryService {

    private final SecurityContext securityContext;
    private final StoredQueryDao dao;

    @Inject
    public StoredQueryServiceImpl(final SecurityContext securityContext,
                                  final StoredQueryDao dao) {
        this.securityContext = securityContext;
        this.dao = dao;
    }

    @Override
    public StoredQuery create(@NotNull final StoredQuery storedQuery) {
        Objects.requireNonNull(storedQuery);
        final UserRef ownerFromReq = storedQuery.getOwner();
        if (ownerFromReq != null && !securityContext.isCurrentUser(ownerFromReq)) {
            throw new PermissionException(securityContext.getUserRef(),
                    LogUtil.message("Attempt to create a stored query for a user ({}) that is " +
                                    "different to the logged in user.", ownerFromReq));
        }

        AuditUtil.stamp(securityContext, storedQuery);
        storedQuery.setOwner(securityContext.getUserRef());
        storedQuery.setUuid(UUID.randomUUID().toString());
        return securityContext.secureResult(() -> dao.create(storedQuery));
    }

    @Override
    public StoredQuery update(@NotNull final StoredQuery storedQuery) {
        Objects.requireNonNull(storedQuery);

        return securityContext.secureResult(() -> {
            final StoredQuery existingStoredQuery = dao.fetch(storedQuery.getId())
                    .orElseThrow(() -> new RuntimeException(LogUtil.message(
                            "Stored query with id {} doesn't exist", storedQuery.getId())));
            final UserRef existingOwner = existingStoredQuery.getOwner();

            if (storedQuery.getOwner() != null && !Objects.equals(storedQuery.getOwner(), existingOwner)) {
                throw new RuntimeException(LogUtil.message(
                        "You cannot change the owner of a stored query. existing: {}, new: {}",
                        existingOwner, storedQuery.getOwner()));
            }

            if (securityContext.isAdmin()
                || securityContext.isCurrentUser(existingOwner)) {

                AuditUtil.stamp(securityContext, storedQuery);
                if (storedQuery.getOwner() == null) {
                    storedQuery.setOwner(securityContext.getUserRef());
                }
                return dao.update(storedQuery);
            } else {
                throw new PermissionException(securityContext.getUserRef(),
                        "You must be the owner of a stored query to update it, or be administrator.");
            }
        });
    }

    @Override
    public boolean delete(final int id) {
        return securityContext.secureResult(() -> {
            final StoredQuery storedQuery = dao.fetch(id)
                    .orElseThrow(() -> new RuntimeException(LogUtil.message(
                            "Stored query with id {} doesn't exist", id)));

            final UserRef ownerUserRef = storedQuery.getOwner();

            if (securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)
                || securityContext.isCurrentUser(ownerUserRef)) {

                return dao.delete(id);
            } else {
                throw new PermissionException(securityContext.getUserRef(),
                        "You must be the owner of a stored query to delete it, or hold "
                        + AppPermission.MANAGE_USERS_PERMISSION.getDisplayValue() + " permission");
            }
        });
    }

    @Override
    public int deleteByOwner(final UserRef ownerUserRef) {
        return securityContext.secureResult(() -> {
            if (securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)
                || securityContext.isCurrentUser(ownerUserRef)) {

                return dao.delete(ownerUserRef);
            } else {
                throw new PermissionException(securityContext.getUserRef(),
                        "You must be the owner of the stored queries to delete them, or hold "
                        + AppPermission.MANAGE_USERS_PERMISSION.getDisplayValue() + " permission");
            }
        });
    }

    @Override
    public StoredQuery fetch(final int id) {
        return securityContext.secureResult(() -> {
            final StoredQuery storedQuery = dao.fetch(id)
                    .orElse(null);

            if (storedQuery != null) {
                if (!securityContext.isCurrentUser(storedQuery.getOwner())) {
                    throw new PermissionException(securityContext.getUserRef(),
                            "This retrieved stored query belongs to another user");
                }
            }
            return storedQuery;
        });
    }

    @Override
    public ResultPage<StoredQuery> find(final FindStoredQueryCriteria criteria) {
        criteria.setOwner(securityContext.getUserRef());
        return securityContext.secureResult(() -> dao.find(criteria));
    }
}
