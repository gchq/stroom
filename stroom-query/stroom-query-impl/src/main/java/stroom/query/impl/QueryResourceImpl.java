/*
 * Copyright 2022 Crown Copyright
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

package stroom.query.impl;

import stroom.dashboard.shared.DashboardSearchResponse;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.node.api.NodeService;
import stroom.query.shared.DownloadQueryResultsRequest;
import stroom.query.shared.QueryDoc;
import stroom.query.shared.QueryResource;
import stroom.query.shared.QuerySearchRequest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourcePaths;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.client.Entity;

@AutoLogged
class QueryResourceImpl implements QueryResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(QueryResourceImpl.class);

    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<QueryService> queryServiceProvider;

    @Inject
    QueryResourceImpl(final Provider<NodeService> nodeServiceProvider,
                      final Provider<QueryService> dashboardServiceProvider) {
        this.nodeServiceProvider = nodeServiceProvider;
        this.queryServiceProvider = dashboardServiceProvider;
    }

    @Override
    public QueryDoc fetch(final String uuid) {
        return queryServiceProvider.get().read(getDocRef(uuid));
    }

    @Override
    public QueryDoc update(final String uuid, final QueryDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return queryServiceProvider.get().update(doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(QueryDoc.DOCUMENT_TYPE)
                .build();
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public ValidateExpressionResult validateQuery(final String query) {
        return queryServiceProvider.get().validateQuery(query);
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public ResourceGeneration downloadSearchResults(final String nodeName, final DownloadQueryResultsRequest request) {
        try {
            // If the client doesn't specify a node then execute locally.
            if (nodeName == null || nodeName.equals("null")) {
                return queryServiceProvider.get().downloadSearchResults(request);
            }

            return nodeServiceProvider.get()
                    .remoteRestResult(
                            nodeName,
                            ResourceGeneration.class,
                            () -> ResourcePaths.buildAuthenticatedApiPath(
                                    QueryResource.BASE_PATH,
                                    QueryResource.DOWNLOAD_SEARCH_RESULTS_PATH_PATH,
                                    nodeName),
                            () -> queryServiceProvider.get().downloadSearchResults(request),
                            builder -> builder.post(Entity.json(request)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public DashboardSearchResponse search(final String nodeName, final QuerySearchRequest request) {
        try {
            // If the client doesn't specify a node then execute locally.
            if (nodeName == null || nodeName.equals("null")) {
                return queryServiceProvider.get().search(request);
            }

            return nodeServiceProvider.get()
                    .remoteRestResult(
                            nodeName,
                            DashboardSearchResponse.class,
                            () -> ResourcePaths.buildAuthenticatedApiPath(
                                    QueryResource.BASE_PATH,
                                    QueryResource.SEARCH_PATH_PART,
                                    nodeName),
                            () -> queryServiceProvider.get().search(request),
                            builder -> builder.post(Entity.json(request)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public List<String> fetchTimeZones() {
        return queryServiceProvider.get().fetchTimeZones();
    }
}
