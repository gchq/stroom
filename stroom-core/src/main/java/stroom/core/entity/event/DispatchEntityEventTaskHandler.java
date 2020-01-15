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

package stroom.core.entity.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.task.api.ClusterDispatchAsyncHelper;
import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;
import java.util.Set;

class DispatchEntityEventTaskHandler extends AbstractTaskHandler<DispatchEntityEventTask, VoidResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DispatchEntityEventTaskHandler.class);

    private final ClusterDispatchAsyncHelper dispatchHelper;
    private final TargetNodeSetFactory targetNodeSetFactory;
    private final SecurityContext securityContext;

    @Inject
    DispatchEntityEventTaskHandler(final ClusterDispatchAsyncHelper dispatchHelper,
                                   final TargetNodeSetFactory targetNodeSetFactory,
                                   final SecurityContext securityContext) {
        this.dispatchHelper = dispatchHelper;
        this.targetNodeSetFactory = targetNodeSetFactory;
        this.securityContext = securityContext;
    }

    @Override
    public VoidResult exec(final DispatchEntityEventTask task) {
        return securityContext.secureResult(() -> {
            try {
                // Get this node.
                final String sourceNode = targetNodeSetFactory.getSourceNode();

                // Get the nodes that we are going to send the entity event to.
                final Set<String> targetNodes = targetNodeSetFactory.getEnabledActiveTargetNodeSet();

                // Only send the event to remote nodes and not this one.
                targetNodes.stream().filter(targetNode -> !targetNode.equals(sourceNode)).forEach(targetNode -> {
                    // Send the entity event.
                    final ClusterEntityEventTask clusterEntityEventTask = new ClusterEntityEventTask(task.getEntityEvent());
                    dispatchHelper.execAsync(clusterEntityEventTask, targetNode);
                });
            } catch (final NullClusterStateException | NodeNotFoundException e) {
                LOGGER.warn(e.getMessage());
                LOGGER.debug(e.getMessage(), e);
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
            return VoidResult.INSTANCE;
        });
    }
}
