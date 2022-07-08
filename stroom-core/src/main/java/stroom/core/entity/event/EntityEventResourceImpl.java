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

package stroom.core.entity.event;

import stroom.cluster.api.EndpointUrlService;
import stroom.cluster.api.RemoteRestUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.util.entityevent.EntityEvent;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.shared.ResourcePaths;

import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@AutoLogged(OperationType.UNLOGGED)
class EntityEventResourceImpl implements EntityEventResource {

    private final Provider<EndpointUrlService> endpointUrlServiceProvider;
    private final Provider<WebTargetFactory> webTargetFactoryProvider;
    private final Provider<EntityEventHandler> entityEventHandlerProvider;

    @Inject
    EntityEventResourceImpl(final Provider<EndpointUrlService> endpointUrlServiceProvider,
                            final Provider<WebTargetFactory> webTargetFactoryProvider,
                            final Provider<EntityEventHandler> entityEventHandlerProvider) {
        this.endpointUrlServiceProvider = endpointUrlServiceProvider;
        this.webTargetFactoryProvider = webTargetFactoryProvider;
        this.entityEventHandlerProvider = entityEventHandlerProvider;
    }

    @Override
    public Boolean fireEvent(final String nodeName, final EntityEvent entityEvent) {
        // If this is the node that was contacted then just return the latency we have incurred within this method.
        final EndpointUrlService endpointUrlService = endpointUrlServiceProvider.get();
        if (endpointUrlService.shouldExecuteLocally(nodeName)) {
            entityEventHandlerProvider.get().fireLocally(entityEvent);
            return true;
        } else {
            final String url = endpointUrlService.getRemoteEndpointUrl(nodeName)
                    + ResourcePaths.buildAuthenticatedApiPath(
                    EntityEventResource.BASE_PATH,
                    nodeName);

            try {
                final Response response = webTargetFactoryProvider
                        .get()
                        .create(url)
                        .request(MediaType.APPLICATION_JSON)
                        .put(Entity.json(entityEvent));
                if (response.getStatus() != 200) {
                    throw new WebApplicationException(response);
                }
                final Boolean success = response.readEntity(Boolean.class);
                Objects.requireNonNull(success, "Null success");
                return success;
            } catch (Throwable e) {
                throw RemoteRestUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }
    }
}
