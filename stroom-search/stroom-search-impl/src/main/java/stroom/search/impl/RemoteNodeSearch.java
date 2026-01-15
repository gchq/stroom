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

package stroom.search.impl;

import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.query.common.v2.ResultStore;
import stroom.task.api.TaskContext;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResourcePaths;

import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import java.io.IOException;
import java.io.InputStream;

public class RemoteNodeSearch implements NodeSearch {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RemoteNodeSearch.class);

    private final NodeService nodeService;
    private final NodeInfo nodeInfo;
    private final WebTargetFactory webTargetFactory;

    @Inject
    public RemoteNodeSearch(final NodeService nodeService,
                            final NodeInfo nodeInfo,
                            final WebTargetFactory webTargetFactory) {
        this.nodeService = nodeService;
        this.nodeInfo = nodeInfo;
        this.webTargetFactory = webTargetFactory;
    }

    @Override
    public void searchNode(final String sourceNode,
                           final String targetNode,
                           final FederatedSearchTask task,
                           final NodeSearchTask nodeSearchTask,
                           final TaskContext parentContext) {
        LOGGER.debug(() -> task.getSearchName() + " - start searching node: " + targetNode);
        parentContext.info(() -> task.getSearchName() + " - start searching node: " + targetNode);
        final String queryKey = task.getKey().getUuid();
        final ResultStore resultCollector = task.getResultStore();

        // Start remote cluster search execution.
        LOGGER.debug(() -> "Dispatching node search task to node: " + targetNode);
        try {
            final boolean success = startRemoteSearch(targetNode, nodeSearchTask);
            if (!success) {
                LOGGER.debug(() -> "Failed to start remote search on node: " + targetNode);
                final SearchException searchException = new SearchException(
                        "Failed to start remote search on node: " + targetNode);
                resultCollector.onFailure(targetNode, searchException);
                throw searchException;
            }
        } catch (final Throwable e) {
            LOGGER.debug(e::getMessage, e);
            final SearchException searchException = new SearchException(e.getMessage(), e);
            resultCollector.onFailure(targetNode, searchException);
            throw searchException;
        }

        try {
            LOGGER.debug(() -> task.getSearchName() + " - searching node: " + targetNode + "...");
            parentContext.info(() -> task.getSearchName() + " - searching node: " + targetNode + "...");

            // Poll for results until completion.
            boolean complete = false;
            while (!Thread.currentThread().isInterrupted() && !complete) {
                complete = pollRemoteSearch(targetNode, queryKey, resultCollector);
            }

        } catch (final Throwable e) {
            LOGGER.debug(e::getMessage, e);
            resultCollector.onFailure(sourceNode, e);

        } finally {
            LOGGER.debug(() -> task.getSearchName() + " - finished searching node: " + targetNode);
            parentContext.info(() -> task.getSearchName() + " - finished searching node: " + targetNode);

            // Destroy search results.
            try {
                final boolean success = destroyRemoteSearch(targetNode, queryKey);
                if (!success) {
                    LOGGER.debug(() -> "Failed to destroy remote search on node: " + targetNode);
                    resultCollector.onFailure(targetNode, new SearchException("Failed to destroy remote search"));
                }
            } catch (final Throwable e) {
                resultCollector.onFailure(targetNode, e);
            }
        }
    }

    private Boolean startRemoteSearch(final String nodeName, final NodeSearchTask nodeSearchTask) {
        final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo, nodeService, nodeName)
                + ResourcePaths.buildAuthenticatedApiPath(
                RemoteSearchResource.BASE_PATH,
                RemoteSearchResource.START_PATH_PART);

        try {
            try (final Response response = webTargetFactory
                    .create(url)
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(nodeSearchTask))) {
                if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                    throw new NotFoundException(response);
                } else if (response.getStatus() != Status.OK.getStatusCode()) {
                    throw new WebApplicationException(response);
                }

                return response.readEntity(Boolean.class);
            }
        } catch (final Throwable e) {
            LOGGER.debug(e::getMessage, e);
            throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
        }
    }

    private Boolean pollRemoteSearch(final String nodeName,
                                     final String queryKey,
                                     final ResultStore resultCollector) throws IOException {
        final boolean complete;
        final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo, nodeService, nodeName)
                + ResourcePaths.buildAuthenticatedApiPath(
                RemoteSearchResource.BASE_PATH,
                RemoteSearchResource.POLL_PATH_PART);

        WebTarget webTarget = webTargetFactory.create(url);
        webTarget = UriBuilderUtil.addParam(webTarget, "queryKey", queryKey);

        try (final InputStream inputStream = webTarget
                .request(MediaType.APPLICATION_OCTET_STREAM)
                .get(InputStream.class)) {

            LOGGER.debug(() -> "Receive result for node: " + nodeName);
            complete = resultCollector.onSuccess(nodeName, inputStream);
        }
        return complete;
    }

    private Boolean destroyRemoteSearch(final String nodeName,
                                        final String queryKey) {
        final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo, nodeService, nodeName)
                + ResourcePaths.buildAuthenticatedApiPath(
                RemoteSearchResource.BASE_PATH,
                RemoteSearchResource.DESTROY_PATH_PART);

        try {
            WebTarget webTarget = webTargetFactory.create(url);
            webTarget = UriBuilderUtil.addParam(webTarget, "queryKey", queryKey);

            try (final Response response = webTarget
                    .request(MediaType.APPLICATION_JSON)
                    .get()) {
                if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                    throw new NotFoundException(response);
                } else if (response.getStatus() != Status.OK.getStatusCode()) {
                    throw new WebApplicationException(response);
                }

                return response.readEntity(Boolean.class);
            }
        } catch (final Throwable e) {
            LOGGER.debug(e::getMessage, e);
            throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
        }
    }
}
