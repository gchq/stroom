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

package stroom.query.impl;

import stroom.node.api.NodeService;
import stroom.query.api.FindResultStoreCriteria;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultStoreInfo;
import stroom.query.common.v2.HasResultStoreInfo;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.shared.DestroyStoreRequest;
import stroom.query.shared.ResultStoreResource;
import stroom.query.shared.ResultStoreResponse;
import stroom.query.shared.UpdateStoreRequest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.Entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ResultStoreService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ResultStoreService.class);

    private final NodeService nodeService;
    private final ResultStoreManager resultStoreManager;

    private final Set<HasResultStoreInfo> resultStoreInfoSet;

    @Inject
    public ResultStoreService(final ResultStoreManager resultStoreManager,
                              final Set<HasResultStoreInfo> resultStoreInfoSet,
                              final NodeService nodeService) {
        this.resultStoreManager = resultStoreManager;
        this.nodeService = nodeService;
        this.resultStoreInfoSet = resultStoreInfoSet;
    }

    public ResultStoreResponse list(final String nodeName) {
        return find(nodeName, new FindResultStoreCriteria());
    }

    public ResultStoreResponse find(final String nodeName, final FindResultStoreCriteria criteria) {
        try {
            return nodeService
                    .remoteRestResult(
                            nodeName,
                            ResultStoreResponse.class,
                            () -> ResourcePaths.buildAuthenticatedApiPath(
                                    ResultStoreResource.BASE_PATH,
                                    ResultStoreResource.FIND_PATH_PART,
                                    nodeName),
                            () -> {
                                final List<ResultStoreInfo> list = new ArrayList<>();
                                resultStoreInfoSet.forEach(provider -> {
                                    final ResultPage<ResultStoreInfo> resultPage = provider
                                            .find(criteria);
                                    list.addAll(resultPage.getValues());
                                });

                                return new ResultStoreResponse(
                                        list,
                                        Collections.emptyList(),
                                        ResultPage.createPageResponse(list));
                            },
                            builder ->
                                    builder.post(Entity.json(criteria)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            return new ResultStoreResponse(Collections.emptyList(), List.of(e.getMessage()), null);
        }
    }

    public Boolean update(final String nodeName, final UpdateStoreRequest updateStoreRequest) {
        try {
            return nodeService
                    .remoteRestResult(
                            nodeName,
                            Boolean.class,
                            () -> ResourcePaths.buildAuthenticatedApiPath(
                                    ResultStoreResource.BASE_PATH,
                                    ResultStoreResource.UPDATE_PATH_PART,
                                    nodeName),
                            () -> {
                                resultStoreManager.update(updateStoreRequest.getQueryKey(),
                                        updateStoreRequest.getSearchProcessLifespan(),
                                        updateStoreRequest.getStoreLifespan());
                                return true;
                            },
                            builder ->
                                    builder.post(Entity.json(updateStoreRequest)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    public Boolean exists(final String nodeName, final QueryKey queryKey) {
        try {
            return nodeService
                    .remoteRestResult(
                            nodeName,
                            Boolean.class,
                            () -> ResourcePaths.buildAuthenticatedApiPath(
                                    ResultStoreResource.BASE_PATH,
                                    ResultStoreResource.TERMINATE_PATH_PART,
                                    nodeName),
                            () -> resultStoreManager
                                    .terminate(queryKey),
                            builder ->
                                    builder.post(Entity.json(queryKey)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            return false;
        }
    }

    public Boolean terminate(final String nodeName, final QueryKey queryKey) {
        try {
            return nodeService
                    .remoteRestResult(
                            nodeName,
                            Boolean.class,
                            () -> ResourcePaths.buildAuthenticatedApiPath(
                                    ResultStoreResource.BASE_PATH,
                                    ResultStoreResource.TERMINATE_PATH_PART,
                                    nodeName),
                            () -> resultStoreManager
                                    .terminate(queryKey),
                            builder ->
                                    builder.post(Entity.json(queryKey)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            return false;
        }
    }

    public Boolean destroy(final String nodeName, final DestroyStoreRequest request) {
        try {
            return nodeService
                    .remoteRestResult(
                            nodeName,
                            Boolean.class,
                            () -> ResourcePaths.buildAuthenticatedApiPath(
                                    ResultStoreResource.BASE_PATH,
                                    ResultStoreResource.DESTROY_PATH_PART,
                                    nodeName),
                            () -> resultStoreManager
                                    .destroy(request.getQueryKey(), request.getDestroyReason()),
                            builder ->
                                    builder.post(Entity.json(request)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            return false;
        }
    }
}
