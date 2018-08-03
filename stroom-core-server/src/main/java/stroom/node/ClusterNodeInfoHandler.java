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
 *
 */

package stroom.node;

import stroom.cluster.api.ClusterCallService;
import stroom.cluster.api.ClusterNodeManager;
import stroom.entity.util.EntityServiceExceptionUtil;
import stroom.node.shared.ClusterNodeInfo;
import stroom.node.shared.ClusterNodeInfoAction;
import stroom.node.shared.Node;
import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;
import stroom.task.api.TaskHandlerBean;

import javax.inject.Inject;
import javax.inject.Named;

@TaskHandlerBean(task = ClusterNodeInfoAction.class)
class ClusterNodeInfoHandler extends AbstractTaskHandler<ClusterNodeInfoAction, ClusterNodeInfo> {
    private final ClusterCallService clusterCallService;
    private final NodeCache nodeCache;
    private final NodeService nodeService;
    private final Security security;

    @Inject
    ClusterNodeInfoHandler(@Named("clusterCallServiceRemote") final ClusterCallService clusterCallService,
                           final NodeCache nodeCache,
                           final NodeService nodeService,
                           final Security security) {
        this.clusterCallService = clusterCallService;
        this.nodeCache = nodeCache;
        this.nodeService = nodeService;
        this.security = security;
    }

    @Override
    public ClusterNodeInfo exec(final ClusterNodeInfoAction action) {
        return security.secureResult(() -> {
            final Node sourceNode = nodeCache.getDefaultNode();
            final Node targetNode = nodeService.loadById(action.getNodeId());

            try {
                return (ClusterNodeInfo) clusterCallService.call(sourceNode, targetNode, ClusterNodeManager.BEAN_NAME,
                        ClusterNodeManager.GET_CLUSTER_NODE_INFO_METHOD, new Class[]{}, new Object[]{});
            } catch (final RuntimeException e) {
                throw EntityServiceExceptionUtil.create(e);
            }
        });
    }
}
