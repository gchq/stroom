/*
 * Copyright 2016 Crown Copyright
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

package stroom.task.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.node.shared.Node;
import stroom.properties.api.PropertyService;
import stroom.task.cluster.TargetNodeSetFactory.TargetType;
import stroom.util.shared.ModelStringUtil;
import stroom.docref.SharedObject;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ClusterDispatchAsyncHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterDispatchAsyncHelper.class);

    private static final String CLUSTER_RESPONSE_TIMEOUT = "stroom.clusterResponseTimeout";
    private static final Long ONE_MINUTE = 60000L;
    private static final Long DEFAULT_CLUSTER_RESPONSE_TIMEOUT = ONE_MINUTE;

    private final PropertyService propertyService;
    private final ClusterResultCollectorCache collectorCache;
    private final Provider<ClusterDispatchAsync> dispatchAsyncProvider;
    private final TargetNodeSetFactory targetNodeSetFactory;

    private volatile long lastClusterStateWarn;

    @Inject
    public ClusterDispatchAsyncHelper(final PropertyService propertyService,
                                      final ClusterResultCollectorCache collectorCache,
                                      final Provider<ClusterDispatchAsync> dispatchAsyncProvider,
                                      final TargetNodeSetFactory targetNodeSetFactory) {
        this.propertyService = propertyService;
        this.collectorCache = collectorCache;
        this.dispatchAsyncProvider = dispatchAsyncProvider;
        this.targetNodeSetFactory = targetNodeSetFactory;
    }

    public <R extends SharedObject> DefaultClusterResultCollector<R> execAsync(final ClusterTask<R> task,
                                                                               final Node targetNode) {
        final Long waitTimeMs = getClusterResponseTimeout();
        final Node sourceNode = targetNodeSetFactory.getSourceNode();
        final Set<Node> targetNodes = Collections.singleton(targetNode);
        return execAsync(task, waitTimeMs, TimeUnit.MILLISECONDS, sourceNode, targetNodes);
    }

    public <R extends SharedObject> DefaultClusterResultCollector<R> execAsync(final ClusterTask<R> task,
                                                                               final long waitTime, final TimeUnit timeUnit, final Node targetNode) {
        final Node sourceNode = targetNodeSetFactory.getSourceNode();
        final Set<Node> targetNodes = Collections.singleton(targetNode);
        return execAsync(task, waitTime, timeUnit, sourceNode, targetNodes);
    }

    public <R extends SharedObject> DefaultClusterResultCollector<R> execAsync(final ClusterTask<R> task,
                                                                               final TargetType targetType) {
        final Long waitTimeMs = getClusterResponseTimeout();
        final Node sourceNode = targetNodeSetFactory.getSourceNode();
        final Set<Node> targetNodes = getTargetNodesByType(targetType);
        return execAsync(task, waitTimeMs, TimeUnit.MILLISECONDS, sourceNode, targetNodes);
    }

    public <R extends SharedObject> DefaultClusterResultCollector<R> execAsync(final ClusterTask<R> task,
                                                                               final long waitTime, final TimeUnit timeUnit, final TargetType targetType) {
        final Node sourceNode = targetNodeSetFactory.getSourceNode();
        final Set<Node> targetNodes = getTargetNodesByType(targetType);
        return execAsync(task, waitTime, timeUnit, sourceNode, targetNodes);
    }

    private Set<Node> getTargetNodesByType(final TargetType targetType) {
        Set<Node> targetNodes = Collections.emptySet();
        try {
            targetNodes = targetNodeSetFactory.getTargetNodesByType(targetType);
        } catch (final NullClusterStateException e) {
            final long now = System.currentTimeMillis();
            if (lastClusterStateWarn < now - ONE_MINUTE) {
                lastClusterStateWarn = now;
                LOGGER.warn(e.getMessage());
            }
        } catch (final NodeNotFoundException e) {
            LOGGER.error(e.getMessage());
        }
        return targetNodes;
    }

    private <R extends SharedObject> DefaultClusterResultCollector<R> execAsync(final ClusterTask<R> task,
                                                                                final long waitTime, final TimeUnit timeUnit, final Node sourceNode, final Set<Node> targetNodes) {
        final DefaultClusterResultCollector<R> collector = new DefaultClusterResultCollector<>(task, sourceNode,
                targetNodes);

        try {
            if (targetNodes != null && targetNodes.size() > 0) {
                // Remember the collector until we get all results.
                collectorCache.put(collector.getId(), collector);
                try {
                    dispatchAsyncProvider.get().execAsync(task, collector, sourceNode, targetNodes);
                    collector.waitToComplete(waitTime, timeUnit);
                } catch (final RuntimeException e) {
                    LOGGER.error(e.getMessage(), e);
                } finally {
                    // Forget the collector from the cache.
                    collectorCache.remove(collector.getId());
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return collector;
    }

    private Long getClusterResponseTimeout() {
        Long timeout = DEFAULT_CLUSTER_RESPONSE_TIMEOUT;
        try {
            final Long tmp = ModelStringUtil
                    .parseDurationString(propertyService.getProperty(CLUSTER_RESPONSE_TIMEOUT));
            if (tmp != null) {
                timeout = tmp;
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return timeout;
    }

    public boolean isClusterStateInitialised() {
        boolean initialised = true;
        try {
            targetNodeSetFactory.getClusterState();
        } catch (final NullClusterStateException e) {
            initialised = false;
            final long now = System.currentTimeMillis();
            if (lastClusterStateWarn < now - ONE_MINUTE) {
                lastClusterStateWarn = now;
                LOGGER.warn(e.getMessage());
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return initialised;
    }
}
