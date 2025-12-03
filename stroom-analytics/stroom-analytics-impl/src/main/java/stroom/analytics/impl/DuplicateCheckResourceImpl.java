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

package stroom.analytics.impl;

import stroom.analytics.shared.DeleteDuplicateCheckRequest;
import stroom.analytics.shared.DuplicateCheckResource;
import stroom.analytics.shared.DuplicateCheckRows;
import stroom.analytics.shared.FetchColumnNamesResponse;
import stroom.analytics.shared.FindDuplicateCheckCriteria;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
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

import java.util.Collections;

@AutoLogged(OperationType.UNLOGGED)
class DuplicateCheckResourceImpl implements DuplicateCheckResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DuplicateCheckResourceImpl.class);

    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<NodeInfo> nodeInfoProvider;
    private final Provider<WebTargetFactory> webTargetFactoryProvider;
    private final Provider<DuplicateCheckService> duplicateCheckServiceProvider;

    @Inject
    public DuplicateCheckResourceImpl(final Provider<NodeService> nodeServiceProvider,
                                      final Provider<NodeInfo> nodeInfoProvider,
                                      final Provider<WebTargetFactory> webTargetFactoryProvider,
                                      final Provider<DuplicateCheckService> duplicateCheckServiceProvider) {
        this.nodeServiceProvider = nodeServiceProvider;
        this.nodeInfoProvider = nodeInfoProvider;
        this.webTargetFactoryProvider = webTargetFactoryProvider;
        this.duplicateCheckServiceProvider = duplicateCheckServiceProvider;
    }

    @Override
    public DuplicateCheckRows find(final FindDuplicateCheckCriteria criteria) {
        try {
            final DuplicateCheckService duplicateCheckService = duplicateCheckServiceProvider.get();
            final String node = duplicateCheckService.getEnabledNodeName(criteria.getAnalyticDocUuid());
            if (node == null) {
                return new DuplicateCheckRows(Collections.emptyList(), ResultPage.empty());
            }

            // If this is the node that was contacted then just resolve it locally
            if (NodeCallUtil.shouldExecuteLocally(nodeInfoProvider.get(), node)) {
                return duplicateCheckService.find(criteria);
            } else {
                final String url = NodeCallUtil
                                           .getBaseEndpointUrl(nodeInfoProvider.get(), nodeServiceProvider.get(), node)
                                   + ResourcePaths.buildAuthenticatedApiPath(
                        DuplicateCheckResource.BASE_PATH, DuplicateCheckResource.FIND_SUB_PATH);
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

                    return response.readEntity(DuplicateCheckRows.class);
                } catch (final Throwable e) {
                    throw NodeCallUtil.handleExceptionsOnNodeCall(node, url, e);
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("find() - Error searching dup check store: {}",
                    LogUtil.exceptionMessage(e), e));
            throw e;
        }
    }

    @Override
    public Boolean delete(final DeleteDuplicateCheckRequest request) {
        final DuplicateCheckService duplicateCheckService = duplicateCheckServiceProvider.get();
        final String node = duplicateCheckService.getEnabledNodeName(request.getAnalyticDocUuid());
        if (node == null) {
            return false;
        }

        // If this is the node that was contacted then just resolve it locally
        if (NodeCallUtil.shouldExecuteLocally(nodeInfoProvider.get(), node)) {
            return duplicateCheckService.delete(request);
        } else {
            final String url = NodeCallUtil
                                       .getBaseEndpointUrl(nodeInfoProvider.get(), nodeServiceProvider.get(), node)
                               + ResourcePaths.buildAuthenticatedApiPath(
                    DuplicateCheckResource.BASE_PATH, DuplicateCheckResource.DELETE_SUB_PATH);
            try {
                // A different node to make a rest call to the required node
                final WebTarget webTarget = webTargetFactoryProvider.get().create(url);
                final Response response = webTarget
                        .request(MediaType.APPLICATION_JSON)
                        .post(Entity.json(request));
                if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                    throw new NotFoundException(response);
                } else if (response.getStatus() != Status.OK.getStatusCode()) {
                    throw new WebApplicationException(response);
                }

                return response.readEntity(Boolean.class);
            } catch (final Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(node, url, e);
            }
        }
    }

    @Override
    public FetchColumnNamesResponse fetchColumnNames(final String analyticUuid) {
        final DuplicateCheckService duplicateCheckService = duplicateCheckServiceProvider.get();
        final String node = duplicateCheckService.getEnabledNodeName(analyticUuid);
        if (node == null) {
            return FetchColumnNamesResponse.unInitialised();
        }

        // If this is the node that was contacted then just resolve it locally
        if (NodeCallUtil.shouldExecuteLocally(nodeInfoProvider.get(), node)) {
            return duplicateCheckService.fetchColumnNames(analyticUuid)
                    .map(FetchColumnNamesResponse::initialised)
                    .orElseGet(FetchColumnNamesResponse::unInitialised);
        } else {
            final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfoProvider.get(), nodeServiceProvider.get(), node)
                               + ResourcePaths.buildAuthenticatedApiPath(
                    DuplicateCheckResource.BASE_PATH,
                    DuplicateCheckResource.FETCH_COL_NAME_SUB_PATH);
            LOGGER.debug("fetchColumnNames() - analyticRuleDoc: {}, node: {}, url: {}",
                    analyticUuid, node, url);
            try {
                // A different node to make a rest call to the required node
                final WebTarget webTarget = webTargetFactoryProvider.get().create(url);
                final Response response = webTarget
                        .request(MediaType.APPLICATION_JSON)
                        .post(Entity.json(analyticUuid));
                if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                    throw new NotFoundException(response);
                } else if (response.getStatus() != Status.OK.getStatusCode()) {
                    throw new WebApplicationException(response);
                }

                return response.readEntity(FetchColumnNamesResponse.class);
            } catch (final Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(node, url, e);
            }
        }
    }
}
