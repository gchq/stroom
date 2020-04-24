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

package stroom.cache.impl;

import com.codahale.metrics.health.HealthCheck.Result;
import stroom.cache.shared.CacheInfo;
import stroom.cache.shared.CacheInfoResponse;
import stroom.cache.shared.CacheResource;
import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.task.api.TaskContextFactory;
import stroom.util.HasHealthCheck;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.StringCriteria;

import javax.inject.Inject;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

// TODO : @66 add event logging
class CacheResourceImpl implements CacheResource, HasHealthCheck {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CacheResourceImpl.class);

    private final NodeService nodeService;
    private final NodeInfo nodeInfo;
    private final WebTargetFactory webTargetFactory;
    private final CacheManagerService cacheManagerService;
    private final TaskContextFactory taskContextFactory;

    @Inject
    CacheResourceImpl(final NodeService nodeService,
                      final NodeInfo nodeInfo,
                      final WebTargetFactory webTargetFactory,
                      final CacheManagerService cacheManagerService,
                      final TaskContextFactory taskContextFactory) {
        this.nodeService = nodeService;
        this.nodeInfo = nodeInfo;
        this.webTargetFactory = webTargetFactory;
        this.cacheManagerService = cacheManagerService;
        this.taskContextFactory = taskContextFactory;
    }

    @Override
    public List<String> list() {
        return cacheManagerService.getCacheNames();
    }

    @Override
    public CacheInfoResponse info(final String cacheName, final String nodeName) {
        CacheInfoResponse result;
        // If this is the node that was contacted then just return our local info.
        if (nodeInfo.getThisNodeName().equals(nodeName)) {
            final FindCacheInfoCriteria criteria = new FindCacheInfoCriteria();
            criteria.setName(new StringCriteria(cacheName, null));
            final List<CacheInfo> list = cacheManagerService.find(criteria);
            result = new CacheInfoResponse(list);

        } else {
            final String url = NodeCallUtil.getBaseEndpointUrl(nodeService, nodeName)
                    + ResourcePaths.buildAuthenticatedApiPath(CacheResource.INFO_PATH);
            try {
                final Response response = webTargetFactory
                        .create(url)
                        .queryParam("cacheName", cacheName)
                        .queryParam("nodeName", nodeName)
                        .request(MediaType.APPLICATION_JSON)
                        .get();
                if (response.getStatus() != 200) {
                    throw new WebApplicationException(response);
                }
                result = response.readEntity(CacheInfoResponse.class);
                if (result == null) {
                    throw new RuntimeException("Unable to contact node \"" + nodeName + "\" at URL: " + url);
                }
            } catch (Exception e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }

        // Add the node name.
        for (final CacheInfo value : result.getValues()) {
            value.setNodeName(nodeName);
        }

        return result;
    }

    @Override
    public Long clear(final String cacheName, final String nodeName) {
        final Long result;
        try {
            if (nodeName == null) {
                result = clearCacheOnAllNodes(cacheName);
            } else {
                result = clearCache(cacheName, nodeName);
            }
        } catch (final RuntimeException e) {
            throw new ServerErrorException(Status.INTERNAL_SERVER_ERROR, e);
        }
        return result;
    }

    private Long clearCacheOnAllNodes(final String cacheName) {

        final FindNodeCriteria criteria = new FindNodeCriteria();
        criteria.setEnabled(true);
        final List<String> allNodes = nodeService.findNodeNames(FindNodeCriteria.allEnabled());

        final Set<String> failedNodes = new ConcurrentSkipListSet<>();
        final AtomicReference<Throwable> exception = new AtomicReference<>();

        return taskContextFactory.contextResult(LogUtil.message("Clear cache [{}] on all active nodes", cacheName), parentContext -> {
            final Long count = allNodes.stream()
                    .map(nodeName -> {
                        final Supplier<Long> supplier = taskContextFactory.contextResult(parentContext, LogUtil.message("Clearing cache [{}] on node [{}]",
                                cacheName, nodeName), taskContext ->
                                clearCache(cacheName, nodeName));

                        return CompletableFuture
                                .supplyAsync(supplier)
                                .exceptionally(throwable -> {
                                    failedNodes.add(nodeName);
                                    exception.set(throwable);
                                    LOGGER.error("Error clearing cache [{}] on node [{}]: {}. Enable DEBUG for stacktrace",
                                            cacheName, nodeName, throwable.getMessage());
                                    LOGGER.debug("Error clearing cache [{}] on node [{}]",
                                            cacheName, nodeName, throwable);
                                    return 0L;
                                });
                    })
                    .map(CompletableFuture::join)
                    .reduce(Long::sum)
                    .orElse(0L);

            if (!failedNodes.isEmpty()) {
                throw new ServerErrorException(LogUtil.message(
                        "Error clearing cache on node(s) [{}]. See logs for details",
                        String.join(",", failedNodes)), Status.INTERNAL_SERVER_ERROR, exception.get());
            }
            return count;
        }).get();
    }

    private Long clearCache(final String cacheName, final String nodeName) {
        Objects.requireNonNull(cacheName);
        Objects.requireNonNull(nodeName);

        final Long result;
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
            // local node
            final FindCacheInfoCriteria criteria = new FindCacheInfoCriteria();
            criteria.setName(new StringCriteria(cacheName, null));
            result = cacheManagerService.clear(criteria);

        } else {
            final String url = NodeCallUtil.getBaseEndpointUrl(nodeService, nodeName)
                    + ResourcePaths.buildAuthenticatedApiPath(CacheResource.BASE_PATH);

            try {
                final Response response = webTargetFactory
                        .create(url)
                        .queryParam("cacheName", cacheName)
                        .queryParam("nodeName", nodeName)
                        .request(MediaType.APPLICATION_JSON)
                        .delete();
                if (response.getStatus() != 200) {
                    throw new WebApplicationException(response);
                }
                result = response.readEntity(Long.class);
            } catch (Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }
        return result;
    }


    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}