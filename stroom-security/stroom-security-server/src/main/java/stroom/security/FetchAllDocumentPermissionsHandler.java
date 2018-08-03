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

import stroom.entity.shared.EntityServiceException;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.DocumentPermissions;
import stroom.security.shared.FetchAllDocumentPermissionsAction;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskHandlerBean;

import javax.inject.Inject;

@TaskHandlerBean(task = FetchAllDocumentPermissionsAction.class)
class FetchAllDocumentPermissionsHandler
        extends AbstractTaskHandler<FetchAllDocumentPermissionsAction, DocumentPermissions> {
    private final DocumentPermissionsCache documentPermissionsCache;
    private final SecurityContext securityContext;
    private final Security security;

    @Inject
    FetchAllDocumentPermissionsHandler(final DocumentPermissionsCache documentPermissionsCache,
                                       final SecurityContext securityContext,
                                       final Security security) {
        this.documentPermissionsCache = documentPermissionsCache;
        this.securityContext = securityContext;
        this.security = security;
    }

    @Override
    public DocumentPermissions exec(final FetchAllDocumentPermissionsAction action) {
        return security.insecureResult(() -> {
            if (securityContext.hasDocumentPermission(action.getDocRef().getType(), action.getDocRef().getUuid(), DocumentPermissionNames.OWNER)) {
                return documentPermissionsCache.get(action.getDocRef());
            }

            throw new EntityServiceException("You do not have sufficient privileges to fetch permissions for this document");
        });
    }
}
