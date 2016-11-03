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

package stroom.node.server;

import javax.annotation.Resource;

import stroom.cluster.server.ClusterCallService;
import stroom.cluster.server.ClusterNodeManager;
import stroom.entity.server.util.EntityServiceExceptionUtil;
import stroom.node.shared.ClusterNodeInfo;
import stroom.node.shared.ClusterNodeInfoAction;
import stroom.node.shared.Node;
import stroom.node.shared.NodeService;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;

@TaskHandlerBean(task = ClusterNodeInfoAction.class)
public class ClusterNodeInfoHandler extends AbstractTaskHandler<ClusterNodeInfoAction, ClusterNodeInfo> {
    @Resource(name = "clusterCallServiceRemote")
    private ClusterCallService clusterCallService;
    @Resource
    private NodeCache nodeCache;
    @Resource
    private NodeService nodeService;

    @Override
    public ClusterNodeInfo exec(final ClusterNodeInfoAction action) {
        final Node sourceNode = nodeCache.getDefaultNode();
        final Node targetNode = nodeService.loadById(action.getNodeId());

        try {
            return (ClusterNodeInfo) clusterCallService.call(sourceNode, targetNode, ClusterNodeManager.BEAN_NAME,
                    ClusterNodeManager.GET_CLUSTER_NODE_INFO_METHOD, new Class[] {}, new Object[] {});
        } catch (final Throwable e) {
            throw EntityServiceExceptionUtil.create(e);
        }
    }
}
