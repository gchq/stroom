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

import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.shared.ResourcePaths;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

class PermissionChangeResourceImpl implements PermissionChangeResource {
    private final NodeService nodeService;
    private final NodeInfo nodeInfo;
    private final PermissionChangeEventHandlers permissionChangeEventHandlers;
    private final WebTargetFactory webTargetFactory;

    @Inject
    PermissionChangeResourceImpl(final NodeService nodeService,
                                 final NodeInfo nodeInfo,
                                 final PermissionChangeEventHandlers permissionChangeEventHandlers,
                                 final WebTargetFactory webTargetFactory) {
        this.nodeService = nodeService;
        this.nodeInfo = nodeInfo;
        this.permissionChangeEventHandlers = permissionChangeEventHandlers;
        this.webTargetFactory = webTargetFactory;
    }

    @Override
    public Boolean fireChange(final String nodeName, final PermissionChangeRequest request) {
        Boolean result;

        // If this is the node that was contacted then just return our local info.
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
            permissionChangeEventHandlers.fireLocally(request.getEvent());
            result = true;

        } else {
            String url = NodeCallUtil.getBaseEndpointUrl(nodeService, nodeName)
                    + ResourcePaths.buildAuthenticatedApiPath(
                    PermissionChangeResource.BASE_PATH,
                    PermissionChangeResource.FIRE_CHANGE_PATH_PART,
                    nodeName);
            final Response response = webTargetFactory
                    .create(url)
                    .request(MediaType.APPLICATION_JSON)
                    .get();
            if (response.getStatus() != 200) {
                throw new WebApplicationException(response);
            }
            result = response.readEntity(Boolean.class);
        }

        return result;
    }
}