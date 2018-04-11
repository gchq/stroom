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

import stroom.security.shared.CheckDocumentPermissionAction;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.util.shared.SharedBoolean;

import javax.inject.Inject;

@TaskHandlerBean(task = CheckDocumentPermissionAction.class)
class CheckDocumentPermissionHandler
        extends AbstractTaskHandler<CheckDocumentPermissionAction, SharedBoolean> {
    private final SecurityContext securityContext;
    private final Security security;

    @Inject
    CheckDocumentPermissionHandler(final SecurityContext securityContext,
                                   final Security security) {
        this.securityContext = securityContext;
        this.security = security;
    }

    @Override
    public SharedBoolean exec(final CheckDocumentPermissionAction action) {
        return security.insecureResult(() -> SharedBoolean.wrap(securityContext.hasDocumentPermission(action.getDocumentType(), action.getDocumentId(), action.getPermission())));
    }
}
