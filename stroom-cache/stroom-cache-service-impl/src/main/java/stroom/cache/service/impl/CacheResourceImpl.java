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

package stroom.cache.service.impl;

import stroom.cache.shared.CacheInfoResponse;
import stroom.cache.shared.CacheNamesResponse;
import stroom.cache.shared.CacheResource;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.StringCriteria;
import stroom.util.shared.cache.CacheInfo;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@AutoLogged
class CacheResourceImpl implements CacheResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CacheResourceImpl.class);

    private final Provider<NodeService> nodeService;
    private final Provider<NodeInfo> nodeInfo;
    private final Provider<WebTargetFactory> webTargetFactory;
    private final Provider<CacheManagerService> cacheManagerService;
    private final Provider<TaskContextFactory> taskContextFactory;

    @Inject
    CacheResourceImpl(final Provider<NodeService> nodeService,
                      final Provider<NodeInfo> nodeInfo,
                      final Provider<WebTargetFactory> webTargetFactory,
                      final Provider<CacheManagerService> cacheManagerService,
                      final Provider<TaskContextFactory> taskContextFactory) {
        this.nodeService = nodeService;
        this.nodeInfo = nodeInfo;
        this.webTargetFactory = webTargetFactory;
        this.cacheManagerService = cacheManagerService;
        this.taskContextFactory = taskContextFactory;
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public CacheNamesResponse list(final String nodeName) {
        final CacheNamesResponse result;

        // If this is the node that was contacted then just return our local info.
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo.get(), nodeName)) {
            result = new CacheNamesResponse(cacheManagerService.get().getCacheIdentities());
        } else {
            final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo.get(), nodeService.get(), nodeName)
                               + ResourcePaths.buildAuthenticatedApiPath(LIST_PATH);
            try {
                WebTarget webTarget = webTargetFactory.get().create(url);
                webTarget = UriBuilderUtil.addParam(webTarget, "nodeName", nodeName);
                try (final Response response = webTarget
                        .request(MediaType.APPLICATION_JSON)
                        .get()) {
                    if (response.getStatus() != 200) {
                        throw new WebApplicationException(response);
                    }
                    result = response.readEntity(CacheNamesResponse.class);
                }
                if (result == null) {
                    throw new RuntimeException("Unable to contact node \"" + nodeName + "\" at URL: " + url);
                }
            } catch (final Exception e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }

        return result;
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public CacheInfoResponse info(final String cacheName, final String nodeName) {
        final List<CacheInfo> cacheInfoList;
        // If this is the node that was contacted then just return our local info.
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo.get(), nodeName)) {
            final FindCacheInfoCriteria criteria = new FindCacheInfoCriteria();
            criteria.setName(new StringCriteria(cacheName, null));
            cacheInfoList = cacheManagerService.get().find(criteria);
        } else {
            final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo.get(), nodeService.get(), nodeName)
                               + ResourcePaths.buildAuthenticatedApiPath(INFO_PATH);
            try {
                WebTarget webTarget = webTargetFactory.get().create(url);
                webTarget = UriBuilderUtil.addParam(webTarget, "cacheName", cacheName);
                webTarget = UriBuilderUtil.addParam(webTarget, "nodeName", nodeName);
                final CacheInfoResponse result;
                try (final Response response = webTarget
                        .request(MediaType.APPLICATION_JSON)
                        .get()) {
                    if (response.getStatus() != 200) {
                        throw new WebApplicationException(response);
                    }
                    result = response.readEntity(CacheInfoResponse.class);
                }
                if (result == null) {
                    throw new RuntimeException("Unable to contact node \"" + nodeName + "\" at URL: " + url);
                }
                cacheInfoList = result.getValues();
            } catch (final Exception e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }

        // Add the node name.
        final List<CacheInfo> decoratedCacheInfoList = cacheInfoList.stream()
                .map(cacheInfo -> cacheInfo.withNodeName(nodeName))
                .collect(Collectors.toList());

        return new CacheInfoResponse(decoratedCacheInfoList);
    }

    @Override
    @AutoLogged(value = OperationType.PROCESS, verb = "Clearing cache")
    public Long clear(final String cacheName, final String nodeName) {
        final Long result;
        if (nodeName == null) {
            result = clearCacheOnAllNodes(cacheName);
        } else {
            result = clearCache(cacheName, nodeName);
        }
        return result;
    }

    private Long clearCacheOnAllNodes(final String cacheName) {

        final FindNodeCriteria criteria = new FindNodeCriteria();
        criteria.setEnabled(true);
        final List<String> allNodes = nodeService.get().findNodeNames(FindNodeCriteria.allEnabled());

        final Set<String> failedNodes = new ConcurrentSkipListSet<>();
        final AtomicReference<Throwable> exception = new AtomicReference<>();

        return taskContextFactory.get().contextResult(
                LogUtil.message("Clear cache [{}] on all active nodes", cacheName),
                TerminateHandlerFactory.NOOP_FACTORY,
                parentContext -> {
                    final Long count = allNodes.stream()
                            .map(nodeName -> {
                                final Supplier<Long> supplier = taskContextFactory.get()
                                        .childContextResult(
                                                parentContext,
                                                LogUtil.message("Clearing cache [{}] on node [{}]",
                                                        cacheName, nodeName),
                                                TerminateHandlerFactory.NOOP_FACTORY,
                                                taskContext ->
                                                        clearCache(cacheName, nodeName));

                                return CompletableFuture
                                        .supplyAsync(supplier)
                                        .exceptionally(throwable -> {
                                            failedNodes.add(nodeName);
                                            exception.set(throwable);
                                            LOGGER.error(
                                                    "Error clearing cache [{}] on node [{}]: {}. Enable DEBUG for " +
                                                    "stacktrace",
                                                    cacheName,
                                                    nodeName,
                                                    throwable.getMessage());
                                            LOGGER.debug("Error clearing cache [{}] on node [{}]",
                                                    cacheName, nodeName, throwable);
                                            return 0L;
                                        });
                            })
                            .map(CompletableFuture::join)
                            .reduce(Long::sum)
                            .orElse(0L);

                    if (!failedNodes.isEmpty()) {
                        throw new RuntimeException(LogUtil.message(
                                "Error clearing cache on node(s) [{}]. See logs for details",
                                String.join(",", failedNodes)), exception.get());
                    }
                    return count;
                }).get();
    }

    private Long clearCache(final String cacheName, final String nodeName) {
        Objects.requireNonNull(cacheName);
        Objects.requireNonNull(nodeName);

        final Long result;
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo.get(), nodeName)) {
            // local node
            final FindCacheInfoCriteria criteria = new FindCacheInfoCriteria();
            criteria.setName(new StringCriteria(cacheName, null));
            result = cacheManagerService.get().clear(criteria);

        } else {
            final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo.get(), nodeService.get(), nodeName)
                               + ResourcePaths.buildAuthenticatedApiPath(BASE_PATH);

            try {
                WebTarget webTarget = webTargetFactory.get().create(url);
                webTarget = UriBuilderUtil.addParam(webTarget, "cacheName", cacheName);
                webTarget = UriBuilderUtil.addParam(webTarget, "nodeName", nodeName);
                try (final Response response = webTarget
                        .request(MediaType.APPLICATION_JSON)
                        .delete()) {
                    if (response.getStatus() != 200) {
                        throw new WebApplicationException(response);
                    }
                    result = response.readEntity(Long.class);
                }
            } catch (final Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }
        return result;
    }
}
