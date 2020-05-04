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

package stroom.cluster.lock.impl.db;

import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.shared.ResourcePaths;

import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

class ClusterLockResourceImpl implements ClusterLockResource {
    private final NodeService nodeService;
    private final NodeInfo nodeInfo;
    private final WebTargetFactory webTargetFactory;
    private final ClusterLockClusterHandler clusterLockClusterHandler;

    @Inject
    ClusterLockResourceImpl(final NodeService nodeService,
                            final NodeInfo nodeInfo,
                            final WebTargetFactory webTargetFactory,
                            final ClusterLockClusterHandler clusterLockClusterHandler) {
        this.nodeService = nodeService;
        this.nodeInfo = nodeInfo;
        this.webTargetFactory = webTargetFactory;
        this.clusterLockClusterHandler = clusterLockClusterHandler;
    }

    @Override
    public Boolean tryLock(final String nodeName, final ClusterLockKey key) {
        // If this is the node that was contacted then call the handler.
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
            return clusterLockClusterHandler.tryLock(key);
        }
        return executeRemotely(ClusterLockResource.TRY_PATH_PART, nodeName, key);
    }

    @Override
    public Boolean releaseLock(final String nodeName, final ClusterLockKey key) {
        // If this is the node that was contacted then call the handler.
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
            return clusterLockClusterHandler.release(key);
        }
        return executeRemotely(ClusterLockResource.RELEASE_PATH_PART, nodeName, key);
    }

    @Override
    public Boolean keepLockAlive(final String nodeName, final ClusterLockKey key) {
        // If this is the node that was contacted then call the handler.
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo, nodeName)) {
            return clusterLockClusterHandler.keepAlive(key);
        }
        return executeRemotely(ClusterLockResource.KEEP_ALIVE_PATH_PART, nodeName, key);
    }

    private Boolean executeRemotely(final String subPath, final String nodeName, final ClusterLockKey key) {
        final String url = NodeCallUtil.getBaseEndpointUrl(nodeService, nodeName)
                + ResourcePaths.buildAuthenticatedApiPath(
                ClusterLockResource.BASE_PATH,
                subPath,
                nodeName);

        try {
            final Response response = webTargetFactory
                    .create(url)
                    .request(MediaType.APPLICATION_JSON)
                    .put(Entity.json(key));
            if (response.getStatus() != 200) {
                throw new WebApplicationException(response);
            }
            return response.readEntity(Boolean.class);
        } catch (Throwable e) {
            throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
        }
    }
}