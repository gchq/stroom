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

import stroom.event.logging.rs.api.AutoLogged;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.shared.ResourcePaths;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static stroom.event.logging.rs.api.AutoLogged.OperationType.UNLOGGED;

@AutoLogged(UNLOGGED)
class ClusterLockResourceImpl implements ClusterLockResource {

    private final Provider<NodeService> nodeService;
    private final Provider<NodeInfo> nodeInfo;
    private final Provider<WebTargetFactory> webTargetFactory;
    private final Provider<ClusterLockClusterHandler> clusterLockClusterHandler;

    @Inject
    ClusterLockResourceImpl(final Provider<NodeService> nodeService,
                            final Provider<NodeInfo> nodeInfo,
                            final Provider<WebTargetFactory> webTargetFactory,
                            final Provider<ClusterLockClusterHandler> clusterLockClusterHandler) {
        this.nodeService = nodeService;
        this.nodeInfo = nodeInfo;
        this.webTargetFactory = webTargetFactory;
        this.clusterLockClusterHandler = clusterLockClusterHandler;
    }

    @Override
    public Boolean tryLock(final String nodeName, final ClusterLockKey key) {
        // If this is the node that was contacted then call the handler.
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo.get(), nodeName)) {
            return clusterLockClusterHandler.get().tryLock(key);
        }
        return executeRemotely(ClusterLockResource.TRY_PATH_PART, nodeName, key);
    }

    @Override
    public Boolean releaseLock(final String nodeName, final ClusterLockKey key) {
        // If this is the node that was contacted then call the handler.
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo.get(), nodeName)) {
            return clusterLockClusterHandler.get().release(key);
        }
        return executeRemotely(ClusterLockResource.RELEASE_PATH_PART, nodeName, key);
    }

    @Override
    public Boolean keepLockAlive(final String nodeName, final ClusterLockKey key) {
        // If this is the node that was contacted then call the handler.
        if (NodeCallUtil.shouldExecuteLocally(nodeInfo.get(), nodeName)) {
            return clusterLockClusterHandler.get().keepAlive(key);
        }
        return executeRemotely(ClusterLockResource.KEEP_ALIVE_PATH_PART, nodeName, key);
    }

    private Boolean executeRemotely(final String subPath, final String nodeName, final ClusterLockKey key) {
        final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo.get(), nodeService.get(), nodeName)
                + ResourcePaths.buildAuthenticatedApiPath(
                ClusterLockResource.BASE_PATH,
                subPath,
                nodeName);

        try {
            final Response response = webTargetFactory.get()
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
