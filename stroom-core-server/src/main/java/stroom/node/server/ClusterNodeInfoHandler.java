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

package stroom.node.server;

import org.springframework.context.annotation.Scope;
import stroom.cluster.server.ClusterCallService;
import stroom.cluster.server.ClusterNodeManager;
import stroom.entity.server.util.EntityServiceExceptionUtil;
import stroom.node.shared.ClusterNodeInfo;
import stroom.node.shared.ClusterNodeInfoAction;
import stroom.node.shared.Node;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import javax.inject.Named;

@TaskHandlerBean(task = ClusterNodeInfoAction.class)
@Scope(value = StroomScope.TASK)
public class ClusterNodeInfoHandler extends AbstractTaskHandler<ClusterNodeInfoAction, ClusterNodeInfo> {
    private final ClusterCallService clusterCallService;
    private final NodeCache nodeCache;
    private final NodeService nodeService;

    @Inject
    ClusterNodeInfoHandler(@Named("clusterCallServiceRemote") final ClusterCallService clusterCallService, final NodeCache nodeCache, final NodeService nodeService) {
        this.clusterCallService = clusterCallService;
        this.nodeCache = nodeCache;
        this.nodeService = nodeService;
    }

    @Override
    public ClusterNodeInfo exec(final ClusterNodeInfoAction action) {
        final Node sourceNode = nodeCache.getDefaultNode();
        final Node targetNode = nodeService.loadById(action.getNodeId());

        try {
            return (ClusterNodeInfo) clusterCallService.call(sourceNode, targetNode, ClusterNodeManager.BEAN_NAME,
                    ClusterNodeManager.GET_CLUSTER_NODE_INFO_METHOD, new Class[]{}, new Object[]{});
        } catch (final Throwable e) {
            throw EntityServiceExceptionUtil.create(e);
        }
    }
}
