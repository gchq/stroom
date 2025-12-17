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

package stroom.pathways.impl;

import stroom.docstore.api.DocumentNotFoundException;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.pathways.shared.AddPathway;
import stroom.pathways.shared.DeletePathway;
import stroom.pathways.shared.FindPathwayCriteria;
import stroom.pathways.shared.PathwayResultPage;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.PathwaysResource;
import stroom.pathways.shared.UpdatePathway;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import java.util.Collections;

@Singleton
public class PathwaysService {

    private final PathwaysStore pathwaysStore;
    private final PathwaysProcessor pathwaysProcessor;
    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<NodeInfo> nodeInfoProvider;
    private final Provider<WebTargetFactory> webTargetFactoryProvider;

    @Inject
    public PathwaysService(final PathwaysProcessor pathwaysProcessor,
                           final PathwaysStore pathwaysStore,
                           final Provider<NodeService> nodeServiceProvider,
                           final Provider<NodeInfo> nodeInfoProvider,
                           final Provider<WebTargetFactory> webTargetFactoryProvider) {
        this.pathwaysProcessor = pathwaysProcessor;
        this.pathwaysStore = pathwaysStore;
        this.nodeServiceProvider = nodeServiceProvider;
        this.nodeInfoProvider = nodeInfoProvider;
        this.webTargetFactoryProvider = webTargetFactoryProvider;
    }

    public PathwayResultPage findPathways(final FindPathwayCriteria criteria) {
        final PathwaysDoc pathwaysDoc = pathwaysStore.readDocument(criteria.getDataSourceRef());
        if (pathwaysDoc == null) {
            throw new DocumentNotFoundException(criteria.getDataSourceRef());
        }

        // Find out which node has the pathways database.
        if (pathwaysDoc.getProcessingNode() == null) {
            return new PathwayResultPage(Collections.emptyList(), PageResponse.empty());
        }

        if (pathwaysDoc.getProcessingNode().equals(nodeInfoProvider.get().getThisNodeName())) {
            return pathwaysProcessor.findPathways(criteria);
        }
        return getRemote(pathwaysDoc.getProcessingNode(), criteria);
    }

    public Boolean addPathway(final AddPathway addPathway) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Boolean updatePathway(final UpdatePathway updatePathway) {
        throw new UnsupportedOperationException("Not implemented");
    }

    public Boolean deletePathway(final DeletePathway deletePathway) {
        throw new UnsupportedOperationException("Not implemented");
    }

    private PathwayResultPage getRemote(final String nodeName,
                                        final FindPathwayCriteria criteria) {
        final String url = NodeCallUtil
                                   .getBaseEndpointUrl(nodeInfoProvider.get(), nodeServiceProvider.get(), nodeName)
                           + ResourcePaths.buildAuthenticatedApiPath(
                PathwaysResource.BASE_PATH, PathwaysResource.FIND_PATHWAYS_SUB_PATH);
        try {
            // A different node to make a rest call to the required node
            final WebTarget webTarget = webTargetFactoryProvider.get().create(url);
            final Response response = webTarget
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(criteria));
            if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                throw new NotFoundException(response);
            } else if (response.getStatus() != Status.OK.getStatusCode()) {
                throw new WebApplicationException(response);
            }

            return response.readEntity(PathwayResultPage.class);
        } catch (final Throwable e) {
            throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
        }
    }
}
