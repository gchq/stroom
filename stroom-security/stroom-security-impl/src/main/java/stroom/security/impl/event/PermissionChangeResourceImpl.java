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
 */

package stroom.security.impl.event;

import stroom.node.api.NodeService;
import stroom.util.shared.ResourcePaths;

import javax.inject.Inject;
import javax.ws.rs.client.Entity;

class PermissionChangeResourceImpl implements PermissionChangeResource {

    private final NodeService nodeService;
    private final PermissionChangeEventHandlers permissionChangeEventHandlers;

    @Inject
    PermissionChangeResourceImpl(final NodeService nodeService,
                                 final PermissionChangeEventHandlers permissionChangeEventHandlers) {
        this.nodeService = nodeService;
        this.permissionChangeEventHandlers = permissionChangeEventHandlers;
    }

    @Override
    public Boolean fireChange(final String nodeName, final PermissionChangeRequest request) {
        final Boolean result = nodeService.remoteRestResult(
                nodeName,
                Boolean.class,
                () -> ResourcePaths.buildAuthenticatedApiPath(
                        PermissionChangeResource.BASE_PATH,
                        PermissionChangeResource.FIRE_CHANGE_PATH_PART,
                        nodeName),
                () -> {
                    permissionChangeEventHandlers.fireLocally(request.getEvent());
                    return true;
                },
                builder ->
                        builder.post(Entity.json(request)));

        return result;
    }
}
