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

package stroom.core.entity.event;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.util.entityevent.EntityEvent;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.shared.ResourcePaths;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Objects;

@AutoLogged(OperationType.UNLOGGED)
class EntityEventResourceImpl implements EntityEventResource {

    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<NodeInfo> nodeInfoProvider;
    private final Provider<WebTargetFactory> webTargetFactoryProvider;
    private final Provider<EntityEventHandler> entityEventHandlerProvider;

    @Inject
    EntityEventResourceImpl(final Provider<NodeService> nodeServiceProvider,
                            final Provider<NodeInfo> nodeInfoProvider,
                            final Provider<WebTargetFactory> webTargetFactoryProvider,
                            final Provider<EntityEventHandler> entityEventHandlerProvider) {
        this.nodeServiceProvider = nodeServiceProvider;
        this.nodeInfoProvider = nodeInfoProvider;
        this.webTargetFactoryProvider = webTargetFactoryProvider;
        this.entityEventHandlerProvider = entityEventHandlerProvider;
    }

    @Override
    public Boolean fireEvent(final String nodeName, final EntityEvent entityEvent) {
        // If this is the node that was contacted then just return the latency we have incurred within this method.
        if (NodeCallUtil.shouldExecuteLocally(nodeInfoProvider.get(), nodeName)) {
            entityEventHandlerProvider.get().fireLocally(entityEvent);
            return true;
        } else {
            final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfoProvider.get(),
                    nodeServiceProvider.get(),
                    nodeName)
                    + ResourcePaths.buildAuthenticatedApiPath(
                    EntityEventResource.BASE_PATH,
                    nodeName);

            try {
                final Boolean success;
                try (final Response response = webTargetFactoryProvider
                        .get()
                        .create(url)
                        .request(MediaType.APPLICATION_JSON)
                        .put(Entity.json(entityEvent))) {
                    if (response.getStatus() != 200) {
                        throw new WebApplicationException(response);
                    }
                    success = response.readEntity(Boolean.class);
                }
                Objects.requireNonNull(success, "Null success");
                return success;
            } catch (final Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }
    }
}
