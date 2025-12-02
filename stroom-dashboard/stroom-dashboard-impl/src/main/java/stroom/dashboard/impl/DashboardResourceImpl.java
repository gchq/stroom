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

package stroom.dashboard.impl;

import stroom.dashboard.shared.AskStroomAiRequest;
import stroom.dashboard.shared.AskStroomAiResponse;
import stroom.dashboard.shared.ColumnValues;
import stroom.dashboard.shared.ColumnValuesRequest;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardResource;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.DownloadSearchResultsRequest;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.node.api.NodeService;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourcePaths;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.client.Entity;

@AutoLogged
class DashboardResourceImpl implements DashboardResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DashboardResourceImpl.class);

    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<DashboardService> dashboardServiceProvider;

    @Inject
    DashboardResourceImpl(final Provider<NodeService> nodeServiceProvider,
                          final Provider<DashboardService> dashboardServiceProvider) {
        this.nodeServiceProvider = nodeServiceProvider;
        this.dashboardServiceProvider = dashboardServiceProvider;
    }

    @Override
    public DashboardDoc fetch(final String uuid) {
        return dashboardServiceProvider.get().read(getDocRef(uuid));
    }

    @Override
    public DashboardDoc update(final String uuid, final DashboardDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return dashboardServiceProvider.get().update(doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(DashboardDoc.TYPE)
                .build();
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public ValidateExpressionResult validateExpression(final String expressionString) {
        return dashboardServiceProvider.get().validateExpression(expressionString);
    }

    @Override
    public ResourceGeneration downloadQuery(final DashboardSearchRequest request) {
        return dashboardServiceProvider.get().downloadQuery(request);
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public ResourceGeneration downloadSearchResults(final String nodeName,
                                                    final DownloadSearchResultsRequest request) {
        try {
            // If the client doesn't specify a node then execute locally.
            final String node = dashboardServiceProvider.get().getBestNode(nodeName, request.getSearchRequest());
            if (node == null) {
                return dashboardServiceProvider.get().downloadSearchResults(request);
            }

            return nodeServiceProvider.get()
                    .remoteRestResult(
                            node,
                            ResourceGeneration.class,
                            () -> ResourcePaths.buildAuthenticatedApiPath(
                                    DashboardResource.BASE_PATH,
                                    DashboardResource.DOWNLOAD_SEARCH_RESULTS_PATH_PART,
                                    node),
                            () -> dashboardServiceProvider.get().downloadSearchResults(request),
                            builder -> builder.post(Entity.json(request)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public AskStroomAiResponse askStroomAi(final String nodeName, final AskStroomAiRequest request) {
        try {
            // If the client doesn't specify a node then execute locally.
            final String node = dashboardServiceProvider.get().getBestNode(nodeName, request.getSearchRequest());
            if (node == null) {
                return dashboardServiceProvider.get().askStroomAi(request);
            }

            return nodeServiceProvider.get()
                    .remoteRestResult(
                            node,
                            AskStroomAiResponse.class,
                            () -> ResourcePaths.buildAuthenticatedApiPath(
                                    DashboardResource.BASE_PATH,
                                    DashboardResource.ASK_STROOM_AI_PATH_PART,
                                    node),
                            () -> dashboardServiceProvider.get().askStroomAi(request),
                            builder -> builder.post(Entity.json(request)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public DashboardSearchResponse search(final String nodeName, final DashboardSearchRequest request) {
        try {
            // If the client doesn't specify a node then execute locally.
            final String node = dashboardServiceProvider.get().getBestNode(nodeName, request);
            if (node == null) {
                return dashboardServiceProvider.get().search(request);
            }

            return nodeServiceProvider.get()
                    .remoteRestResult(
                            node,
                            DashboardSearchResponse.class,
                            () -> ResourcePaths.buildAuthenticatedApiPath(
                                    DashboardResource.BASE_PATH,
                                    DashboardResource.SEARCH_PATH_PART,
                                    node),
                            () -> dashboardServiceProvider.get().search(request),
                            builder -> builder.post(Entity.json(request)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public ColumnValues getColumnValues(final String nodeName,
                                        final ColumnValuesRequest request) {
        try {
            // If the client doesn't specify a node then execute locally.
            final String node = dashboardServiceProvider.get().getBestNode(nodeName, request.getSearchRequest());
            if (node == null) {
                return dashboardServiceProvider.get().getColumnValues(request);
            }

            return nodeServiceProvider.get()
                    .remoteRestResult(
                            node,
                            ColumnValues.class,
                            () -> ResourcePaths.buildAuthenticatedApiPath(
                                    DashboardResource.BASE_PATH,
                                    DashboardResource.COLUMN_VALUES_PATH_PART,
                                    node),
                            () -> dashboardServiceProvider.get().getColumnValues(request),
                            builder -> builder.post(Entity.json(request)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }
}
