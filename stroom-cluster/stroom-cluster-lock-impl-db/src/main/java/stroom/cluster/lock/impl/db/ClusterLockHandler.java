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

package stroom.cluster.lock.impl.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.security.api.SecurityContext;

import javax.inject.Inject;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;


class ClusterLockHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterLockHandler.class);

    private final ClusterLockResource clusterLockResource;
    private final TargetNodeSetFactory targetNodeSetFactory;
    private final SecurityContext securityContext;

    @Inject
    ClusterLockHandler(final ClusterLockResource clusterLockResource,
                       final TargetNodeSetFactory targetNodeSetFactory,
                       final SecurityContext securityContext) {
        this.clusterLockResource = clusterLockResource;
        this.targetNodeSetFactory = targetNodeSetFactory;
        this.securityContext = securityContext;
    }

    Boolean tryLock(final ClusterLockKey key) {
        return call(nodeName -> clusterLockResource.tryLock(nodeName, key));
    }

    Boolean releaseLock(final ClusterLockKey key) {
        return call(nodeName -> clusterLockResource.releaseLock(nodeName, key));
    }

    Boolean keepLockAlive(final ClusterLockKey key) {
        return call(nodeName -> clusterLockResource.keepLockAlive(nodeName, key));
    }

    private Boolean call(final Function<String, Boolean> function) {
        final String masterNodeName = getMasterNodeName().orElse(null);
        if (masterNodeName != null) {
            try {
                return securityContext.secureResult(() -> function.apply(masterNodeName));
            } catch (final RuntimeException e) {
                LOGGER.error("Error connecting to master node '" + masterNodeName + "' - " + e.getMessage(), e);
            }
        } else {
            LOGGER.error("No master node can be determined");
        }

        return false;
    }

    private Optional<String> getMasterNodeName() {
        try {
            final Set<String> nodes = targetNodeSetFactory.getMasterTargetNodeSet();
            if (nodes.size() > 0) {
                return Optional.of(nodes.iterator().next());
            }
        } catch (final NullClusterStateException | NodeNotFoundException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return Optional.empty();
    }
}
