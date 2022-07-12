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

import stroom.cache.shared.CacheInfo;
import stroom.cache.shared.CacheInfoResponse;
import stroom.cache.shared.CacheNamesResponse;
import stroom.cache.shared.CacheResource;
import stroom.cluster.api.ClusterMember;
import stroom.cluster.api.ClusterService;
import stroom.cluster.api.EndpointUrlService;
import stroom.cluster.api.RemoteRestUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.rest.RestUtil;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.StringCriteria;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@AutoLogged
class CacheResourceImpl implements CacheResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CacheResourceImpl.class);

    private final Provider<EndpointUrlService> endpointUrlServiceProvider;
    private final Provider<WebTargetFactory> webTargetFactory;
    private final Provider<CacheManagerService> cacheManagerService;
    private final Provider<TaskContextFactory> taskContextFactory;
    private final Provider<ClusterService> clusterServiceProvider;

    @Inject
    CacheResourceImpl(final Provider<EndpointUrlService> endpointUrlServiceProvider,
                      final Provider<WebTargetFactory> webTargetFactory,
                      final Provider<CacheManagerService> cacheManagerService,
                      final Provider<TaskContextFactory> taskContextFactory,
                      final Provider<ClusterService> clusterServiceProvider) {
        this.endpointUrlServiceProvider = endpointUrlServiceProvider;
        this.webTargetFactory = webTargetFactory;
        this.cacheManagerService = cacheManagerService;
        this.taskContextFactory = taskContextFactory;
        this.clusterServiceProvider = clusterServiceProvider;
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public CacheNamesResponse list(final String memberUuid) {
        RestUtil.requireNonNull(memberUuid, "memberUuid not supplied");
        final ClusterMember member = new ClusterMember(memberUuid);

        CacheNamesResponse result;

        // If this is the node that was contacted then just return our local info.
        final EndpointUrlService endpointUrlService = endpointUrlServiceProvider.get();
        if (endpointUrlService.shouldExecuteLocally(member)) {
            result = new CacheNamesResponse(cacheManagerService.get().getCacheNames());
        } else {
            final String url = endpointUrlService.getRemoteEndpointUrl(member)
                    + ResourcePaths.buildAuthenticatedApiPath(CacheResource.LIST_PATH);
            try {
                WebTarget webTarget = webTargetFactory.get().create(url);
                webTarget = UriBuilderUtil.addParam(webTarget, "memberUuid", memberUuid);
                final Response response = webTarget
                        .request(MediaType.APPLICATION_JSON)
                        .get();
                if (response.getStatus() != 200) {
                    throw new WebApplicationException(response);
                }
                result = response.readEntity(CacheNamesResponse.class);
                if (result == null) {
                    throw new RuntimeException("Unable to contact member \"" + member + "\" at URL: " + url);
                }
            } catch (Exception e) {
                throw RemoteRestUtil.handleExceptions(member, url, e);
            }
        }

        return result;
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public CacheInfoResponse info(final String cacheName, final String memberUuid) {
        RestUtil.requireNonNull(memberUuid, "memberUuid not supplied");
        final ClusterMember member = new ClusterMember(memberUuid);
        CacheInfoResponse result;
        // If this is the node that was contacted then just return our local info.
        final EndpointUrlService endpointUrlService = endpointUrlServiceProvider.get();
        if (endpointUrlService.shouldExecuteLocally(member)) {
            final FindCacheInfoCriteria criteria = new FindCacheInfoCriteria();
            criteria.setName(new StringCriteria(cacheName, null));
            final List<CacheInfo> list = cacheManagerService.get().find(criteria);
            result = new CacheInfoResponse(list);

        } else {
            final String url = endpointUrlService.getRemoteEndpointUrl(member)
                    + ResourcePaths.buildAuthenticatedApiPath(CacheResource.INFO_PATH);
            try {
                WebTarget webTarget = webTargetFactory.get().create(url);
                webTarget = UriBuilderUtil.addParam(webTarget, "cacheName", cacheName);
                webTarget = UriBuilderUtil.addParam(webTarget, "memberUuid", memberUuid);
                final Response response = webTarget
                        .request(MediaType.APPLICATION_JSON)
                        .get();
                if (response.getStatus() != 200) {
                    throw new WebApplicationException(response);
                }
                result = response.readEntity(CacheInfoResponse.class);
                if (result == null) {
                    throw new RuntimeException("Unable to contact \"" + member + "\" at URL: " + url);
                }
            } catch (Exception e) {
                throw RemoteRestUtil.handleExceptions(member, url, e);
            }
        }

        // Add the node name.
        for (final CacheInfo value : result.getValues()) {
            value.setNodeName(memberUuid);
        }

        return result;
    }

    @Override
    @AutoLogged(value = OperationType.PROCESS, verb = "Clearing cache")
    public Long clear(final String cacheName, final String memberUuid) {
        final Long result;
        if (memberUuid == null) {
            result = clearCacheOnAllNodes(cacheName);
        } else {
            final ClusterMember member = new ClusterMember(memberUuid);
            result = clearCache(cacheName, member);
        }
        return result;
    }

    private Long clearCacheOnAllNodes(final String cacheName) {
        final Set<ClusterMember> allNodes = clusterServiceProvider.get().getMembers();

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
                                            failedNodes.add(nodeName.toString());
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

    private Long clearCache(final String cacheName, final ClusterMember member) {
        Objects.requireNonNull(cacheName);
        Objects.requireNonNull(member);

        final Long result;
        final EndpointUrlService endpointUrlService = endpointUrlServiceProvider.get();
        if (endpointUrlService.shouldExecuteLocally(member)) {
            // local node
            final FindCacheInfoCriteria criteria = new FindCacheInfoCriteria();
            criteria.setName(new StringCriteria(cacheName, null));
            result = cacheManagerService.get().clear(criteria);

        } else {
            final String url = endpointUrlService.getRemoteEndpointUrl(member)
                    + ResourcePaths.buildAuthenticatedApiPath(CacheResource.BASE_PATH);

            try {
                WebTarget webTarget = webTargetFactory.get().create(url);
                webTarget = UriBuilderUtil.addParam(webTarget, "cacheName", cacheName);
                webTarget = UriBuilderUtil.addParam(webTarget, "memberUuid", member.getUuid());
                final Response response = webTarget
                        .request(MediaType.APPLICATION_JSON)
                        .delete();
                if (response.getStatus() != 200) {
                    throw new WebApplicationException(response);
                }
                result = response.readEntity(Long.class);
            } catch (Throwable e) {
                throw RemoteRestUtil.handleExceptions(member, url, e);
            }
        }
        return result;
    }
}
