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

package stroom.index.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeResource;
import stroom.index.shared.ValidationResult;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.rest.RestUtil;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

@AutoLogged
class IndexVolumeResourceImpl implements IndexVolumeResource {

    private final Provider<IndexVolumeService> indexVolumeServiceProvider;
    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<NodeInfo> nodeInfoProvider;
    private final Provider<WebTargetFactory> webTargetFactoryProvider;

    @Inject
    public IndexVolumeResourceImpl(final Provider<IndexVolumeService> indexVolumeServiceProvider,
                                   final Provider<NodeService> nodeServiceProvider,
                                   final Provider<NodeInfo> nodeInfoProvider,
                                   final Provider<WebTargetFactory> webTargetFactoryProvider) {
        this.indexVolumeServiceProvider = indexVolumeServiceProvider;
        this.nodeServiceProvider = nodeServiceProvider;
        this.nodeInfoProvider = nodeInfoProvider;
        this.webTargetFactoryProvider = webTargetFactoryProvider;
    }

    @Override
    public ResultPage<IndexVolume> find(final ExpressionCriteria criteria) {
        return indexVolumeServiceProvider.get().find(criteria);
    }

    @Override
    public ValidationResult validate(final IndexVolume indexVolume) {
        if (indexVolume.getNodeName() == null || indexVolume.getNodeName().isEmpty()) {
            return indexVolumeServiceProvider.get().validate(indexVolume);
        } else {
            return nodeServiceProvider.get().remoteRestResult(
                    indexVolume.getNodeName(),
                    ValidationResult.class,
                    () -> ResourcePaths.buildAuthenticatedApiPath(
                            IndexVolumeResource.BASE_PATH,
                            IndexVolumeResource.VALIDATE_SUB_PATH),
                    () ->
                            indexVolumeServiceProvider.get().validate(indexVolume),
                    builder ->
                            builder.post(Entity.json(indexVolume)));
        }
    }

    @Override
    public IndexVolume create(final IndexVolume indexVolume) {
        return indexVolumeServiceProvider.get().create(indexVolume);
    }

    @Override
    public IndexVolume fetch(final Integer id) {
        return indexVolumeServiceProvider.get().read(id);
    }

    @Override
    public IndexVolume update(final Integer id, final IndexVolume indexVolume) {
        return indexVolumeServiceProvider.get().update(indexVolume);
    }

    @Override
    public Boolean delete(final Integer id) {
        return indexVolumeServiceProvider.get().delete(id);
    }

    @Override
    public Boolean rescan(final String nodeName) {
        RestUtil.requireNonNull(nodeName, "nodeName not supplied");

        // If this is the node that was contacted then just resolve it locally
        if (NodeCallUtil.shouldExecuteLocally(nodeInfoProvider.get(), nodeName)) {
            indexVolumeServiceProvider.get().rescan();
        } else {
            final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfoProvider.get(),
                    nodeServiceProvider.get(), nodeName)
                    + ResourcePaths.buildAuthenticatedApiPath(
                    IndexVolumeResource.BASE_PATH,
                    IndexVolumeResource.RESCAN_SUB_PATH);
            try {
                // A different node to make a rest call to the required node
                WebTarget webTarget = webTargetFactoryProvider.get().create(url);
                webTarget = UriBuilderUtil.addParam(webTarget, "nodeName", nodeName);
                final Response response = webTarget
                        .request(MediaType.APPLICATION_JSON)
                        .get();
                if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                    throw new NotFoundException(response);
                } else if (response.getStatus() != Status.OK.getStatusCode()) {
                    throw new WebApplicationException(response);
                }

                return response.readEntity(Boolean.class);
            } catch (final Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }

        return true;
    }
}
