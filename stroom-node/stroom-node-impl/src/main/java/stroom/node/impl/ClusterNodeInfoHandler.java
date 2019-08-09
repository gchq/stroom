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

package stroom.node.impl;

import stroom.cluster.api.ClusterCallService;
import stroom.cluster.api.ClusterCallServiceRemote;
import stroom.cluster.api.ClusterNodeManager;
import stroom.node.api.NodeInfo;
import stroom.node.shared.ClusterNodeInfo;
import stroom.node.shared.ClusterNodeInfoAction;
import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.EntityServiceExceptionUtil;

import javax.inject.Inject;


class ClusterNodeInfoHandler extends AbstractTaskHandler<ClusterNodeInfoAction, ClusterNodeInfo> {
    private final ClusterCallService clusterCallService;
    private final NodeInfo nodeInfo;
    private final SecurityContext securityContext;

    @Inject
    ClusterNodeInfoHandler(final ClusterCallServiceRemote clusterCallService,
                           final NodeInfo nodeInfo,
                           final SecurityContext securityContext) {
        this.clusterCallService = clusterCallService;
        this.nodeInfo = nodeInfo;
        this.securityContext = securityContext;
    }

    @Override
    public ClusterNodeInfo exec(final ClusterNodeInfoAction action) {
        return securityContext.secureResult(() -> {
            final String sourceNode = nodeInfo.getThisNodeName();
            final String targetNode = action.getNodeName();

            try {
                return (ClusterNodeInfo) clusterCallService.call(sourceNode, targetNode, ClusterNodeManager.SERVICE_NAME,
                        ClusterNodeManager.GET_CLUSTER_NODE_INFO_METHOD, new Class[]{}, new Object[]{});
            } catch (final RuntimeException e) {
                throw EntityServiceExceptionUtil.create(e);
            }
        });
    }
}
