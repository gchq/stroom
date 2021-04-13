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
import stroom.cache.shared.CacheResource;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeService;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
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
import javax.ws.rs.client.SyncInvoker;

@AutoLogged
class CacheResourceImpl implements CacheResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CacheResourceImpl.class);

    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<CacheManagerService> cacheManagerServiceProvider;
    private final Provider<TaskContextFactory> taskContextFactoryProvider;

    @Inject
    CacheResourceImpl(final Provider<NodeService> nodeServiceProvider,
                      final Provider<CacheManagerService> cacheManagerServiceProvider,
                      final Provider<TaskContextFactory> taskContextFactoryProvider) {
        this.nodeServiceProvider = nodeServiceProvider;
        this.cacheManagerServiceProvider = cacheManagerServiceProvider;
        this.taskContextFactoryProvider = taskContextFactoryProvider;
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public List<String> list() {
        return cacheManagerServiceProvider.get().getCacheNames();
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public CacheInfoResponse info(final String cacheName, final String nodeName) {
        final CacheInfoResponse result = nodeServiceProvider.get()
                .remoteRestResult(
                        nodeName,
                        CacheInfoResponse.class,
                        () -> ResourcePaths.buildAuthenticatedApiPath(
                                CacheResource.INFO_PATH,
                                cacheName,
                                nodeName),
                        () -> {
                            final FindCacheInfoCriteria criteria = new FindCacheInfoCriteria();
                            criteria.setName(new StringCriteria(cacheName, null));
                            final List<CacheInfo> list = cacheManagerServiceProvider.get()
                                    .find(criteria);
                            return new CacheInfoResponse(list);
                        },
                        SyncInvoker::get);

        // Add the node name.
        for (final CacheInfo value : result.getValues()) {
            value.setNodeName(nodeName);
        }

        return result;
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
        final List<String> allNodes = nodeServiceProvider.get().findNodeNames(FindNodeCriteria.allEnabled());

        final Set<String> failedNodes = new ConcurrentSkipListSet<>();
        final AtomicReference<Throwable> exception = new AtomicReference<>();

        return taskContextFactoryProvider.get().contextResult(
                LogUtil.message("Clear cache [{}] on all active nodes", cacheName),
                parentContext -> {
                    final Long count = allNodes.stream()
                            .map(nodeName -> {
                                final Supplier<Long> supplier = taskContextFactoryProvider.get().contextResult(
                                        parentContext,
                                        LogUtil.message("Clearing cache [{}] on node [{}]",
                                                cacheName, nodeName),
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

        final Long result = nodeServiceProvider.get()
                .remoteRestResult(
                        nodeName,
                        Long.class,
                        () -> ResourcePaths.buildAuthenticatedApiPath(
                                CacheResource.BASE_PATH,
                                cacheName,
                                nodeName),
                        () -> {
                            final FindCacheInfoCriteria criteria = new FindCacheInfoCriteria();
                            criteria.setName(new StringCriteria(cacheName, null));
                            return cacheManagerServiceProvider.get().clear(criteria);
                        },
                        SyncInvoker::delete);
        return result;
    }
}
